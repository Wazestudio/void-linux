package com.voidlinux.feature.tor

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.voidlinux.core.common.Logger
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

/**
 * Routes HTTP(S) requests through Orbot's SOCKS proxy and fails closed if it is unavailable.
 */
class TorWebViewClient(
    private val socksPort: Int = 9050
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val scheme = request.url.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null

        if (!request.method.equals("GET", ignoreCase = true)) {
            return errorResponse(501, "Not Implemented", "Request method is not supported")
        }

        return try {
            fetchViaTor(request)
        } catch (e: IOException) {
            Logger.e("Tor proxy request failed for ${request.url.host}", e)
            errorResponse(502, "Bad Gateway", "Tor proxy unavailable; request blocked")
        }
    }

    private fun fetchViaTor(request: WebResourceRequest): WebResourceResponse {
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", socksPort)
        )
        val connection = URL(request.url.toString()).openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false

        request.requestHeaders.forEach { (name, value) ->
            if (name.lowercase(Locale.ROOT) !in HOP_BY_HOP_REQUEST_HEADERS) {
                connection.setRequestProperty(name, value)
            }
        }
        connection.setRequestProperty("Accept-Encoding", "identity")

        val statusCode = connection.responseCode
        val contentType = connection.contentType ?: "text/plain; charset=utf-8"
        val (mimeType, encoding) = parseContentType(contentType)
        val headers = connection.headerFields
            .filterKeys { key ->
                key != null && key.lowercase(Locale.ROOT) !in HOP_BY_HOP_RESPONSE_HEADERS
            }
            .mapNotNull { (key, values) ->
                key?.let { header -> values.firstOrNull()?.let { header to it } }
            }
            .toMap()
        val body = if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
            connection.errorStream
        } else {
            connection.inputStream
        } ?: ByteArrayInputStream(ByteArray(0))

        return WebResourceResponse(
            mimeType,
            encoding,
            statusCode,
            connection.responseMessage ?: "HTTP response",
            headers,
            body
        )
    }

    private fun parseContentType(contentType: String): Pair<String, String> {
        val mimeType = contentType.substringBefore(';').trim().ifEmpty { "text/plain" }
        val charset = CHARSET_PATTERN.find(contentType)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'')
            ?.ifEmpty { null }
            ?: "utf-8"
        return mimeType to charset
    }

    private fun errorResponse(
        statusCode: Int,
        reason: String,
        message: String
    ) = WebResourceResponse(
        "text/plain",
        "utf-8",
        statusCode,
        reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(message.toByteArray(Charsets.UTF_8))
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private val CHARSET_PATTERN = Regex("charset\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
        private val HOP_BY_HOP_REQUEST_HEADERS = setOf(
            "connection",
            "content-length",
            "expect",
            "host",
            "proxy-authorization",
            "proxy-connection",
            "transfer-encoding",
            "upgrade"
        )
        private val HOP_BY_HOP_RESPONSE_HEADERS = setOf(
            "connection",
            "content-encoding",
            "content-length",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
        )
    }
}
