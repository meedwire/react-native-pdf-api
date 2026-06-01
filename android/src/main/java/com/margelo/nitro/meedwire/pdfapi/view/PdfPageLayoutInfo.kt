package com.margelo.nitro.meedwire.pdfapi.view

import kotlin.math.max
import kotlin.math.roundToInt

internal data class PdfPageLayoutInfo(
  val pageIndex: Int,
  val width: Int,
  val height: Int
) {
  fun targetBitmapSize(maxPageResolution: Int): Pair<Int, Int> {
    val maxPageSide = max(width, height).coerceAtLeast(1)
    val scale = maxPageResolution / maxPageSide.toDouble()

    return Pair(
      max(1, (width * scale).roundToInt()),
      max(1, (height * scale).roundToInt())
    )
  }
}
