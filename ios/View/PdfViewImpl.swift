import Foundation
import PDFKit
import UIKit

/// Plain `UIView` wrapping `PDFView` with the viewer behaviour (fit-to-width
/// scaling, zoom, search highlight/focus). It is hosted by the Objective-C++
/// Fabric component (`PdfViewComponentView`), which feeds it props via the
/// `@objc` setters and forwards its callbacks as Fabric events. No React or
/// codegen types leak into this file.
@objc public final class PdfViewImpl: UIView {
  private let pdfView = PDFView()
  private var initialPageIndex = 0
  private var maxZoomScale: CGFloat = 5
  private var shouldResetScale = false
  private var lastScaleWidth: CGFloat = 0
  private var lastScaleHeight: CGFloat = 0
  private var viewerBackgroundUIColor = pdfDefaultViewBackgroundColor
  private var searchHighlightAnnotations: [PDFAnnotation] = []
  private var pendingSource: String?
  private var shouldLoadSource = false

  // Search highlight state
  private var highlightRequestId = -1
  private var highlightPageIndex: Int?
  private var highlightBounds: [CGRect] = []
  private var highlightFocusBounds: [CGRect] = []
  private var lastFocusedSearchRequestId = -1

  @objc public var onLoadHandler: ((NSDictionary) -> Void)?
  @objc public var onPageChangeHandler: ((NSDictionary) -> Void)?
  @objc public var onErrorHandler: ((NSDictionary) -> Void)?

