package com.mukhda.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MukhdaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before any call to the server, so every call carries an App Check token.
        installAppCheck()
    }
}
