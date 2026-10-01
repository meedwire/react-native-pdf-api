import CryptoKit
import Foundation

// Foundation/CryptoKit only (no UIKit/PDFKit/React), so the cache logic can be
// compiled and exercised on its own.

// MARK: - Policy

/// Every tunable of the on-disk cache. Layout under the app's Caches directory:
///
///     react-native-pdf-api/
///       <sha256(identity)>/<display name>.pdf   one entry per remote document
///       renders/                                  renderPageAsync output
///
/// The identity of a remote source is its `cacheKey` when non-empty, otherwise
/// its full URI (query string included). The display name (`fileName`, else the
/// URI's last path segment) only names the file inside the entry, so an entry
/// can hold the same document under several names (hard links).
internal enum PdfCacheConfig {
  static let directoryName = "react-native-pdf-api"
  static let rendersDirectoryName = "renders"
  /// Entries not used for this long, and rendered pages older than this, are
  /// deleted by the sweep.
  static let maxEntryAge: TimeInterval = 7 * 24 * 60 * 60
  /// Size budget for all entries. Above it the sweep deletes the least recently
  /// used entries until the rest fits.
  static let maxTotalSize: Int64 = 200 * 1024 * 1024
  /// Leftovers inside an entry (e.g. an interrupted atomic write) older than
  /// this are deleted by the sweep.
  static let maxTempAge: TimeInterval = 60 * 60
  /// A download is accepted only if `%PDF-` appears within this many leading
  /// bytes.
  static let headerWindow = 1024
  /// Longest stem (the part before `.pdf`) kept in a display name.
  static let maxStemLength = 100
  /// Download idle timeout (no data received), in seconds.
  static let downloadTimeout: TimeInterval = 30
}

internal func pdfCacheDirectory() throws -> URL {
  let directory = FileManager.default
    .urls(for: .cachesDirectory, in: .userDomainMask)[0]
    .appendingPathComponent(PdfCacheConfig.directoryName, isDirectory: true)

  try FileManager.default.createDirectory(
    at: directory,
    withIntermediateDirectories: true
  )

  return directory
}

internal func pdfRendersDirectory() throws -> URL {
  let directory = try pdfCacheDirectory()
    .appendingPathComponent(PdfCacheConfig.rendersDirectoryName, isDirectory: true)

  try FileManager.default.createDirectory(
    at: directory,
    withIntermediateDirectories: true
  )

  return directory
}

// MARK: - Identity and naming

/// The cache identity of a remote source: `cacheKey` when non-empty, otherwise
/// the full URI. `fileName` is deliberately not part of it.
internal func pdfCacheIdentity(uri: String, cacheKey: String) -> String {
  cacheKey.isEmpty ? uri : cacheKey
}

/// Directory name of the entry for `identity`: its SHA-256 as 64 lowercase hex
/// characters.
internal func pdfCacheEntryName(identity: String) -> String {
  SHA256.hash(data: Data(identity.utf8))
    .map { String(format: "%02x", $0) }
    .joined()
}

private let pdfHexDigits = Set("0123456789abcdef".utf8)

internal func pdfIsCacheEntryName(_ name: String) -> Bool {
  name.utf8.count == 64 && name.utf8.allSatisfy(pdfHexDigits.contains)
}

/// Name of the cached file: `fileName`, else the URI's last path segment,
/// restricted to `[A-Za-z0-9._-]`, with a stem of at most `maxStemLength`
/// characters (`document` when nothing usable is left) and always ending in
/// `.pdf`.
internal func pdfDisplayFileName(uri: String, fileName: String) -> String {
  var stem = sanitizePdfFileName(
    fileName.isEmpty ? pdfLastPathSegment(uri) : fileName
  )

  if stem.lowercased().hasSuffix(".pdf") {
    stem.removeLast(4)
  }

  stem = String(stem.prefix(PdfCacheConfig.maxStemLength))

  if stem.allSatisfy({ $0 == "." || $0 == "_" }) {
    stem = "document"
  }

  return "\(stem).pdf"
}

/// Percent-decoded last non-empty segment of the URI's path, or "" when there
/// is none. The URI is parsed as a URL, so a `/` inside the query or fragment
/// never counts.
private func pdfLastPathSegment(_ uri: String) -> String {
  guard
    let url = URL(string: uri),
    let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
    let segment = components.percentEncodedPath.split(separator: "/").last
  else {
    return ""
  }

  return segment.removingPercentEncoding ?? String(segment)
}

private func sanitizePdfFileName(_ value: String) -> String {
  let allowed = CharacterSet(
    charactersIn:
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-"
  )

  return String(
    value.unicodeScalars.map { allowed.contains($0) ? Character($0) : "_" }
  )
}

