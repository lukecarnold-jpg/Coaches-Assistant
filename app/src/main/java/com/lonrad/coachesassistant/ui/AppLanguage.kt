package com.lonrad.coachesassistant.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages the app can be shown in, chosen in Settings (or in Android 13+'s own per-app
 * language setting). [tag] is the language the app is set to, or null to follow the phone's
 * language. [nativeName] is the language's name in itself, so it's recognisable whatever the app is
 * showing; null for [SYSTEM], whose name is translated.
 */
enum class AppLanguage(val tag: String?, val nativeName: String?) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano"),
    ;

    companion object {
        /** The language chosen for the app, or [SYSTEM] if none is (or it isn't one of these). */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return SYSTEM
            return entries.firstOrNull { it.tag == chosen } ?: SYSTEM
        }

        /**
         * Switches the app to [language]. Android remembers it (AppCompat stores it before Android
         * 13) and recreates the screen in the new language.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
            )
        }
    }
}
