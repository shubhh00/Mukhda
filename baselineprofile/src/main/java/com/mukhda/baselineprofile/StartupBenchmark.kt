package com.mukhda.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, and a visit to the show, with and without the Baseline Profile, so its effect is a
 * measured number. Startup reports time to initial display (first frame) and to full display (the
 * show in, the curtain up); the visit reports frame times.
 * Run with `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun browseWithoutProfile() = browse(CompilationMode.None())

    @Test
    fun browseWithProfile() = browse(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        openGame()
    }

    private fun browse(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { openGame() },
    ) {
        browseTheShow()
    }
}
