package com.lonrad.coachesassistant.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider

/** [this] as the app shows it in English, its default language. Needs Robolectric for the resources. */
fun UiText.english(): String = resolve(ApplicationProvider.getApplicationContext<Context>().resources)
