import NitroModules
import PDFKit
import UIKit

private final class PdfContainerView: UIView {
  weak var owner: HybridPdfView?

  override func layoutSubviews() {
    super.layoutSubviews()
    owner?.handleLayoutSubviews()
  }
}

private struct PdfSearchHighlight {
  let requestId: Int
  let pageIndex: Int?
  let bounds: [CGRect]
  let focusBounds: [CGRect]

  init?(_ value: PdfViewerSearchHighlight?) {
    guard let value else {
      return nil
    }

    requestId = Int(value.requestId)
    pageIndex = value.pageIndex.map(Int.init)
    bounds = Self.bounds(from: value.bounds)
    focusBounds = Self.bounds(from: value.focusBounds)
  }

  private static func bounds(from value: [PdfSearchBounds]?) -> [CGRect] {
    value?.map { item in
      CGRect(
        x: CGFloat(item.x),
        y: CGFloat(item.y),
        width: CGFloat(item.width),
        height: CGFloat(item.height)
      )
    } ?? []
  }
}

public final class HybridPdfView: HybridPdfViewSpec {
  private let containerView = PdfContainerView()
  private let pdfView = PDFView()
  private var initialPageIndex = 0
  private var maxZoomScale: CGFloat = 5
  private var shouldResetScale = false
  private var lastScaleWidth: CGFloat = 0
  private var lastScaleHeight: CGFloat = 0
  private var viewerBackgroundUIColor = pdfDefaultViewBackgroundColor
  private var searchHighlightAnnotations: [PDFAnnotation] = []
  private var searchHighlightState: PdfSearchHighlight?
  private var lastFocusedSearchRequestId = -1
  private var shouldLoadSourceAfterUpdate = false

  public var view: UIView {
    containerView
  }

  public var source: String? {
    didSet {
      guard source != oldValue else { return }
      shouldLoadSourceAfterUpdate = true
    }
  }

  public var viewerBackgroundColor: String? {
    didSet {
      setViewerBackgroundColor(viewerBackgroundColor)
    }
  }

  public var initialPage: Double? {
    didSet {
      initialPageIndex = max(0, Int(initialPage ?? 0))
    }
  }

  public var pageSpacing: Double? {
    didSet {
      let value = pageSpacing ?? 16
      pdfView.pageBreakMargins = UIEdgeInsets(
        top: value / 2,
        left: 0,
        bottom: value / 2,
        right: 0
      )
    }
  }

  public var maxZoom: Double? {
    didSet {
      maxZoomScale = CGFloat(max(maxZoom ?? 5, 1))
      updateScaleToFitWidth(force: true)
    }
  }

  public var maxPageResolution: Double?

  public var singlePage: Bool? {
    didSet {
      pdfView.displayMode = singlePage == true ? .singlePage : .singlePageContinuous
      resetScaleToFitWidth()
    }
  }

  public var searchHighlight: PdfViewerSearchHighlight? {
    didSet {
      searchHighlightState = PdfSearchHighlight(searchHighlight)
      applySearchHighlight()
      focusSearchHighlightIfNeeded()
    }
  }

  public var onLoad: ((PdfOpenDocumentResult) -> Void)?
  public var onPageChange: ((PdfPageChangeEvent) -> Void)?
  public var onError: ((PdfErrorEvent) -> Void)?

