package com.parserforge.core.model

import com.parserforge.core.json.ParserSchema
import com.parserforge.core.net.Urls
import java.net.URI
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExtractMode {
    @SerialName("text")
    TEXT,

    @SerialName("href")
    HREF,

    @SerialName("src")
    SRC,
}

@Serializable
data class FieldRule(
    val selector: String = "",
    val extract: ExtractMode = ExtractMode.TEXT,
)

@Serializable
data class SelectorSet(
    val mangaListTitle: FieldRule = FieldRule(extract = ExtractMode.TEXT),
    val coverThumbnail: FieldRule = FieldRule(extract = ExtractMode.SRC),
    val chapterUrl: FieldRule = FieldRule(extract = ExtractMode.HREF),
    val readerPageImages: FieldRule = FieldRule(extract = ExtractMode.SRC),
)

enum class SelectorKey(
    val id: String,
    val label: String,
    val extract: ExtractMode,
) {
    MANGA_LIST_TITLE("mangaListTitle", "Manga List Title", ExtractMode.TEXT),
    COVER_THUMBNAIL("coverThumbnail", "Cover Thumbnail", ExtractMode.SRC),
    CHAPTER_URL("chapterUrl", "Chapter URL", ExtractMode.HREF),
    READER_PAGE_IMAGES("readerPageImages", "Reader Page Images", ExtractMode.SRC),
    ;

    fun rule(set: SelectorSet): FieldRule = when (this) {
        MANGA_LIST_TITLE -> set.mangaListTitle
        COVER_THUMBNAIL -> set.coverThumbnail
        CHAPTER_URL -> set.chapterUrl
        READER_PAGE_IMAGES -> set.readerPageImages
    }

    fun assign(set: SelectorSet, selector: String): SelectorSet {
        val rule = FieldRule(selector = selector, extract = extract)
        return when (this) {
            MANGA_LIST_TITLE -> set.copy(mangaListTitle = rule)
            COVER_THUMBNAIL -> set.copy(coverThumbnail = rule)
            CHAPTER_URL -> set.copy(chapterUrl = rule)
            READER_PAGE_IMAGES -> set.copy(readerPageImages = rule)
        }
    }

    fun bind(set: SelectorSet): BoundField {
        val rule = rule(set)
        return BoundField(
            key = id,
            label = label,
            selector = rule.selector,
            extract = extract,
        )
    }
}

fun SelectorSet.canonical(): SelectorSet =
    SelectorKey.entries.fold(this) { acc, key ->
        key.assign(acc, key.rule(this).selector)
    }

fun SelectorSet.filledCount(): Int =
    SelectorKey.entries.count { key -> key.rule(this).selector.isNotBlank() }

data class BoundField(
    val key: String,
    val label: String,
    val selector: String,
    val extract: ExtractMode,
)

@Serializable
data class CookiePair(
    val name: String,
    val value: String,
    val domain: String = "",
) {
    fun appliesTo(host: String): Boolean {
        if (domain.isBlank() || host.isBlank()) return false
        return host.equals(domain, ignoreCase = true)
    }
}

@Serializable
data class SessionSnapshot(
    val userAgent: String = "",
    val capturedAtUrl: String = "",
    val cookies: List<CookiePair> = emptyList(),
) {
    fun hasClearance(): Boolean = cookies.any { it.name.equals("cf_clearance", ignoreCase = true) }

    fun cookieHeaderFor(host: String): String =
        cookies.asSequence()
            .filter { it.appliesTo(host) }
            .filter { it.name.isNotBlank() && it.name.length <= 256 && it.value.length <= 8192 }
            .filter { cookie ->
                cookie.name.none { it == ';' || it == '\n' || it == '\r' || it == ' ' } &&
                    cookie.value.none { it == ';' || it == '\n' || it == '\r' }
            }
            .joinToString("; ") { "${it.name}=${it.value}" }
}

@Serializable
data class ParserProfile(
    val schemaVersion: Int = ParserSchema.CURRENT_VERSION,
    val id: String,
    val name: String,
    val entryUrl: String,
    val session: SessionSnapshot = SessionSnapshot(),
    val selectors: SelectorSet = SelectorSet(),
) {
    fun canonical(): ParserProfile = copy(
        schemaVersion = ParserSchema.CURRENT_VERSION,
        selectors = selectors.canonical(),
    )

    fun boundFields(): List<BoundField> = SelectorKey.entries.map { it.bind(selectors) }

    companion object {
        fun create(name: String, entryUrl: String): ParserProfile {
            val url = Urls.normalize(entryUrl)
                ?: throw IllegalArgumentException("URL must be http or https")
            val host = runCatching { URI(url).host }.getOrNull()
            val resolved = name.trim().ifBlank { host ?: "untitled" }
            return ParserProfile(
                id = UUID.randomUUID().toString(),
                name = resolved,
                entryUrl = url,
            ).canonical()
        }
    }
}

data class ElementCapture(
    val selector: String,
    val tag: String,
    val text: String,
    val href: String,
    val src: String,
)
