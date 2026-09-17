package com.metrolist.innertube.pages

import timber.log.Timber

internal actual object ParserLog {
    actual fun d(message: String) = Timber.d(message)

    actual fun w(message: String) = Timber.w(message)
}
