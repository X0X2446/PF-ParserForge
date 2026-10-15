package com.extforge.core.apk

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * ZIP writer that stores [resources.arsc] and classes.dex uncompressed and
 * 4-byte aligned, which is what PackageManager requires.
 */
object AlignedZip {
    data class Entry(
        val name: String,
        val data: ByteArray,
        val compress: Boolean,
        val align: Int = 4,
    )

    fun store(entries: List<Entry>): ByteArray {
        val locals = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        entries.forEach { entry ->
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            val method: Int
            val payload: ByteArray
            if (entry.compress) {
                method = 8
                payload = deflate(entry.data)
            } else {
                method = 0
                payload = entry.data
            }
            val crc = CRC32().apply { update(entry.data) }.value.toInt()
            val headerAt = locals.size()
            val base = headerAt + 30 + nameBytes.size
            val padding = if (!entry.compress && entry.align > 1) {
                val mod = base % entry.align
                if (mod == 0) 0 else entry.align - mod
            } else {
                0
            }
            val extra = ByteArray(padding)
            writeLocal(locals, nameBytes, extra, method, crc, payload.size, entry.data.size)
            locals.write(payload)
            writeCentral(central, nameBytes, extra, method, crc, payload.size, entry.data.size, headerAt)
        }
        val cd = central.toByteArray()
        val cdOffset = locals.size()
        locals.write(cd)
        writeEocd(locals, entries.size, cd.size, cdOffset)
        return locals.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun writeLocal(
        out: ByteArrayOutputStream,
        name: ByteArray,
        extra: ByteArray,
        method: Int,
        crc: Int,
        compressed: Int,
        size: Int,
    ) {
        out.writeU32(0x04034b50)
        out.writeU16(20)
        out.writeU16(0)
        out.writeU16(method)
        out.writeU16(0)
        out.writeU16(0x21)
        out.writeU32(crc)
        out.writeU32(compressed)
        out.writeU32(size)
        out.writeU16(name.size)
        out.writeU16(extra.size)
        out.write(name)
        out.write(extra)
    }

    private fun writeCentral(
        out: ByteArrayOutputStream,
        name: ByteArray,
        extra: ByteArray,
        method: Int,
        crc: Int,
        compressed: Int,
        size: Int,
        localOffset: Int,
    ) {
        out.writeU32(0x02014b50)
        out.writeU16(20)
        out.writeU16(20)
        out.writeU16(0)
        out.writeU16(method)
        out.writeU16(0)
        out.writeU16(0x21)
        out.writeU32(crc)
        out.writeU32(compressed)
        out.writeU32(size)
        out.writeU16(name.size)
        out.writeU16(extra.size)
        out.writeU16(0)
        out.writeU16(0)
        out.writeU16(0)
        out.writeU32(0)
        out.writeU32(localOffset)
        out.write(name)
        out.write(extra)
    }

    private fun writeEocd(out: ByteArrayOutputStream, count: Int, cdSize: Int, cdOffset: Int) {
        out.writeU32(0x06054b50)
        out.writeU16(0)
        out.writeU16(0)
        out.writeU16(count)
        out.writeU16(count)
        out.writeU32(cdSize)
        out.writeU32(cdOffset)
        out.writeU16(0)
    }
}

internal fun ByteArrayOutputStream.writeU16(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

internal fun ByteArrayOutputStream.writeU32(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}
