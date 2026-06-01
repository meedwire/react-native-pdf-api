package com.margelo.nitro.meedwire.pdfapi

import com.margelo.nitro.core.NullType
import com.margelo.nitro.core.Promise
import com.margelo.nitro.meedwire.pdfapi.document.PdfDocumentStore
import com.margelo.nitro.meedwire.pdfapi.document.PdfTextOperations
import com.margelo.nitro.meedwire.pdfapi.document.openPdfDocument
import com.margelo.nitro.meedwire.pdfapi.document.pdfCacheDirectory
import com.margelo.nitro.meedwire.pdfapi.document.renderPageToFile
import com.margelo.nitro.meedwire.pdfapi.support.pdfCapabilities

class HybridPdfApi : HybridPdfApiSpec() {
  private val documentStore = PdfDocumentStore()

  override fun getCapabilities(): PdfCapabilities = pdfCapabilities()

  override fun openDocumentAsync(uri: String): Promise<PdfOpenDocumentResult> {
    return Promise.parallel {
      val context = PdfApiContext.require()
      val holder = openPdfDocument(context, uri)
      documentStore.put(holder)

      PdfOpenDocumentResult(
        documentId = holder.id,
        pageCount = holder.renderer.pageCount.toDouble(),
        sourceUri = uri,
        capabilities = pdfCapabilities()
      )
    }
  }

  override fun closeDocumentAsync(documentId: String): Promise<Unit> {
    return Promise.parallel {
      documentStore.close(documentId)
    }
  }

  override fun closeAllDocumentsAsync(): Promise<Unit> {
    return Promise.parallel {
      documentStore.closeAll()
    }
  }

  override fun getMetadataAsync(documentId: String): Promise<PdfMetadata> {
    return Promise.parallel {
      val holder = documentStore.get(documentId)
      PdfMetadata(
        title = null,
        author = null,
        subject = null,
        creator = null,
        producer = null,
        keywords = null,
        creationDate = null,
        modificationDate = null,
        pageCount = holder.renderer.pageCount.toDouble(),
        isEncrypted = false,
        isLocked = false
      )
    }
  }

  override fun getPageInfoAsync(
    documentId: String,
    pageIndex: Double
  ): Promise<PdfPageInfo> {
    return Promise.parallel {
      val index = pageIndex.toInt()
      val holder = documentStore.get(documentId)
      holder.withPage(index) { page ->
        PdfPageInfo(
          pageIndex = index.toDouble(),
          width = page.width.toDouble(),
          height = page.height.toDouble(),
          rotation = 0.0,
          label = null,
          annotationCount = 0.0
        )
      }
    }
  }

  override fun renderPageAsync(
    documentId: String,
    pageIndex: Double,
    options: PdfRenderOptions?
  ): Promise<PdfRenderedPage> {
    return Promise.parallel {
      val rendered = renderPageToFile(
        holder = documentStore.get(documentId),
        context = PdfApiContext.require(),
        pageIndex = pageIndex.toInt(),
        options = options.toMap()
      )

      PdfRenderedPage(
        uri = rendered.getString("uri"),
        width = rendered.getNumber("width").toDouble(),
        height = rendered.getNumber("height").toDouble(),
        scale = rendered.getNumber("scale").toDouble(),
        pageIndex = rendered.getNumber("pageIndex").toDouble()
      )
    }
  }

  override fun getTextAsync(
    documentId: String,
    pageIndex: Double?
  ): Promise<Variant_NullType_String> {
    return Promise.parallel {
      val text = PdfTextOperations.getText(
        holder = documentStore.get(documentId),
        pageIndex = pageIndex?.toInt()
      )

      if (text == null) {
        Variant_NullType_String.create(NullType.NULL)
      } else {
        Variant_NullType_String.create(text)
      }
    }
  }

  override fun searchTextAsync(
    documentId: String,
    query: String,
    options: PdfSearchOptions?
  ): Promise<Array<PdfSearchResult>> {
    return Promise.parallel {
      PdfTextOperations.searchText(
        holder = documentStore.get(documentId),
        query = query,
        options = options.toMap()
      )
        .map { it.toPdfSearchResult() }
        .toTypedArray()
    }
  }

  override fun clearPdfCacheAsync(): Promise<Unit> {
    return Promise.parallel {
      documentStore.closeAll()
      val directory = pdfCacheDirectory(PdfApiContext.require())
      directory.deleteRecursively()
      directory.mkdirs()
    }
  }
}

private fun PdfRenderOptions?.toMap(): Map<String, Any?>? {
  if (this == null) return null

  return mapOf(
    "format" to format,
    "quality" to quality,
    "width" to width,
    "height" to height,
    "scale" to scale,
    "backgroundColor" to backgroundColor,
    "maxPixels" to maxPixels
  )
}

private fun PdfSearchOptions?.toMap(): Map<String, Any?>? {
  if (this == null) return null

  return mapOf(
    "pageIndex" to pageIndex,
    "caseSensitive" to caseSensitive,
    "maxResults" to maxResults,
    "focus" to focus,
    "highlight" to highlight,
    "resultIndex" to resultIndex
  )
}

private fun Map<String, Any>.getNumber(key: String): Number {
  return this[key] as? Number
    ?: error("PDF native result is missing numeric field \"$key\".")
}

private fun Map<String, Any>.getString(key: String): String {
  return this[key] as? String
    ?: error("PDF native result is missing string field \"$key\".")
}

private fun Map<String, Any?>.toPdfSearchResult(): PdfSearchResult {
  return PdfSearchResult(
    pageIndex = (this["pageIndex"] as? Number)?.toDouble() ?: 0.0,
    text = this["text"] as? String,
    bounds = boundsFromValue(this["bounds"]).toTypedArray()
  )
}

private fun boundsFromValue(value: Any?): List<PdfSearchBounds> {
  return (value as? List<*>)
    ?.mapNotNull { item ->
      val bound = item as? Map<*, *> ?: return@mapNotNull null
      PdfSearchBounds(
        x = (bound["x"] as? Number)?.toDouble() ?: return@mapNotNull null,
        y = (bound["y"] as? Number)?.toDouble() ?: return@mapNotNull null,
        width = (bound["width"] as? Number)?.toDouble() ?: return@mapNotNull null,
        height = (bound["height"] as? Number)?.toDouble() ?: return@mapNotNull null
      )
    }
    ?: emptyList()
}
