package com.meedwire.pdfapi.document

import android.content.Context
import android.net.Uri
import com.meedwire.pdfapi.support.PdfException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal data class PdfCachedDownload(val file: File, val fromCache: Boolean)

/**
 * Downloads a remote (`http`/`https`) PDF into the package cache and returns
 * `{ uri, fromCache }`. Local URIs are returned unchanged. Runs synchronously;
 * callers already dispatch it onto a background thread.
 *
 * The document is cached under [pdfCacheIdentity] and stored as
 * [pdfDisplayFileName] (see [PdfCacheConfig]).
 */
internal fun preparePdfSource(
  context: Context,
  uri: String,
  headersJson: String,
  fileName: String,
  cacheKey: String
): Map<String, Any> {
  if (!isRemotePdfUri(uri)) {
    return mapOf("uri" to uri, "fromCache" to false)
  }

  val download = cachePdfDownload(
    pdfCacheDirectory(context),
    uri,
    headersJson,
    fileName,
    cacheKey,
    PdfCacheRegistry.shared
  )

  return mapOf("uri" to Uri.fromFile(download.file).toString(), "fromCache" to download.fromCache)
}

internal fun isRemotePdfUri(uri: String): Boolean =
  uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)

/** [preparePdfSource] for a remote URI against an explicit cache root. */
internal fun cachePdfDownload(
  root: File,
  uri: String,
  headersJson: String,
  fileName: String,
  cacheKey: String,
  registry: PdfCacheRegistry
): PdfCachedDownload {
  val url = try {
    URL(uri)
  } catch (error: Exception) {
    throw PdfException("ERR_PDF_SOURCE", "Invalid PDF uri: $uri", error)
  }

  val entryName = pdfCacheEntryName(pdfCacheIdentity(uri, cacheKey))
  // Pin before the sweep (started here, once per process) can see the entry,
  // so it never deletes what this call returns.
  registry.pin(entryName)
  registry.sweepOnce(root)

  val destination = File(File(root, entryName), pdfDisplayFileName(uri, fileName))
  findCachedPdf(destination)?.let { cached ->
    return PdfCachedDownload(cached, fromCache = true)
  }

  downloadPdf(url, headersJson, destination, registry)
  return PdfCachedDownload(destination, fromCache = false)
}

/**
 * Streams [url] into a temporary file next to [destination] and renames it into
 * place only when the response is a 2xx, complete (`Content-Length`) and looks
 * like a PDF. On failure nothing is left in the cache.
 */
private fun downloadPdf(
  url: URL,
  headersJson: String,
  destination: File,
  registry: PdfCacheRegistry
) {
  val entry = destination.parentFile
    ?: throw PdfException("ERR_PDF_SOURCE", "Invalid PDF cache location.")
  var connection: HttpURLConnection? = null
  var temp: File? = null

  try {
    val openedConnection = (url.openConnection() as HttpURLConnection).apply {
      connectTimeout = PdfCacheConfig.DOWNLOAD_TIMEOUT_MS
      readTimeout = PdfCacheConfig.DOWNLOAD_TIMEOUT_MS
      parseHeaders(headersJson).forEach { (key, value) -> setRequestProperty(key, value) }
    }
    connection = openedConnection
    openedConnection.connect()

    val status = openedConnection.responseCode
    if (status !in 200..299) {
      throw PdfException("ERR_PDF_SOURCE", "Failed to download PDF (HTTP $status).")
    }

    val part = registry.createDownloadTemp(entry)
    temp = part

    val expectedLength = openedConnection.contentLengthLong
    val receivedLength = openedConnection.inputStream.use { input ->
      FileOutputStream(part).use { output ->
        input.copyTo(output).also { output.fd.sync() }
      }
    }

    if (expectedLength >= 0 && receivedLength != expectedLength) {
      throw PdfException(
        "ERR_PDF_SOURCE",
        "Incomplete PDF download ($receivedLength of $expectedLength bytes)."
      )
    }

    if (!fileHasPdfHeader(part)) {
      throw PdfException("ERR_PDF_SOURCE", "Downloaded content is not a PDF.")
    }

    // Same directory, so the rename is atomic: readers never see a partial file.
    if (!part.renameTo(destination)) {
      throw PdfException("ERR_PDF_SOURCE", "Unable to cache the downloaded PDF.")
    }
  } catch (error: PdfException) {
    throw error
  } catch (error: Throwable) {
    throw PdfException("ERR_PDF_SOURCE", error.message ?: "Failed to download PDF.", error)
  } finally {
    // No-op after a successful rename.
    temp?.delete()
    connection?.disconnect()
    // A failed first download must not leave its entry behind. Only an empty
    // directory is removed, under the registry lock, so a concurrent download
    // of the same identity keeps its directory.
    if (!destination.exists()) {
      registry.deleteEntryIfEmpty(entry)
    }
  }
}

private fun parseHeaders(headersJson: String): Map<String, String> {
  if (headersJson.isEmpty()) return emptyMap()

  return runCatching {
    val json = JSONObject(headersJson)
    buildMap {
      json.keys().forEach { key -> put(key, json.getString(key)) }
    }
  }.getOrDefault(emptyMap())
}
