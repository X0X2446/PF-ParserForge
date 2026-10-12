package com.parserforge.app

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import com.parserforge.core.store.ParserRepository
import java.io.File

class ParserForgeApp : Application() {
    lateinit var repository: ParserRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().setAcceptCookie(true)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        repository = ParserRepository(File(filesDir, "parserforge"))
    }
}
