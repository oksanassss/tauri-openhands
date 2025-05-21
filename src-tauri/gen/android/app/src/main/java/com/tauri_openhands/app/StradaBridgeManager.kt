package com.tauri_openhands.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * StradaBridgeManager handles communication between web content and native Android components.
 * It detects data-controller attributes in the DOM and routes messages to the appropriate native components.
 * 
 * This implementation is based on the Hotwire Strada pattern for native bridge communication.
 */
class StradaBridgeManager(private val context: Context) {
    private var activity: Activity? = null
    private var webView: WebView? = null
    
    init {
        if (context is Activity) {
            activity = context
        }
    }
    
    /**
     * Set the WebView instance to use for JavaScript communication
     */
    fun setWebView(webView: WebView) {
        this.webView = webView
        initialize()
    }
    
    /**
     * Handle activity results from the parent activity
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Check for both camelCase and kebab-case component names
        val filePickerComponent = components["filePicker"] as? FilePickerComponent
        filePickerComponent?.handleActivityResult(requestCode, resultCode, data)
        
        // Also check for kebab-case component name (used by Stimulus)
        val kebabCaseComponent = components["file-picker"] as? FilePickerComponent
        if (kebabCaseComponent != null && kebabCaseComponent !== filePickerComponent) {
            kebabCaseComponent.handleActivityResult(requestCode, resultCode, data)
        }
    }
    
    companion object {
        private const val TAG = "StradaBridgeManager"
        private const val BRIDGE_NAME = "StradaBridge"
    }

    private val components = ConcurrentHashMap<String, NativeComponent>()

    /**
     * Initialize the bridge by injecting JavaScript and setting up the JavascriptInterface.
     */
    private fun initialize() {
        val webView = this.webView ?: return
        
        // Add JavaScript interface
        webView.addJavascriptInterface(this, "StradaNativeBridge")

        // Inject the Strada JavaScript bridge
        injectStradaJavaScript(webView)

        // Scan for data-controller attributes when page is loaded
        webView.setOnPageFinishedListener { url ->
            Log.d(TAG, "Page finished loading: $url")
            scanForControllers()
            
            // Send connected events to all registered components
            components.forEach { (name, _) ->
                sendToWeb(name, "connected", mapOf("status" to "ready"))
            }
        }
    }

