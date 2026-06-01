import PDFKit

internal final class PdfDocumentHolder {
  let id: String
  let sourceUri: String
  let document: PDFDocument

  init(id: String, sourceUri: String, document: PDFDocument) {
    self.id = id
    self.sourceUri = sourceUri
    self.document = document
  }

  func page(at pageIndex: Int) throws -> PDFPage {
    guard pageIndex >= 0 && pageIndex < document.pageCount else {
      throw pdfException("ERR_PDF_PAGE_OUT_OF_BOUNDS", "PDF page index is out of bounds.")
    }

    guard let page = document.page(at: pageIndex) else {
      throw pdfException("ERR_PDF_PAGE", "Unable to load PDF page.")
    }

    return page
  }
}
