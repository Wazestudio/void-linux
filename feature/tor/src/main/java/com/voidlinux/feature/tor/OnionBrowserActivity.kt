package com.voidlinux.feature.tor

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.net.Uri
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.voidlinux.feature.tor.R

/**
 * Navigateur Tor intégré : charge les URL http(s) et .onion via SOCKS.
 */
class OnionBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onion_browser)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)
        backButton = findViewById(R.id.backButton)

        setupWebView()
        setupUrlBar()
        setupBackButton()

        urlBar.setText("https://check.torproject.org")
        webView.loadUrl("https://check.torproject.org")
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = TorWebViewClient(socksPort = 9050)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress < 100) android.view.View.VISIBLE
                    else android.view.View.GONE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                supportActionBar?.title = title ?: "Void Tor"
            }
        }
    }

    private fun setupUrlBar() {
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(urlBar.text.toString())
                true
            } else false
        }
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
            else finish()
        }
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val hasScheme = SCHEME_PATTERN.containsMatchIn(value)
        val candidate = if (hasScheme) value else "https://$value"
        val parsed = Uri.parse(candidate)
        val url = when {
            hasScheme && parsed.scheme !in ALLOWED_SCHEMES -> {
                urlBar.error = "Seules les URL HTTP(S) sont autorisées"
                return
            }
            parsed.host?.endsWith(".onion", ignoreCase = true) == true && !hasScheme ->
                "http://$value"
            parsed.host != null -> candidate
            else -> "https://duckduckgo.com/?q=${Uri.encode(value)}"
        }
        urlBar.error = null
        urlBar.setText(url)
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.destroy()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }
        WebStorage.getInstance().deleteAllData()
        super.onDestroy()
    }

    companion object {
        private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        private val ALLOWED_SCHEMES = setOf("http", "https")
    }
}