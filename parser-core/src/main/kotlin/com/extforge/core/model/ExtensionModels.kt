package com.extforge.core.model

import com.extforge.core.json.ExtensionSchema
import com.extforge.core.net.Urls
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CookiePolicy {
    @SerialName("none")
    NONE,

    @SerialName("manual")
    MANUAL,
}

@Serializable
enum class RefererMode {
    @SerialName("none")
    NONE,

    @SerialName("base")
    BASE,

    @SerialName("page")
    PAGE,
}

@Serializable
enum class ChapterOrder {
    @SerialName("document")
    DOCUMENT,

    @SerialName("reverse")
    REVERSE,
}

@Serializable
enum class ImageAttr {
    @SerialName("auto")
    AUTO,

    @SerialName("src")
    SRC,

    @SerialName("data-src")
    DATA_SRC,

    @SerialName("data-lazy-src")
    DATA_LAZY,

    @SerialName("srcset")
    SRCSET,
}

@Serializable
data class AccessPolicy(
    val cookiePolicy: CookiePolicy = CookiePolicy.NONE,
    val userAgent: String = "",
    val cookieHeader: String = "",
    val delayMs: Int = 0,
    val referer: RefererMode = RefererMode.NONE,
)

@Serializable
data class ListSelectors(
    val popularTemplate: String = "{base}/",
    val searchTemplate: String = "",
    val latestTemplate: String = "",
    val nextPage: String = "",
    val item: String = "",
    val title: String = "",
    val url: String = "",
    val cover: String = "",
)

@Serializable
data class DetailSelectors(
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val artist: String = "",
    val genre: String = "",
    val status: String = "",
    val ongoingWords: String = "ongoing,updating,publishing",
    val completedWords: String = "completed,complete,finished",
)

@Serializable
data class ChapterSelectors(
    val item: String = "",
    val name: String = "",
    val url: String = "",
    val date: String = "",
    val datePattern: String = "",
    val order: ChapterOrder = ChapterOrder.DOCUMENT,
)

@Serializable
data class PageSelectors(
    val image: String = "",
    val attribute: ImageAttr = ImageAttr.AUTO,
)

@Serializable
data class ExtensionProfile(
    val schemaVersion: Int = ExtensionSchema.CURRENT_VERSION,
    val id: String,
    val name: String,
    val lang: String = "en",
    val baseUrl: String,
    val nsfw: Boolean = false,
    val supportsLatest: Boolean = false,
    val versionId: Int = 1,
    val apkVersionCode: Int = 1,
    val access: AccessPolicy = AccessPolicy(),
    val list: ListSelectors = ListSelectors(),
    val details: DetailSelectors = DetailSelectors(),
    val chapters: ChapterSelectors = ChapterSelectors(),
    val pages: PageSelectors = PageSelectors(),
) {
    fun packageName(): String {
        val hex = id.lowercase(Locale.US).filter { it in '0'..'9' || it in 'a'..'f' }
        val token = (hex + "0000000000000000").take(16)
        return "ex.extforge.s$token"
    }

    fun versionName(): String = "1.4.$apkVersionCode"

    fun selectorCount(): Int = listOf(
        list.item,
        list.title,
        list.url,
        list.cover,
        details.title,
        chapters.item,
        pages.image,
    ).count { it.isNotBlank() }

    fun canonical(): ExtensionProfile {
        val url = Urls.normalize(baseUrl)
            ?: throw IllegalArgumentException("Base URL must be http or https")
        val cleanedLang = lang.trim()
        if (!LANG.matches(cleanedLang)) {
            throw IllegalArgumentException("Language must look like en or pt-BR")
        }
        val cleanedName = name.trim().replace(Regex("[\\r\\n]+"), " ").take(80)
        if (cleanedName.isEmpty()) throw IllegalArgumentException("Name is empty")
        return copy(
            schemaVersion = ExtensionSchema.CURRENT_VERSION,
            name = cleanedName,
            lang = cleanedLang,
            baseUrl = url.trimEnd('/'),
            versionId = versionId.coerceIn(1, 9999),
            apkVersionCode = apkVersionCode.coerceIn(1, 99999),
            access = access.canonical(),
            list = list.copy(
                popularTemplate = list.popularTemplate.trim().ifBlank { "{base}/" },
                searchTemplate = list.searchTemplate.trim(),
                latestTemplate = list.latestTemplate.trim(),
                nextPage = list.nextPage.trim(),
                item = list.item.trim(),
                title = list.title.trim(),
                url = list.url.trim(),
                cover = list.cover.trim(),
            ),
            details = details.copy(
                title = details.title.trim(),
                description = details.description.trim(),
                author = details.author.trim(),
                artist = details.artist.trim(),
                genre = details.genre.trim(),
                status = details.status.trim(),
                ongoingWords = details.ongoingWords.trim().ifBlank { "ongoing,updating,publishing" },
                completedWords = details.completedWords.trim().ifBlank { "completed,complete,finished" },
            ),
            chapters = chapters.copy(
                item = chapters.item.trim(),
                name = chapters.name.trim(),
                url = chapters.url.trim(),
                date = chapters.date.trim(),
                datePattern = chapters.datePattern.trim(),
            ),
            pages = pages.copy(image = pages.image.trim()),
        )
    }

    companion object {
        private val LANG = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})?$")

        fun create(name: String, lang: String, baseUrl: String): ExtensionProfile {
            val url = Urls.normalize(baseUrl)
                ?: throw IllegalArgumentException("URL must be http or https")
            val host = runCatching { URI(url).host }.getOrNull()
            val resolved = name.trim().ifBlank { host ?: "untitled" }
            val resolvedLang = lang.trim().ifBlank { "en" }
            return ExtensionProfile(
                id = UUID.randomUUID().toString(),
                name = resolved,
                lang = resolvedLang,
                baseUrl = url,
            ).canonical()
        }
    }
}

fun AccessPolicy.canonical(): AccessPolicy {
    val ua = userAgent.trim().replace(Regex("[\\r\\n]"), "").take(400)
    val cookie = cookieHeader.replace(Regex("[\\r\\n]"), "").trim().take(8192)
    return copy(
        userAgent = ua,
        cookieHeader = cookie,
        delayMs = delayMs.coerceIn(0, 10_000),
    )
}

fun ExtensionProfile.originHost(): String =
    runCatching { URI(baseUrl).host }.getOrNull().orEmpty()
