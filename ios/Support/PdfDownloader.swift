import Foundation

private func parsePdfHeaders(_ headersJson: String) -> [String: String] {
  guard
    !headersJson.isEmpty,
    let data = headersJson.data(using: .utf8),
    let object = try? JSONSerialization.jsonObject(with: data),
    let dictionary = object as? [String: Any]
  else {
    return [:]
  }

  var headers: [String: String] = [:]
  for (key, value) in dictionary {
    if let stringValue = value as? String {
      headers[key] = stringValue
    }
  }

  return headers
}

internal func pdfIsRemoteUri(_ uri: String) -> Bool {
  let lowercased = uri.lowercased()
  return lowercased.hasPrefix("http://") || lowercased.hasPrefix("https://")
}

/// Downloads a remote (`http`/`https`) PDF into the package cache and returns
/// `{ uri, fromCache }`. Local URIs are returned unchanged. Runs synchronously;
/// callers already dispatch it onto a background queue.
///
/// The document is cached under `pdfCacheIdentity(uri:cacheKey:)` and stored
/// as `pdfDisplayFileName(uri:fileName:)` (see `PdfCacheConfig`).
internal func preparePdfSource(
  _ uri: String,
  headersJson: String,
  fileName: String,
  cacheKey: String
) throws -> [String: Any] {
  guard pdfIsRemoteUri(uri) else {
    return ["uri": uri, "fromCache": false]
  }

  let root: URL
  do {
    root = try pdfCacheDirectory()
  } catch {
    throw pdfException(
      "ERR_PDF_SOURCE",
      "PDF cache is not available: \(error.localizedDescription)"
    )
  }

  return try preparePdfSource(
    uri,
    headersJson: headersJson,
    fileName: fileName,
    cacheKey: cacheKey,
    in: root,
    registry: .shared
  )
}

/// `preparePdfSource` for a remote URI against an explicit cache root.
internal func preparePdfSource(
  _ uri: String,
  headersJson: String,
  fileName: String,
  cacheKey: String,
  in root: URL,
  registry: PdfCacheRegistry
) throws -> [String: Any] {
  guard let url = URL(string: uri) else {
    throw pdfException("ERR_PDF_SOURCE", "Invalid PDF uri: \(uri)")
  }

  let entryName = pdfCacheEntryName(
    identity: pdfCacheIdentity(uri: uri, cacheKey: cacheKey)
  )
  // Pin before the sweep (started here, once per process) can see the entry,
  // so it never deletes what this call returns.
  registry.pin(entryName)
  registry.sweepOnce(root: root)

  let entry = root.appendingPathComponent(entryName, isDirectory: true)
  let destination = entry.appendingPathComponent(
    pdfDisplayFileName(uri: uri, fileName: fileName)
  )

  if let cached = pdfCachedFile(at: destination) {
    return ["uri": cached.absoluteString, "fromCache": true]
  }

  let data = try downloadPdf(from: url, headersJson: headersJson)

  do {
    try FileManager.default.createDirectory(
      at: entry,
      withIntermediateDirectories: true
    )
    // Written to a temporary file and renamed into place: readers never see a
    // partial document.
    try data.write(to: destination, options: .atomic)
  } catch {
    throw pdfException(
      "ERR_PDF_SOURCE",
      "Unable to cache the downloaded PDF: \(error.localizedDescription)"
    )
  }

  return ["uri": destination.absoluteString, "fromCache": false]
}

/// Fetches `url` and returns its body, which must come with a 2xx status, be
/// non-empty and look like a PDF. Nothing is written to disk.
private func downloadPdf(from url: URL, headersJson: String) throws -> Data {
  var request = URLRequest(url: url)
  request.timeoutInterval = PdfCacheConfig.downloadTimeout
  for (key, value) in parsePdfHeaders(headersJson) {
    request.setValue(value, forHTTPHeaderField: key)
  }

  let semaphore = DispatchSemaphore(value: 0)
  var downloadedData: Data?
  var downloadError: Error?
  var statusCode = 0

  let task = URLSession.shared.dataTask(with: request) { data, response, error in
    downloadedData = data
    downloadError = error
    statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
    semaphore.signal()
  }
  task.resume()
  semaphore.wait()

  if let downloadError {
    throw pdfException("ERR_PDF_SOURCE", downloadError.localizedDescription)
  }

  guard
    (200...299).contains(statusCode),
    let downloadedData,
    !downloadedData.isEmpty
  else {
    throw pdfException(
      "ERR_PDF_SOURCE",
      "Failed to download PDF (HTTP \(statusCode))."
    )
  }

  guard pdfHasPdfHeader(downloadedData) else {
    throw pdfException("ERR_PDF_SOURCE", "Downloaded content is not a PDF.")
  }

  return downloadedData
}
