package com.voidlinux.feature.tor

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.voidlinux.feature.tor.OnionDirectory.Category
import com.voidlinux.feature.tor.OnionDirectory.OnionLink

class OnionBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var tabLayout: TabLayout
    private lateinit var tabContainer: LinearLayout
    private lateinit var directoryButton: FloatingActionButton

    private val tabs = mutableListOf<BrowserTab>()
    private var currentTabIndex = 0
    private var javaScriptEnabled = false
    private lateinit var browserLock: BrowserLock

    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        browserLock = BrowserLock(this)

        // Vérifie le verrouillage au démarrage
        if (browserLock.isLockEnabled()) {
            unlockLauncher.launch(Intent(this, LockActivity::class.java))
        }

        setContentView(R.layout.activity_onion_browser)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        progressBar = findViewById(R.id.progressBar)
        backButton = findViewById(R.id.backButton)
        forwardButton = findViewById(R.id.forwardButton)
        menuButton = findViewById(R.id.menuButton)
        tabLayout = findViewById(R.id.tabLayout)
        tabContainer = findViewById(R.id.tabContainer)
        directoryButton = findViewById(R.id.directoryButton)

        setupWebView()
        setupButtons()
        setupTabs()

        // Crée le premier onglet
        newTab("https://check.torproject.org")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = false
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

        webView.webViewClient = object : TorWebViewClient(socksPort = 9050) {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { urlBar.setText(it) }
                tabs[currentTabIndex].url = url ?: ""
                updateTabTitle(url ?: "Nouvel onglet")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress < 100) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                updateTabTitle(title ?: "Sans titre")
            }
        }
    }

    private fun setupButtons() {
        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        forwardButton.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        menuButton.setOnClickListener { showMenu() }
        directoryButton.setOnClickListener { showDirectory() }

        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(urlBar.text.toString())
                true
            } else false
        }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchToTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {}
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                // Ferme l'onglet au double-clic
                closeTab(tab.position)
            }
        })
    }

    private fun newTab(url: String = "about:blank") {
        val tab = BrowserTab(url = url)
        tabs.add(tab)
        tabLayout.addTab(tabLayout.newTab().setText("Nouvel onglet"))
        switchToTab(tabs.size - 1)
        if (url != "about:blank") navigate(url)
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        tabs.removeAt(index)
        tabLayout.removeTabAt(index)
        if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
        switchToTab(currentTabIndex)
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices) return
        currentTabIndex = index
        urlBar.setText(tabs[index].url)
    }

    private fun updateTabTitle(title: String) {
        if (currentTabIndex in tabs.indices) {
            tabs[currentTabIndex].title = title
            tabLayout.getTabAt(currentTabIndex)?.setText(title.take(20))
        }
    }

    private fun showMenu() {
        val options = arrayOf(
            "Nouvel onglet",
            "Nouvel onglet privé",
            "Fermer l'onglet",
            "Téléchargements",
            "Historique",
            "Vider les données",
            "Verrouiller le navigateur",
            "Paramètres"
        )

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> newTab()
                    1 -> newTabIncognito()
                    2 -> closeTab(currentTabIndex)
                    3 -> showDownloads()
                    4 -> showHistory()
                    5 -> clearData()
                    6 -> showLockSettings()
                    7 -> showSettings()
                }
            }
            .show()
    }

    private fun newTabIncognito() {
        val tab = BrowserTab(url = "about:blank", isIncognito = true)
        tabs.add(tab)
        tabLayout.addTab(tabLayout.newTab().setText("Incognito"))
        switchToTab(tabs.size - 1)
    }

    private fun showDownloads() {
        Toast.makeText(this, "Téléchargements : fonctionnalité à venir", Toast.LENGTH_SHORT).show()
    }

    private fun showHistory() {
        Toast.makeText(this, "Historique : fonctionnalité à venir", Toast.LENGTH_SHORT).show()
    }

    private fun clearData() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.clearFormData()
        Toast.makeText(this, "Données de navigation effacées", Toast.LENGTH_SHORT).show()
    }

    private fun showLockSettings() {
        val options = arrayOf(
            "Désactiver le verrouillage",
            "Définir un PIN",
            "Définir un schéma"
        )

        AlertDialog.Builder(this)
            .setTitle("Verrouillage du navigateur")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        browserLock.disable()
                        Toast.makeText(this, "Verrouillage désactivé", Toast.LENGTH_SHORT).show()
                    }
                    1 -> setPin()
                    2 -> setPattern()
                }
            }
            .show()
    }

    private fun setPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Nouveau PIN (4-8 chiffres)"
        }

        AlertDialog.Builder(this)
            .setTitle("Définir un PIN")
            .setView(input)
            .setPositiveButton("Valider") { _, _ ->
                val pin = input.text.toString()
                if (pin.length in 4..8 && pin.all { it.isDigit() }) {
                    browserLock.setPin(pin)
                    Toast.makeText(this, "PIN défini", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN invalide (4-8 chiffres)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun setPattern() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Schéma (ex: 1235789)"
        }

        AlertDialog.Builder(this)
            .setTitle("Définir un schéma")
            .setMessage("Dessine un schéma en reliant au moins 4 points. Entre la séquence de chiffres correspondante.")
            .setView(input)
            .setPositiveButton("Valider") { _, _ ->
                val pattern = input.text.toString()
                if (pattern.length >= 4 && pattern.all { it.isDigit() }) {
                    browserLock.setPattern(pattern)
                    Toast.makeText(this, "Schéma défini", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Schéma invalide (min 4 points)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showSettings() {
        val options = arrayOf(
            if (javaScriptEnabled) "Désactiver JavaScript" else "Activer JavaScript",
            "Vider le cache",
            "Réinitialiser le navigateur"
        )

        AlertDialog.Builder(this)
            .setTitle("Paramètres")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleJavaScript()
                    1 -> webView.clearCache(true)
                    2 -> {
                        clearData()
                        browserLock.disable()
                    }
                }
            }
            .show()
    }

    private fun toggleJavaScript() {
        javaScriptEnabled = !javaScriptEnabled
        webView.settings.javaScriptEnabled = javaScriptEnabled
        webView.reload()
        Toast.makeText(
            this,
            if (javaScriptEnabled) "JavaScript activé" else "JavaScript désactivé",
            Toast.LENGTH_SHORT
        ).show()
    }

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

    private fun showCategory(category: Category) {
        val links = OnionDirectory.byCategory(category)
        if (links.isEmpty()) {
            Toast.makeText(this, "Aucun lien dans cette catégorie", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = links.map { "${it.name} — ${it.description}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(category.label)
            .setItems(labels) { _, which ->
                confirmNavigation(links[which])
            }
            .setNegativeButton("Retour", null)
            .show()
    }

    private fun confirmNavigation(link: OnionLink) {
        AlertDialog.Builder(this)
            .setTitle(link.name)
            .setMessage(
                "${link.description}\n\n${link.url}\n\n" +
                "⚠️ Void-Linux ne contrôle pas le contenu de ce site."
            )
            .setPositiveButton("Ouvrir") { _, _ -> navigate(link.url) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun navigate(input: String) {
        val url = normalizeUrl(input)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "URL non autorisée", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}