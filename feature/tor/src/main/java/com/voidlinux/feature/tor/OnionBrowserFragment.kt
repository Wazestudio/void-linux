package com.voidlinux.feature.tor

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.voidlinux.feature.tor.databinding.FragmentOnionBrowserBinding

/**
 * Version Fragment du navigateur .onion.
 * Utilisable si tu préfères la navigation par fragments.
 */
class OnionBrowserFragment : Fragment() {

    private var _binding: FragmentOnionBrowserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnionBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settings = binding.webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, false)

        binding.webView.webViewClient = TorWebViewClient()

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility =
                    if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        binding.urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(binding.urlBar.text.toString())
                true
            } else false
        }

        binding.webView.loadUrl("https://check.torproject.org")
        binding.urlBar.setText("https://check.torproject.org")
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val hasScheme = SCHEME_PATTERN.containsMatchIn(value)
        val candidate = if (hasScheme) value else "https://$value"
        val parsed = Uri.parse(candidate)
        val url = when {
            hasScheme && parsed.scheme !in ALLOWED_SCHEMES -> {
                binding.urlBar.error = "Seules les URL HTTP(S) sont autorisées"
                return
            }
            parsed.host?.endsWith(".onion", ignoreCase = true) == true && !hasScheme ->
                "http://$value"
            parsed.host != null -> candidate
            else -> "https://duckduckgo.com/?q=${Uri.encode(value)}"
        }
        binding.urlBar.error = null
        binding.urlBar.setText(url)
        binding.webView.loadUrl(url)
    }

    override fun onDestroyView() {
        binding.webView.clearHistory()
        binding.webView.clearCache(true)
        binding.webView.destroy()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }
        WebStorage.getInstance().deleteAllData()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val SCHEME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        private val ALLOWED_SCHEMES = setOf("http", "https")
    }
}