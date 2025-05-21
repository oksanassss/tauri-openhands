package com.tauri_openhands.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
            "dialog" -> DialogComponent(context)
            "filePicker" -> FilePickerComponent(context)
            "file-picker" -> FilePickerComponent(context) // Support kebab-case for Stimulus
            "camera" -> CameraComponent(context)
            "share" -> ShareComponent(context)
            "notification" -> NotificationComponent(context)
            "location" -> LocationComponent(context)
            "custom" -> CustomComponent(name)
            else -> {
                Log.d(TAG, "Creating generic component for: $name")
                GenericComponent(name)
            }
        }.also {
            components[name] = it
        }
    }

    /**
     * Send a message to a component.
     */
    private fun sendMessage(messageJson: String) {
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
            } else {
                console.error('Strada: StradaReceiveMessage not available');
            }
        """.trimIndent()
        
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Parse a message from JSON.
     */
    private fun parseMessage(messageJson: String): Message {
        val json = JSONObject(messageJson)
        val component = json.getString("component")
        val event = json.getString("event")
        val data = json.optJSONObject("data") ?: JSONObject()
        
        return Message(component, event, data)
    }

    /**
     * JavaScript interface method to receive messages from web content.
     */
    @JavascriptInterface
    fun receiveMessage(messageJson: String) {
        Log.d(TAG, "Received message from web: $messageJson")
        
        // Process the message on the main thread
        val activity = this.activity
        if (activity != null) {
            activity.runOnUiThread {
                sendMessage(messageJson)
            }
        } else {
            // Fallback to direct processing if activity is not available
            sendMessage(messageJson)
        }
    }

    /**
     * Message class for Strada bridge.
     */
    data class Message(
        val component: String,
        val event: String,
        val data: JSONObject
    )

    /**
     * Base class for native components.
     */
    abstract class NativeComponent(val name: String) {
        abstract fun handleMessage(message: Message)
    }

    /**
     * Generic component for unknown component types.
     */
    inner class GenericComponent(name: String) : NativeComponent(name) {
        override fun handleMessage(message: Message) {
            Log.d(TAG, "Generic component received message: $message")
            
            // Forward to Rust backend using Tauri's invoke mechanism
            val rustCommand = "handle_strada_message"
            val jsonData = JSONObject().apply {
                put("component", message.component)
                put("event", message.event)
                put("data", JSONObject(message.data))
            }
            
            // Create a JSON string for the Rust command
            val jsonMessage = jsonData.toString()
            Log.d(TAG, "Invoking Rust command: $rustCommand with args: $jsonMessage")
            
            // Use the WebView to execute JavaScript that calls the Tauri API
            webView?.post {
                val jsCode = """
                    window.__TAURI__.invoke('$rustCommand', ${jsonMessage})
                        .then(function(response) {
                            console.log('Rust command response:', response);
                            // If the response contains data to send back to the web
                            if (response && response.sendToWeb) {
                                window.StradaReceiveMessage(JSON.stringify(response.data));
                            }
                        })
                        .catch(function(error) {
                            console.error('Error invoking Rust command:', error);
                        });
                """
                webView?.evaluateJavascript(jsCode, null)
            }
            
            // Also send a generic response back to web immediately
            sendToWeb(name, "received", mapOf(
                "status" to "ok",
                "message" to "Message received by native component",
                "timestamp" to System.currentTimeMillis()
            ))
        }
    }
    
    /**
     * Custom component for demonstrating custom functionality
     */
    inner class CustomComponent(name: String) : NativeComponent(name) {
        override fun handleMessage(message: Message) {
            Log.d(TAG, "Custom component received message: $message")
            
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                "action" -> {
                    // Get device info and send back to web
                    val manufacturer = android.os.Build.MANUFACTURER
                    val model = android.os.Build.MODEL
                    val osVersion = android.os.Build.VERSION.RELEASE
                    
                    // Get timestamp from message if available
                    val timestamp = message.data.optString("timestamp", "")
                    
                    sendToWeb(name, "result", mapOf(
                        "manufacturer" to manufacturer,
                        "model" to model,
                        "osVersion" to osVersion,
                        "timestamp" to timestamp,
                        "receivedTimestamp" to System.currentTimeMillis()
                    ))
                    
                    // Simulate a delayed response from Rust backend
                    Thread {
                        Thread.sleep(1000)
                        sendToWeb(name, "rust-result", mapOf(
                            "processedBy" to "Rust Backend",
                            "rustTimestamp" to System.currentTimeMillis(),
                            "originalTimestamp" to timestamp,
                            "message" to "Hello from Rust backend!",
                            "randomValue" to (Math.random() * 100).toInt()
                        ))
                    }.start()
                }
                else -> {
                    Log.w(TAG, "Unknown event for custom component: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Unknown event: ${message.event}"))
                }
            }
        }
    }

    /**
     * Toast component for showing toast messages.
     */
    inner class ToastComponent(private val context: Context) : NativeComponent("toast") {
        override fun handleMessage(message: Message) {
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                "show" -> {
                    val text = message.data.optString("text", "")
                    val duration = if (message.data.optBoolean("long", false)) {
                        Toast.LENGTH_LONG
                    } else {
                        Toast.LENGTH_SHORT
                    }
                    
                    val activity = this@StradaBridgeManager.activity
                    if (activity != null) {
                        activity.runOnUiThread {
                            Toast.makeText(context, text, duration).show()
                        }
                    } else {
                        // Fallback to context if activity is not available
                        Toast.makeText(context, text, duration).show()
                    }
                    
                    // Send response back to web
                    sendToWeb(name, "shown", mapOf("success" to true))
                }
                else -> {
                    Log.w(TAG, "Unknown event for toast: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Unknown event: ${message.event}"))
                }
            }
        }
    }

    /**
     * Dialog component for showing alert and confirm dialogs.
     */
    inner class DialogComponent(private val context: Context) : NativeComponent("dialog") {
        override fun handleMessage(message: Message) {
            val activity = this@StradaBridgeManager.activity ?: run {
                Log.e(TAG, "Cannot show dialog: activity is null")
                sendToWeb(name, "error", mapOf("error" to "Cannot show dialog: activity is null"))
                return
            }
            
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                "alert" -> {
                    val title = message.data.optString("title", "Alert")
                    val text = message.data.optString("text", "")
                    val buttonText = message.data.optString("buttonText", "OK")
                    
                    activity.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle(title)
                            .setMessage(text)
                            .setPositiveButton(buttonText) { dialog, _ ->
                                dialog.dismiss()
                                sendToWeb(name, "clicked", mapOf("button" to "ok"))
                            }
                            .show()
                    }
                }
                "confirm" -> {
                    val title = message.data.optString("title", "Confirm")
                    val text = message.data.optString("text", "")
                    val okText = message.data.optString("okText", "OK")
                    val cancelText = message.data.optString("cancelText", "Cancel")
                    
                    activity.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle(title)
                            .setMessage(text)
                            .setPositiveButton(okText) { dialog, _ ->
                                dialog.dismiss()
                                sendToWeb(name, "clicked", mapOf("button" to "ok"))
                            }
                            .setNegativeButton(cancelText) { dialog, _ ->
                                dialog.dismiss()
                                sendToWeb(name, "clicked", mapOf("button" to "cancel"))
                            }
                            .show()
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown event for dialog: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Unknown event: ${message.event}"))
                }
            }
        }
    }

    /**
     * FilePicker component for opening the native file picker.
     */
    inner class FilePickerComponent(private val context: Context) : NativeComponent("filePicker") {
        private val FILE_PICKER_REQUEST_CODE = 1001
        
        override fun handleMessage(message: Message) {
            val activity = this@StradaBridgeManager.activity ?: run {
                Log.e(TAG, "Cannot open file picker: activity is null")
                sendToWeb(name, "error", mapOf("error" to "Cannot open file picker: activity is null"))
                return
            }
            
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                "open" -> {
                    // Get mime types from the message
                    val mimeTypes = if (message.data.has("mimeTypes")) {
                        val mimeTypesArray = message.data.optJSONArray("mimeTypes")
                        if (mimeTypesArray != null) {
                            val types = mutableListOf<String>()
                            for (i in 0 until mimeTypesArray.length()) {
                                types.add(mimeTypesArray.optString(i))
                            }
                            types.toTypedArray()
                        } else {
                            arrayOf(message.data.optString("mimeType", "*/*"))
                        }
                    } else {
                        arrayOf(message.data.optString("mimeType", "*/*"))
                    }
                    
                    val multiple = message.data.optBoolean("multiple", false)
                    
                    activity.runOnUiThread {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        
                        // If multiple mime types are specified, use the first one as the primary type
                        // and add the rest as EXTRA_MIME_TYPES
                        if (mimeTypes.isNotEmpty()) {
                            intent.type = mimeTypes[0]
                            if (mimeTypes.size > 1) {
                                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                            }
                        } else {
                            intent.type = "*/*"
                        }
                        
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                        activity.startActivityForResult(
                            Intent.createChooser(intent, "Select File"),
                            FILE_PICKER_REQUEST_CODE
                        )
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown event for filePicker: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Unknown event: ${message.event}"))
                }
            }
        }
        
        fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
                val result = mutableListOf<Map<String, Any>>()
                
                if (data?.clipData != null) {
                    // Multiple files selected
                    val clipData = data.clipData!!
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        val fileInfo = getFileInfo(uri)
                        if (fileInfo != null) {
                            result.add(fileInfo)
                        }
                    }
                } else if (data?.data != null) {
                    // Single file selected
                    val uri = data.data!!
                    val fileInfo = getFileInfo(uri)
                    if (fileInfo != null) {
                        result.add(fileInfo)
                    }
                }
                
                sendToWeb(name, "result", mapOf("files" to result))
            } else if (requestCode == FILE_PICKER_REQUEST_CODE) {
                // User cancelled the picker
                sendToWeb(name, "cancelled", mapOf<String, Any>())
            }
        }
        
        private fun getFileInfo(uri: Uri): Map<String, Any>? {
            try {
                val contentResolver = context.contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex("_display_name")
                        val sizeIndex = it.getColumnIndex("_size")
                        
                        val displayName = if (displayNameIndex != -1) {
                            it.getString(displayNameIndex)
                        } else {
                            uri.lastPathSegment ?: "unknown"
                        }
                        
                        val size = if (sizeIndex != -1) {
                            it.getLong(sizeIndex)
                        } else {
                            -1
                        }
                        
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                        
                        return mapOf(
                            "name" to displayName,
                            "size" to size,
                            "type" to mimeType,
                            "uri" to uri.toString()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file info", e)
            }
            
            return null
        }
    }
    
    /**
     * Camera component for accessing the device camera
     */
    inner class CameraComponent(private val context: Context) : NativeComponent("camera") {
        override fun handleMessage(message: Message) {
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                else -> {
                    Log.w(TAG, "Camera functionality not yet implemented for event: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Camera functionality not yet implemented"))
                }
            }
        }
    }
    
    /**
     * Share component for sharing content
     */
    inner class ShareComponent(private val context: Context) : NativeComponent("share") {
        override fun handleMessage(message: Message) {
            val activity = this@StradaBridgeManager.activity ?: run {
                Log.e(TAG, "Cannot share: activity is null")
                sendToWeb(name, "error", mapOf("error" to "Cannot share: activity is null"))
                return
            }
            
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                "text" -> {
                    val text = message.data.optString("text", "")
                    val title = message.data.optString("title", "Share")
                    
                    activity.runOnUiThread {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.type = "text/plain"
                        intent.putExtra(Intent.EXTRA_TEXT, text)
                        activity.startActivity(Intent.createChooser(intent, title))
                        
                        // Send response back to web
                        sendToWeb(name, "shared", mapOf("success" to true))
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown event for share: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Unknown event: ${message.event}"))
                }
            }
        }
    }
    
    /**
     * Notification component for showing notifications
     */
    inner class NotificationComponent(private val context: Context) : NativeComponent("notification") {
        override fun handleMessage(message: Message) {
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                else -> {
                    Log.w(TAG, "Notification functionality not yet implemented for event: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Notification functionality not yet implemented"))
                }
            }
        }
    }
    
    /**
     * Location component for accessing device location
     */
    inner class LocationComponent(private val context: Context) : NativeComponent("location") {
        override fun handleMessage(message: Message) {
            when (message.event) {
                "connect" -> {
                    // Send connected event
                    sendToWeb(name, "connected", mapOf("status" to "ready"))
                }
                else -> {
                    Log.w(TAG, "Location functionality not yet implemented for event: ${message.event}")
                    sendToWeb(name, "error", mapOf("error" to "Location functionality not yet implemented"))
                }
            }
        }
    }
    
    /**
     * Receive a message from the Rust backend and forward it to the WebView.
     * This method is called from JavaScript using the JavascriptInterface.
     */
    @JavascriptInterface
    fun receiveFromRust(messageJson: String) {
        try {
            Log.d(TAG, "Received message from Rust: $messageJson")
            
            // Parse the message
            val json = JSONObject(messageJson)
            val component = json.getString("component")
            val event = json.getString("event")
            val data = json.getJSONObject("data")
            
            // Convert JSONObject to Map
            val dataMap = mutableMapOf<String, Any>()
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                dataMap[key] = data.get(key)
            }
            
            // Forward to WebView
            sendToWeb(component, event, dataMap)
            
            // If this is a component-specific message, also notify the component
            val componentObj = components[component]
            if (componentObj != null) {
                Log.d(TAG, "Notifying component $component of event $event")
                // You could add component-specific handling here if needed
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message from Rust", e)
        }
    }
}

/**
 * Extension function to set an OnPageFinishedListener on a WebView.
 */
fun WebView.setOnPageFinishedListener(listener: (String) -> Unit) {
    webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            listener(url)
        }
    }
}