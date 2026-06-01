import Foundation
import UIKit

internal let pdfDefaultMaxPixels = 16_777_216
internal let pdfDefaultViewBackgroundColor = UIColor(
  red: 244.0 / 255.0,
  green: 244.0 / 255.0,
  blue: 241.0 / 255.0,
  alpha: 1
)

internal func pdfCapabilities() -> PdfCapabilities {
  PdfCapabilities(
    supportsMetadata: true,
    supportsPageText: true,
    supportsSearch: true,
    supportsLinks: true,
    supportsForms: false,
    supportsAnnotations: true
  )
}
