package com.meedwire.pdfapi.view

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap

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
    fun fromReadableMap(value: ReadableMap?): PdfSearchHighlight? {
      if (value == null) return null

      val requestId =
        if (value.hasKey("requestId")) value.getDouble("requestId").toInt() else 0
      val pageIndex =
        if (value.hasKey("pageIndex") && !value.isNull("pageIndex")) {
          value.getDouble("pageIndex").toInt()
        } else {
          null
        }

      return PdfSearchHighlight(
        requestId = requestId,
        pageIndex = pageIndex,
        bounds = boundsFromArray(
          if (value.hasKey("bounds")) value.getArray("bounds") else null
        ),
        focusBounds = boundsFromArray(
          if (value.hasKey("focusBounds")) value.getArray("focusBounds") else null
        )
      )
    }

    private fun boundsFromArray(array: ReadableArray?): List<PdfSearchBounds> {
      if (array == null) return emptyList()

      val result = mutableListOf<PdfSearchBounds>()
      for (index in 0 until array.size()) {
        val item = array.getMap(index) ?: continue
        result.add(
          PdfSearchBounds(
            x = item.getDouble("x").toFloat(),
            y = item.getDouble("y").toFloat(),
            width = item.getDouble("width").toFloat(),
            height = item.getDouble("height").toFloat()
          )
        )
      }
      return result
    }
  }
}
