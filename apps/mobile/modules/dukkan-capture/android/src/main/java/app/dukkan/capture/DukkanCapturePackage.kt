package app.dukkan.capture

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class DukkanCapturePackage : BaseReactPackage() {

  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
    if (name == DukkanCaptureModule.NAME) DukkanCaptureModule(reactContext) else null

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
    ReactModuleInfoProvider {
      mapOf(
        DukkanCaptureModule.NAME to
          ReactModuleInfo(DukkanCaptureModule.NAME, DukkanCaptureModule.NAME, false, false, false, true),
      )
    }
}
