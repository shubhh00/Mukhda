package com.mukhda.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val PACKAGE = "com.mukhda.app"
private const val WAIT_MS = 15_000L

/** Launches the app and waits for the launch curtain to part on the game. */
fun MacrobenchmarkScope.openGame() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.gone(By.textContains("NEW SHOW EVERY DAY")), WAIT_MS)
    device.wait(Until.hasObject(By.text("EASY")), WAIT_MS)
}

/**
 * What a player does in a visit, without using up a try: every tier, the search list, a clip, the
 * housefull calendar. Never taps Skip or Guess, so it can run on a real account.
 */
fun MacrobenchmarkScope.browseTheShow() {
    for (tier in listOf("MEDIUM", "HARD", "EXPERT", "LEGEND", "EASY")) {
        device.findObject(By.text(tier))?.click()
        device.waitForIdle()
    }
    // The guess box itself; its "Type a song or film" hint is a separate, uneditable text.
    device.findObject(By.clazz("android.widget.EditText"))?.let { field ->
        field.click()
        field.text = "tum hi"
        device.wait(Until.hasObject(By.textContains("·")), WAIT_MS)
        device.pressBack()
        device.pressBack()
    }
    device.findObject(By.desc("Play clip"))?.let {
        it.click()
        Thread.sleep(1_500)
    }
    // The streak badge: "N DAYS HOUSEFULL", or "FIRST SHOW" before a player's first try.
    device.findObject(By.text(Pattern.compile(".*(HOUSEFULL|FIRST SHOW)")))?.let {
        it.click()
        device.waitForIdle()
        device.pressBack()
    }
}
