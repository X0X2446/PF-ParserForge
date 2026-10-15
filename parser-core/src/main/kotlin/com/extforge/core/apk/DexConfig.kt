package com.extforge.core.apk

import com.extforge.core.model.ExtensionProfile

/**
 * Flat ASCII JSON stored inside the extension dex. Non-ASCII is escaped so the
 * dex string can be rewritten without changing its byte length.
 */
object DexConfig {
    fun encode(profile: ExtensionProfile): String {
        val source = profile.canonical()
        val access = source.access
        return buildString {
            append('{')
            field("name", source.name)
            field("lang", source.lang)
            field("base", source.baseUrl)
            raw("nsfw", if (source.nsfw) "true" else "false")
            raw("latest", if (source.supportsLatest) "true" else "false")
            raw("versionId", source.versionId.toString())
            field("cookies", access.cookiePolicy.name.lowercase())
            field("ua", access.userAgent)
            field("cookie", access.cookieHeader)
            raw("delayMs", access.delayMs.toString())
            field("referer", access.referer.name.lowercase())
            field("popular", source.list.popularTemplate)
            field("search", source.list.searchTemplate)
            field("latestUrl", source.list.latestTemplate)
            field("next", source.list.nextPage)
            field("item", source.list.item)
            field("title", source.list.title)
            field("url", source.list.url)
            field("cover", source.list.cover)
            field("detailTitle", source.details.title)
            field("description", source.details.description)
            field("author", source.details.author)
            field("artist", source.details.artist)
            field("genre", source.details.genre)
            field("status", source.details.status)
            field("ongoing", source.details.ongoingWords)
            field("completed", source.details.completedWords)
            field("chapterItem", source.chapters.item)
            field("chapterName", source.chapters.name)
            field("chapterUrl", source.chapters.url)
            field("chapterDate", source.chapters.date)
            field("datePattern", source.chapters.datePattern)
            field("chapterOrder", source.chapters.order.name.lowercase())
            field("pageImage", source.pages.image)
            field("pageAttr", pageAttr(source))
            append('}')
        }.also { encoded ->
            require(encoded.all { it.code in 32..126 }) { "Config was not ASCII" }
        }
    }

    private fun pageAttr(source: ExtensionProfile): String = when (source.pages.attribute) {
        com.extforge.core.model.ImageAttr.AUTO -> "auto"
        com.extforge.core.model.ImageAttr.SRC -> "src"
        com.extforge.core.model.ImageAttr.DATA_SRC -> "data-src"
        com.extforge.core.model.ImageAttr.DATA_LAZY -> "data-lazy-src"
        com.extforge.core.model.ImageAttr.SRCSET -> "srcset"
    }

    private fun StringBuilder.field(key: String, value: String) {
        if (isNotEmpty() && last() != '{') append(',')
        append(jsonString(key))
        append(':')
        append(jsonString(value))
    }

    private fun StringBuilder.raw(key: String, value: String) {
        if (isNotEmpty() && last() != '{') append(',')
        append(jsonString(key))
        append(':')
        append(value)
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20 || ch.code > 0x7E) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }
}
