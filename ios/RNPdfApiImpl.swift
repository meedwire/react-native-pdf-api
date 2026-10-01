import Foundation

/// Resolve/reject blocks bridged from `RCTPromiseResolveBlock` /
/// `RCTPromiseRejectBlock` by the Objective-C++ TurboModule, kept as plain
/// closures so this Swift implementation has no direct React dependency.
public typealias PdfResolve = (Any?) -> Void
public typealias PdfReject = (String, String, Error?) -> Void

/// Pure Swift implementation backing the `PdfApi` TurboModule. It reuses the
/// PDFKit logic in `Document/` and `Support/` and resolves results as
/// dictionaries/arrays, which the Objective-C++ layer hands straight to JSI.
@objc public final class RNPdfApiImpl: NSObject {
  private let documentStore = PdfDocumentStore()

  @objc(getCapabilities) public func getCapabilities() -> [String: Any] {
    pdfCapabilities()
  }

  @objc(prepareSource:headersJson:fileName:cacheKey:resolve:reject:)
  public func prepareSource(
    _ uri: String,
    headersJson: String,
    fileName: String,
    cacheKey: String,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      try preparePdfSource(
        uri,
        headersJson: headersJson,
        fileName: fileName,
        cacheKey: cacheKey
      )
    }
  }

  @objc(openDocument:resolve:reject:)
  public func openDocument(
    _ uri: String,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) { try self.documentStore.openDocument(uri) }
  }

  @objc(closeDocument:resolve:reject:)
  public func closeDocument(
    _ documentId: String,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      self.documentStore.closeDocument(documentId)
      return nil
    }
  }

  @objc(closeAllDocumentsWithResolve:reject:)
  public func closeAllDocuments(
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      self.documentStore.closeAll()
      return nil
    }
  }

  @objc(getMetadata:resolve:reject:)
  public func getMetadata(
    _ documentId: String,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      try PdfMetadataReader.metadata(
        for: self.documentStore.holder(for: documentId)
      )
    }
  }

  @objc(getPageInfo:pageIndex:resolve:reject:)
  public func getPageInfo(
    _ documentId: String,
    pageIndex: Double,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      try PdfPageRenderer.pageInfo(
        for: self.documentStore.holder(for: documentId),
        pageIndex: Int(pageIndex)
      )
    }
  }

  @objc(renderPage:pageIndex:optionsJson:resolve:reject:)
  public func renderPage(
    _ documentId: String,
    pageIndex: Double,
    optionsJson: String,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      try PdfPageRenderer.renderPage(
        for: self.documentStore.holder(for: documentId),
        pageIndex: Int(pageIndex),
        options: parsePdfOptions(optionsJson)
      )
    }
  }

  @objc(getText:pageIndex:resolve:reject:)
  public func getText(
    _ documentId: String,
    pageIndex: Double,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      let index = pageIndex < 0 ? nil : Int(pageIndex)
      let text = try PdfTextOperations.getText(
        for: self.documentStore.holder(for: documentId),
        pageIndex: index
      )

      return text ?? NSNull()
    }
  }

  @objc(searchText:query:optionsJson:resolve:reject:)
  public func searchText(
    _ documentId: String,
    query: String,
    optionsJson: String,
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      try PdfTextOperations.searchText(
        for: self.documentStore.holder(for: documentId),
        query: query,
        options: parsePdfOptions(optionsJson)
      )
    }
  }

  @objc(clearCacheWithResolve:reject:)
  public func clearCache(
    resolve: @escaping PdfResolve,
    reject: @escaping PdfReject
  ) {
    run(resolve, reject) {
      self.documentStore.closeAll()
      let directory = try pdfCacheDirectory()
      try? FileManager.default.removeItem(at: directory)
      try FileManager.default.createDirectory(
        at: directory,
        withIntermediateDirectories: true
      )
      return nil
    }
  }

  private func run(
    _ resolve: @escaping PdfResolve,
    _ reject: @escaping PdfReject,
    _ work: @escaping () throws -> Any?
  ) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        resolve(try work())
      } catch let error as PdfApiError {
        reject(error.code, error.message, error)
      } catch {
        reject("ERR_PDF", error.localizedDescription, error)
      }
    }
  }
}

private func parsePdfOptions(_ optionsJson: String) -> [String: Any]? {
  guard
    !optionsJson.isEmpty,
    let data = optionsJson.data(using: .utf8),
    let object = try? JSONSerialization.jsonObject(with: data),
    let dictionary = object as? [String: Any]
  else {
    return nil
  }

  return dictionary
}
