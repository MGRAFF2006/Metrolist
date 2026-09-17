package com.metrolist.innertube.pages

import platform.Foundation.NSLog

internal actual object ParserLog {
    actual fun d(message: String) = NSLog("%@", message)

    actual fun w(message: String) = NSLog("%@", message)
}
