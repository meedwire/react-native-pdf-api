import Foundation
import CoreGraphics
import NitroModules

public final class HybridPdfApi: HybridPdfApiSpec {
  private let documentStore = PdfDocumentStore()

  public override init() {
    super.init()
  }

  public func getCapabilities() throws -> PdfCapabilities {
    pdfCapabilities()
  }

  public func openDocumentAsync(uri: String) throws -> Promise<PdfOpenDocumentResult> {
    Promise.parallel {
      let result = try self.documentStore.openDocument(uri)

      return PdfOpenDocumentResult(
        documentId: result.documentId,
        pageCount: result.pageCount,
        sourceUri: result.sourceUri,
        capabilities: result.capabilities
      )
    }
  }

  public func closeDocumentAsync(documentId: String) throws -> Promise<Void> {
    Promise.parallel {
      self.documentStore.closeDocument(documentId)
    }
  }

  public func closeAllDocumentsAsync() throws -> Promise<Void> {
    Promise.parallel {
      self.documentStore.closeAll()
    }
  }

  public func getMetadataAsync(documentId: String) throws -> Promise<PdfMetadata> {
    Promise.parallel {
      try PdfMetadataReader
        .metadata(for: self.documentStore.holder(for: documentId))
        .toPdfMetadata()
    }
  }

  public func getPageInfoAsync(
    documentId: String,
    pageIndex: Double
  ) throws -> Promise<PdfPageInfo> {
    Promise.parallel {
      try PdfPageRenderer
        .pageInfo(
          for: self.documentStore.holder(for: documentId),
          pageIndex: Int(pageIndex)
        )
        .toPdfPageInfo()
    }
  }

  public func renderPageAsync(
    documentId: String,
    pageIndex: Double,
    options: PdfRenderOptions?
  ) throws -> Promise<PdfRenderedPage> {
    Promise.parallel {
      try PdfPageRenderer
        .renderPage(
          for: self.documentStore.holder(for: documentId),
          pageIndex: Int(pageIndex),
          options: options.toDictionary()
        )
        .toPdfRenderedPage()
    }
  }

  public func getTextAsync(
    documentId: String,
    pageIndex: Double?
  ) throws -> Promise<Variant_NullType_String> {
    Promise.parallel {
      let text = try PdfTextOperations.getText(
        for: self.documentStore.holder(for: documentId),
        pageIndex: pageIndex.map(Int.init)
      )

      guard let text else {
        return .first(.null)
      }

      return .second(text)
    }
  }

  public func searchTextAsync(
    documentId: String,
    query: String,
    options: PdfSearchOptions?
  ) throws -> Promise<[PdfSearchResult]> {
    Promise.parallel {
      try PdfTextOperations.searchText(
        for: self.documentStore.holder(for: documentId),
        query: query,
        options: options.toDictionary()
      )
      .map { try $0.toPdfSearchResult() }
    }
  }

  public func clearPdfCacheAsync() throws -> Promise<Void> {
    Promise.parallel {
      self.documentStore.closeAll()
      let directory = try pdfCacheDirectory()
      try? FileManager.default.removeItem(at: directory)
      try FileManager.default.createDirectory(
        at: directory,
        withIntermediateDirectories: true
      )
    }
  }
}

private extension PdfRenderOptions? {
  func toDictionary() -> [String: Any]? {
    guard let self else {
      return nil
    }

    return [
      "format": self.format as Any,
      "quality": self.quality as Any,
      "width": self.width as Any,
      "height": self.height as Any,
      "scale": self.scale as Any,
      "backgroundColor": self.backgroundColor as Any,
      "maxPixels": self.maxPixels as Any
    ]
  }
}

private extension PdfSearchOptions? {
  func toDictionary() -> [String: Any]? {
    guard let self else {
      return nil
    }

    return [
      "pageIndex": self.pageIndex as Any,
      "caseSensitive": self.caseSensitive as Any,
      "maxResults": self.maxResults as Any,
      "focus": self.focus as Any,
      "highlight": self.highlight as Any,
      "resultIndex": self.resultIndex as Any
    ]
  }
}

private extension [String: Any] {
  func toPdfMetadata() throws -> PdfMetadata {
    PdfMetadata(
      title: self["title"] as? String,
      author: self["author"] as? String,
      subject: self["subject"] as? String,
      creator: self["creator"] as? String,
      producer: self["producer"] as? String,
      keywords: self["keywords"] as? [String],
      creationDate: self["creationDate"] as? String,
      modificationDate: self["modificationDate"] as? String,
      pageCount: try double("pageCount"),
      isEncrypted: self["isEncrypted"] as? Bool,
      isLocked: self["isLocked"] as? Bool
    )
  }

  func toPdfPageInfo() throws -> PdfPageInfo {
    PdfPageInfo(
      pageIndex: try double("pageIndex"),
      width: try double("width"),
      height: try double("height"),
      rotation: try double("rotation"),
      label: self["label"] as? String,
      annotationCount: optionalDouble("annotationCount")
    )
  }

  func toPdfRenderedPage() throws -> PdfRenderedPage {
    PdfRenderedPage(
      uri: try string("uri"),
      width: try double("width"),
      height: try double("height"),
      scale: try double("scale"),
      pageIndex: try double("pageIndex")
    )
  }

  func toPdfSearchResult() throws -> PdfSearchResult {
    PdfSearchResult(
      pageIndex: try double("pageIndex"),
      text: self["text"] as? String,
      bounds: (self["bounds"] as? [[String: Any]])?.map {
        PdfSearchBounds(
          x: $0.number("x"),
          y: $0.number("y"),
          width: $0.number("width"),
          height: $0.number("height")
        )
      } ?? []
    )
  }

  private func string(_ key: String) throws -> String {
    guard let value = self[key] as? String else {
      throw pdfException("ERR_PDF_RESULT", "PDF native result is missing string field \(key).")
    }

    return value
  }

  private func double(_ key: String) throws -> Double {
    guard let value = optionalDouble(key) else {
      throw pdfException("ERR_PDF_RESULT", "PDF native result is missing numeric field \(key).")
    }

    return value
  }

  private func optionalDouble(_ key: String) -> Double? {
    if let value = self[key] as? Double {
      return value
    }

    if let value = self[key] as? Int {
      return Double(value)
    }

    if let value = self[key] as? CGFloat {
      return Double(value)
    }

    return nil
  }

  private func number(_ key: String) -> Double {
    optionalDouble(key) ?? 0
  }
}
