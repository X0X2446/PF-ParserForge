package com.parserforge.core.net

import com.parserforge.core.model.CookiePair
import java.net.URI

object CookieHeader {
    fun parse(header: String?, pageUrl: String): List<CookiePair> {
        if (header.isNullOrBlank()) return emptyList()
        val domain = runCatching { URI(pageUrl).host }.getOrNull().orEmpty()
        val seen = LinkedHashMap<String, CookiePair>()
        header.split(';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val separator = trimmed.indexOf('=')
            if (separator <= 0) return@forEach
            val name = trimmed.substring(0, separator).trim()
            val value = trimmed.substring(separator + 1).trim()
            if (!isSendable(name, value)) return@forEach
            seen[name] = CookiePair(name = name, value = value, domain = domain)
        }
        return seen.values.toList()
    }

    private fun isSendable(name: String, value: String): Boolean {
        if (name.isEmpty() || name.length > 256 || value.length > 8192) return false
        if (name.any { it == ';' || it == '\n' || it == '\r' || it == ' ' }) return false
        if (value.any { it == ';' || it == '\n' || it == '\r' }) return false
        return true
    }
}
