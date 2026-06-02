package com.meedwire.pdfapi.document

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.meedwire.pdfapi.support.PdfException
import java.io.File

internal data class PdfDocumentHolder(
  val id: String,
  val sourceUri: String,
  val file: File,
  val descriptor: ParcelFileDescriptor,
  val renderer: PdfRenderer
) {
  private val lock = Any()

  fun <T> withPage(pageIndex: Int, block: (PdfRenderer.Page) -> T): T = synchronized(lock) {
    if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
      throw PdfException("ERR_PDF_PAGE_OUT_OF_BOUNDS", "PDF page index is out of bounds.")
    }

    renderer.openPage(pageIndex).use(block)
  }

  fun close() {
    runCatching { renderer.close() }
    runCatching { descriptor.close() }
  }
}
