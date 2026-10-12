package com.parserforge.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.parserforge.core.model.ElementCapture
import com.parserforge.core.net.Urls
import com.parserforge.core.web.SelectorPickerScript
import org.json.JSONObject

/**
 * WebView canvas that installs [SelectorPickerScript] and forwards picked
 * elements to Kotlin through [Bridge].
 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
class SelectorWebView(context: Context) : WebView(context) {
    interface Listener {
        fun onProgress(value: Int) {}
        fun onStatus(message: String) {}
        fun onScriptReady() {}
        fun onSession(url: String, userAgent: String, cookieHeader: String) {}
        fun onElementPicked(capture: ElementCapture) {}
        fun onHtmlSnapshot(selector: String, html: String, pageUrl: String) {}
    }

    var listener: Listener? = null

    private val mainThread = Handler(Looper.getMainLooper())
    private var pickMode = false
    private var scriptReady = false
    private var pendingSnapshot: String? = null
    private var lastSessionKey = ""
    private var lastSessionAt = 0L

    @Volatile
    private var lastPickAt = 0L

    @Volatile
    private var lastHtmlAt = 0L

    init {
        setBackgroundColor(Color.BLACK)
        configure()
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(Bridge(), SelectorPickerScript.BRIDGE_NAME)
        webViewClient = Client()
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                listener?.onProgress(newProgress)
            }
        }
        setOnLongClickListener { pickMode }
    }

    fun loadAddress(raw: String) {
        val url = Urls.normalize(raw) ?: run {
            listener?.onStatus("URL MUST BE HTTP OR HTTPS")
            return
        }
        loadUrl(url)
    }

    fun setPickMode(enabled: Boolean) {
        pickMode = enabled
        if (scriptReady) applyPick()
    }

    fun captureSession() {
        lastSessionAt = 0L
        emitSession()
    }

    fun requestSnapshot(selector: String) {
        if (!scriptReady) {
            pendingSnapshot = selector
            return
        }
        val quoted = JSONObject.quote(selector)
        evaluateJavascript("window.__pf&&window.__pf.snapshot($quoted);", null)
    }

    private fun configure() {
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = WebSettings.FORCE_DARK_OFF
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isAlgorithmicDarkeningAllowed = false
            }
        }
    }

    private fun applyPick() {
        val flag = if (pickMode) "true" else "false"
        evaluateJavascript("window.__pf&&window.__pf.setPick($flag);", null)
    }

    private fun inject() {
        val script = try {
            SelectorPickerScript.source
        } catch (error: Exception) {
            listener?.onStatus(error.message ?: "PICKER SCRIPT MISSING")
            return
        }
        scriptReady = false
        evaluateJavascript(executableScript(script)) { result ->
            scriptReady = true
            applyPick()
            val pending = pendingSnapshot
            pendingSnapshot = null
            if (result == "true") listener?.onScriptReady()
            if (pending != null) requestSnapshot(pending)
        }
    }

    private fun sameDocument(view: WebView, finished: String?): Boolean {
        val main = view.url ?: return true
        if (finished.isNullOrBlank() || finished == main) return true
        val left = Urls.normalize(main)
        val right = Urls.normalize(finished)
        return left != null && left == right
    }

    private fun executableScript(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("/*")) return trimmed
        val end = trimmed.indexOf("*/")
        if (end < 0) return trimmed
        return trimmed.substring(end + 2).trim()
    }

    private fun emitSession() {
        val page = url ?: return
        if (Urls.normalize(page) == null) return
        val now = SystemClock.uptimeMillis()
        if (page == lastSessionKey && now - lastSessionAt < 1500L) return
        lastSessionKey = page
        lastSessionAt = now
        CookieManager.getInstance().flush()
        val header = CookieManager.getInstance().getCookie(page).orEmpty()
        listener?.onSession(page, settings.userAgentString.orEmpty(), header)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url?.scheme?.lowercase().orEmpty()
            val allowed = scheme == "http" || scheme == "https"
            if (!allowed) listener?.onStatus("BLOCKED ${request.url?.scheme ?: ""}".trim())
            return !allowed
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            listener?.onProgress(0)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (!sameDocument(view, url)) return
            emitSession()
            inject()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            listener?.onStatus(error.description?.toString() ?: "LOAD FAILED")
        }
    }

    private inner class Bridge {
        @android.webkit.JavascriptInterface
        fun onElementPicked(selector: String, tag: String, text: String, href: String, src: String) {
            val now = SystemClock.uptimeMillis()
            if (now - lastPickAt < 80L) return
            lastPickAt = now
            val capture = ElementCapture(
                selector = selector.take(2000),
                tag = tag.take(40),
                text = text.take(180),
                href = href.take(2000),
                src = src.take(2000),
            )
            mainThread.post { listener?.onElementPicked(capture) }
        }

        @android.webkit.JavascriptInterface
        fun onHtmlSnapshot(selector: String, html: String, pageUrl: String) {
            val now = SystemClock.uptimeMillis()
            if (now - lastHtmlAt < 150L) return
            lastHtmlAt = now
            val capped = if (html.length > 400_000) html.substring(0, 400_000) else html
            mainThread.post {
                listener?.onHtmlSnapshot(selector.take(2000), capped, pageUrl.take(2000))
            }
        }
    }
}
