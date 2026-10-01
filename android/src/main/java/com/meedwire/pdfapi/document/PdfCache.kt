package com.meedwire.pdfapi.document

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Every tunable of the on-disk cache. Layout under `Context.cacheDir`:
 *
 * ```
 * react-native-pdf-api/
 *   <sha256(identity)>/<display name>.pdf   one entry per remote document
 *   <sha256(identity)>/download-*.part      a download in progress
 *   tmp/source-<uuid>.pdf                    content:// copies, deleted on close
 *   renders/                                 renderPageAsync output
 * ```
 *
 * The identity of a remote source is its `cacheKey` when non-empty, otherwise
 * its full URI (query string included). The display name (`fileName`, else the
 * URI's last path segment) only names the file inside the entry, so an entry
 * can hold the same document under several names.
 */
internal object PdfCacheConfig {
  const val DIRECTORY_NAME = "react-native-pdf-api"
  const val TEMP_DIRECTORY_NAME = "tmp"
  const val RENDERS_DIRECTORY_NAME = "renders"

  /** Entries not used for this long, and rendered pages older than this, are deleted by the sweep. */
  const val MAX_ENTRY_AGE_MS = 7L * 24 * 60 * 60 * 1000

  /** Size budget for all entries. Above it the sweep deletes the least recently used entries. */
  const val MAX_TOTAL_SIZE_BYTES = 200L * 1024 * 1024

  /** Temporary files (`*.part` downloads, `tmp/` copies) older than this are deleted by the sweep. */
  const val MAX_TEMP_AGE_MS = 60L * 60 * 1000

  /** A download is accepted only if `%PDF-` appears within this many leading bytes. */
  const val HEADER_WINDOW_BYTES = 1024

  /** Longest stem (the part before `.pdf`) kept in a display name. */
  const val MAX_STEM_LENGTH = 100

  /** Connect and per-read (idle) timeout of a download, not a total limit. */
  const val DOWNLOAD_TIMEOUT_MS = 30_000
}

internal fun pdfCacheDirectory(context: Context): File {
  return File(context.cacheDir, PdfCacheConfig.DIRECTORY_NAME).also { directory ->
    directory.mkdirs()
  }
}

internal fun pdfCacheSubdirectory(context: Context, name: String): File {
  return File(pdfCacheDirectory(context), name).also { directory ->
    directory.mkdirs()
  }
}

// MARK: - Identity and naming

/**
 * The cache identity of a remote source: [cacheKey] when non-empty, otherwise
 * the full [uri]. The file name is deliberately not part of it.
 */
internal fun pdfCacheIdentity(uri: String, cacheKey: String): String = cacheKey.ifEmpty { uri }

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/** Directory name of the entry for [identity]: its SHA-256 as 64 lowercase hex characters. */
internal fun pdfCacheEntryName(identity: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
  val hex = CharArray(digest.size * 2)
  digest.forEachIndexed { index, byte ->
    val value = byte.toInt() and 0xff
    hex[index * 2] = HEX_DIGITS[value ushr 4]
    hex[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
  }
  return String(hex)
}

internal fun isPdfCacheEntryName(name: String): Boolean =
  name.length == 64 && name.all { it in '0'..'9' || it in 'a'..'f' }

/**
 * Name of the cached file: [fileName], else the URI's last path segment,
 * restricted to `[A-Za-z0-9._-]`, with a stem of at most
 * [PdfCacheConfig.MAX_STEM_LENGTH] characters (`document` when nothing usable
 * is left) and always ending in `.pdf`.
 */
internal fun pdfDisplayFileName(uri: String, fileName: String): String {
  var stem = sanitizeFileName(fileName.ifEmpty { lastPathSegment(uri) })

  if (stem.endsWith(".pdf", ignoreCase = true)) {
    stem = stem.dropLast(4)
  }

  stem = stem.take(PdfCacheConfig.MAX_STEM_LENGTH)

  if (stem.all { it == '.' || it == '_' }) {
    stem = "document"
  }

  return "$stem.pdf"
}

/**
 * Percent-decoded last non-empty segment of the URI's path, or "" when there
 * is none. The URI is parsed as a URL, so a `/` inside the query or fragment
 * never counts.
 */
private fun lastPathSegment(uri: String): String {
  val path = runCatching { URL(uri).path }.getOrNull() ?: return ""
  val segment = path.split('/').lastOrNull { it.isNotEmpty() } ?: return ""

  return runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
}

private val UNSAFE_FILE_NAME_CHARACTERS = Regex("[^a-zA-Z0-9._-]")

private fun sanitizeFileName(value: String): String = value.replace(UNSAFE_FILE_NAME_CHARACTERS, "_")

// MARK: - Integrity

private val PDF_HEADER_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

/**
 * Whether the first [length] bytes of [bytes] look like a PDF: `%PDF-` within
 * the first [PdfCacheConfig.HEADER_WINDOW_BYTES] bytes (the format tolerates a
 * little junk before the header). An HTML error or captive-portal page served
 * with HTTP 200 fails this check.
 */
internal fun hasPdfHeader(bytes: ByteArray, length: Int = bytes.size): Boolean {
  val lastStart = minOf(length, bytes.size, PdfCacheConfig.HEADER_WINDOW_BYTES) - PDF_HEADER_MAGIC.size

  for (start in 0..lastStart) {
    if (PDF_HEADER_MAGIC.indices.all { bytes[start + it] == PDF_HEADER_MAGIC[it] }) {
      return true
    }
  }

  return false
}

internal fun fileHasPdfHeader(file: File): Boolean {
  val buffer = ByteArray(PdfCacheConfig.HEADER_WINDOW_BYTES)
  var length = 0

  FileInputStream(file).use { input ->
    while (length < buffer.size) {
      val read = input.read(buffer, length, buffer.size - length)
      if (read < 0) break
      length += read
    }
  }

  return hasPdfHeader(buffer, length)
}

// MARK: - Lookup

/**
 * The cached document for [destination] (`<entry>/<display name>`), or null on
 * a miss. When the entry holds the document under another name only, that
 * file is hard linked (copied as a fallback) to the requested name. A hit
 * marks the entry as recently used for the sweep.
 */
internal fun findCachedPdf(destination: File): File? {
  val entry = destination.parentFile ?: return null
  var cached = destination

  if (!destination.exists()) {
    val existing = entry.listFiles()
      ?.firstOrNull { file -> file.isFile && file.name.endsWith(".pdf") }
      ?: return null

    if (!linkOrCopy(existing, destination)) {
      // The right document under another name beats failing the request.
      cached = existing
    }
  }

  entry.setLastModified(System.currentTimeMillis())
  return cached
}

private fun linkOrCopy(source: File, destination: File): Boolean {
  try {
    Os.link(source.path, destination.path)
    return true
  } catch (_: Exception) {
    // Most devices deny link(2) to apps (SELinux); fall back to a copy.
  }

  if (destination.exists()) return true

  var temp: File? = null
  return try {
    val copy = File.createTempFile("copy-", ".part", destination.parentFile)
    temp = copy
    source.copyTo(copy, overwrite = true)
    // Renamed into place so a reader never sees a partial copy.
    copy.renameTo(destination)
  } catch (_: Exception) {
    destination.exists()
  } finally {
    temp?.delete()
  }
}

// MARK: - Sweep

/** One child of the cache root, as the sweep sees it. */
internal data class PdfCacheItem(
  val name: String,
  val isDirectory: Boolean,
  val lastModified: Long,
  /** Bytes used on disk; only meaningful for entries. */
  val size: Long
)

internal data class PdfCacheSweepPlan(
  /** Regular files directly under the root: the 0.1.x flat cache layout. */
  val legacyFiles: List<String> = emptyList(),
  /** Entries not used within the maximum age. */
  val expiredEntries: List<String> = emptyList(),
  /** Least recently used entries deleted to get back under the size budget. */
  val evictedEntries: List<String> = emptyList()
)

/**
 * Decides what the sweep deletes at the cache root. Pinned entries (used by
 * this process) are never selected, but still count towards the size budget.
 * Directories that are not entries (`tmp/`, `renders/`) are left alone here.
 */
internal fun planPdfCacheSweep(
  items: List<PdfCacheItem>,
  now: Long,
  maxEntryAgeMs: Long = PdfCacheConfig.MAX_ENTRY_AGE_MS,
  maxTotalSizeBytes: Long = PdfCacheConfig.MAX_TOTAL_SIZE_BYTES,
  isPinned: (String) -> Boolean
): PdfCacheSweepPlan {
  val legacyFiles = mutableListOf<String>()
  val expiredEntries = mutableListOf<String>()
  val liveEntries = mutableListOf<PdfCacheItem>()

  for (item in items) {
    when {
      !item.isDirectory -> legacyFiles.add(item.name)
      !isPdfCacheEntryName(item.name) -> Unit
      now - item.lastModified > maxEntryAgeMs && !isPinned(item.name) -> expiredEntries.add(item.name)
      else -> liveEntries.add(item)
    }
  }

  var totalSize = liveEntries.sumOf { it.size }
  val evictedEntries = mutableListOf<String>()
  for (entry in liveEntries.sortedWith(compareBy({ it.lastModified }, { it.name }))) {
    if (totalSize <= maxTotalSizeBytes) break
    if (isPinned(entry.name)) continue

    evictedEntries.add(entry.name)
    totalSize -= entry.size
  }

  return PdfCacheSweepPlan(legacyFiles, expiredEntries, evictedEntries)
}

/**
 * Applies the cache policy to [root]: removes 0.1.x flat files, `*.part` and
 * `tmp/` leftovers, rendered pages older than [maxEntryAgeMs], expired entries
 * and, above [maxTotalSizeBytes], the least recently used entries. Best
 * effort: it skips anything it cannot read or delete.
 */
internal fun sweepPdfCache(
  root: File,
  registry: PdfCacheRegistry,
  now: Long = System.currentTimeMillis(),
  maxEntryAgeMs: Long = PdfCacheConfig.MAX_ENTRY_AGE_MS,
  maxTotalSizeBytes: Long = PdfCacheConfig.MAX_TOTAL_SIZE_BYTES
) {
  val children = root.listFiles() ?: return
  val items = mutableListOf<PdfCacheItem>()

  for (child in children) {
    val isDirectory = child.isDirectory
    // Read before removing leftovers below, which bumps a directory's mtime.
    val lastModified = child.lastModified()

    when {
      isDirectory && child.name == PdfCacheConfig.TEMP_DIRECTORY_NAME ->
        deleteFilesModifiedBefore(child, now - PdfCacheConfig.MAX_TEMP_AGE_MS)

      isDirectory && child.name == PdfCacheConfig.RENDERS_DIRECTORY_NAME ->
        deleteFilesModifiedBefore(child, now - maxEntryAgeMs)

      isDirectory && isPdfCacheEntryName(child.name) -> {
        deleteFilesModifiedBefore(child, now - PdfCacheConfig.MAX_TEMP_AGE_MS) { file ->
          file.name.endsWith(".pdf")
        }
        items.add(PdfCacheItem(child.name, isDirectory = true, lastModified, entrySize(child)))
      }

      !isDirectory -> items.add(PdfCacheItem(child.name, isDirectory = false, lastModified, 0L))
    }
  }

  val plan = planPdfCacheSweep(items, now, maxEntryAgeMs, maxTotalSizeBytes, registry::isPinned)

  plan.legacyFiles.forEach { name -> File(root, name).delete() }
  (plan.expiredEntries + plan.evictedEntries).forEach { name ->
    registry.deleteIfNotPinned(File(root, name))
  }
}

private fun deleteFilesModifiedBefore(
  directory: File,
  cutoff: Long,
  keep: (File) -> Boolean = { false }
) {
  directory.listFiles()?.forEach { file ->
    if (!keep(file) && file.lastModified() < cutoff) {
      file.deleteRecursively()
    }
  }
}

/**
 * Bytes used by an entry. Every name is counted: apps cannot hard link on most
 * devices, so extra names are real copies.
 */
private fun entrySize(entry: File): Long = entry.listFiles()?.sumOf { it.length() } ?: 0L

// MARK: - Process state

/**
 * Per-process cache state: the entries this process has used, which the sweep
 * never deletes, and whether the sweep has already been started.
 */
internal class PdfCacheRegistry {
  private val lock = Any()
  private val pinnedEntries = HashSet<String>()
  private val sweepStarted = AtomicBoolean(false)

  /**
   * Protects an entry from the sweep for the rest of the process. Call it
   * before looking the entry up, so a concurrent sweep cannot delete a file
   * that is about to be returned.
   */
  fun pin(entryName: String) {
    synchronized(lock) { pinnedEntries.add(entryName) }
  }

  fun isPinned(entryName: String): Boolean = synchronized(lock) { entryName in pinnedEntries }

  /** Deletes [entry] unless it is pinned, atomically with respect to [pin]. */
  fun deleteIfNotPinned(entry: File) {
    synchronized(lock) {
      if (entry.name !in pinnedEntries) {
        entry.deleteRecursively()
      }
    }
  }

  /**
   * Creates [entry] if needed and a download temp file inside it. Serialized
   * with [deleteEntryIfEmpty], so a failed download of the same identity cannot
   * remove the directory between the two steps.
   */
  fun createDownloadTemp(entry: File): File = synchronized(lock) {
    entry.mkdirs()
    File.createTempFile("download-", ".part", entry)
  }

  /** Removes [entry] only when it is an empty directory. */
  fun deleteEntryIfEmpty(entry: File) {
    synchronized(lock) { entry.delete() }
  }

  /**
   * Sweeps [root] on a background thread the first time it is called in this
   * process; later calls return immediately. Never blocks or fails the caller.
   */
  fun sweepOnce(root: File) {
    if (!sweepStarted.compareAndSet(false, true)) return

    try {
      thread(name = "RNPdfApiCacheSweep", isDaemon = true) {
        runCatching { sweepPdfCache(root, this@PdfCacheRegistry) }
      }
    } catch (_: Throwable) {
      // The sweep is best effort.
    }
  }

  companion object {
    val shared = PdfCacheRegistry()
  }
}
