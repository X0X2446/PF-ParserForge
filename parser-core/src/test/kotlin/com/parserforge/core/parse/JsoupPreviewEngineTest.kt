package com.parserforge.core.parse

import com.parserforge.core.model.BoundField
import com.parserforge.core.model.ExtractMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsoupPreviewEngineTest {
    private val html = """
        <html><head><title>Demo Shelf</title></head><body>
        <div class="container">
          <h1 class="title">Example Manga</h1>
          <ul class="list">
            <li class="chapter"><a href="/c/1">Chapter 1</a><img src="/covers/1.jpg" alt="c1"></li>
            <li class="chapter"><a href="/c/2">Chapter 2</a><img data-src="/covers/2.jpg" alt="c2"></li>
          </ul>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun extractsTextHrefAndLazyImages() {
        val digest = JsoupPreviewEngine.inspect(
            html,
            "https://example.com/manga/list",
            listOf(
                BoundField("mangaListTitle", "Manga List Title", "h1.title", ExtractMode.TEXT),
                BoundField("coverThumbnail", "Cover Thumbnail", "li.chapter img", ExtractMode.SRC),
                BoundField("chapterUrl", "Chapter URL", "li.chapter", ExtractMode.HREF),
                BoundField("readerPageImages", "Reader Page Images", "", ExtractMode.SRC),
            ),
        )
        assertEquals("Demo Shelf", digest.title)
        val byKey = digest.previews.associateBy { it.key }
        assertEquals(listOf("Example Manga"), byKey.getValue("mangaListTitle").values)
        assertEquals(
            listOf("https://example.com/covers/1.jpg", "https://example.com/covers/2.jpg"),
            byKey.getValue("coverThumbnail").values,
        )
        assertEquals(
            listOf("https://example.com/c/1", "https://example.com/c/2"),
            byKey.getValue("chapterUrl").values,
        )
        assertEquals(2, byKey.getValue("chapterUrl").matchCount)
        assertTrue(byKey.getValue("readerPageImages").values.isEmpty())
        assertNull(byKey.getValue("readerPageImages").error)
    }

    @Test
    fun invalidSelectorIsAnError() {
        val preview = JsoupPreviewEngine.preview(
            html,
            "https://example.com/",
            listOf(BoundField("mangaListTitle", "Manga List Title", "%%%", ExtractMode.TEXT)),
        ).single()
        assertNotNull(preview.error)
        assertTrue(preview.values.isEmpty())
    }

    @Test
    fun srcsetUsesTheFirstCandidate() {
        val values = JsoupPreviewEngine.preview(
            """<img srcset="/pages/1.jpg 800w, /pages/2.jpg 1200w">""",
            "https://example.com/read/1",
            listOf(BoundField("readerPageImages", "Reader Page Images", "img", ExtractMode.SRC)),
        ).single().values
        assertEquals(listOf("https://example.com/pages/1.jpg"), values)
    }
}
