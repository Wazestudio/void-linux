package com.voidlinux.feature.tor

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.voidlinux.feature.tor.OnionDirectory.Category
import com.voidlinux.feature.tor.OnionDirectory.OnionLink
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Navigateur .onion durci avec annuaire intégré.
 *
 * Sécurité :
 * - JavaScript désactivé par défaut (activable manuellement)
 * - Pas d'allowFileAccess / allowContentAccess
 * - Pas de JavascriptInterface
 * - Schémas autorisés : http, https, .onion
 * - Blocage des redirections non-.onion
 */
class OnionBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var directoryButton: FloatingActionButton
    private lateinit var toggleJsButton: ImageButton

    private var javaScriptEnabled = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onion_browser)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)
        backButton = findViewById(R.id.backButton)
        directoryButton = findViewById(R.id.directoryButton)
        toggleJsButton = findViewById(R.id.toggleJsButton)

        setupWebView()
        setupUrlBar()
        setupButtons()

        webView.loadUrl("https://check.torproject.org")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        // ---- Sécurité maximale ----
        settings.javaScriptEnabled = false              // Désactivé par défaut
        settings.domStorageEnabled = false
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString = TorWebViewClient.USER_AGENT
        settings.setSupportZoom(true)

        // ---- WebViewClient durci ----
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

    private fun setupButtons() {
        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        directoryButton.setOnClickListener {
            showDirectory()
        }

        toggleJsButton.setOnClickListener {
            toggleJavaScript()
        }
    }

    /**
     * Affiche l'annuaire .onion groupé par catégories.
     */
    private fun showDirectory() {
        val categories = Category.values()
        val labels = categories.map { it.label }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Annuaire .onion")
            .setItems(labels) { _, which ->
                showCategory(categories[which])
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * Affiche la liste des liens d'une catégorie.
     */
    private fun showCategory(category: Category) {
        val links = OnionDirectory.byCategory(category)
        if (links.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(category.label)
                .setMessage("Aucun lien dans cette catégorie.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val labels = links.map { "${it.name} — ${it.description}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(category.label)
            .setItems(labels) { _, which ->
                val link = links[which]
                confirmNavigation(link)
            }
            .setNegativeButton("Retour", null)
            .show()
    }

    /**
     * Demande confirmation avant de naviguer vers un site externe.
     */
    private fun confirmNavigation(link: OnionLink) {
        AlertDialog.Builder(this)
            .setTitle(link.name)
            .setMessage(
                "${link.description}\n\n" +
                "${link.url}\n\n" +
                "⚠️ Void-Linux ne contrôle pas le contenu de ce site.\n" +
                "Navigue de manière responsable."
            )
            .setPositiveButton("Ouvrir") { _, _ ->
                navigate(link.url)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /**
     * Active/désactive JavaScript (désactivé par défaut pour la sécurité).
     */
    private fun toggleJavaScript() {
        javaScriptEnabled = !javaScriptEnabled

        AlertDialog.Builder(this)
            .setTitle(if (javaScriptEnabled) "Activer JavaScript ?" else "Désactiver JavaScript ?")
            .setMessage(
                if (javaScriptEnabled)
                    "⚠️ Activer JavaScript augmente les risques d'exploitation XSS " +
                    "sur les sites .onion. À n'utiliser que si le site l'exige."
                else
                    "JavaScript sera désactivé. Le site peut ne plus fonctionner correctement."
            )
            .setPositiveButton("Confirmer") { _, _ ->
                webView.settings.javaScriptEnabled = javaScriptEnabled
                webView.reload()
            }
            .setNegativeButton("Annuler") { _, _ ->
                javaScriptEnabled = !javaScriptEnabled
            }
            .show()
    }

    /**
     * Navigue vers une URL en normalisant le format.
     */
    private fun navigate(input: String) {
        val url = normalizeUrl(input)
        if (!isUrlAllowed(url)) {
            AlertDialog.Builder(this)
                .setTitle("URL non autorisée")
                .setMessage("Seuls les schémas http, https et les domaines .onion sont autorisés.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        webView.loadUrl(url)
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.endsWith(".onion") -> "http://$trimmed"
            trimmed.contains(".") -> "https://$trimmed"
            else -> "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=$trimmed"
        }
    }

    private fun isUrlAllowed(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    @Deprecated("Deprecated in API 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}