package com.extforge.core.apk

import com.android.apksig.ApkSigner
import com.extforge.core.model.ExtensionProfile
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object MihonApkBuilder {
    fun build(profile: ExtensionProfile): ByteArray {
        val dex = resource("mihon/forge-source.dex")
        val key = MihonApkBuilder::class.java.getResourceAsStream("/mihon/extforge.p12")
            ?: error("Signing key is missing from the app")
        key.use { return build(profile, dex, it) }
    }

    fun build(profile: ExtensionProfile, dexTemplate: ByteArray, keyStore: InputStream): ByteArray {
        val source = profile.canonical()
        val json = DexConfig.encode(source)
        val dex = DexConfigPatcher.patch(dexTemplate, json)
        val manifest = BinaryManifest.write(
            BinaryManifest.Spec(
                packageName = source.packageName(),
                versionCode = source.apkVersionCode,
                versionName = source.versionName(),
                label = "Tachiyomi: ${source.name}",
                sourceName = source.name,
                nsfw = source.nsfw,
            ),
        )
        val unsigned = AlignedZip.store(
            listOf(
                AlignedZip.Entry("AndroidManifest.xml", manifest, compress = true, align = 1),
                AlignedZip.Entry(
                    "resources.arsc",
                    BinaryManifest.emptyResourcesArsc(),
                    compress = false,
                    align = 4,
                ),
                AlignedZip.Entry("classes.dex", dex, compress = false, align = 4),
            ),
        )
        return sign(unsigned, keyStore)
    }

    private fun sign(unsigned: ByteArray, keyStore: InputStream): ByteArray {
        val store = KeyStore.getInstance("PKCS12")
        val password = "extforge".toCharArray()
        store.load(keyStore, password)
        val key = store.getKey("extforge", password) as PrivateKey
        val cert = store.getCertificate("extforge") as X509Certificate
        val signer = ApkSigner.SignerConfig.Builder("EXTFORGE", key, listOf(cert)).build()
        val input = File.createTempFile("extforge-unsigned", ".apk")
        val output = File.createTempFile("extforge-signed", ".apk")
        try {
            input.writeBytes(unsigned)
            ApkSigner.Builder(listOf(signer))
                .setInputApk(input)
                .setOutputApk(output)
                .setMinSdkVersion(26)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .setV4SigningEnabled(false)
                .setAlignmentPreserved(true)
                .build()
                .sign()
            return output.readBytes()
        } finally {
            input.delete()
            output.delete()
        }
    }

    private fun resource(path: String): ByteArray {
        val stream = MihonApkBuilder::class.java.getResourceAsStream("/$path")
            ?: error("Missing $path. Build the extension dex before assembling the app.")
        return stream.use { it.readBytes() }
    }
}
