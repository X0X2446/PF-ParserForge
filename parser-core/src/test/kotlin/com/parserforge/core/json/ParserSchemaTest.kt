package com.parserforge.core.json

import com.parserforge.core.model.CookiePair
import com.parserforge.core.model.ExtractMode
import com.parserforge.core.model.ParserProfile
import com.parserforge.core.model.SelectorKey
import com.parserforge.core.model.SessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserSchemaTest {
    @Test
    fun roundTripKeepsSelectorsSessionAndExtractModes() {
        val created = ParserProfile.create("Shelf", "example.com/manga").copy(
            session = SessionSnapshot(
                userAgent = "ParserForgeTest",
                capturedAtUrl = "https://example.com/manga",
                cookies = listOf(CookiePair("cf_clearance", "abc=def", "example.com")),
            ),
            selectors = SelectorKey.CHAPTER_URL.assign(
                SelectorKey.MANGA_LIST_TITLE.assign(
                    ParserProfile.create("Shelf", "example.com/manga").selectors,
                    "h1.title",
                ),
                "li.chapter > a",
            ),
        )
        val encoded = ParserSchema.encode(created)
        assertTrue(encoded.contains("\"mangaListTitle\""))
        assertTrue(encoded.contains("\"coverThumbnail\""))
        assertTrue(encoded.contains("\"chapterUrl\""))
        assertTrue(encoded.contains("\"readerPageImages\""))
        assertTrue(encoded.contains("\"extract\": \"src\""))
        assertTrue(encoded.contains("cf_clearance"))

        val decoded = ParserSchema.decode(encoded)
        assertEquals(created.id, decoded.id)
        assertEquals("https://example.com/manga", decoded.entryUrl)
        assertEquals("h1.title", decoded.selectors.mangaListTitle.selector)
        assertEquals(ExtractMode.TEXT, decoded.selectors.mangaListTitle.extract)
        assertEquals(ExtractMode.SRC, decoded.selectors.coverThumbnail.extract)
        assertEquals(ExtractMode.HREF, decoded.selectors.chapterUrl.extract)
        assertEquals("abc=def", decoded.session.cookies.single().value)
        assertTrue(decoded.session.hasClearance())
    }

    @Test
    fun forcesExtractModeWhenJsonDrifts() {
        val profile = ParserProfile.create("Shelf", "https://example.com/")
        val raw = ParserSchema.encode(profile).replace(
            "\"coverThumbnail\": {\n      \"selector\": \"\",\n      \"extract\": \"src\"",
            "\"coverThumbnail\": {\n      \"selector\": \"img.cover\",\n      \"extract\": \"text\"",
        )
        val decoded = ParserSchema.decode(raw)
        assertEquals("img.cover", decoded.selectors.coverThumbnail.selector)
        assertEquals(ExtractMode.SRC, decoded.selectors.coverThumbnail.extract)
    }

    @Test
    fun rejectsFutureSchemaVersion() {
        val raw = ParserSchema.encode(ParserProfile.create("Shelf", "https://example.com/"))
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 9")
        val error = runCatching { ParserSchema.decode(raw) }.exceptionOrNull()
        assertTrue(error is ParserSchemaException)
    }

    @Test
    fun listRoundTripPreservesOrderOnUpdate() {
        val first = ParserProfile.create("One", "https://one.example/")
        val second = ParserProfile.create("Two", "https://two.example/")
        val encoded = ParserSchema.encodeList(listOf(second, first))
        val decoded = ParserSchema.decodeList(encoded)
        assertEquals(listOf(second.id, first.id), decoded.map { it.id })
        assertFalse(decoded.first().selectors.chapterUrl.selector.isNotEmpty())
    }
}