  public override init() {
    super.init()

    containerView.owner = self
    containerView.clipsToBounds = true
    containerView.backgroundColor = viewerBackgroundUIColor
    pdfView.autoScales = false
    pdfView.displayMode = .singlePageContinuous
    pdfView.displayDirection = .vertical
    pdfView.backgroundColor = viewerBackgroundUIColor
    pdfView.translatesAutoresizingMaskIntoConstraints = false

    containerView.addSubview(pdfView)
    NSLayoutConstraint.activate([
      pdfView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
      pdfView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
      pdfView.topAnchor.constraint(equalTo: containerView.topAnchor),
      pdfView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor)
    ])

    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handlePageChange),
      name: Notification.Name.PDFViewPageChanged,
      object: pdfView
    )
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  public func afterUpdate() {
    guard shouldLoadSourceAfterUpdate else {
      return
    }

    shouldLoadSourceAfterUpdate = false
    setSource(source)
  }

  fileprivate func handleLayoutSubviews() {
    applyViewerBackgroundColor()
    updateScaleToFitWidth()
  }

  private func setSource(_ source: String?) {
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

      onLoad?(
        PdfOpenDocumentResult(
          documentId: "",
          pageCount: Double(document.pageCount),
          sourceUri: source,
          capabilities: pdfCapabilities()
        )
      )
      handlePageChange()
    } catch {
      onError?(
        PdfErrorEvent(
          code: (error as? PdfApiError)?.code ?? "ERR_PDF_OPEN",
          message: error.localizedDescription
        )
      )
    }
  }

  private func setViewerBackgroundColor(_ color: String?) {
    viewerBackgroundUIColor = parseColor(
      color,
      default: pdfDefaultViewBackgroundColor
    )
    applyViewerBackgroundColor()
  }

  @objc private func handlePageChange() {
    guard
      let document = pdfView.document,
      let page = pdfView.currentPage
    else {
      return
    }

    onPageChange?(
      PdfPageChangeEvent(
        currentPage: Double(document.index(for: page)),
        pageCount: Double(document.pageCount)
      )
    )
  }

  private func applySearchHighlight() {
    removeSearchHighlightAnnotations()

    guard
      let document = pdfView.document,
      let highlight = searchHighlightState,
      let pageIndex = highlight.pageIndex,
      let page = document.page(at: pageIndex)
    else {
      return
    }

    searchHighlightAnnotations = highlight.bounds.map { bounds in
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
    searchHighlightAnnotations.forEach { annotation in
      annotation.page?.removeAnnotation(annotation)
    }
    searchHighlightAnnotations = []
  }

  private func focusSearchHighlightIfNeeded() {
    guard
      let document = pdfView.document,
      let highlight = searchHighlightState,
      let pageIndex = highlight.pageIndex,
      highlight.requestId != lastFocusedSearchRequestId,
      let page = document.page(at: pageIndex)
    else {
      return
    }

    lastFocusedSearchRequestId = highlight.requestId

    if
      let bounds = highlight.focusBounds.first ?? highlight.bounds.first,
      let selection = page.selection(for: bounds)
    {
      pdfView.setCurrentSelection(selection, animate: true)
      pdfView.go(to: selection)
      return
    }

    pdfView.go(to: page)
  }

  private func resetScaleToFitWidth() {
    shouldResetScale = true
    containerView.setNeedsLayout()
    DispatchQueue.main.async { [weak self] in
      self?.updateScaleToFitWidth(force: true)
    }
  }

  private func updateScaleToFitWidth(force: Bool = false) {
    guard
      containerView.bounds.width > 0,
      let document = pdfView.document,
      let page = pdfView.currentPage ?? document.page(at: 0)
    else {
      return
    }

    let pageSize = effectivePageSize(for: page)
    guard pageSize.width > 0, pageSize.height > 0 else {
      return
    }

    let widthScale = containerView.bounds.width / pageSize.width
    let heightScale = containerView.bounds.height / pageSize.height
    let shouldFitInsideViewport = document.pageCount == 1 || pdfView.displayMode == .singlePage
    let targetScale = max(
      shouldFitInsideViewport ? min(widthScale, heightScale) : widthScale,
      0.01
    )
    let didWidthChange = abs(containerView.bounds.width - lastScaleWidth) > 0.5
    let didHeightChange = abs(containerView.bounds.height - lastScaleHeight) > 0.5

    pdfView.autoScales = false
    pdfView.minScaleFactor = targetScale
    pdfView.maxScaleFactor = max(maxZoomScale, targetScale)

    if force || shouldResetScale || didWidthChange || didHeightChange || pdfView.scaleFactor < targetScale {
      pdfView.scaleFactor = targetScale
      shouldResetScale = false
      lastScaleWidth = containerView.bounds.width
      lastScaleHeight = containerView.bounds.height
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

    let shouldCenterVertically = document.pageCount == 1 || pdfView.displayMode == .singlePage
    if shouldCenterVertically && frame.height < pdfView.bounds.height {
      frame.origin.y = (pdfView.bounds.height - frame.height) / 2
    } else if !shouldCenterVertically && frame.height < pdfView.bounds.height {
      frame.origin.y = 0
    }

    documentView.frame = frame
  }

  private func applyViewerBackgroundColor() {
    containerView.backgroundColor = viewerBackgroundUIColor
    pdfView.backgroundColor = viewerBackgroundUIColor
    pdfView.subviews.forEach { subview in
      if let scrollView = subview as? UIScrollView {
        scrollView.backgroundColor = viewerBackgroundUIColor
      }
    }
  }
}
