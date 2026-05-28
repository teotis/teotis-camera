package com.opencamera.app

import android.app.Application

class OpenCameraApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
