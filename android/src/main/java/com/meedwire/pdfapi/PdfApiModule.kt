package com.meedwire.pdfapi

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.meedwire.pdfapi.document.PdfDocumentStore
import com.meedwire.pdfapi.document.PdfTextOperations
import com.meedwire.pdfapi.document.openPdfDocument
import com.meedwire.pdfapi.document.pdfCacheDirectory
import com.meedwire.pdfapi.document.preparePdfSource
import com.meedwire.pdfapi.document.renderPageToFile
import com.meedwire.pdfapi.support.PdfException
import com.meedwire.pdfapi.support.pdfCapabilities
import org.json.JSONObject
import java.util.concurrent.Executors

class PdfApiModule(reactContext: ReactApplicationContext) :
  NativePdfApiSpec(reactContext) {

  private val documentStore = PdfDocumentStore()
  private val executor = Executors.newCachedThreadPool()

  override fun getCapabilities(): WritableMap =
    Arguments.makeNativeMap(pdfCapabilities().toMap())

  override fun prepareSourceAsync(
    uri: String,
    headersJson: String,
    fileName: String,
    promise: Promise
  ) {
    runAsync(promise) {
      Arguments.makeNativeMap(
        preparePdfSource(reactApplicationContext, uri, headersJson, fileName)
      )
    }
  }

  override fun openDocumentAsync(uri: String, promise: Promise) {
    runAsync(promise) {
      val holder = openPdfDocument(reactApplicationContext, uri)
      documentStore.put(holder)
      Arguments.makeNativeMap(
        mapOf(
          "documentId" to holder.id,
          "pageCount" to holder.renderer.pageCount,
          "sourceUri" to uri,
          "capabilities" to pdfCapabilities()
        )
      )
    }
  }

  override fun closeDocumentAsync(documentId: String, promise: Promise) {
    runAsync(promise) {
      documentStore.close(documentId)
      null
    }
  }

  override fun closeAllDocumentsAsync(promise: Promise) {
    runAsync(promise) {
      documentStore.closeAll()
      null
    }
  }

  override fun getMetadataAsync(documentId: String, promise: Promise) {
    runAsync(promise) {
      val holder = documentStore.get(documentId)
      Arguments.makeNativeMap(
        mapOf(
          "pageCount" to holder.renderer.pageCount,
          "isEncrypted" to false,
          "isLocked" to false
        )
      )
    }
  }

  override fun getPageInfoAsync(documentId: String, pageIndex: Double, promise: Promise) {
    runAsync(promise) {
      val index = pageIndex.toInt()
      documentStore.get(documentId).withPage(index) { page ->
        Arguments.makeNativeMap(
          mapOf(
            "pageIndex" to index,
            "width" to page.width,
            "height" to page.height,
            "rotation" to 0,
            "annotationCount" to 0
          )
        )
      }
    }
  }

  override fun renderPageAsync(
    documentId: String,
    pageIndex: Double,
    optionsJson: String,
    promise: Promise
  ) {
    runAsync(promise) {
      Arguments.makeNativeMap(
        renderPageToFile(
          documentStore.get(documentId),
          reactApplicationContext,
          pageIndex.toInt(),
          parseOptions(optionsJson)
        )
      )
    }
  }

  override fun getTextAsync(documentId: String, pageIndex: Double, promise: Promise) {
    runAsync(promise) {
      val index = if (pageIndex < 0) null else pageIndex.toInt()
      PdfTextOperations.getText(documentStore.get(documentId), index)
    }
  }

  override fun searchTextAsync(
    documentId: String,
    query: String,
    optionsJson: String,
    promise: Promise
  ) {
    runAsync(promise) {
      Arguments.makeNativeArray(
        PdfTextOperations.searchText(
          documentStore.get(documentId),
          query,
          parseOptions(optionsJson)
        )
      )
    }
  }

  override fun clearPdfCacheAsync(promise: Promise) {
    runAsync(promise) {
      documentStore.closeAll()
      val directory = pdfCacheDirectory(reactApplicationContext)
      directory.deleteRecursively()
      directory.mkdirs()
      null
    }
  }

  private fun runAsync(promise: Promise, work: () -> Any?) {
    executor.execute {
      try {
        promise.resolve(work())
      } catch (error: PdfException) {
        promise.reject(error.code, error.message, error)
      } catch (error: Throwable) {
        promise.reject("ERR_PDF", error.message, error)
      }
    }
  }

  private fun parseOptions(optionsJson: String): Map<String, Any?>? {
    if (optionsJson.isEmpty()) return null

    return runCatching {
      val json = JSONObject(optionsJson)
      buildMap<String, Any?> {
        json.keys().forEach { key ->
          put(key, if (json.isNull(key)) null else json.get(key))
        }
      }
    }.getOrNull()
  }

  companion object {
    const val NAME = NativePdfApiSpec.NAME
  }
}
