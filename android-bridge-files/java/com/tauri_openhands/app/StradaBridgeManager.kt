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
 */
class StradaBridgeManager(
    private val context: Context,
    private val activity: Activity,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "StradaBridgeManager"
        private const val BRIDGE_NAME = "StradaBridge"
    }

    private val components = ConcurrentHashMap<String, NativeComponent>()

    /**
     * Initialize the bridge by injecting JavaScript and setting up the JavascriptInterface.
     */
    fun initialize() {
        // Add JavaScript interface
        webView.addJavascriptInterface(StradaJavaScriptInterface(), BRIDGE_NAME)

        // Inject the Strada JavaScript bridge
        injectStradaJavaScript()

        // Scan for data-controller attributes when page is loaded
        webView.setOnPageFinishedListener { url ->
            scanForControllers()
        }
    }

    /**
     * Inject the Strada JavaScript bridge into the WebView.
     */
    private fun injectStradaJavaScript() {
        val js = """
            if (!window.Strada) {
                window.Strada = {};
                
                // Create a proxy to handle component access
                window.Strada = new Proxy({}, {
                    get: function(target, prop) {
                        if (prop === 'registerComponent') {
                            return function(name) {
                                if (!target[name]) {
                                    target[name] = {
                                        send: function(event, data) {
                                            const message = {
                                                component: name,
                                                event: event,
                                                data: data || {}
                                            };
                                            window.StradaBridge.sendMessage(JSON.stringify(message));
                                            return true;
                                        }
                                    };
                                }
                                return target[name];
                            };
                        }
                        
                        // Auto-register components when accessed
                        if (!target[prop] && typeof prop === 'string' && prop !== 'toJSON') {
                            target[prop] = {
                                send: function(event, data) {
                                    const message = {
                                        component: prop,
                                        event: event,
                                        data: data || {}
                                    };
                                    window.StradaBridge.sendMessage(JSON.stringify(message));
                                    return true;
                                }
                            };
                        }
                        
                        return target[prop];
                    }
                });
                
                // Function to dispatch events from native to web
                window.Strada.dispatchEvent = function(component, event, data) {
                    const customEvent = new CustomEvent('strada:' + component + ':' + event, {
                        detail: data,
                        bubbles: true
                    });
                    document.dispatchEvent(customEvent);
                    return true;
                };
                
                console.log('Strada bridge initialized');
            }
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }

    /**
     * Scan the DOM for data-controller attributes and register the corresponding components.
     */
    private fun scanForControllers() {
        val js = """
            (function() {
                const controllers = document.querySelectorAll('[data-controller]');
                const result = [];
                
                controllers.forEach(element => {
                    const controllerNames = element.getAttribute('data-controller').split(' ');
                    controllerNames.forEach(name => {
                        if (name === 'native' || name.startsWith('native--')) {
                            const componentName = name === 'native' ? 'generic' : name.substring(8);
                            result.push(componentName);
                        }
                    });
                });
                
                return JSON.stringify(result);
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(js) { result ->
            try {
                // Remove quotes from the result string
                val jsonArray = result.trim('"').replace("\\\"", "\"")
                Log.d(TAG, "Found controllers: $jsonArray")
                
                // Register components
                val components = jsonArray.split(",")
                components.forEach { componentName ->
                    if (componentName.isNotEmpty()) {
                        registerComponent(componentName.trim())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing controllers", e)
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
            "dialog" -> DialogComponent(context, activity)
            "filePicker" -> FilePickerComponent(context, activity)
            else -> {
                Log.w(TAG, "Unknown component: $name, creating generic component")
                GenericComponent(name)
            }
        }.also {
            components[name] = it
        }
    }

    /**
     * Send a message to a component.
     */
    fun sendMessage(messageJson: String) {
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
        val jsonData = JSONObject(data).toString()
        val js = """
            window.Strada.dispatchEvent('$component', '$event', $jsonData);
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
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
     * JavaScript interface for the Strada bridge.
     */
    inner class StradaJavaScriptInterface {
        @JavascriptInterface
        fun sendMessage(messageJson: String) {
            Log.d(TAG, "Received message: $messageJson")
            this@StradaBridgeManager.sendMessage(messageJson)
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
            // Forward to Rust backend
            val rustCommand = "strada_bridge_handle_message"
            val args = mapOf(
                "component" to message.component,
                "event" to message.event,
                "data" to message.data.toString()
            )
            
            // This would call into the Rust backend
            // For now, just log the message
            Log.d(TAG, "Would call Rust command: $rustCommand with args: $args")
        }
    }

    /**
     * Toast component for showing toast messages.
     */
    inner class ToastComponent(private val context: Context) : NativeComponent("toast") {
        override fun handleMessage(message: Message) {
            when (message.event) {
                "show" -> {
                    val text = message.data.optString("text", "")
                    val duration = if (message.data.optBoolean("long", false)) {
                        Toast.LENGTH_LONG
                    } else {
                        Toast.LENGTH_SHORT
                    }
                    
                    activity.runOnUiThread {
                        Toast.makeText(context, text, duration).show()
                    }
                    
                    // Send response back to web
                    sendToWeb("toast", "shown", mapOf("success" to true))
                }
                else -> {
                    Log.w(TAG, "Unknown event: ${message.event}")
                }
            }
        }
    }

    /**
     * Dialog component for showing alert and confirm dialogs.
     */
    inner class DialogComponent(
        private val context: Context,
        private val activity: Activity
    ) : NativeComponent("dialog") {
        override fun handleMessage(message: Message) {
            when (message.event) {
                "alert" -> {
                    val title = message.data.optString("title", "Alert")
                    val text = message.data.optString("text", "")
                    
                    activity.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle(title)
                            .setMessage(text)
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                                sendToWeb("dialog", "closed", mapOf("result" to "ok"))
                            }
                            .show()
                    }
                }
                "confirm" -> {
                    val title = message.data.optString("title", "Confirm")
                    val text = message.data.optString("text", "")
                    
                    activity.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle(title)
                            .setMessage(text)
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                                sendToWeb("dialog", "closed", mapOf("result" to true))
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                                sendToWeb("dialog", "closed", mapOf("result" to false))
                            }
                            .show()
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown event: ${message.event}")
                }
            }
        }
    }

    /**
     * FilePicker component for opening the native file picker.
     */
    inner class FilePickerComponent(
        private val context: Context,
        private val activity: Activity
    ) : NativeComponent("filePicker") {
        private val FILE_PICKER_REQUEST_CODE = 1001
        
        override fun handleMessage(message: Message) {
            when (message.event) {
                "open" -> {
                    val mimeType = message.data.optString("mimeType", "*/*")
                    val multiple = message.data.optBoolean("multiple", false)
                    
                    activity.runOnUiThread {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.type = mimeType
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
                        activity.startActivityForResult(
                            Intent.createChooser(intent, "Select File"),
                            FILE_PICKER_REQUEST_CODE
                        )
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown event: ${message.event}")
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
                
                sendToWeb("filePicker", "selected", mapOf("files" to result))
            } else if (requestCode == FILE_PICKER_REQUEST_CODE) {
                // User cancelled the picker
                sendToWeb("filePicker", "cancelled", mapOf<String, Any>())
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