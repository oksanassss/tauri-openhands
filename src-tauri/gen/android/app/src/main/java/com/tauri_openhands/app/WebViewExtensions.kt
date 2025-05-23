package com.tauri_openhands.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity
import android.webkit.WebSettings

private const val TAG = "WebViewExtensions"

/**
 * Extension function to enable Strada bridge support for a WebView.
 * This function configures the WebView with the necessary settings and injects the Strada bridge JavaScript.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.enableStradaBridge(activity: Activity): StradaBridgeManager {
    // Enable JavaScript
    settings.javaScriptEnabled = true
    
    // Enable DOM storage
    settings.domStorageEnabled = true
    
    // Create and initialize the bridge manager
    val bridgeManager = StradaBridgeManager(activity)
    bridgeManager.setWebView(this)
    
    Log.d(TAG, "Strada bridge enabled for WebView")
    
    return bridgeManager
}

/**
 * Extension function to set a callback for when a page finishes loading.
 */
fun WebView.setOnPageFinishedListener(listener: (String) -> Unit) {
    // Store the original WebViewClient
    val originalClient = webViewClient
    
    // Create a new WebViewClient that calls the listener
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            // Call the original client's onPageFinished method
            if (originalClient != null) {
                originalClient.onPageFinished(view, url)
            }
            
            // Call the listener
            listener(url)
        }
    }
}

/**
 * Extension function to load a URL and ensure Strada bridge is enabled.
 * This function loads the specified URL and ensures the Strada bridge is initialized.
 */
fun WebView.loadUrlWithStrada(url: String, bridgeManager: StradaBridgeManager) {
    // Set the WebView in the bridge manager
    bridgeManager.setWebView(this)
    
    // Load the URL
    loadUrl(url)
    
    Log.d(TAG, "Loading URL with Strada bridge: $url")
}

fun WebView.setupForStrada(activity: Activity) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        databaseEnabled = true
        setGeolocationEnabled(true)
    }
    
    val bridge = StradaBridge(activity, this)
    addJavascriptInterface(bridge, "StradaNativeBridge")
}