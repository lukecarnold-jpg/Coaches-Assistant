package com.lonrad.coachesassistant.data

import com.lonrad.coachesassistant.shared.R
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The stream's text as UTF-8, read in full. Throws a [UserFacingException] as soon as it's longer
 * than [limit] bytes, so a file that isn't what the coach meant to pick can't exhaust memory.
 */
internal fun InputStream.readTextUpTo(limit: Int): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (out.size() + read > limit) throw UserFacingException(R.string.file_too_large)
        out.write(buffer, 0, read)
    }
    return String(out.toByteArray(), Charsets.UTF_8)
}
