package com.extforge.core.apk

import java.io.ByteArrayOutputStream

/**
 * Binary AndroidManifest matching the chunk layout aapt2 emits for a Mihon
 * extension: UTF-16 string pool, resource map for the android attributes,
 * then the XML nodes. Integer meta-data values stay TYPE_INT_DEC so Mihon's
 * Bundle.getInt reads them.
 */
object BinaryManifest {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val NULL = -1

    private val ATTRS = listOf(
        "label" to 0x01010001,
        "name" to 0x01010003,
        "hasCode" to 0x0101000c,
        "value" to 0x01010024,
        "minSdkVersion" to 0x0101020c,
        "versionCode" to 0x0101021b,
        "versionName" to 0x0101021c,
        "targetSdkVersion" to 0x01010270,
        "required" to 0x0101028e,
        "compileSdkVersion" to 0x01010572,
        "compileSdkVersionCodename" to 0x01010573,
    )

    data class Spec(
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val label: String,
        val sourceName: String,
        val className: String = "ex.extforge.runtime.ForgeSource",
        val nsfw: Boolean = false,
    )

    fun write(spec: Spec): ByteArray {
        val pool = Pool()
        ATTRS.forEach { (name, _) -> pool.id(name) }
        val tree = node(
            "manifest",
            android = listOf(
                "versionCode" to Value.IntDec(spec.versionCode),
                "versionName" to Value.Str(spec.versionName),
                "compileSdkVersion" to Value.IntDec(35),
                "compileSdkVersionCodename" to Value.Str("15"),
            ),
            plain = listOf(
                "package" to Value.Str(spec.packageName),
                "platformBuildVersionCode" to Value.IntDec(35),
                "platformBuildVersionName" to Value.IntDec(15),
            ),
            children = listOf(
                node(
                    "uses-sdk",
                    android = listOf(
                        "minSdkVersion" to Value.IntDec(26),
                        "targetSdkVersion" to Value.IntDec(35),
                    ),
                ),
                node(
                    "uses-feature",
                    android = listOf(
                        "name" to Value.Str("tachiyomi.extension"),
                        "required" to Value.Bool(true),
                    ),
                ),
                node(
                    "application",
                    android = listOf(
                        "label" to Value.Str(spec.label),
                        "hasCode" to Value.Bool(true),
                    ),
                    children = listOf(
                        meta("tachiyomi.extension.class", Value.Str(spec.className)),
                        meta("tachiyomi.extension.nsfw", Value.IntDec(if (spec.nsfw) 1 else 0)),
                        meta("tachiyomix.name", Value.Str(spec.sourceName)),
                        meta("tachiyomix.contentWarning", Value.IntDec(if (spec.nsfw) 2 else 0)),
                    ),
                ),
            ),
        )
        val body = ByteArrayOutputStream()
        val androidUri = pool.id(ANDROID_URI)
        val androidPrefix = pool.id("android")
        writeElement(body, pool, tree)
        val nodes = body.toByteArray()
        val packed = ByteArrayOutputStream()
        packed.write(pool.bytes())
        packed.write(resourceMap())
        writeNode(packed, 0x0100, androidPrefix, androidUri)
        packed.write(nodes)
        writeNode(packed, 0x0101, androidPrefix, androidUri)
        val payload = packed.toByteArray()
        val out = ByteArrayOutputStream()
        out.writeU16(0x0003)
        out.writeU16(8)
        out.writeU32(8 + payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    fun emptyResourcesArsc(): ByteArray {
        // aapt2 link of a manifest with no resources. packageCount is 0.
        val hex = "02000c00280000000000000001001c001c0000000000000000000000000100001c00000000000000"
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun meta(name: String, value: Value) = node(
        "meta-data",
        android = listOf("name" to Value.Str(name), "value" to value),
    )

    private sealed class Value {
        data class Str(val text: String) : Value()
        data class IntDec(val value: Int) : Value()
        data class Bool(val value: Boolean) : Value()
    }

    private data class Node(
        val name: String,
        val android: List<Pair<String, Value>>,
        val plain: List<Pair<String, Value>>,
        val children: List<Node>,
    )

    private fun node(
        name: String,
        android: List<Pair<String, Value>> = emptyList(),
        plain: List<Pair<String, Value>> = emptyList(),
        children: List<Node> = emptyList(),
    ) = Node(name, android, plain, children)

    private fun writeElement(out: ByteArrayOutputStream, pool: Pool, node: Node) {
        val uri = pool.id(ANDROID_URI)
        val androidAttrs = node.android.sortedBy { pair ->
            ATTRS.first { it.first == pair.first }.second
        }
        val attrs = ArrayList<WrittenAttr>()
        androidAttrs.forEach { (name, value) ->
            attrs.add(written(pool, uri, name, value))
        }
        node.plain.forEach { (name, value) ->
            attrs.add(written(pool, NULL, name, value))
        }
        val size = 36 + attrs.size * 20
        out.writeU16(0x0102)
        out.writeU16(16)
        out.writeU32(size)
        out.writeU32(1)
        out.writeU32(NULL)
        out.writeU32(NULL)
        out.writeU32(pool.id(node.name))
        out.writeU16(20)
        out.writeU16(20)
        out.writeU16(attrs.size)
        out.writeU16(0)
        out.writeU16(0)
        out.writeU16(0)
        attrs.forEach { attr ->
            out.writeU32(attr.ns)
            out.writeU32(attr.name)
            out.writeU32(attr.raw)
            out.writeU16(8)
            out.write(0)
            out.write(attr.type)
            out.writeU32(attr.data)
        }
        node.children.forEach { child -> writeElement(out, pool, child) }
        writeNode(out, 0x0103, NULL, pool.id(node.name))
    }

    private data class WrittenAttr(val ns: Int, val name: Int, val raw: Int, val type: Int, val data: Int)

    private fun written(pool: Pool, ns: Int, name: String, value: Value): WrittenAttr {
        val nameId = pool.id(name)
        return when (value) {
            is Value.Str -> {
                val id = pool.id(value.text)
                WrittenAttr(ns, nameId, id, TYPE_STRING, id)
            }
            is Value.IntDec -> WrittenAttr(ns, nameId, NULL, TYPE_INT_DEC, value.value)
            is Value.Bool -> WrittenAttr(
                ns,
                nameId,
                NULL,
                TYPE_INT_BOOLEAN,
                if (value.value) NULL else 0,
            )
        }
    }

    private fun writeNode(out: ByteArrayOutputStream, type: Int, first: Int, second: Int) {
        out.writeU16(type)
        out.writeU16(16)
        out.writeU32(24)
        out.writeU32(1)
        out.writeU32(NULL)
        out.writeU32(first)
        out.writeU32(second)
    }

    private fun resourceMap(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeU16(0x0180)
        out.writeU16(8)
        out.writeU32(8 + ATTRS.size * 4)
        ATTRS.forEach { (_, id) -> out.writeU32(id) }
        return out.toByteArray()
    }

    private class Pool {
        private val items = ArrayList<String>()
        private val index = HashMap<String, Int>()

        fun id(value: String): Int = index.getOrPut(value) {
            items.add(value)
            items.lastIndex
        }

        fun bytes(): ByteArray {
            val encoded = items.map { encodeUtf16(it) }
            var cursor = 0
            val offsets = encoded.map { raw ->
                val at = cursor
                cursor += raw.size
                at
            }
            val pad = (4 - (cursor % 4)) % 4
            val stringsStart = 28 + items.size * 4
            val chunkSize = stringsStart + cursor + pad
            val out = ByteArrayOutputStream()
            out.writeU16(0x0001)
            out.writeU16(28)
            out.writeU32(chunkSize)
            out.writeU32(items.size)
            out.writeU32(0)
            out.writeU32(0)
            out.writeU32(stringsStart)
            out.writeU32(0)
            offsets.forEach { out.writeU32(it) }
            encoded.forEach { out.write(it) }
            repeat(pad) { out.write(0) }
            return out.toByteArray()
        }

        private fun encodeUtf16(value: String): ByteArray {
            require(value.length < 0x7FFF) { "Manifest string is too long" }
            val out = ByteArray(2 + value.length * 2 + 2)
            out[0] = (value.length and 0xFF).toByte()
            out[1] = ((value.length ushr 8) and 0xFF).toByte()
            var at = 2
            value.forEach { ch ->
                out[at++] = (ch.code and 0xFF).toByte()
                out[at++] = ((ch.code ushr 8) and 0xFF).toByte()
            }
            return out
        }
    }
}
