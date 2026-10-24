package com.mukhda.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code a launch and a visit to the show run, so it's compiled ahead of time at
 * install instead of running interpreted on first use. Run with
 * `./gradlew :app:generateReleaseBaselineProfile` and commit the profile it writes into :app.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        openGame()
        browseTheShow()
    }
}
