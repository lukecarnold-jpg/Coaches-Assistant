package com.lonrad.coachesassistant.ai

import android.content.Intent
import android.net.Uri
import com.lonrad.coachesassistant.data.Sport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrillReportTest {

    private val drill = Drill(
        title = "Dribble Relay",
        description = "Players dribble around cones and back.",
        teachingPoints = "Keep your head up.",
    )
    private val source = DrillSource(Sport.NETBALL, "Under 12's", AiProvider.ANTHROPIC, "claude-sonnet-5")

    private fun report(details: String = "") =
        buildDrillReport(drill, source, DrillReportReason.UNSAFE, details, appVersion = "4.3.0", androidVersion = "16")

    @Test
    fun buildDrillReport_holdsTheReasonHowTheDrillWasMadeAndTheDrill() {
        val report = report(details = "  Tackling drill for under 8s  ")

        assertEquals("Coaches Assistant drill report: Unsafe for players", report.subject)
        assertEquals(
            """
            Reason: Unsafe for players
            Details: Tackling drill for under 8s

            Sport: NETBALL
            Age group: Under 12's
            Provider: ANTHROPIC
            Model: claude-sonnet-5
            App version: 4.3.0
            Android: 16

            --- Drill ---
            Dribble Relay

            Players dribble around cones and back.

            Keep your head up.
            """.trimIndent(),
            report.body,
        )
    }

    @Test
    fun buildDrillReport_leavesOutBlankDetails() {
        assertFalse("Details:" in report(details = "   ").body)
    }

    @Test
    fun toEmailIntent_opensOnlyEmailApps_addressedToTheDeveloperWithTheReportFilledIn() {
        val report = report()
        val intent = report.toEmailIntent()

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        // mailto: URIs aren't hierarchical, so their query can't be read back with getQueryParameter.
        assertEquals(
            "mailto:$DRILL_REPORT_EMAIL?subject=${Uri.encode(report.subject)}&body=${Uri.encode(report.body)}",
            intent.data.toString(),
        )
        assertArrayEquals(arrayOf(DRILL_REPORT_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(report.subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(report.body, intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
