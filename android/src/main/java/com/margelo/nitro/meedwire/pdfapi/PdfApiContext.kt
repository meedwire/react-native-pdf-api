package com.margelo.nitro.meedwire.pdfapi

import android.content.Context
import com.margelo.nitro.meedwire.pdfapi.support.PdfException

internal object PdfApiContext {
  @Volatile
  private var appContext: Context? = null

  fun set(context: Context) {
    appContext = context.applicationContext
  }

  fun require(): Context {
    return appContext
      ?: throw PdfException("ERR_PDF_CONTEXT", "React context is not available.")
  }
}
