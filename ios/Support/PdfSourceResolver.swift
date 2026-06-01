import Foundation
import PDFKit

internal func resolvePdfUrl(_ uri: String) throws -> URL {
  if uri.hasPrefix("/") {
    return URL(fileURLWithPath: uri)
  }

  guard let url = URL(string: uri) else {
    throw pdfException("ERR_PDF_SOURCE", "Invalid PDF uri: \(uri)")
  }

  return url
}

internal func loadPdfDocument(_ source: String) throws -> PDFDocument {
  let url = try resolvePdfUrl(source)

  guard let document = PDFDocument(url: url) else {
    throw pdfException("ERR_PDF_OPEN", "Unable to open PDF document.")
  }

  if document.isLocked {
    throw pdfException("ERR_PDF_LOCKED", "Protected PDFs are not supported.")
  }

  return document
}
