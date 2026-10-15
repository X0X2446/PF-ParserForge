package com.extforge.core.net

import com.extforge.core.model.AccessPolicy
import com.extforge.core.model.CookiePolicy
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionHttpClientTest {
    @Test
    fun manualCookieIsSentOnlyForTheSourceHost() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val url = server.url("/manga").toString()
            SessionHttpClient().fetch(
                url,
                AccessPolicy(
                    cookiePolicy = CookiePolicy.MANUAL,
                    userAgent = "ExtForgeTest",
                    cookieHeader = "session=abc",
                ),
                url,
            )
            val recorded = server.takeRequest()
            assertEquals("ExtForgeTest", recorded.getHeader("User-Agent"))
            assertEquals("session=abc", recorded.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun nonePolicySendsNoCookieEvenIfOneIsStored() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val url = server.url("/").toString()
            SessionHttpClient().fetch(
                url,
                AccessPolicy(
                    cookiePolicy = CookiePolicy.NONE,
                    userAgent = "ExtForgeTest",
                    cookieHeader = "session=secret",
                ),
                url,
            )
            val recorded = server.takeRequest()
            assertEquals("ExtForgeTest", recorded.getHeader("User-Agent"))
            assertNull(recorded.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }
}
