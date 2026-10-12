package com.parserforge.core.store

import com.parserforge.core.model.ParserProfile
import com.parserforge.core.model.SelectorKey
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserRepositoryTest {
    @Test
    fun upsertKeepsPositionAndSurvivesANewInstance() {
        val root = Files.createTempDirectory("parserforge").toFile()
        val repository = ParserRepository(root)
        val first = ParserProfile.create("One", "https://one.example/")
        val second = ParserProfile.create("Two", "https://two.example/")
        repository.upsert(first)
        repository.upsert(second)
        assertEquals(listOf(second.id, first.id), repository.list().map { it.id })

        val updated = second.copy(
            selectors = SelectorKey.READER_PAGE_IMAGES.assign(second.selectors, "img.page"),
        )
        repository.upsert(updated)
        val reloaded = ParserRepository(root)
        assertEquals(listOf(second.id, first.id), reloaded.list().map { it.id })
        assertEquals("img.page", reloaded.get(second.id)!!.selectors.readerPageImages.selector)

        reloaded.delete(first.id)
        assertNull(reloaded.get(first.id))
        assertEquals(1, reloaded.list().size)
    }
}
