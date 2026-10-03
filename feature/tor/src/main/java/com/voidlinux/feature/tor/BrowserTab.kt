package com.voidlinux.feature.tor

import android.graphics.Bitmap
import android.webkit.WebView

/**
 * Représente un onglet du navigateur.
 */
data class BrowserTab(
    val id: Long = System.currentTimeMillis(),
    var title: String = "Nouvel onglet",
    var url: String = "about:blank",
    var favicon: Bitmap? = null,
    var webView: WebView? = null,
    var isIncognito: Boolean = false
)