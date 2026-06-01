import Foundation
import PDFKit

internal enum PdfSearchOptionKey {
  static let pageIndex = "pageIndex"
  static let maxResults = "maxResults"
  static let caseSensitive = "caseSensitive"
}

internal enum PdfTextOperations {
  static func getText(
    for holder: PdfDocumentHolder,
    pageIndex: Int?
  ) throws -> String? {
    if let pageIndex {
      return try holder.page(at: pageIndex).string
    }

    return holder.document.string
  }

  static func searchText(
    for holder: PdfDocumentHolder,
    query: String,
    options: [String: Any]?
  ) throws -> [[String: Any]] {
    let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)

    guard !trimmedQuery.isEmpty else {
      return []
    }

    let pageFilter = optionsInt(options, PdfSearchOptionKey.pageIndex)
    let maxResults = max(0, Int(optionsNumber(options, PdfSearchOptionKey.maxResults, default: 100)))
    guard maxResults > 0 else {
      return []
    }
    let caseSensitive = options?[PdfSearchOptionKey.caseSensitive] as? Bool ?? false
    let findOptions: NSString.CompareOptions = caseSensitive ? [] : [.caseInsensitive]
    let selections = holder.document.findString(trimmedQuery, withOptions: findOptions)
    var results: [[String: Any]] = []

    for selection in selections {
      for page in selection.pages {
        let pageIndex = holder.document.index(for: page)
        if let pageFilter, pageIndex != pageFilter {
          continue
        }

        let bounds = selection.bounds(for: page)
        results.append([
          "pageIndex": pageIndex,
          "text": selection.string ?? trimmedQuery,
          "bounds": [[
            "x": bounds.origin.x,
            "y": bounds.origin.y,
            "width": bounds.width,
            "height": bounds.height
          ]]
        ])

        if results.count >= maxResults {
          return results
        }
      }
    }

    return results
  }
}
