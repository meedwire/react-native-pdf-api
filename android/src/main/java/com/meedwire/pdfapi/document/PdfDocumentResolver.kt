package com.meedwire.pdfapi.document

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.meedwire.pdfapi.support.PdfException
import java.io.File
import java.util.UUID

/** A local PDF file; [isTemporary] marks a private copy owned by the document. */
private class ResolvedPdfFile(val file: File, val isTemporary: Boolean)

private fun resolvePdfFile(context: Context, uriString: String): ResolvedPdfFile {
  val uri = Uri.parse(uriString)

  if (uri.scheme == "content") {
    return ResolvedPdfFile(copyContentUri(context, uri), isTemporary = true)
  }

  if (uri.scheme == "file") {
    val path = uri.path ?: throw PdfException("ERR_PDF_SOURCE", "Invalid file uri.")
    return ResolvedPdfFile(File(path), isTemporary = false)
  }

  if (uri.scheme == null) {
    return ResolvedPdfFile(File(uriString), isTemporary = false)
  }

  throw PdfException("ERR_PDF_SOURCE", "Unsupported PDF uri scheme: ${uri.scheme}.")
}

/**
 * PdfRenderer needs a seekable file, so a content:// source is copied into
 * `tmp/`. The copy belongs to the opened document and is deleted when it
 * closes; the cache sweep removes copies orphaned by a crash.
 */
private fun copyContentUri(context: Context, uri: Uri): File {
  PdfCacheRegistry.shared.sweepOnce(pdfCacheDirectory(context))

  val outputFile = File(
    pdfCacheSubdirectory(context, PdfCacheConfig.TEMP_DIRECTORY_NAME),
    "source-${UUID.randomUUID()}.pdf"
  )

  try {
    val input = context.contentResolver.openInputStream(uri)
      ?: throw PdfException("ERR_PDF_SOURCE", "Unable to read content uri.")

    input.use { source ->
      outputFile.outputStream().use { output -> source.copyTo(output) }
    }

    return outputFile
  } catch (error: Throwable) {
    outputFile.delete()

    throw when (error) {
      is PdfException -> error
      is SecurityException ->
        PdfException("ERR_PDF_SOURCE", "Permission denied reading content uri.", error)
      else -> PdfException("ERR_PDF_SOURCE", "Unable to read content uri.", error)
    }
  }
}

internal fun openPdfDocument(context: Context, uri: String): PdfDocumentHolder {
  val source = resolvePdfFile(context, uri)
  val file = source.file

  if (!file.exists()) {
    throw PdfException("ERR_PDF_SOURCE", "PDF file does not exist.")
  }

  var descriptor: ParcelFileDescriptor? = null
  try {
    val openedDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor = openedDescriptor
    val renderer = PdfRenderer(openedDescriptor)
    val documentId = UUID.randomUUID().toString()

    return PdfDocumentHolder(
      documentId,
      uri,
      file,
      openedDescriptor,
      renderer,
      deleteFileOnClose = source.isTemporary
    )
  } catch (error: Throwable) {
    // The renderer only owns the descriptor once constructed: a locked or
    // corrupt file would otherwise leak it (and its content:// copy).
    runCatching { descriptor?.close() }
    if (source.isTemporary) {
      file.delete()
    }

    if (error is SecurityException) {
      throw PdfException("ERR_PDF_LOCKED", "Protected PDFs are not supported.", error)
    }

    throw PdfException("ERR_PDF_OPEN", "Unable to open PDF document.", error)
  }
}
