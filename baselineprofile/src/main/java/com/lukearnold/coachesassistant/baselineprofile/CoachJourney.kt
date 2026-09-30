package com.lukearnold.coachesassistant.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "com.lukearnold.coachesassistant"

private const val WAIT_MILLIS = 15_000L

/**
 * What a coach does most, for the baseline profile to cover: pick a sport (on a fresh install),
 * add a player, create a game, scroll the Game tab, and visit every tab. Tolerant of existing
 * data, so it can run repeatedly.
 */
fun MacrobenchmarkScope.coachThroughTheApp() {
    // A fresh install opens on the sport picker.
    device.wait(Until.hasObject(By.text("Basketball")), 3_000)
    device.findObject(By.text("Basketball"))?.click()
    device.wait(Until.hasObject(By.text("Roster")), WAIT_MILLIS)

    openTab("Roster")
    val fields = device.findObjects(By.clazz("android.widget.EditText"))
    if (fields.size >= 2) {
        fields[0].text = "Alex"
        fields[1].text = "4"
        device.findObject(By.text("Add player"))?.click()
        device.waitForIdle()
    }

    openTab("Game")
    device.findObject(By.text("Create game"))?.click()
    device.waitForIdle()
    scrollThroughTheScreen()

    for (tab in listOf("Training", "History", "Settings", "Game")) openTab(tab)
}

private fun MacrobenchmarkScope.openTab(label: String) {
    device.wait(Until.hasObject(By.text(label)), WAIT_MILLIS)
    device.findObject(By.text(label))?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollThroughTheScreen() {
    val scrollable = device.findObject(By.scrollable(true)) ?: return
    scrollable.setGestureMargin(device.displayWidth / 5)
    repeat(3) { scrollable.fling(Direction.DOWN) }
    repeat(3) { scrollable.fling(Direction.UP) }
    device.waitForIdle()
}
