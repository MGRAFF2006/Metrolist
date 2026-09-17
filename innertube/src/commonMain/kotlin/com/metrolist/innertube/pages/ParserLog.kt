package com.metrolist.innertube.pages

internal expect object ParserLog {
    fun d(message: String)

    fun w(message: String)
}
