package com.metrolist.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {
    @Test
    fun `parses cookies and localized durations`() {
        assertEquals(mapOf("SID" to "abc=", "HSID" to "def"), parseCookieString("SID=abc=; HSID=def"))
        assertEquals(184, "3:04".parseTime())
        assertEquals(3723, "1.02.03".parseTime())
        assertNull("live".parseTime())
    }
}
