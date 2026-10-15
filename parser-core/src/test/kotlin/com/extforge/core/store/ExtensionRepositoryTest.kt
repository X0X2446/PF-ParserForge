package com.extforge.core.store

import com.extforge.core.model.ExtensionProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionRepositoryTest {
    @Test
    fun migratesLegacyParserFileAndUpdatesInPlace() {
        val root = File.createTempFile("extforge", "dir")
        root.delete()
        val legacy = File(root, "parserforge")
        legacy.mkdirs()
        File(legacy, "parsers.json").writeText(
            """
            [{
              "schemaVersion": 1,
              "id": "22222222-2222-2222-2222-222222222222",
              "name": "Legacy",
              "entryUrl": "https://example.com/a",
              "selectors": {
                "mangaListTitle": {"selector": ".t", "extract": "text"},
                "coverThumbnail": {"selector": "", "extract": "src"},
                "chapterUrl": {"selector": "", "extract": "href"},
                "readerPageImages": {"selector": "", "extract": "src"}
              }
            }]
            """.trimIndent(),
        )
        val store = File(root, "extforge")
        val repository = ExtensionRepository(store, legacy)
        val loaded = repository.list().single()
        assertEquals("Legacy", loaded.name)
        assertEquals(".t", loaded.list.item)
        repository.upsert(loaded.copy(name = "Legacy 2"))
        assertEquals("Legacy 2", ExtensionRepository(store, legacy).list().single().name)
        root.deleteRecursively()
    }

    @Test
    fun newestInsertIsFirst() {
        val root = File.createTempFile("extforge", "store")
        root.delete()
        root.mkdirs()
        val repository = ExtensionRepository(root)
        val first = ExtensionProfile.create("A", "en", "https://a.example")
        val second = ExtensionProfile.create("B", "en", "https://b.example")
        repository.upsert(first)
        repository.upsert(second)
        assertEquals(listOf("B", "A"), repository.list().map { it.name })
        root.deleteRecursively()
    }
}
