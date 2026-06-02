package com.meedwire.pdfapi.view

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.PdfViewManagerDelegate
import com.facebook.react.viewmanagers.PdfViewManagerInterface

@ReactModule(name = PdfViewManager.NAME)
class PdfViewManager :
  SimpleViewManager<PdfContainerView>(),
  PdfViewManagerInterface<PdfContainerView> {

  private val delegate = PdfViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<PdfContainerView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): PdfContainerView =
    PdfContainerView(context)

  override fun onAfterUpdateTransaction(view: PdfContainerView) {
    super.onAfterUpdateTransaction(view)
    view.applyPendingSource()
  }

  override fun setSource(view: PdfContainerView, value: String?) {
    view.setSource(value)
  }

  override fun setViewerBackgroundColor(view: PdfContainerView, value: String?) {
    view.setViewerBackgroundColor(value)
  }

  override fun setInitialPage(view: PdfContainerView, value: Int) {
    view.setInitialPage(value.toDouble())
  }

  override fun setPageSpacing(view: PdfContainerView, value: Double) {
    view.setPageSpacing(value)
  }

  override fun setMaxZoom(view: PdfContainerView, value: Double) {
    view.setMaxZoom(value)
  }

  override fun setMaxPageResolution(view: PdfContainerView, value: Double) {
    view.setMaxPageResolution(value)
  }

  override fun setSinglePage(view: PdfContainerView, value: Boolean) {
    view.setSinglePage(value)
  }

  override fun setSearchHighlight(view: PdfContainerView, value: ReadableMap?) {
    view.setSearchHighlight(value)
  }

  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> {
    return mutableMapOf(
      "topLoad" to mapOf("registrationName" to "onLoad"),
      "topPageChange" to mapOf("registrationName" to "onPageChange"),
      "topError" to mapOf("registrationName" to "onError")
    )
  }

  companion object {
    const val NAME = "PdfView"
  }
}
