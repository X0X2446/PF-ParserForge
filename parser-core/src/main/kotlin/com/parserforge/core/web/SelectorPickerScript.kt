package com.parserforge.core.web

object SelectorPickerScript {
    const val BRIDGE_NAME = "ParserForgeBridge"

    val source: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val stream = SelectorPickerScript::class.java.getResourceAsStream("/selector_picker.js")
            ?: error("Missing /selector_picker.js")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
