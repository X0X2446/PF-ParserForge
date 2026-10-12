package com.parserforge.core.net

import com.parserforge.core.model.SessionSnapshot
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class FetchResult(
    val code: Int,
    val body: String,
    val finalUrl: String,
)

/**
 * Sends a raw GET with the user agent and cookies already stored by the WebView
 * for the page the user opened. Cookies are attached only when the request host
 * matches the host they were captured on.
 */
class SessionHttpClient internal constructor(
    private val client: OkHttpClient,
) {
    constructor() : this(defaultClient())

    fun fetch(url: String, session: SessionSnapshot): FetchResult {
        val normalized = Urls.normalize(url)
            ?: throw IllegalArgumentException("URL must be http or https")
        val callClient = client.newBuilder()
            .addInterceptor(SessionHeaderInterceptor(session))
            .build()
        val request = Request.Builder().url(normalized).get().build()
        callClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            val body = if (payload.length > MAX_BODY) payload.substring(0, MAX_BODY) else payload
            return FetchResult(
                code = response.code,
                body = body,
                finalUrl = response.request.url.toString(),
            )
        }
    }

    private class SessionHeaderInterceptor(
        private val session: SessionSnapshot,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder = request.newBuilder()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            if (session.userAgent.isNotBlank()) {
                builder.header("User-Agent", session.userAgent)
            }
            val cookie = session.cookieHeaderFor(request.url.host)
            if (cookie.isNotBlank()) {
                builder.header("Cookie", cookie)
            }
            return chain.proceed(builder.build())
        }
    }

    companion object {
        private const val MAX_BODY = 2_000_000

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
