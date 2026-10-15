package com.extforge.core.net

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object Urls {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return null
        if (trimmed.any { it.isISOControl() || it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val noFragment = withScheme.substringBefore('#')
        val uri = try {
            URI(noFragment)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val formattedHost = if (':' in host) "[$host]" else host
        val path = uri.rawPath?.takeIf { it.isNotEmpty() }
        val query = uri.rawQuery
        val ssp = buildString {
            append("//")
            append(formattedHost)
            if (uri.port != -1) {
                append(':')
                append(uri.port)
            }
            if (path != null) {
                append(path)
            } else if (query != null) {
                append('/')
            }
            if (query != null) {
                append('?')
                append(query)
            }
        }
        return "$scheme:$ssp"
    }
}
