package com.metrolist.innertube.pages

import kotlin.test.Test

class ParserLogTest {
    @Test
    fun logsKotlinStringsWithoutNativeFormatConversion() {
        ParserLog.d("Track: 100% — 日本語 %@ %s")
        ParserLog.w("Unable to parse track")
    }
}
