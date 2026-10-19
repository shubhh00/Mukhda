package com.mukhda.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds made with `-Pmukhda.appcheck=true` use a debug token: on first launch it's printed
 * to logcat ("App Check debug token") and must be added once under App Check → Manage debug
 * tokens in the Firebase console.
 */
fun Application.installAppCheck() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
