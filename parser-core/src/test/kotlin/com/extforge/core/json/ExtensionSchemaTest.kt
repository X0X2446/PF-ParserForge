package com.extforge.core.json

import com.extforge.core.model.CookiePolicy
import com.extforge.core.model.ExtensionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionSchemaTest {
    @Test
    fun roundTripKeepsSelectors() {
        val profile = ExtensionProfile.create("Demo", "pt-BR", "https://example.com/series").copy(
            list = com.extforge.core.model.ListSelectors(
                popularTemplate = "{base}/popular?page={page}",
                searchTemplate = "{base}/search?q={query}&page={page}",
                item = "div.card",
                title = "a",
                url = "a",
                cover = "img",
            ),
            nsfw = true,
        ).canonical()
        val again = ExtensionSchema.decode(ExtensionSchema.encode(profile))
        assertEquals(2, again.schemaVersion)
        assertEquals("pt-BR", again.lang)
        assertEquals("div.card", again.list.item)
        assertEquals(true, again.nsfw)
        assertEquals("https://example.com/series", again.baseUrl)
    }

    @Test
    fun migratesV1SelectorsAndCookieHeader() {
        val raw = """
            {
              "schemaVersion": 1,
              "id": "11111111-1111-1111-1111-111111111111",
              "name": "Old",
              "entryUrl": "https://example.com/series",
              "session": {
                "userAgent": "UA",
                "cookies": [{"name": "session", "value": "abc", "domain": "example.com"}]
              },
              "selectors": {
                "mangaListTitle": {"selector": ".title", "extract": "text"},
                "coverThumbnail": {"selector": "img", "extract": "src"},
                "chapterUrl": {"selector": "a.ch", "extract": "href"},
                "readerPageImages": {"selector": "img.page", "extract": "src"}
              }
            }
        """.trimIndent()
        val profile = ExtensionSchema.decode(raw)
        assertEquals(CookiePolicy.MANUAL, profile.access.cookiePolicy)
        assertEquals("session=abc", profile.access.cookieHeader)
        assertEquals("UA", profile.access.userAgent)
        assertEquals(".title", profile.list.item)
        assertEquals("img", profile.list.cover)
        assertEquals("a.ch", profile.chapters.item)
        assertEquals("img.page", profile.pages.image)
        assertEquals("https://example.com", profile.baseUrl)
        assertTrue(profile.list.popularTemplate.contains("/series"))
    }

    @Test
    fun rejectsFutureSchema() {
        try {
            ExtensionSchema.decode("""{"schemaVersion":9,"id":"x","name":"n","baseUrl":"https://e.com"}""")
            throw AssertionError("expected failure")
        } catch (error: ExtensionSchemaException) {
            assertTrue(error.message!!.contains("9"))
        }
    }
}
