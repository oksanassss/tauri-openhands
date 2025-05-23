package com.tauri_openhands.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import app.tauri.android.TauriActivity
import com.tauri_openhands.app.BuildConfig

class MainActivity : TauriActivity() {
    private val TAG = "MainActivity"
    private lateinit var bridgeManager: StradaBridgeManager
    private var webView: WebView? = null
    private val FILE_PICKER_REQUEST_CODE = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridgeManager = StradaBridgeManager(this)
    }
    
    /**
     * Handle activity results for file picker
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Forward the result to the bridge manager
        bridgeManager.handleActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "Activity result forwarded to bridge manager: requestCode=$requestCode, resultCode=$resultCode")
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        
        try {
            webView = findViewById(getWebViewId())
            webView?.let { 
                setupWebView(it)
                bridgeManager.setWebView(it)
            } ?: run {
                Log.e(TAG, "WebView not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up WebView", e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                bridgeManager.setWebView(webView)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                bridgeManager.initialize()
            }
        }
    }

    private fun getWebViewId(): Int {
        // Try different possible WebView IDs
        val possibleIds = arrayOf("webview", "tauri_webview", "web_view")
        
        for (id in possibleIds) {
            val resId = resources.getIdentifier(id, "id", packageName)
            if (resId != 0) {
                Log.d(TAG, "Found WebView with ID: $id")
                return resId
            }
        }
        
        // Default to the first one if none found
        Log.w(TAG, "WebView ID not found, using default")
        return resources.getIdentifier("webview", "id", packageName)
    }

    // The injectStradaJavaScript method is now handled by the StradaBridgeManager
}
