package com.extforge.app.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.extforge.core.apk.MihonApkBuilder
import com.extforge.core.json.ExtensionSchema
import com.extforge.core.model.ExtensionProfile
import com.extforge.core.store.ExtensionRepository
import java.io.File

object ShareKit {
    const val APK = "application/vnd.android.package-archive"
    const val JSON = "application/json"

    fun writeJson(context: Context, profile: ExtensionProfile): File {
        val file = exportFile(context, profile, "json")
        file.writeText(ExtensionSchema.encode(profile.canonical()))
        return file
    }

    fun writeApk(context: Context, profile: ExtensionProfile): File {
        val dex = context.assets.open("mihon/forge-source.dex").use { it.readBytes() }
        val bytes = context.assets.open("mihon/extforge.p12").use { key ->
            MihonApkBuilder.build(profile, dex, key)
        }
        val file = exportFile(context, profile, "apk")
        file.writeBytes(bytes)
        return file
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = uri(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("extforge", uri)
        }
        context.startActivity(
            Intent.createChooser(send, "EXTFORGE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, file: File) {
        val uri = uri(context, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri("extforge", uri)
        }
        context.startActivity(view)
    }

    fun publishDownloads(context: Context, file: File, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val item = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(item)?.use { stream ->
                file.inputStream().use { input -> input.copyTo(stream) }
            } ?: return false
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(item, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    data class Exported(val file: File, val notice: String)

    fun exportApk(
        context: Context,
        repository: ExtensionRepository,
        profile: ExtensionProfile,
    ): Exported {
        val code = profile.apkVersionCode.coerceAtLeast(1)
        val built = profile.canonical().copy(apkVersionCode = code)
        val file = writeApk(context, built)
        repository.upsert(built.copy(apkVersionCode = code + 1))
        val saved = publishDownloads(context, file, APK)
        val where = if (saved) "  SAVED TO DOWNLOADS" else ""
        return Exported(file, "APK ${file.name}$where  TRUST THIS SIGNATURE IN MIHON")
    }

    private fun exportFile(context: Context, profile: ExtensionProfile, ext: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) {
            error("Could not create export folder")
        }
        val slug = profile.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "extension" }
        return File(dir, "$slug-${profile.lang}-${profile.apkVersionCode}.$ext")
    }

    private fun uri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
