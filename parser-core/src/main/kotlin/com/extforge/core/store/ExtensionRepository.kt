package com.extforge.core.store

import com.extforge.core.json.ExtensionSchema
import com.extforge.core.model.ExtensionProfile
import java.io.File
import java.io.IOException

class ExtensionRepository(
    private val root: File,
    private val legacyRoot: File? = null,
) {
    private val file = File(root, "extensions.json")
    private val lock = Any()

    fun list(): List<ExtensionProfile> = synchronized(lock) { read() }

    fun get(id: String): ExtensionProfile? = synchronized(lock) {
        read().firstOrNull { it.id == id }
    }

    fun upsert(profile: ExtensionProfile) = synchronized(lock) {
        val next = profile.canonical()
        val all = read().toMutableList()
        val index = all.indexOfFirst { it.id == next.id }
        if (index >= 0) {
            all[index] = next
        } else {
            all.add(0, next)
        }
        write(all)
    }

    fun delete(id: String) = synchronized(lock) {
        write(read().filterNot { it.id == id })
    }

    private fun read(): List<ExtensionProfile> {
        if (!file.exists()) {
            val legacy = legacyRoot?.let { File(it, "parsers.json") }
            if (legacy != null && legacy.isFile) {
                val migrated = ExtensionSchema.decodeList(legacy.readText())
                write(migrated)
                return migrated
            }
            return emptyList()
        }
        val raw = file.readText()
        if (raw.isBlank()) return emptyList()
        return ExtensionSchema.decodeList(raw)
    }

    private fun write(items: List<ExtensionProfile>) {
        if (!root.exists() && !root.mkdirs() && !root.exists()) {
            throw IOException("Could not create ${root.path}")
        }
        val tmp = File(root, "extensions.json.tmp")
        tmp.writeText(ExtensionSchema.encodeList(items))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }
}
