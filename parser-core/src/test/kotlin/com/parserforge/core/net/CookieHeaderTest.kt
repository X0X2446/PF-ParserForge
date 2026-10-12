package com.parserforge.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieHeaderTest {
    @Test
    fun parsesClearanceAndKeepsEqualsInsideTheValue() {
        val cookies = CookieHeader.parse(
            "session=1; cf_clearance=ab=cd-ef; theme=dark",
            "https://example.com/manga",
        )
        assertEquals(listOf("session", "cf_clearance", "theme"), cookies.map { it.name })
        assertEquals("ab=cd-ef", cookies[1].value)
        assertEquals("example.com", cookies[1].domain)
        assertTrue(cookies[1].appliesTo("example.com"))
        assertTrue(!cookies[1].appliesTo("other.example"))
    }

    @Test
    fun lastDuplicateNameWins() {
        val cookies = CookieHeader.parse("a=1; a=2", "https://example.com/")
        assertEquals(1, cookies.size)
        assertEquals("2", cookies.single().value)
    }
}
