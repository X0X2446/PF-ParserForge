package com.extforge.core.apk

object DexConfigPatcher {
    const val MARKER = "@@EXTFORGE_CONFIG_V1@@"
    const val SLOT = 32768

    fun patch(dex: ByteArray, json: String): ByteArray {
        require(json.length <= SLOT) {
            "Extension config is ${json.length} chars and the slot holds $SLOT. Shorten the selectors."
        }
        require(json.trimStart().startsWith("{")) { "Config must be JSON" }
        require(json.all { it.code in 32..126 }) { "Config must be ASCII" }
        val marker = MARKER.toByteArray(Charsets.US_ASCII)
        val start = indexOf(dex, marker, 0)
        require(start >= 0) { "Extension template is missing its config slot" }
        require(indexOf(dex, marker, start + marker.size) < 0) {
            "Extension template config slot is ambiguous"
        }
        require(start + SLOT < dex.size && dex[start + SLOT] == 0.toByte()) {
            "Config slot is not a single dex string"
        }
        val out = dex.copyOf()
        val payload = json.toByteArray(Charsets.US_ASCII)
        payload.copyInto(out, start)
        for (i in payload.size until SLOT) {
            out[start + i] = 0x20
        }
        return out
    }

    private fun indexOf(data: ByteArray, needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || from >= data.size) return -1
        var i = from
        while (i + needle.size <= data.size) {
            var matched = true
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
            i++
        }
        return -1
    }
}
