package com.metrolist.innertube.utils

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }.toMap()

fun String.parseTime(): Int? {
    // YouTube Music uses locale-dependent duration separators.
    val parts = split(Regex("[:.,]")).map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}
