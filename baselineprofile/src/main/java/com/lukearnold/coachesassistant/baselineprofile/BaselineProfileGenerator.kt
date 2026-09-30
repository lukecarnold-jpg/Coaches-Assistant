package com.lukearnold.coachesassistant.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code the coach's journey runs, for Android to compile ahead of time. Run with
 * `./gradlew :app:generateReleaseBaselineProfile`; the result is committed under
 * app/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Just the launch, which also goes in the startup profile (used to lay out the app's code). */
    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }

    /** The coach's journey after launch: compiled ahead of time, but not part of startup. */
    @Test
    fun coachJourney() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        coachThroughTheApp()
    }
}
