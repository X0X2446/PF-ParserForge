package com.parserforge.core.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorPickerScriptTest {
    @Test
    fun exposesTheNativeBridgeContract() {
        val source = SelectorPickerScript.source
        assertTrue(source.contains("ParserForgeBridge.onElementPicked"))
        assertTrue(source.contains("ParserForgeBridge.onHtmlSnapshot"))
        assertTrue(source.contains("setPick"))
        assertTrue(source.contains("snapshot"))
        assertTrue(source.contains(":nth-of-type"))
        assertFalse(source.contains("cf_clearance"))
    }
}
