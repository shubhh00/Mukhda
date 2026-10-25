package com.mukhda.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

const val PACKAGE = "com.mukhda.app"
private const val WAIT_MS = 15_000L
private const val EDIT_TEXT = "android.widget.EditText"
private val TIERS = listOf("EASY", "MEDIUM", "HARD", "EXPERT", "LEGEND")

/** Launches the app and waits for the launch curtain to part on the game; fails if it doesn't. */
fun MacrobenchmarkScope.openGame() {
    pressHome()
    startActivityAndWait()
    check(device.wait(Until.gone(By.textContains("NEW SHOW EVERY DAY")), WAIT_MS)) { "The launch curtain never opened" }
    require(By.text("EASY"), "The tier tabs")
}

/**
 * What a player does in a visit, without using up a try: every tier, the search list, a clip, the
 * housefull calendar. Never taps Skip or Guess, so it can run on a real account. Every step must
 * happen, so a changed screen fails the run instead of quietly recording less. Needs a tier still
 * being played today, which has the guess box and the clip.
 */
fun MacrobenchmarkScope.browseTheShow() {
    for (tier in TIERS) {
        require(By.text(tier), "The $tier tab").click()
        device.waitForIdle()
    }
    val open = TIERS.firstOrNull { tier ->
        require(By.text(tier), "The $tier tab").click()
        device.wait(Until.hasObject(By.clazz(EDIT_TEXT)), 2_000)
    } ?: error("Every tier is finished today: the journey needs one still being played")

    // The guess box itself; its "Type a song or film" hint is a separate, uneditable text.
    require(By.clazz(EDIT_TEXT), "The guess box on $open").apply {
        click()
        text = "tum hi"
    }
    // Its header reads "PICK A SONG" or "5 OF N SHOWN", but always has the replay button.
    require(By.textContains("REPLAY"), "The search list")
    device.pressBack()
    device.pressBack()

    require(By.desc("Play clip"), "The play button").click()
    Thread.sleep(1_500)

    // The streak badge: "N DAYS HOUSEFULL", or "FIRST SHOW" before a player's first try.
    require(By.text(Pattern.compile(".*(HOUSEFULL|FIRST SHOW)")), "The streak badge").click()
    require(By.text("BEST RUN"), "The housefull calendar")
    device.pressBack()
}

/** Waits for [selector] and returns it, or fails the run naming [what] was missing. */
private fun MacrobenchmarkScope.require(selector: BySelector, what: String): UiObject2 =
    device.wait(Until.findObject(selector), WAIT_MS) ?: error("$what didn't appear")
