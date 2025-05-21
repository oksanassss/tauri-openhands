package com.tauri_openhands.app

import android.annotation.SuppressLint
import android.content.Intent
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

        // Initialize the bridge manager
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
        // Add JavaScript interface
        webView.addJavascriptInterface(bridgeManager, "StradaNativeBridge")

        // Enable JavaScript
        webView.settings.javaScriptEnabled = true

        // Inject Strada JavaScript API
        injectStradaJavaScript(webView)
        
        // For testing, load our test HTML in debug mode
        if (BuildConfig.DEBUG) {
            // Load our test page directly
            webView.loadUrl("file:///android_asset/strada-test.html")
            
            // For testing with the Stimulus demo site
            // webView.loadUrl("https://stimulusjs.demo.tebe.ch/")
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

    private fun injectStradaJavaScript(webView: WebView) {
        // Inject the Strada JavaScript API when the page loads
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished loading: $url")

                // Inject the Strada JavaScript API
                val stradaJs = """
                    if (!window.Strada) {
                        window.Strada = {
                            registerComponent: function(name) {
                                if (!this[name]) {
                                    this[name] = {
                                        send: function(event, data) {
                                            const message = {
                                                component: name,
                                                event: event,
                                                data: data || {}
                                            };
                                            window.StradaNativeBridge.receiveMessage(JSON.stringify(message));
                                            console.log('Strada: Sent message', message);
                                            return true;
                                        }
                                    };
                                }
                                return this[name];
                            },

                            // Scan the DOM for data-controller attributes and register components
                            scanForComponents: function() {
                                const elements = document.querySelectorAll('[data-controller]');
                                elements.forEach(function(element) {
                                    const controllers = element.getAttribute('data-controller').split(' ');
                                    controllers.forEach(function(controller) {
                                        if (!window.Strada[controller]) {
                                            window.Strada[controller] = window.Strada.registerComponent(controller);
                                            console.log('Strada: Registered component', controller);
                                        }
                                    });
                                });
                            }
                        };

                        // Function to receive messages from native code
                        window.StradaReceiveMessage = function(messageJson) {
                            try {
                                const message = JSON.parse(messageJson);
                                console.log('Strada: Received message from native', message);
                                
                                // Dispatch a custom event
                                const event = new CustomEvent('strada:' + message.component + ':' + message.event, {
                                    detail: message.data,
                                    bubbles: true
                                });
                                document.dispatchEvent(event);
                                return true;
                            } catch (e) {
                                console.error('Strada: Error processing message', e);
                                return false;
                            }
                        };

                        // Observe DOM changes to detect new components
                        const observer = new MutationObserver(function() {
                            window.Strada.scanForComponents();
                        });

                        // Start observing once the body is available
                        if (document.body) {
                            observer.observe(document.body, {
                                childList: true,
                                subtree: true,
                                attributes: true,
                                attributeFilter: ['data-controller']
                            });
                            
                            // Initial scan
                            window.Strada.scanForComponents();
                            console.log('Strada: Bridge initialized');
                        } else {
                            // Wait for body to be available
                            document.addEventListener('DOMContentLoaded', function() {
                                observer.observe(document.body, {
                                    childList: true,
                                    subtree: true,
                                    attributes: true,
                                    attributeFilter: ['data-controller']
                                });
                                
                                // Initial scan
                                window.Strada.scanForComponents();
                                console.log('Strada: Bridge initialized');
                            });
                        }
                    }
                """.trimIndent()

                view?.evaluateJavascript(stradaJs, null)
            }
        }
    }
}
