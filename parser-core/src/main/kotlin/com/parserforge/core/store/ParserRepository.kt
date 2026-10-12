package com.parserforge.core.store

import com.parserforge.core.json.ParserSchema
import com.parserforge.core.model.ParserProfile
import java.io.File
import java.io.IOException

class ParserRepository(private val root: File) {
    private val file = File(root, "parsers.json")
    private val lock = Any()

    fun list(): List<ParserProfile> = synchronized(lock) { read() }

    fun get(id: String): ParserProfile? = synchronized(lock) {
        read().firstOrNull { it.id == id }
    }

    fun upsert(profile: ParserProfile) = synchronized(lock) {
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

    private fun read(): List<ParserProfile> {
        if (!file.exists()) return emptyList()
        val raw = file.readText()
        if (raw.isBlank()) return emptyList()
        return ParserSchema.decodeList(raw)
    }

    private fun write(items: List<ParserProfile>) {
        if (!root.exists() && !root.mkdirs() && !root.exists()) {
            throw IOException("Could not create ${root.path}")
        }
        val tmp = File(root, "parsers.json.tmp")
        tmp.writeText(ParserSchema.encodeList(items))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }
}
