package com.parserforge.core.parse

import com.parserforge.core.model.BoundField
import com.parserforge.core.model.ExtractMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class FieldPreview(
    val key: String,
    val label: String,
    val values: List<String>,
    val matchCount: Int,
    val error: String? = null,
)

data class PageDigest(
    val title: String,
    val previews: List<FieldPreview>,
)

object JsoupPreviewEngine {
    private const val SAMPLE_LIMIT = 20
    private const val VALUE_LIMIT = 240
    private val SRC_ATTRS = listOf("src", "data-src", "data-lazy-src", "data-original")

    fun preview(html: String, baseUri: String, fields: List<BoundField>): List<FieldPreview> =
        inspect(html, baseUri, fields).previews

    fun inspect(html: String, baseUri: String, fields: List<BoundField>): PageDigest {
        val document = try {
            Jsoup.parse(html, baseUri.ifBlank { "https://localhost/" })
        } catch (error: Exception) {
            val message = error.message ?: "parse failed"
            return PageDigest(
                title = "",
                previews = fields.map { field ->
                    FieldPreview(field.key, field.label, emptyList(), 0, message)
                },
            )
        }
        return PageDigest(
            title = document.title(),
            previews = fields.map { field -> previewField(document, field) },
        )
    }

    private fun previewField(document: Document, field: BoundField): FieldPreview {
        val selector = field.selector.trim()
        if (selector.isEmpty()) {
            return FieldPreview(field.key, field.label, emptyList(), 0, null)
        }
        return try {
            val matches = document.select(selector)
            val values = matches.mapNotNull { element ->
                val value = when (field.extract) {
                    ExtractMode.TEXT -> textOf(element)
                    ExtractMode.HREF -> hrefOf(element)
                    ExtractMode.SRC -> srcOf(element)
                }
                value.takeIf { it.isNotBlank() }
            }
            FieldPreview(
                key = field.key,
                label = field.label,
                values = values.take(SAMPLE_LIMIT),
                matchCount = matches.size,
                error = null,
            )
        } catch (error: Exception) {
            FieldPreview(
                key = field.key,
                label = field.label,
                values = emptyList(),
                matchCount = 0,
                error = error.message ?: "selector failed",
            )
        }
    }

    private fun textOf(element: Element): String {
        val text = element.text().replace(Regex("\\s+"), " ").trim()
        val value = if (text.isNotBlank()) text else element.attr("alt").trim()
        return value.take(VALUE_LIMIT)
    }

    private fun hrefOf(element: Element): String {
        for (attr in listOf("href", "data-href")) {
            val raw = element.attr(attr)
            if (raw.isNotBlank()) return resolveUrl(element, raw)
        }
        if (element.tagName() == "a") return ""
        val link = element.selectFirst("a[href]") ?: return ""
        return hrefOf(link)
    }

    private fun srcOf(element: Element): String {
        for (attr in SRC_ATTRS) {
            val raw = element.attr(attr)
            if (raw.isNotBlank()) return resolveUrl(element, raw)
        }
        val srcset = firstSrcset(element.attr("srcset"))
        if (srcset.isNotBlank()) return resolveUrl(element, srcset)
        if (element.tagName() == "img") return ""
        val image = element.selectFirst("img") ?: return ""
        return srcOf(image)
    }

    private fun firstSrcset(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.split(',')
            .firstOrNull()
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            .orEmpty()
    }

    private fun resolveUrl(element: Element, raw: String): String {
        val anchor = Element("a")
        anchor.setBaseUri(element.baseUri())
        anchor.attr("href", raw)
        return anchor.absUrl("href").ifBlank { raw }
    }
}
