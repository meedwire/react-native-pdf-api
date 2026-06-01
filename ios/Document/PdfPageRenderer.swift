import PDFKit
import UIKit

internal enum PdfPageRenderer {
  static func pageInfo(
    for holder: PdfDocumentHolder,
    pageIndex: Int
  ) throws -> [String: Any] {
    let page = try holder.page(at: pageIndex)
    let bounds = page.bounds(for: .mediaBox)
    var info: [String: Any] = [
      "pageIndex": pageIndex,
      "width": bounds.width,
      "height": bounds.height,
      "rotation": page.rotation,
      "annotationCount": page.annotations.count
    ]

    if let label = page.label {
      info["label"] = label
    }

    return info
  }

  static func renderPage(
    for holder: PdfDocumentHolder,
    pageIndex: Int,
    options: [String: Any]?
  ) throws -> [String: Any] {
    let page = try holder.page(at: pageIndex)
    let bounds = page.bounds(for: .mediaBox)
    let scale = CGFloat(optionsNumber(options, "scale", default: 1))
    let requestedWidth = CGFloat(optionsNumber(options, "width", default: 0))
    let requestedHeight = CGFloat(optionsNumber(options, "height", default: 0))
    let format = optionsString(options, "format", default: "png")
    let maxPixels = Int(optionsNumber(options, "maxPixels", default: Double(pdfDefaultMaxPixels)))

    var targetWidth = requestedWidth > 0 ? requestedWidth : bounds.width * scale
    var targetHeight = requestedHeight > 0 ? requestedHeight : bounds.height * scale

    if requestedWidth > 0 && requestedHeight <= 0 {
      targetHeight = requestedWidth * bounds.height / bounds.width
    }

    if requestedHeight > 0 && requestedWidth <= 0 {
      targetWidth = requestedHeight * bounds.width / bounds.height
    }

    targetWidth = max(1, targetWidth.rounded())
    targetHeight = max(1, targetHeight.rounded())

    if Int(targetWidth * targetHeight) > maxPixels {
      throw pdfException("ERR_PDF_RENDER_TOO_LARGE", "Requested render size exceeds maxPixels.")
    }

    let targetSize = CGSize(width: targetWidth, height: targetHeight)
    let rendererFormat = UIGraphicsImageRendererFormat.default()
    rendererFormat.opaque = true
    let backgroundColor = parseColor(options?["backgroundColor"] as? String)

    let image = UIGraphicsImageRenderer(size: targetSize, format: rendererFormat).image { context in
      backgroundColor.setFill()
      context.fill(CGRect(origin: .zero, size: targetSize))

      let cgContext = context.cgContext
      cgContext.saveGState()
      cgContext.translateBy(x: 0, y: targetSize.height)
      cgContext.scaleBy(
        x: targetSize.width / bounds.width,
        y: -targetSize.height / bounds.height
      )
      cgContext.translateBy(x: -bounds.origin.x, y: -bounds.origin.y)
      page.draw(with: .mediaBox, to: cgContext)
      cgContext.restoreGState()
    }

    let fileExtension = format == "jpeg" ? "jpg" : "png"
    let outputUrl = try pdfCacheDirectory()
      .appendingPathComponent("\(holder.id)-\(pageIndex)-\(UUID().uuidString).\(fileExtension)")

    let data: Data?
    if format == "jpeg" {
      let quality = CGFloat(min(max(optionsNumber(options, "quality", default: 0.9), 0), 1))
      data = image.jpegData(compressionQuality: quality)
    } else {
      data = image.pngData()
    }

    guard let data else {
      throw pdfException("ERR_PDF_RENDER", "Unable to encode rendered PDF page.")
    }

    try data.write(to: outputUrl, options: .atomic)

    return [
      "uri": outputUrl.absoluteString,
      "width": Int(targetWidth),
      "height": Int(targetHeight),
      "scale": targetWidth / bounds.width,
      "pageIndex": pageIndex
    ]
  }
}
