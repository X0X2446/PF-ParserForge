package com.parserforge.core.net

import com.parserforge.core.model.CookiePair
import com.parserforge.core.model.ExtractMode
import com.parserforge.core.model.BoundField
import com.parserforge.core.model.SessionSnapshot
import com.parserforge.core.parse.JsoupPreviewEngine
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHttpClientTest {
    @Test
    fun sendsUserAgentAndHostMatchedClearanceCookie() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<html><h1 class='title'>Demo</h1></html>"))
        server.start()
        try {
            val host = server.hostName
            val result = SessionHttpClient().fetch(
                server.url("/manga").toString(),
                SessionSnapshot(
                    userAgent = "ParserForgeTest",
                    capturedAtUrl = "http://$host/",
                    cookies = listOf(CookiePair("cf_clearance", "token", host)),
                ),
            )
            val recorded = server.takeRequest()
            assertEquals("ParserForgeTest", recorded.getHeader("User-Agent"))
            assertTrue(recorded.getHeader("Cookie")!!.contains("cf_clearance=token"))
            assertEquals(200, result.code)
            val title = JsoupPreviewEngine.preview(
                result.body,
                result.finalUrl,
                listOf(BoundField("mangaListTitle", "Manga List Title", "h1.title", ExtractMode.TEXT)),
            ).single()
            assertEquals(listOf("Demo"), title.values)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun doesNotForwardCookiesToADifferentHost() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            SessionHttpClient().fetch(
                server.url("/").toString(),
                SessionSnapshot(
                    userAgent = "ParserForgeTest",
                    capturedAtUrl = "https://example.com/",
                    cookies = listOf(CookiePair("cf_clearance", "secret", "example.com")),
                ),
            )
            val recorded = server.takeRequest()
            assertEquals("ParserForgeTest", recorded.getHeader("User-Agent"))
            assertNull(recorded.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }
}
