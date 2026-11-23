package com.scluzlep.fxplusmodule

import android.app.Application
import com.scluzlep.fxplusmodule.shizuku.ShizukuStatusHelper
import com.scluzlep.fxplusmodule.xposed.ModuleStatus

class FxPlusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ModuleStatus.init(this)
        ShizukuStatusHelper.init(this)
    }
}
