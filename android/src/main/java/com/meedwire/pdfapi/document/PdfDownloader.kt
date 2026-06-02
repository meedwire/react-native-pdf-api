package com.meedwire.pdfapi.document

import android.content.Context
import android.net.Uri
import com.meedwire.pdfapi.support.PdfException
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

/**
 * Downloads a remote (`http`/`https`) PDF into the package cache and returns
 * `{ uri, fromCache }`. Local URIs are returned unchanged. Runs synchronously;
 * callers already dispatch it onto a background thread.
 */
internal fun preparePdfSource(
  context: Context,
  uri: String,
  headersJson: String,
  fileName: String
): Map<String, Any> {
  val isRemote = uri.startsWith("http://") || uri.startsWith("https://")
  if (!isRemote) {
    return mapOf("uri" to uri, "fromCache" to false)
  }

  val destination = File(pdfCacheDirectory(context), pdfSafeFileName(uri, fileName))
  if (destination.exists()) {
    return mapOf("uri" to Uri.fromFile(destination).toString(), "fromCache" to true)
  }

  val connection = URL(uri).openConnection() as HttpURLConnection
  try {
    connection.connectTimeout = 30_000
    connection.readTimeout = 30_000
    parseHeaders(headersJson).forEach { (key, value) ->
      connection.setRequestProperty(key, value)
    }
    connection.connect()

    val status = connection.responseCode
    if (status >= 400) {
      throw PdfException("ERR_PDF_SOURCE", "Failed to download PDF (HTTP $status).")
    }

    connection.inputStream.use { input ->
      destination.outputStream().use { output -> input.copyTo(output) }
    }
  } catch (error: PdfException) {
    throw error
  } catch (error: Throwable) {
    throw PdfException("ERR_PDF_SOURCE", error.message ?: "Failed to download PDF.", error)
  } finally {
    connection.disconnect()
  }

  return mapOf("uri" to Uri.fromFile(destination).toString(), "fromCache" to false)
}

private fun pdfSafeFileName(uri: String, override: String): String {
  if (override.isNotEmpty()) {
    return sanitize(override)
  }

  val lastComponent = uri.substringAfterLast('/').substringBefore('?').ifEmpty { "document.pdf" }
  val decoded = runCatching { URLDecoder.decode(lastComponent, "UTF-8") }.getOrDefault(lastComponent)
  val normalized = sanitize(decoded)

  return if (normalized.lowercase(Locale.ROOT).endsWith(".pdf")) normalized else "$normalized.pdf"
}

private fun sanitize(value: String): String = value.replace(Regex("[^a-zA-Z0-9._-]"), "_")

private fun parseHeaders(headersJson: String): Map<String, String> {
  if (headersJson.isEmpty()) return emptyMap()

  return runCatching {
    val json = JSONObject(headersJson)
    buildMap {
      json.keys().forEach { key -> put(key, json.getString(key)) }
    }
  }.getOrDefault(emptyMap())
}
