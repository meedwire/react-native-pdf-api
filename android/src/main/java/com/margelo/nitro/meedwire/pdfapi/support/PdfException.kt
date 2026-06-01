package com.margelo.nitro.meedwire.pdfapi.support

internal class PdfException(
  val code: String,
  message: String,
  cause: Throwable? = null
) : RuntimeException(message, cause)
