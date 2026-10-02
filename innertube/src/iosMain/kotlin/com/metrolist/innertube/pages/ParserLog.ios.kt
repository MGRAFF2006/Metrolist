package com.metrolist.innertube.pages

internal actual object ParserLog {
    actual fun d(message: String) = println(message)

    actual fun w(message: String) = println(message)
}
