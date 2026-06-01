package com.margelo.nitro.meedwire.pdfapi.document

import com.margelo.nitro.meedwire.pdfapi.support.PdfException
import java.util.concurrent.ConcurrentHashMap

internal class PdfDocumentStore {
  private val documents = ConcurrentHashMap<String, PdfDocumentHolder>()

  fun put(holder: PdfDocumentHolder) {
    documents[holder.id] = holder
  }

  fun get(documentId: String): PdfDocumentHolder {
    return documents[documentId]
      ?: throw PdfException("ERR_PDF_DOCUMENT_NOT_FOUND", "PDF document was not found or is already closed.")
  }

  fun close(documentId: String) {
    documents.remove(documentId)?.close()
  }

  fun closeAll() {
    documents.values.forEach { it.close() }
    documents.clear()
  }
}
