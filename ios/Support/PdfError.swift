import Foundation

internal struct PdfApiError: LocalizedError {
  let code: String
  let message: String

  var errorDescription: String? {
    message
  }
}

internal func pdfException(_ code: String, _ message: String) -> PdfApiError {
  PdfApiError(code: code, message: message)
}
