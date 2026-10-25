package com.mukhda.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code a launch and a visit to the show run, so it's compiled ahead of time at
 * install instead of running interpreted on first use. The launch also goes into the startup
 * profile, which lays that code out to load first; the visit goes into the baseline profile only.
 * Run with `./gradlew :app:generateReleaseBaselineProfile` and commit the profiles it writes into
 * :app.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        openGame()
    }

    @Test
    fun visit() = rule.collect(packageName = PACKAGE, includeInStartupProfile = false) {
        openGame()
        browseTheShow()
    }
}
