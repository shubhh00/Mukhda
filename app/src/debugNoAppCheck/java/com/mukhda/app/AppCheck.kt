package com.mukhda.app

import android.app.Application

/**
 * Other debug builds leave App Check out entirely: the emulators don't check it, and the real
 * server doesn't require it until it's switched on at publish.
 */
@Suppress("UnusedReceiverParameter")
fun Application.installAppCheck() = Unit
