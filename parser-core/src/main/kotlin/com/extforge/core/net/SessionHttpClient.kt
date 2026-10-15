package com.extforge.core.net

import com.extforge.core.model.AccessPolicy
import com.extforge.core.model.CookiePolicy
import com.extforge.core.model.canonical
import java.net.URI
import java.util.Locale
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
 * Plain GET used by the test run. Cookie policy `none` sends no Cookie header.
 * `manual` sends only the header the user pasted, and only to the source host.
 */
class SessionHttpClient internal constructor(
    private val client: OkHttpClient,
) {
    constructor() : this(defaultClient())

    fun fetch(url: String, access: AccessPolicy, baseUrl: String): FetchResult {
        val normalized = Urls.normalize(url)
            ?: throw IllegalArgumentException("URL must be http or https")
        val callClient = client.newBuilder()
            .addInterceptor(AccessInterceptor(access.canonical(), baseUrl))
            .build()
        val request = Request.Builder().url(normalized).get().build()
        if (access.delayMs > 0) {
            try {
                Thread.sleep(access.delayMs.coerceIn(0, 10_000).toLong())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
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

    private class AccessInterceptor(
        private val access: AccessPolicy,
        private val baseUrl: String,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder = request.newBuilder()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            if (access.userAgent.isNotBlank()) {
                builder.header("User-Agent", access.userAgent)
            }
            if (access.cookiePolicy == CookiePolicy.MANUAL && access.cookieHeader.isNotBlank()) {
                val baseHost = runCatching { URI(baseUrl).host }.getOrNull()
                if (hostMatches(request.url.host, baseHost)) {
                    builder.header("Cookie", access.cookieHeader)
                }
            }
            return chain.proceed(builder.build())
        }
    }

    companion object {
        private const val MAX_BODY = 2_000_000

        fun hostMatches(host: String?, baseHost: String?): Boolean {
            if (host.isNullOrBlank() || baseHost.isNullOrBlank()) return false
            val left = host.lowercase(Locale.US)
            val right = baseHost.lowercase(Locale.US)
            return left == right || left.endsWith(".$right")
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
