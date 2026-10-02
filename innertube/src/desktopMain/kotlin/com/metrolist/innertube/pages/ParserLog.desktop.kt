package com.metrolist.innertube.pages

internal actual object ParserLog {
    actual fun d(message: String) = Unit
    actual fun w(message: String) = System.err.println(message)
}
