package com.lukearnold.coachesassistant.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.lukearnold.coachesassistant.data.UserFacingException

/**
 * Text for the user that's built in code: a string resource and its arguments, turned into words
 * only when shown. View models hold messages across a language change, so they keep these rather
 * than finished strings. Arguments that are themselves [UiText] are resolved too, so one message
 * can contain another (e.g. an event's description in its "removed" message).
 */
sealed interface UiText {
    fun resolve(resources: Resources): String

    /** Text that isn't translated: names the user typed, numbers, or a server's own error message. */
    data class Plain(val text: String) : UiText {
        override fun resolve(resources: Resources) = text
    }

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        override fun resolve(resources: Resources): String =
            if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolveArgs(args, resources))
    }

    /** A plurals resource chosen by [count]; [args] fill it in, and default to just the count. */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText {
        override fun resolve(resources: Resources): String =
            resources.getQuantityString(id, count, *resolveArgs(args, resources))
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun pluralText(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
    UiText.Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

private fun resolveArgs(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()

/** [this] in the language currently shown (recomposing if that changes). */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String = resolve(LocalResources.current)

/**
 * What to tell the user about a failure: its own explanation if it has one, otherwise the system's
 * message (e.g. a file provider's error), otherwise [fallback].
 */
fun Throwable.toUiText(@StringRes fallback: Int): UiText = when {
    this is UserFacingException -> uiText(messageRes, *formatArgs.toTypedArray())
    !message.isNullOrBlank() -> UiText.Plain(message!!)
    else -> uiText(fallback)
}
