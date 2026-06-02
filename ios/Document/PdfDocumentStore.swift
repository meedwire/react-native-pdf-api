import Foundation

internal final class PdfDocumentStore {
  private var documents: [String: PdfDocumentHolder] = [:]

  func openDocument(_ uri: String) throws -> [String: Any] {
    let document = try loadPdfDocument(uri)
    let documentId = UUID().uuidString
    documents[documentId] = PdfDocumentHolder(
      id: documentId,
      sourceUri: uri,
      document: document
    )

    return [
      "documentId": documentId,
      "pageCount": document.pageCount,
      "sourceUri": uri,
      "capabilities": pdfCapabilities(),
    ]
  }

  func holder(for documentId: String) throws -> PdfDocumentHolder {
    guard let holder = documents[documentId] else {
      throw pdfException("ERR_PDF_DOCUMENT_NOT_FOUND", "PDF document was not found or is already closed.")
    }

    return holder
  }

  func closeDocument(_ documentId: String) {
    documents.removeValue(forKey: documentId)
  }

  func closeAll() {
    documents.removeAll()
  }
}