// MARK: - Integrity

private let pdfHeaderMagic = Data("%PDF-".utf8)

/// Whether `data` looks like a PDF: `%PDF-` within its first `headerWindow`
/// bytes (the format tolerates a little junk before the header). An HTML error
/// or captive-portal page served with HTTP 200 fails this check.
internal func pdfHasPdfHeader(_ data: Data) -> Bool {
  data.prefix(PdfCacheConfig.headerWindow).range(of: pdfHeaderMagic) != nil
}

// MARK: - Lookup

/// The cached document for `destination` (`<entry>/<display name>`), or nil on
/// a miss. When the entry holds the document under another name only, that
/// file is hard linked (copied as a fallback) to the requested name. A hit
/// marks the entry as recently used for the sweep.
internal func pdfCachedFile(at destination: URL) -> URL? {
  let fileManager = FileManager.default
  let entry = destination.deletingLastPathComponent()
  var cached = destination

  if !fileManager.fileExists(atPath: destination.path) {
    guard
      let existingName = (try? fileManager.contentsOfDirectory(atPath: entry.path))?
        .first(where: { $0.hasSuffix(".pdf") })
    else {
      return nil
    }

    let existing = entry.appendingPathComponent(existingName)
    do {
      try fileManager.linkItem(at: existing, to: destination)
    } catch {
      try? fileManager.copyItem(at: existing, to: destination)
    }

    if !fileManager.fileExists(atPath: destination.path) {
      // The right document under another name beats failing the request.
      cached = existing
    }
  }

  try? fileManager.setAttributes(
    [.modificationDate: Date()],
    ofItemAtPath: entry.path
  )

  return cached
}

// MARK: - Sweep

/// One child of the cache root, as the sweep sees it.
internal struct PdfCacheItem {
  let name: String
  let isDirectory: Bool
  let modificationDate: Date
  /// Bytes used on disk; only meaningful for entries.
  let size: Int64
}

internal struct PdfCacheSweepPlan: Equatable {
  /// Regular files directly under the root: the 0.1.x flat cache layout.
  var legacyFiles: [String] = []
  /// Entries not used within `maxEntryAge`.
  var expiredEntries: [String] = []
  /// Least recently used entries deleted to get back under `maxTotalSize`.
  var evictedEntries: [String] = []
}

/// Decides what the sweep deletes at the cache root. Pinned entries (used by
/// this process) are never selected, but still count towards the size budget.
/// Directories that are not entries (e.g. `renders/`) are left alone here.
internal func pdfPlanCacheSweep(
  _ items: [PdfCacheItem],
  now: Date,
  maxEntryAge: TimeInterval = PdfCacheConfig.maxEntryAge,
  maxTotalSize: Int64 = PdfCacheConfig.maxTotalSize,
  isPinned: (String) -> Bool
) -> PdfCacheSweepPlan {
  var plan = PdfCacheSweepPlan()
  var liveEntries: [PdfCacheItem] = []

  for item in items {
    if !item.isDirectory {
      plan.legacyFiles.append(item.name)
    } else if pdfIsCacheEntryName(item.name) {
      if now.timeIntervalSince(item.modificationDate) > maxEntryAge
        && !isPinned(item.name)
      {
        plan.expiredEntries.append(item.name)
      } else {
        liveEntries.append(item)
      }
    }
  }

  var totalSize = liveEntries.reduce(Int64(0)) { $0 + $1.size }
  let leastRecentlyUsedFirst = liveEntries.sorted {
    ($0.modificationDate, $0.name) < ($1.modificationDate, $1.name)
  }
  for entry in leastRecentlyUsedFirst
  where totalSize > maxTotalSize && !isPinned(entry.name) {
    plan.evictedEntries.append(entry.name)
    totalSize -= entry.size
  }

  return plan
}

