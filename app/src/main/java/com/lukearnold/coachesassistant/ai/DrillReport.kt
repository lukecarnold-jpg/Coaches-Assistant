package com.lukearnold.coachesassistant.ai

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.Sport

/**
 * Where a coach's report about a generated drill goes. Google Play requires apps that generate
 * content with AI to let users report offensive output from inside the app; with no server, the
 * report is an email the coach sends from their own email app.
 */
const val DRILL_REPORT_EMAIL = "lonrad.apps@gmail.com"

/** Why a coach reported a drill. [code] is what the email says, in English for the developer. */
enum class DrillReportReason(@param:StringRes val label: Int, val code: String) {
    OFFENSIVE(R.string.report_reason_offensive, "Offensive or inappropriate"),
    UNSAFE(R.string.report_reason_unsafe, "Unsafe for players"),
    INACCURATE(R.string.report_reason_inaccurate, "Wrong or not useful"),
    OTHER(R.string.report_reason_other, "Something else"),
}

/** What a set of drills was generated from, so a report can say how to reproduce it. */
data class DrillSource(
    val sport: Sport,
    /** The age group as the coach saw it, in the app's language. */
    val ageGroup: String,
    val provider: AiProvider,
    /** The model actually used: the one chosen in Settings, or the provider's default. */
    val model: String,
)

data class DrillReport(val subject: String, val body: String)

/**
 * The email for a report about [drill]. It holds only what the coach can see in the dialog: the
 * drill, the reason and [details] they gave, how it was generated, and the app and Android
 * versions. For the Other provider it names the model but never the endpoint, which may be a
 * private address.
 */
fun buildDrillReport(
    drill: Drill,
    source: DrillSource,
    reason: DrillReportReason,
    details: String,
    appVersion: String,
    androidVersion: String,
): DrillReport {
    val body = buildString {
        appendLine("Reason: ${reason.code}")
        details.trim().takeIf { it.isNotEmpty() }?.let { appendLine("Details: $it") }
        appendLine()
        appendLine("Sport: ${source.sport.name}")
        appendLine("Age group: ${source.ageGroup}")
        appendLine("Provider: ${source.provider.name}")
        appendLine("Model: ${source.model}")
        appendLine("App version: $appVersion")
        appendLine("Android: $androidVersion")
        appendLine()
        appendLine("--- Drill ---")
        appendLine(drill.title)
        appendLine()
        appendLine(drill.description)
        appendLine()
        append(drill.teachingPoints)
    }
    return DrillReport(subject = "Coaches Assistant drill report: ${reason.code}", body = body)
}

/**
 * Opens an email app (and only an email app, through `mailto:`) with this report filled in. The
 * subject and body are in both the URI and the extras, since email apps differ in which they read.
 */
fun DrillReport.toEmailIntent(): Intent {
    val uri = "mailto:$DRILL_REPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}".toUri()
    return Intent(Intent.ACTION_SENDTO, uri)
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(DRILL_REPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
}
