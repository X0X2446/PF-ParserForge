package com.extforge.core.apk

import com.extforge.core.model.ExtensionProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonApkTest {
    @Test
    fun patchReplacesTheSlotAndKeepsTheTerminator() {
        val marker = DexConfigPatcher.MARKER
        val slot = DexConfigPatcher.SLOT
        val original = (marker + " ".repeat(slot - marker.length)).toByteArray(Charsets.US_ASCII)
        val dex = ByteArray(original.size + 2)
        original.copyInto(dex, 1)
        val patched = DexConfigPatcher.patch(dex, "{\"a\":1}")
        val text = patched.copyOfRange(1, 1 + slot).toString(Charsets.US_ASCII).trim()
        assertEquals("{\"a\":1}", text)
        assertEquals(0, patched[1 + slot].toInt())
    }

    @Test
    fun signedApkIsAMihonExtension() {
        val profile = ExtensionProfile.create("Sample", "en", "https://example.com").copy(
            id = "01234567-89ab-cdef-0123-456789abcdef",
            nsfw = true,
        ).canonical()
        val apk = MihonApkBuilder.build(profile)
        val file = File.createTempFile("extforge", ".apk")
        file.writeBytes(apk)
        try {
            val sdk = System.getenv("ANDROID_HOME") ?: "/home/ubuntu/android-sdk"
            val tools = File(sdk, "build-tools/35.0.0")
            val badging = exec(File(tools, "aapt"), "dump", "badging", file.path)
            assertTrue(badging.contains("package: name='${profile.packageName()}'"))
            assertTrue(badging.contains("versionCode='${profile.apkVersionCode}'"))
            assertTrue(badging.contains("versionName='${profile.versionName()}'"))
            assertTrue(badging.contains("uses-feature: name='tachiyomi.extension'"))
            val tree = exec(File(tools, "aapt"), "dump", "xmltree", file.path, "AndroidManifest.xml")
            assertTrue(tree.contains("ex.extforge.runtime.ForgeSource"))
            assertTrue(tree.contains("tachiyomix.contentWarning"))
            assertTrue(tree.contains("(type 0x10)0x2\n") || tree.trimEnd().endsWith("(type 0x10)0x2"))
            exec(File(tools, "apksigner"), "verify", "--min-sdk-version", "26", file.path)
            exec(File(tools, "zipalign"), "-c", "-p", "4", file.path)
        } finally {
            file.delete()
        }
    }

    private fun exec(tool: File, vararg args: String): String {
        assertTrue("missing ${tool.path}", tool.isFile)
        val process = ProcessBuilder(listOf(tool.path) + args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        assertEquals(text, 0, code)
        return text
    }
}
