package com.margelo.nitro.meedwire.pdfapi.document

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.margelo.nitro.meedwire.pdfapi.support.PdfException
import java.io.File
import java.util.UUID

internal fun pdfCacheDirectory(context: Context): File {
  return File(context.cacheDir, "react-native-pdf-api").also { directory ->
    directory.mkdirs()
  }
}

private fun resolvePdfFile(context: Context, uriString: String): File {
  val uri = Uri.parse(uriString)

  if (uri.scheme == "content") {
    val outputFile = File(
      pdfCacheDirectory(context),
      "source-${UUID.randomUUID()}.pdf"
    )

    context.contentResolver.openInputStream(uri)?.use { input ->
      outputFile.outputStream().use { output ->
        input.copyTo(output)
      }
    } ?: throw PdfException("ERR_PDF_SOURCE", "Unable to read content uri.")

    return outputFile
  }

  if (uri.scheme == "file") {
    return File(uri.path ?: throw PdfException("ERR_PDF_SOURCE", "Invalid file uri."))
  }

  if (uri.scheme == null) {
    return File(uriString)
  }

  throw PdfException("ERR_PDF_SOURCE", "Unsupported PDF uri scheme: ${uri.scheme}.")
}

internal fun openPdfDocument(context: Context, uri: String): PdfDocumentHolder {
  val file = resolvePdfFile(context, uri)

  if (!file.exists()) {
    throw PdfException("ERR_PDF_SOURCE", "PDF file does not exist.")
  }

  try {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(descriptor)
    val documentId = UUID.randomUUID().toString()

    return PdfDocumentHolder(documentId, uri, file, descriptor, renderer)
  } catch (error: SecurityException) {
    throw PdfException("ERR_PDF_LOCKED", "Protected PDFs are not supported.", error)
  } catch (error: Throwable) {
    throw PdfException("ERR_PDF_OPEN", "Unable to open PDF document.", error)
  }
}
