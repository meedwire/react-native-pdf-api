import Foundation

/// Builds a filesystem-safe file name for a cached remote PDF, mirroring the
/// behaviour the JS layer used to implement with expo-file-system.
internal func pdfSafeFileName(uri: String, override: String?) -> String {
  if let override, !override.isEmpty {
    return sanitizePdfFileName(override)
  }

  let lastComponent =
    uri.split(separator: "/").last.map(String.init)?
    .split(separator: "?").first.map(String.init) ?? "document.pdf"
  let decoded = lastComponent.removingPercentEncoding ?? lastComponent
  let normalized = sanitizePdfFileName(decoded)

  return normalized.lowercased().hasSuffix(".pdf")
    ? normalized
    : "\(normalized).pdf"
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

/// Downloads a remote (`http`/`https`) PDF into the package cache and returns
/// `{ uri, fromCache }`. Local URIs are returned unchanged. Runs synchronously;
/// callers already dispatch it onto a background queue.
internal func preparePdfSource(
  _ uri: String,
  headersJson: String,
  fileName: String
) throws -> [String: Any] {
  let isRemote = uri.hasPrefix("http://") || uri.hasPrefix("https://")
  guard isRemote else {
    return ["uri": uri, "fromCache": false]
  }

  guard let url = URL(string: uri) else {
    throw pdfException("ERR_PDF_SOURCE", "Invalid PDF uri: \(uri)")
  }

  let directory = try pdfCacheDirectory()
  let destination = directory.appendingPathComponent(
    pdfSafeFileName(uri: uri, override: fileName)
  )

  if FileManager.default.fileExists(atPath: destination.path) {
    return ["uri": destination.absoluteString, "fromCache": true]
  }

  var request = URLRequest(url: url)
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

  guard statusCode < 400, let downloadedData, !downloadedData.isEmpty else {
    throw pdfException(
      "ERR_PDF_SOURCE",
      "Failed to download PDF (HTTP \(statusCode))."
    )
  }

  try downloadedData.write(to: destination, options: .atomic)

  return ["uri": destination.absoluteString, "fromCache": false]
}
