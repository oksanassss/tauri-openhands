package com.tauri_openhands.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tauri_openhands.app.TauriActivity

class MainActivity : TauriActivity() {
    private val TAG = "MainActivity"
    private lateinit var bridgeManager: StradaBridgeManager
    private var webView: WebView? = null
    private val FILE_PICKER_REQUEST_CODE = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the bridge manager
        bridgeManager = StradaBridgeManager(this)
        
        // Log that the bridge manager has been initialized
        Log.d(TAG, "StradaBridgeManager initialized")
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
    
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        
        // Get the WebView from Tauri after it's created
        try {
            webView = findViewById(getWebViewId())
            if (webView != null) {
                // Set the WebView reference in the bridge manager
                bridgeManager.setWebView(webView!!)
                
                setupWebView(webView!!)
                Log.d(TAG, "WebView setup complete")
            } else {
                Log.e(TAG, "WebView not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up WebView", e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        // Enable JavaScript and DOM storage
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        // Enable Strada bridge support
        bridgeManager = webView.enableStradaBridge(this)
        
        // Load the Stimulus demo page
        webView.loadUrlWithStrada("https://stimulusjs.demo.tebe.ch/", bridgeManager)
        
        // Uncomment the line below to load the bundled web content instead
        // No need to manually load a URL - Tauri will load the bundled web content automatically
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
