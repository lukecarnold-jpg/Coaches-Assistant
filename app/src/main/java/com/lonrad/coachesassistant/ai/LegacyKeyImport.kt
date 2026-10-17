package com.lonrad.coachesassistant.ai

import android.content.Context
import java.util.ServiceLoader

/**
 * Copies API keys out of storage that older builds wrote. Only the transition build (installed over
 * the old app ID, with -PlegacyAppId) has an implementation, in src/legacy; every other build finds
 * none, so the deprecated storage library isn't part of the app that's published.
 */
interface LegacyKeyImport {
    fun importInto(context: Context, store: AiSettingsStore)

    companion object {
        fun all(context: Context): List<LegacyKeyImport> =
            ServiceLoader.load(LegacyKeyImport::class.java, context.classLoader).toList()
    }
}
