package com.extforge.core.parse

import com.extforge.core.apk.DexConfig
import com.extforge.core.model.ChapterOrder
import com.extforge.core.model.CookiePolicy
import com.extforge.core.model.ExtensionProfile
import com.extforge.core.model.ImageAttr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeHtmlTest {
    @Test
    fun parsesListDetailsChaptersAndPages() {
        val profile = sample()
        val config = ForgeHtml.Config.parse(DexConfig.encode(profile) + "   ")
        assertEquals("https://reads.example", config.base)
        assertEquals("https://reads.example/popular?page=2", config.popularUrl(2))
        assertEquals(
            "https://reads.example/search?q=one%20piece&page=1",
            config.searchUrl(1, "one piece"),
        )

        val listHtml = """
            <div class="card"><a href="/m/1">One</a><img data-src="/c/1.jpg"></div>
            <div class="card"><a href="/m/2">Two</a><img src="/c/2.jpg"></div>
            <a class="next" href="?page=2">Next</a>
        """.trimIndent()
        val listed = ForgeHtml.parseListing(config, listHtml, "https://reads.example/popular?page=1")
        assertEquals(2, listed.items.size)
        assertEquals("One", listed.items[0].title)
        assertEquals("/m/1", listed.items[0].url)
        assertEquals("https://reads.example/c/1.jpg", listed.items[0].cover)
        assertTrue(listed.hasNext)

        val detailHtml = """
            <h1 class="title">One</h1>
            <div class="desc">Hello there</div>
            <span class="author">Ann</span>
            <span class="genre">Action</span>
            <span class="genre">Drama</span>
            <span class="status">Ongoing</span>
            <div class="ch"><a href="/m/1/ch-2">Chapter 2</a><span class="date">2020-01-02</span></div>
            <div class="ch"><a href="/m/1/ch-1">Vol.1 Chapter 1</a><span class="date">2020-01-01</span></div>
        """.trimIndent()
        val detail = ForgeHtml.parseDetail(config, detailHtml, "https://reads.example/m/1")
        assertEquals("One", detail.title)
        assertEquals("Ann", detail.author)
        assertEquals("Action, Drama", detail.genre)
        assertEquals(1, detail.status)
        val chapters = ForgeHtml.parseChapters(config, detailHtml, "https://reads.example/m/1")
        assertEquals("Chapter 2", chapters[0].name)
        assertEquals(2f, chapters[0].number)
        assertTrue(chapters[0].dateUpload > 0L)
        assertEquals("/m/1/ch-1", chapters[1].url)
        assertEquals(1f, chapters[1].number)

        val reversed = ForgeHtml.Config.parse(
            DexConfig.encode(profile.copy(chapters = profile.chapters.copy(order = ChapterOrder.REVERSE))),
        )
        val flipped = ForgeHtml.parseChapters(reversed, detailHtml, "https://reads.example/m/1")
        assertEquals("Vol.1 Chapter 1", flipped[0].name)

        val pages = ForgeHtml.parsePages(
            config,
            """<div class="reader"><img data-src="/p/1.jpg"><img data-src="/p/2.jpg"></div>""",
            "https://reads.example/m/1/ch-1",
        )
        assertEquals(
            listOf("https://reads.example/p/1.jpg", "https://reads.example/p/2.jpg"),
            pages,
        )
    }

    @Test
    fun escapesNonAsciiAndIgnoresCheckPagesThatAreNormalHtml() {
        val profile = sample().copy(name = "漫画")
        val encoded = DexConfig.encode(profile)
        assertTrue(encoded.all { it.code in 32..126 })
        val config = ForgeHtml.Config.parse(encoded)
        assertEquals("漫画", config.name)
        assertFalse(ForgeHtml.looksLikeCheck(200, "<html><title>One</title></html>"))
        assertTrue(
            ForgeHtml.looksLikeCheck(403, "<html>Just a moment</html>"),
        )
    }

    private fun sample(): ExtensionProfile = ExtensionProfile.create("Demo", "en", "https://reads.example").copy(
        access = com.extforge.core.model.AccessPolicy(cookiePolicy = CookiePolicy.NONE),
        list = com.extforge.core.model.ListSelectors(
            popularTemplate = "{base}/popular?page={page}",
            searchTemplate = "{base}/search?q={query}&page={page}",
            nextPage = "a.next",
            item = "div.card",
            title = "a",
            url = "a",
            cover = "img",
        ),
        details = com.extforge.core.model.DetailSelectors(
            title = "h1.title",
            description = "div.desc",
            author = "span.author",
            genre = "span.genre",
            status = "span.status",
        ),
        chapters = com.extforge.core.model.ChapterSelectors(
            item = "div.ch",
            name = "a",
            url = "a",
            date = "span.date",
            datePattern = "yyyy-MM-dd",
        ),
        pages = com.extforge.core.model.PageSelectors(
            image = "div.reader img",
            attribute = ImageAttr.DATA_SRC,
        ),
    ).canonical()
}
