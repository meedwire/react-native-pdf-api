package com.margelo.nitro.meedwire.pdfapi

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import com.margelo.nitro.meedwire.pdfapi.views.HybridPdfViewManager

class NitroPdfApiPackage : BaseReactPackage() {
  override fun getModule(
    name: String,
    reactContext: ReactApplicationContext
  ): NativeModule? {
    PdfApiContext.set(reactContext)
    return null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
    ReactModuleInfoProvider { HashMap() }

  override fun createViewManagers(
    reactContext: ReactApplicationContext
  ): List<ViewManager<*, *>> {
    PdfApiContext.set(reactContext)
    return listOf(HybridPdfViewManager())
  }

  companion object {
    init {
      NitroPdfApiOnLoad.initializeNative()
    }
  }
}