    /**
     * Inject the Strada JavaScript bridge into the WebView.
     */
    private fun injectStradaJavaScript(webView: WebView) {
        val js = """
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
                        
                        // Also dispatch a generic strada-event for React components
                        const genericEvent = new CustomEvent('strada-event', {
                            detail: JSON.stringify(message),
                            bubbles: true
                        });
                        document.dispatchEvent(genericEvent);
                        
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
                
                // Check if this is a Stimulus page
                if (window.Stimulus || document.querySelector('script[src*="stimulus"]')) {
                    console.log('Strada: Detected Stimulus, loading adapter');
                    
                    // Load the Stimulus adapter
                    const stimulusAdapter = `
                    /**
                     * Strada Stimulus Adapter
                     * 
                     * This script provides integration between the Strada bridge and Stimulus controllers.
                     * It automatically registers Strada components for Stimulus controllers and forwards
                     * events between them.
                     */
                    
                    (function() {
                      // Wait for both Strada and Stimulus to be available
                      function waitForDependencies(callback) {
                        if (window.Strada && window.Stimulus) {
                          callback();
                        } else {
                          setTimeout(() => waitForDependencies(callback), 100);
                        }
                      }
                    
                      waitForDependencies(() => {
                        console.log('Strada Stimulus Adapter: Initializing');
                        
                        // Store original Stimulus controller registration
                        const originalRegister = window.Stimulus.register;
                        
                        // Override Stimulus.register to automatically register Strada components
                        window.Stimulus.register = function(name, controller) {
                          // Register with Stimulus
                          const result = originalRegister.call(this, name, controller);
                          
                          // Register with Strada
                          if (window.Strada && !window.Strada[name]) {
                            window.Strada.registerComponent(name);
                            console.log('Strada Stimulus Adapter: Registered component for controller', name);
                            
                            // Listen for Strada events and dispatch them to Stimulus controllers
                            document.addEventListener('strada:' + name + ':*', function(event) {
                              const eventName = event.type.split(':')[2];
                              const detail = event.detail;
                              
                              // Find all instances of this controller
                              const elements = document.querySelectorAll('[data-controller~="' + name + '"]');
                              elements.forEach(element => {
                                // Dispatch a custom event to the element
                                const customEvent = new CustomEvent('strada:' + eventName, {
                                  detail: detail,
                                  bubbles: true
                                });
                                element.dispatchEvent(customEvent);
                              });
                            });
                          }
                          
                          return result;
                        };
                        
                        // Patch existing controllers
                        if (window.Stimulus.application) {
                          const controllerNames = Object.keys(window.Stimulus.application.controllers);
                          controllerNames.forEach(name => {
                            if (!window.Strada[name]) {
                              window.Strada.registerComponent(name);
                              console.log('Strada Stimulus Adapter: Registered component for existing controller', name);
                            }
                          });
                        }
                        
                        // Add helper to Stimulus controllers
                        window.Stimulus.Controller.prototype.strada = function(event, data) {
                          const controllerName = this.identifier;
                          if (window.Strada && window.Strada[controllerName]) {
                            window.Strada[controllerName].send(event, data);
                            return true;
                          }
                          return false;
                        };
                        
                        console.log('Strada Stimulus Adapter: Initialized');
                      });
                    })();
                    `;
                    
                    // Evaluate the adapter script
                    setTimeout(function() {
                        eval(stimulusAdapter);
                    }, 500);
                }
            }
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }

    /**
     * Scan the DOM for data-controller attributes and register the corresponding components.
     */
    private fun scanForControllers() {
        val webView = this.webView ?: return
        
        val js = """
            (function() {
                const controllers = document.querySelectorAll('[data-controller]');
                const result = [];
                
                controllers.forEach(element => {
                    const controllerNames = element.getAttribute('data-controller').split(' ');
                    controllerNames.forEach(name => {
                        result.push(name);
                    });
                });
                
                return JSON.stringify(result);
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(js) { result ->
            try {
                // Parse the JSON array of controller names
                val jsonString = result.trim()
                if (jsonString.startsWith("\"[") && jsonString.endsWith("]\"")) {
                    // Remove the extra quotes and parse the JSON array
                    val controllerNamesJson = jsonString.substring(1, jsonString.length - 1)
                    val controllerNames = JSONObject("{\"names\":$controllerNamesJson}").getJSONArray("names")
                    
                    for (i in 0 until controllerNames.length()) {
                        val name = controllerNames.getString(i)
                        if (name.isNotEmpty()) {
                            registerComponent(name.trim())
                        }
                    }
                } else {
                    Log.d(TAG, "No controllers found or invalid format: $jsonString")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing controllers: ${e.message}", e)
            }
        }
    }

    /**
     * Register a component by name.
     */
    private fun registerComponent(name: String) {
        getOrCreateComponent(name)
        Log.d(TAG, "Registered component: $name")
    }

    /**
     * Get or create a component by name.
     */
    private fun getOrCreateComponent(name: String): NativeComponent {
        return components[name] ?: when (name) {
            "toast" -> ToastComponent(context)
            "dialog" -> DialogComponent(context).also { it.setBridgeManager(this) }
            "filePicker" -> FilePickerComponent(context).also { it.setBridgeManager(this) }
            "file-picker" -> FilePickerComponent(context).also { it.setBridgeManager(this) } // Support kebab-case for Stimulus
            else -> {
                Log.d(TAG, "Creating generic component for: $name")
                GenericComponent(name)
            }
        }.also {
            components[name] = it
        }
    }

    /**
     * Parse a message from JSON.
     */
    private fun parseMessage(messageJson: String): StradaMessage {
        val json = JSONObject(messageJson)
        val component = json.getString("component")
        val event = json.getString("event")
        val data = mutableMapOf<String, Any>()
        
        if (json.has("data") && !json.isNull("data")) {
            val dataJson = json.getJSONObject("data")
            val keys = dataJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = dataJson.get(key)
                data[key] = value
            }
        }
        
        return StradaMessage(component, event, data)
    }

    /**
     * This method is called from JavaScript to send a message to the native bridge.
     * This method is called from JavaScript using the JavascriptInterface.
     */
    @JavascriptInterface
    fun receiveMessage(messageJson: String) {
        Log.d(TAG, "Received message from JavaScript: $messageJson")
        
        try {
            val message = parseMessage(messageJson)
            val component = getOrCreateComponent(message.component)
            component.handleMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    /**
     * Send a message from native to web.
     */
    fun sendToWeb(component: String, event: String, data: Map<String, Any>) {
        val webView = this.webView ?: return
        
        val jsonData = JSONObject(data).toString()
        val js = """
            if (window.StradaReceiveMessage) {
                const message = {
                    component: '$component',
                    event: '$event',
                    data: $jsonData
                };
                window.StradaReceiveMessage(JSON.stringify(message));
            }
        """.trimIndent()
        
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }
}