/// Applies the cache policy to `root`: removes 0.1.x flat files, stale
/// leftovers inside entries, rendered pages older than `maxEntryAge`, expired
/// entries and, above `maxTotalSize`, the least recently used entries. Best
/// effort: it never throws and skips anything it cannot read or delete.
internal func pdfSweepCache(
  root: URL,
  registry: PdfCacheRegistry,
  now: Date = Date(),
  maxEntryAge: TimeInterval = PdfCacheConfig.maxEntryAge,
  maxTotalSize: Int64 = PdfCacheConfig.maxTotalSize
) {
  let fileManager = FileManager.default
  let keys: Set<URLResourceKey> = [.isDirectoryKey, .contentModificationDateKey]

  guard
    let children = try? fileManager.contentsOfDirectory(
      at: root,
      includingPropertiesForKeys: Array(keys)
    )
  else {
    return
  }

  var items: [PdfCacheItem] = []
  for child in children {
    let values = try? child.resourceValues(forKeys: keys)
    let name = child.lastPathComponent
    let isDirectory = values?.isDirectory ?? false
    // Read before removing leftovers below, which bumps a directory's mtime.
    let modificationDate = values?.contentModificationDate ?? .distantPast

    if isDirectory && name == PdfCacheConfig.rendersDirectoryName {
      pdfRemoveItems(in: child, modifiedBefore: now.addingTimeInterval(-maxEntryAge))
      continue
    }

    var size: Int64 = 0
    if isDirectory && pdfIsCacheEntryName(name) {
      pdfRemoveItems(
        in: child,
        modifiedBefore: now.addingTimeInterval(-PdfCacheConfig.maxTempAge),
        keeping: { $0.hasSuffix(".pdf") }
      )
      size = pdfEntrySize(child)
    }

    items.append(
      PdfCacheItem(
        name: name,
        isDirectory: isDirectory,
        modificationDate: modificationDate,
        size: size
      )
    )
  }

  let plan = pdfPlanCacheSweep(
    items,
    now: now,
    maxEntryAge: maxEntryAge,
    maxTotalSize: maxTotalSize,
    isPinned: registry.isPinned
  )

  for name in plan.legacyFiles {
    try? fileManager.removeItem(at: root.appendingPathComponent(name))
  }

  for name in plan.expiredEntries + plan.evictedEntries {
    registry.ifNotPinned(name) {
      try? fileManager.removeItem(
        at: root.appendingPathComponent(name, isDirectory: true)
      )
    }
  }
}

private func pdfRemoveItems(
  in directory: URL,
  modifiedBefore cutoff: Date,
  keeping keep: (String) -> Bool = { _ in false }
) {
  let fileManager = FileManager.default

  guard
    let children = try? fileManager.contentsOfDirectory(
      at: directory,
      includingPropertiesForKeys: [.contentModificationDateKey]
    )
  else {
    return
  }

  for child in children where !keep(child.lastPathComponent) {
    let modificationDate =
      (try? child.resourceValues(forKeys: [.contentModificationDateKey]))?
      .contentModificationDate ?? .distantPast

    if modificationDate < cutoff {
      try? fileManager.removeItem(at: child)
    }
  }
}

/// Bytes used by an entry. Its names are normally hard links to one file, so
/// every inode is counted once.
private func pdfEntrySize(_ entry: URL) -> Int64 {
  let fileManager = FileManager.default

  guard let names = try? fileManager.contentsOfDirectory(atPath: entry.path) else {
    return 0
  }

  var seenFiles = Set<UInt64>()
  var size: Int64 = 0
  for name in names {
    guard
      let attributes = try? fileManager.attributesOfItem(
        atPath: entry.appendingPathComponent(name).path
      )
    else {
      continue
    }

    if let fileNumber = (attributes[.systemFileNumber] as? NSNumber)?.uint64Value,
      !seenFiles.insert(fileNumber).inserted
    {
      continue
    }

    size += (attributes[.size] as? NSNumber)?.int64Value ?? 0
  }

  return size
}

// MARK: - Process state

/// Per-process cache state: the entries this process has used, which the sweep
/// never deletes, and whether the sweep has already been started.
internal final class PdfCacheRegistry: @unchecked Sendable {
  static let shared = PdfCacheRegistry()

  private let lock = NSLock()
  private var pinnedEntries = Set<String>()
  private var didStartSweep = false

  /// Protects an entry from the sweep for the rest of the process. Call it
  /// before looking the entry up, so a concurrent sweep cannot delete a file
  /// that is about to be returned.
  func pin(_ entryName: String) {
    lock.lock()
    defer { lock.unlock() }
    pinnedEntries.insert(entryName)
  }

  func isPinned(_ entryName: String) -> Bool {
    lock.lock()
    defer { lock.unlock() }
    return pinnedEntries.contains(entryName)
  }

  /// Runs `body` (a deletion) unless the entry is pinned, atomically with
  /// respect to `pin`.
  func ifNotPinned(_ entryName: String, _ body: () -> Void) {
    lock.lock()
    defer { lock.unlock() }

    if !pinnedEntries.contains(entryName) {
      body()
    }
  }

  /// Sweeps `root` on a background queue the first time it is called in this
  /// process; later calls return immediately. Never blocks the caller.
  func sweepOnce(root: URL) {
    lock.lock()
    let shouldSweep = !didStartSweep
    didStartSweep = true
    lock.unlock()

    guard shouldSweep else { return }

    DispatchQueue.global(qos: .utility).async {
      pdfSweepCache(root: root, registry: self)
    }
  }
}
