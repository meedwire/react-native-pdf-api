package com.meedwire.pdfapi.support

import android.graphics.Color
import android.os.Build
import kotlin.math.roundToInt

/** Platform capabilities as a plain map, consumed by the module and the view. */
internal fun pdfCapabilities(): Map<String, Boolean> {
  val supportsApi35Features = Build.VERSION.SDK_INT >= SEARCH_SUPPORTED_API

  return mapOf(
    "supportsMetadata" to false,
    "supportsPageText" to supportsApi35Features,
    "supportsSearch" to supportsApi35Features,
    "supportsLinks" to false,
    "supportsForms" to false,
    "supportsAnnotations" to false
  )
}

internal fun numberOption(
  options: Map<String, Any?>?,
  key: String,
  defaultValue: Double
): Double {
  return when (val value = options?.get(key)) {
    is Number -> value.toDouble()
    else -> defaultValue
  }
}

internal fun stringOption(
  options: Map<String, Any?>?,
  key: String,
  defaultValue: String
): String {
  return options?.get(key) as? String ?: defaultValue
}

internal fun parseBackgroundColor(color: String?, defaultColor: Int = Color.WHITE): Int {
  return try {
    if (color == null) defaultColor else Color.parseColor(color)
  } catch (_: IllegalArgumentException) {
    defaultColor
  }
}

internal fun qualityOption(options: Map<String, Any?>?): Int {
  val quality = numberOption(options, "quality", 0.9)
  val normalized = if (quality <= 1) quality * 100 else quality

  return normalized.roundToInt().coerceIn(0, 100)
}
