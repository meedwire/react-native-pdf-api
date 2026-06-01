import PDFKit

internal enum PdfMetadataReader {
  static func metadata(for holder: PdfDocumentHolder) -> [String: Any] {
    let attributes = holder.document.documentAttributes ?? [:]
    var metadata: [String: Any] = [
      "pageCount": holder.document.pageCount,
      "isEncrypted": holder.document.isEncrypted,
      "isLocked": holder.document.isLocked
    ]

    metadata["title"] = attributes[PDFDocumentAttribute.titleAttribute] as? String
    metadata["author"] = attributes[PDFDocumentAttribute.authorAttribute] as? String
    metadata["subject"] = attributes[PDFDocumentAttribute.subjectAttribute] as? String
    metadata["creator"] = attributes[PDFDocumentAttribute.creatorAttribute] as? String
    metadata["producer"] = attributes[PDFDocumentAttribute.producerAttribute] as? String
    metadata["keywords"] = attributes[PDFDocumentAttribute.keywordsAttribute] as? [String]
    metadata["creationDate"] = isoDate(attributes[PDFDocumentAttribute.creationDateAttribute])
    metadata["modificationDate"] = isoDate(attributes[PDFDocumentAttribute.modificationDateAttribute])

    return metadata
  }
}
