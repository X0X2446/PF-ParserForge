package com.parserforge.core.json

import com.parserforge.core.model.ParserProfile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ParserSchemaException(message: String) : IllegalArgumentException(message)

@OptIn(ExperimentalSerializationApi::class)
object ParserSchema {
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(profile: ParserProfile): String = json.encodeToString(profile.canonical())

    fun decode(raw: String): ParserProfile {
        val parsed = try {
            json.decodeFromString<ParserProfile>(raw)
        } catch (error: SerializationException) {
            throw ParserSchemaException(error.message ?: "Invalid JSON")
        }
        if (parsed.schemaVersion > CURRENT_VERSION) {
            throw ParserSchemaException("Unsupported schemaVersion ${parsed.schemaVersion}")
        }
        return parsed.canonical()
    }

    fun encodeList(profiles: List<ParserProfile>): String =
        json.encodeToString(profiles.map { it.canonical() })

    fun decodeList(raw: String): List<ParserProfile> {
        val parsed = try {
            json.decodeFromString<List<ParserProfile>>(raw)
        } catch (error: SerializationException) {
            throw ParserSchemaException(error.message ?: "Invalid JSON")
        }
        return parsed.map { profile ->
            if (profile.schemaVersion > CURRENT_VERSION) {
                throw ParserSchemaException("Unsupported schemaVersion ${profile.schemaVersion}")
            }
            profile.canonical()
        }
    }
}
