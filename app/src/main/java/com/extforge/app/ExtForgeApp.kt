package com.extforge.app

import android.app.Application
import com.extforge.core.store.ExtensionRepository
import java.io.File

class ExtForgeApp : Application() {
    lateinit var repository: ExtensionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ExtensionRepository(
            root = File(filesDir, "extforge"),
            legacyRoot = File(filesDir, "parserforge"),
        )
    }
}
