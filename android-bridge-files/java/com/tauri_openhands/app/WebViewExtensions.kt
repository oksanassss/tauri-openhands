package com.tauri_openhands.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

private const val TAG = "WebViewExtensions"

/**
 * Extension function to enable Strada bridge support for a WebView.
 * This function configures the WebView with the necessary settings and injects the Strada bridge JavaScript.
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.enableStradaBridge(context: Context): StradaBridgeManager {
    // Enable JavaScript
    settings.javaScriptEnabled = true
    
    // Enable DOM storage
    settings.domStorageEnabled = true
    
    // Create and initialize the bridge manager
    val bridgeManager = StradaBridgeManager(context as Context)
    bridgeManager.setWebView(this)
    
    Log.d(TAG, "Strada bridge enabled for WebView")
    
    return bridgeManager
}

/**
 * Extension function to detect Hotwire/Stimulus pages and automatically enable Strada bridge support.
 * This function sets a WebViewClient that checks for Stimulus-specific elements and injects the Strada bridge.
 */
fun WebView.enableAutoStradaBridge(context: Context): StradaBridgeManager {
    val bridgeManager = StradaBridgeManager(context as Context)
    
    // Set a WebViewClient that detects Stimulus pages
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            
            // Check if the page uses Stimulus
            view.evaluateJavascript("""
                (function() {
                    // Check for Stimulus controllers
                    const hasControllers = document.querySelectorAll('[data-controller]').length > 0;
                    
                    // Check for Stimulus script
                    const hasScript = Array.from(document.scripts).some(script => 
                        script.src && (
                            script.src.includes('stimulus') || 
                            script.src.includes('hotwired') ||
                            script.src.includes('turbo')
                        )
                    );
                    
                    return hasControllers || hasScript;
                })();
            """) { result ->
                val isHotwirePage = result.equals("true", ignoreCase = true)
                
                if (isHotwirePage) {
                    Log.d(TAG, "Detected Hotwire/Stimulus page at $url, enabling Strada bridge")
                    bridgeManager.setWebView(view)
                } else {
                    Log.d(TAG, "Not a Hotwire/Stimulus page at $url")
                }
            }
        }
    }
    
    return bridgeManager
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

fun WebView.detectStimulusControllers() {
    evaluateJavascript("""
        (function() {
            const controllers = [];
            document.querySelectorAll('[data-controller]').forEach(el => {
                const names = el.getAttribute('data-controller').split(' ');
                controllers.push(...names);
            });
            return JSON.stringify([...new Set(controllers)]);
        })()
    """) { result ->
        if (result != null && result != "null") {
            val controllerNames = JSONArray(result)
            for (i in 0 until controllerNames.length()) {
                val name = controllerNames.getString(i)
                Log.d("Strada", "Detected Stimulus controller: $name")
            }
        }
    }
}