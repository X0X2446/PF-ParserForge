package com.extforge.core.json

import com.extforge.core.model.AccessPolicy
import com.extforge.core.model.ChapterSelectors
import com.extforge.core.model.CookiePolicy
import com.extforge.core.model.DetailSelectors
import com.extforge.core.model.ExtensionProfile
import com.extforge.core.model.ListSelectors
import com.extforge.core.model.PageSelectors
import com.extforge.core.net.Urls
import java.net.URI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ExtensionSchemaException(message: String) : IllegalArgumentException(message)

@OptIn(ExperimentalSerializationApi::class)
object ExtensionSchema {
    const val CURRENT_VERSION = 2

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val tree = Json { ignoreUnknownKeys = true }

    fun encode(profile: ExtensionProfile): String = json.encodeToString(profile.canonical())

    fun decode(raw: String): ExtensionProfile {
        val element = try {
            tree.parseToJsonElement(raw)
        } catch (error: SerializationException) {
            throw ExtensionSchemaException(error.message ?: "Invalid JSON")
        }
        if (element !is JsonObject) throw ExtensionSchemaException("JSON must be an object")
        return decodeObject(element)
    }

    fun encodeList(profiles: List<ExtensionProfile>): String =
        json.encodeToString(profiles.map { it.canonical() })

    fun decodeAny(raw: String): List<ExtensionProfile> {
        val element = try {
            tree.parseToJsonElement(raw)
        } catch (error: SerializationException) {
            throw ExtensionSchemaException(error.message ?: "Invalid JSON")
        }
        return when (element) {
            is JsonArray -> element.map { item ->
                if (item !is JsonObject) throw ExtensionSchemaException("JSON list items must be objects")
                decodeObject(item)
            }
            is JsonObject -> listOf(decodeObject(element))
            else -> throw ExtensionSchemaException("JSON must be an object or a list")
        }
    }

    fun decodeList(raw: String): List<ExtensionProfile> {
        val element = try {
            tree.parseToJsonElement(raw)
        } catch (error: SerializationException) {
            throw ExtensionSchemaException(error.message ?: "Invalid JSON")
        }
        val array = element as? JsonArray ?: throw ExtensionSchemaException("JSON must be a list")
        return array.map { item ->
            if (item !is JsonObject) throw ExtensionSchemaException("JSON list items must be objects")
            decodeObject(item)
        }
    }

    private fun decodeObject(obj: JsonObject): ExtensionProfile {
        val version = obj.primitive("schemaVersion")?.intOrNull ?: 1
        if (version > CURRENT_VERSION) {
            throw ExtensionSchemaException("Unsupported schemaVersion $version")
        }
        val profile = if (version <= 1 && obj.containsKey("entryUrl")) {
            migrateV1(obj)
        } else {
            try {
                tree.decodeFromJsonElement(ExtensionProfile.serializer(), obj)
            } catch (error: SerializationException) {
                throw ExtensionSchemaException(error.message ?: "Invalid JSON")
            }
        }
        return profile.canonical()
    }

    private fun migrateV1(obj: JsonObject): ExtensionProfile {
        val entry = obj.string("entryUrl").orEmpty()
        val normalized = Urls.normalize(entry)
        val base = normalized?.let { originOf(it) } ?: "https://example.com"
        val selectors = obj["selectors"] as? JsonObject
        val title = fieldSelector(selectors, "mangaListTitle")
        val cover = fieldSelector(selectors, "coverThumbnail")
        val chapter = fieldSelector(selectors, "chapterUrl")
        val images = fieldSelector(selectors, "readerPageImages")
        val session = obj["session"] as? JsonObject
        val cookies = (session?.get("cookies") as? JsonArray).orEmpty().mapNotNull { item ->
            val cookie = item as? JsonObject ?: return@mapNotNull null
            val name = cookie.string("name").orEmpty()
            val value = cookie.string("value").orEmpty()
            if (name.isBlank()) null else "$name=$value"
        }
        val header = cookies.joinToString("; ")
        val id = obj.string("id")?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: "untitled"
        return ExtensionProfile(
            id = id,
            name = name,
            lang = "en",
            baseUrl = base,
            access = AccessPolicy(
                cookiePolicy = if (header.isBlank()) CookiePolicy.NONE else CookiePolicy.MANUAL,
                userAgent = session?.string("userAgent").orEmpty(),
                cookieHeader = header,
            ),
            list = ListSelectors(
                popularTemplate = normalized ?: "{base}/",
                item = title,
                cover = cover,
            ),
            chapters = ChapterSelectors(item = chapter),
            pages = PageSelectors(image = images),
            details = DetailSelectors(),
        )
    }

    private fun fieldSelector(selectors: JsonObject?, key: String): String {
        val field = selectors?.get(key) as? JsonObject ?: return ""
        return field.string("selector").orEmpty()
    }

    private fun originOf(url: String): String {
        val uri = URI(url)
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val host = uri.host ?: return url
        return "${uri.scheme}://$host$port"
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull
}
