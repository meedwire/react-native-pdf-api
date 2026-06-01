package com.margelo.nitro.meedwire.pdfapi.document

import android.os.Build
import com.margelo.nitro.meedwire.pdfapi.support.PdfException
import com.margelo.nitro.meedwire.pdfapi.support.SEARCH_SUPPORTED_API

internal object PdfTextOperations {
  fun getText(holder: PdfDocumentHolder, pageIndex: Int?): String? {
    assertSupported("ERR_PDF_TEXT_UNSUPPORTED", "PDF text extraction is not supported on this Android version.")

    if (pageIndex != null) {
      return holder.withPage(pageIndex) { page ->
        page.textContents.joinToString(separator = "") { it.text }
      }
    }

    return (0 until holder.renderer.pageCount).joinToString(separator = "\n") { index ->
      holder.withPage(index) { page ->
        page.textContents.joinToString(separator = "") { it.text }
      }
    }
  }

  fun searchText(
    holder: PdfDocumentHolder,
    query: String,
    options: Map<String, Any?>?
  ): List<Map<String, Any?>> {
    assertSupported("ERR_PDF_SEARCH_UNSUPPORTED", "PDF search is not supported on this Android version.")

    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return emptyList()

    val pageFilter = (options?.get("pageIndex") as? Number)?.toInt()
    val maxResults = ((options?.get("maxResults") as? Number)?.toInt() ?: 100).coerceAtLeast(0)
    val caseSensitive = options?.get("caseSensitive") as? Boolean ?: false
    if (maxResults == 0) return emptyList()

    val pageRange = pageFilter?.let { it..it } ?: 0 until holder.renderer.pageCount
    val results = mutableListOf<Map<String, Any?>>()

    for (pageIndex in pageRange) {
      holder.withPage(pageIndex) { page ->
        val pageText = if (caseSensitive) {
          page.textContents.joinToString(separator = "") { it.text }
        } else {
          null
        }
        page.searchText(trimmedQuery).forEach { match ->
          val textStartIndex = match.textStartIndex.coerceIn(0, pageText?.length ?: 0)
          val matchText = pageText?.substring(
            textStartIndex,
            (textStartIndex + trimmedQuery.length).coerceAtMost(pageText.length)
          )
          if (caseSensitive && matchText != trimmedQuery) {
            return@forEach
          }

          results.add(
            mapOf(
              "pageIndex" to pageIndex,
              "text" to (matchText ?: trimmedQuery),
              "bounds" to match.bounds.map { bounds ->
                mapOf(
                  "x" to bounds.left,
                  "y" to bounds.top,
                  "width" to bounds.width(),
                  "height" to bounds.height()
                )
              }
            )
          )
        }
      }

      if (results.size >= maxResults) {
        return results.take(maxResults)
      }
    }

    return results.take(maxResults)
  }

  private fun assertSupported(code: String, message: String) {
    if (Build.VERSION.SDK_INT < SEARCH_SUPPORTED_API) {
      throw PdfException(code, message)
    }
  }
}
