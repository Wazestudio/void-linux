package com.voidlinux.feature.tor

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.voidlinux.core.common.Logger
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLConnection

/**
 * WebViewClient qui force chaque requête à passer par le proxy SOCKS de Tor.
 *
 * Le WebView Android ne supporte pas nativement le proxy SOCKS par requête.
 * La technique consiste à intercepter chaque requête, ouvrir manuellement
 * la connexion via Tor (127.0.0.1:9050), et retourner la réponse au WebView.
 */
open class TorWebViewClient(
    private val socksPort: Int = 9050
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        // Autorise uniquement les schémas http/https
        if (!url.startsWith("http")) return null

        return try {
            fetchViaTor(url, request)
        } catch (e: Exception) {
            Logger.e("Erreur Tor WebView : $url", e)
            null
        }
    }

    private fun fetchViaTor(
        url: String,
        request: WebResourceRequest
    ): WebResourceResponse? {

        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", socksPort)
        )

        val connection = URL(url).openConnection(proxy) as URLConnection

        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", USER_AGENT)

        // Recopie des en-têtes de la requête
        request.requestHeaders.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        connection.connect()

        val contentType = connection.contentType ?: "text/html"
        val encoding = connection.contentEncoding ?: "utf-8"
        val (mime, charset) = parseContentType(contentType, encoding)

        val httpConnection = connection as? java.net.HttpURLConnection
        val statusCode = httpConnection?.responseCode ?: 200
        val reasonPhrase = httpConnection?.responseMessage ?: "OK"

        val headers = mutableMapOf<String, String>()

        connection.headerFields.forEach { (key, value) ->
            if (key != null && value.isNotEmpty()) {
                headers[key] = value.first()
            }
        }

        return WebResourceResponse(
            mime,
            charset,
            statusCode,
            reasonPhrase,
            headers,
            connection.inputStream
        )
    }

    private fun parseContentType(
        contentType: String,
        fallback: String
    ): Pair<String, String> {
        val parts = contentType.split(";")

        val mime = parts
            .getOrNull(0)
            ?.trim()
            ?: "text/html"

        val charset = parts
            .firstOrNull { it.contains("charset", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            ?: fallback

        return mime to charset
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:115.0) Gecko/20100101 Firefox/115.0"
    }
}