  public override init(frame: CGRect) {
    super.init(frame: frame)
    clipsToBounds = true
    backgroundColor = viewerBackgroundUIColor
    pdfView.autoScales = false
    pdfView.displayMode = .singlePageContinuous
    pdfView.displayDirection = .vertical
    pdfView.backgroundColor = viewerBackgroundUIColor
    pdfView.translatesAutoresizingMaskIntoConstraints = false

    addSubview(pdfView)
    NSLayoutConstraint.activate([
      pdfView.leadingAnchor.constraint(equalTo: leadingAnchor),
      pdfView.trailingAnchor.constraint(equalTo: trailingAnchor),
      pdfView.topAnchor.constraint(equalTo: topAnchor),
      pdfView.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])

    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handlePageChange),
      name: Notification.Name.PDFViewPageChanged,
      object: pdfView
    )
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not supported")
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  public override func layoutSubviews() {
    super.layoutSubviews()
    applyViewerBackgroundColor()
    updateScaleToFitWidth()
  }

  // MARK: - Prop setters

  @objc public func setSource(_ source: String?) {
    let normalized = (source?.isEmpty ?? true) ? nil : source
    guard normalized != pendingSource else { return }
    pendingSource = normalized
    shouldLoadSource = true
  }

  @objc public func setViewerBackgroundColor(_ color: String?) {
    viewerBackgroundUIColor = parseColor(color, default: pdfDefaultViewBackgroundColor)
    applyViewerBackgroundColor()
  }

  @objc public func setInitialPage(_ page: Int) {
    initialPageIndex = max(0, page)
  }

  @objc public func setPageSpacing(_ spacing: Double) {
    let value = CGFloat(spacing)
    pdfView.pageBreakMargins = UIEdgeInsets(
      top: value / 2,
      left: 0,
      bottom: value / 2,
      right: 0
    )
  }

  @objc public func setMaxZoom(_ zoom: Double) {
    maxZoomScale = CGFloat(max(zoom, 1))
    updateScaleToFitWidth(force: true)
  }

  @objc public func setSinglePage(_ singlePage: Bool) {
    pdfView.displayMode = singlePage ? .singlePage : .singlePageContinuous
    resetScaleToFitWidth()
  }

  @objc public func setSearchHighlight(_ value: NSDictionary?) {
    guard let value else {
      highlightPageIndex = nil
      highlightBounds = []
      highlightFocusBounds = []
      applySearchHighlight()
      return
    }

    highlightRequestId =
      (value["requestId"] as? NSNumber)?.intValue ?? highlightRequestId
    highlightBounds = rects(from: value["bounds"])
    highlightFocusBounds = rects(from: value["focusBounds"])
    // `pageIndex` is a non-optional Int in codegen; treat "no bounds" as a clear.
    let hasBounds = !highlightBounds.isEmpty || !highlightFocusBounds.isEmpty
    highlightPageIndex = hasBounds ? (value["pageIndex"] as? NSNumber)?.intValue : nil
    applySearchHighlight()
    focusSearchHighlightIfNeeded()
  }

  /// Called by the component view after a batch of props has been applied so
  /// the document loads with all related props (initial page, spacing, …) set.
  @objc public func applyPendingSourceIfNeeded() {
    guard shouldLoadSource else { return }
    shouldLoadSource = false
    setSourceInternal(pendingSource)
  }

  // MARK: - Loading

  private func setSourceInternal(_ source: String?) {
    guard let source else {
      pdfView.document = nil
      pdfView.clearSelection()
      removeSearchHighlightAnnotations()
      return
    }

    do {
      let document = try loadPdfDocument(source)
      pdfView.document = document
      pdfView.clearSelection()
      removeSearchHighlightAnnotations()
      applyViewerBackgroundColor()

      let targetPage = min(max(initialPageIndex, 0), max(document.pageCount - 1, 0))
      if let page = document.page(at: targetPage) {
        pdfView.go(to: page)
      }
      resetScaleToFitWidth()

      onLoadHandler?([
        "documentId": "",
        "pageCount": document.pageCount,
        "sourceUri": source,
        "capabilities": pdfCapabilities(),
      ] as NSDictionary)
      handlePageChange()
    } catch {
      onErrorHandler?([
        "code": (error as? PdfApiError)?.code ?? "ERR_PDF_OPEN",
        "message": error.localizedDescription,
      ] as NSDictionary)
    }
  }

  @objc private func handlePageChange() {
    guard
      let document = pdfView.document,
      let page = pdfView.currentPage
    else {
      return
    }

    onPageChangeHandler?([
      "currentPage": document.index(for: page),
      "pageCount": document.pageCount,
    ] as NSDictionary)
  }

  // MARK: - Search highlight

  private func applySearchHighlight() {
    removeSearchHighlightAnnotations()

    guard
      let document = pdfView.document,
      let pageIndex = highlightPageIndex,
      let page = document.page(at: pageIndex)
    else {
      return
    }

    searchHighlightAnnotations = highlightBounds.map { bounds in
      let annotation = PDFAnnotation(
        bounds: bounds,
        forType: .highlight,
        withProperties: nil
      )
      annotation.color = UIColor.systemYellow.withAlphaComponent(0.55)
      page.addAnnotation(annotation)
      return annotation
    }
  }

  private func removeSearchHighlightAnnotations() {
    searchHighlightAnnotations.forEach { $0.page?.removeAnnotation($0) }
    searchHighlightAnnotations = []
  }

  private func focusSearchHighlightIfNeeded() {
    guard
      let document = pdfView.document,
      let pageIndex = highlightPageIndex,
      highlightRequestId != lastFocusedSearchRequestId,
      let page = document.page(at: pageIndex)
    else {
      return
    }

    lastFocusedSearchRequestId = highlightRequestId

    if
      let bounds = highlightFocusBounds.first ?? highlightBounds.first,
      let selection = page.selection(for: bounds)
    {
      pdfView.setCurrentSelection(selection, animate: true)
      pdfView.go(to: selection)
      return
    }

    pdfView.go(to: page)
  }

  // MARK: - Scaling

  private func resetScaleToFitWidth() {
    shouldResetScale = true
    setNeedsLayout()
    DispatchQueue.main.async { [weak self] in
      self?.updateScaleToFitWidth(force: true)
    }
  }

  private func updateScaleToFitWidth(force: Bool = false) {
    guard
      bounds.width > 0,
      let document = pdfView.document,
      let page = pdfView.currentPage ?? document.page(at: 0)
    else {
      return
    }

    let pageSize = effectivePageSize(for: page)
    guard pageSize.width > 0, pageSize.height > 0 else { return }

    let widthScale = bounds.width / pageSize.width
    let heightScale = bounds.height / pageSize.height
    let shouldFitInsideViewport =
      document.pageCount == 1 || pdfView.displayMode == .singlePage
    let targetScale = max(
      shouldFitInsideViewport ? min(widthScale, heightScale) : widthScale,
      0.01
    )
    let didWidthChange = abs(bounds.width - lastScaleWidth) > 0.5
    let didHeightChange = abs(bounds.height - lastScaleHeight) > 0.5

    pdfView.autoScales = false
    pdfView.minScaleFactor = targetScale
    pdfView.maxScaleFactor = max(maxZoomScale, targetScale)

    if force || shouldResetScale || didWidthChange || didHeightChange
      || pdfView.scaleFactor < targetScale
    {
      pdfView.scaleFactor = targetScale
      shouldResetScale = false
      lastScaleWidth = bounds.width
      lastScaleHeight = bounds.height
    }
    pdfView.layoutDocumentView()
    alignDocumentView()
  }

  private func effectivePageSize(for page: PDFPage) -> CGSize {
    let pageBounds = page.bounds(for: .cropBox)
    let normalizedRotation = ((page.rotation % 360) + 360) % 360

    if normalizedRotation == 90 || normalizedRotation == 270 {
      return CGSize(width: pageBounds.height, height: pageBounds.width)
    }

    return pageBounds.size
  }

  private func alignDocumentView() {
    guard
      let documentView = pdfView.documentView,
      let document = pdfView.document
    else {
      return
    }

    var frame = documentView.frame
    if frame.width < pdfView.bounds.width {
      frame.origin.x = (pdfView.bounds.width - frame.width) / 2
    }

    let shouldCenterVertically =
      document.pageCount == 1 || pdfView.displayMode == .singlePage
    if shouldCenterVertically && frame.height < pdfView.bounds.height {
      frame.origin.y = (pdfView.bounds.height - frame.height) / 2
    } else if !shouldCenterVertically && frame.height < pdfView.bounds.height {
      frame.origin.y = 0
    }

    documentView.frame = frame
  }

  private func applyViewerBackgroundColor() {
    backgroundColor = viewerBackgroundUIColor
    pdfView.backgroundColor = viewerBackgroundUIColor
    pdfView.subviews.forEach { subview in
      if let scrollView = subview as? UIScrollView {
        scrollView.backgroundColor = viewerBackgroundUIColor
      }
    }
  }

  private func rects(from value: Any?) -> [CGRect] {
    guard let items = value as? [[String: Any]] else { return [] }

    return items.map { item in
      CGRect(
        x: (item["x"] as? NSNumber)?.doubleValue ?? 0,
        y: (item["y"] as? NSNumber)?.doubleValue ?? 0,
        width: (item["width"] as? NSNumber)?.doubleValue ?? 0,
        height: (item["height"] as? NSNumber)?.doubleValue ?? 0
      )
    }
  }
}
