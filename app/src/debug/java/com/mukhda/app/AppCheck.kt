package com.mukhda.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.mukhda.data.BuildConfig

/**
 * Debug builds built with `-Pmukhda.appcheck=true` use a debug token: on first launch it's printed
 * to logcat ("App Check debug token") and must be added once under App Check → Manage debug
 * tokens in the Firebase console. Other debug builds skip App Check: the emulators don't check it,
 * and the real server doesn't require it until it's switched on at publish.
 */
fun Application.installAppCheck() {
    if (!BuildConfig.USE_APP_CHECK_DEBUG) return
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
