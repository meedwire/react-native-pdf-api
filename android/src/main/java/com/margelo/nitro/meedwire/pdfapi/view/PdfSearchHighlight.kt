package com.margelo.nitro.meedwire.pdfapi.view

import com.margelo.nitro.meedwire.pdfapi.PdfViewerSearchHighlight

internal data class PdfSearchBounds(
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float
)

internal data class PdfSearchHighlight(
  val requestId: Int,
  val pageIndex: Int?,
  val bounds: List<PdfSearchBounds>,
  val focusBounds: List<PdfSearchBounds>
) {
  companion object {
    fun fromNitro(value: PdfViewerSearchHighlight?): PdfSearchHighlight? {
      if (value == null) return null

      val requestId = value.requestId.toInt()
      val pageIndex = value.pageIndex?.toInt()
      val bounds = boundsFromValue(value.bounds)
      val focusBounds = boundsFromValue(value.focusBounds)

      return PdfSearchHighlight(
        requestId = requestId,
        pageIndex = pageIndex,
        bounds = bounds,
        focusBounds = focusBounds
      )
    }

    private fun boundsFromValue(
      value: Array<com.margelo.nitro.meedwire.pdfapi.PdfSearchBounds>?
    ): List<PdfSearchBounds> {
      return value
        ?.map { bound ->
          PdfSearchBounds(
            x = bound.x.toFloat(),
            y = bound.y.toFloat(),
            width = bound.width.toFloat(),
            height = bound.height.toFloat()
          )
        }
        ?: emptyList()
    }
  }
}
