package com.lukearnold.coachesassistant.data

import androidx.annotation.StringRes
import java.io.IOException

/**
 * A failure with an explanation for the user: the string resource [messageRes], filled in with
 * [formatArgs]. The exception's own message is only for logs. It's an IOException so it can be
 * thrown from OkHttp interceptors, which only pass those through.
 */
open class UserFacingException(
    @param:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList(),
    logMessage: String? = null,
) : IOException(logMessage)
