package com.parserforge.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlsTest {
    @Test
    fun addsHttpsAndDropsFragment() {
        assertEquals("https://example.com/manga", Urls.normalize("example.com/manga"))
        assertEquals("https://example.com/a", Urls.normalize("https://example.com/a#section"))
        assertEquals("http://127.0.0.1:9/manga", Urls.normalize("http://127.0.0.1:9/manga"))
    }

    @Test
    fun rejectsNonHttpAndCredentials() {
        assertNull(Urls.normalize(""))
        assertNull(Urls.normalize("javascript:alert(1)"))
        assertNull(Urls.normalize("file:///tmp/x"))
        assertNull(Urls.normalize("http://user:pass@example.com/"))
        assertNull(Urls.normalize("http://"))
        assertNull(Urls.normalize("https://example.com/a b"))
    }
}
