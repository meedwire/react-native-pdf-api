import Foundation

internal func pdfCacheDirectory() throws -> URL {
  let directory = FileManager.default
    .urls(for: .cachesDirectory, in: .userDomainMask)[0]
    .appendingPathComponent("react-native-pdf-api", isDirectory: true)

  try FileManager.default.createDirectory(
    at: directory,
    withIntermediateDirectories: true
  )

  return directory
}
