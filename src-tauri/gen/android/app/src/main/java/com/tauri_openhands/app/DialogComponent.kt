package com.tauri_openhands.app

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog

/**
 * Native component that handles dialog messages.
 */
class DialogComponent(private val context: Context) : NativeComponent {
    private val TAG = "DialogComponent"
    private var bridgeManager: StradaBridgeManager? = null

    fun setBridgeManager(bridgeManager: StradaBridgeManager) {
        this.bridgeManager = bridgeManager
    }

    override fun handleMessage(message: StradaMessage) {
        when (message.event) {
            "alert" -> {
                val title = message.data["title"] as? String ?: "Alert"
                val messageText = message.data["message"] as? String ?: ""
                val buttonText = message.data["buttonText"] as? String ?: "OK"
                
                try {
                    AlertDialog.Builder(context)
                        .setTitle(title)
                        .setMessage(messageText)
                        .setPositiveButton(buttonText) { _, _ ->
                            // Send result back to web
                            bridgeManager?.sendToWeb("dialog", "result", mapOf("confirmed" to true))
                        }
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing alert dialog", e)
                }
            }
            "confirm" -> {
                val title = message.data["title"] as? String ?: "Confirm"
                val messageText = message.data["message"] as? String ?: ""
                val confirmText = message.data["confirmText"] as? String ?: "OK"
                val cancelText = message.data["cancelText"] as? String ?: "Cancel"
                
                try {
                    AlertDialog.Builder(context)
                        .setTitle(title)
                        .setMessage(messageText)
                        .setPositiveButton(confirmText) { _, _ ->
                            // Send result back to web
                            bridgeManager?.sendToWeb("dialog", "result", mapOf("confirmed" to true))
                        }
                        .setNegativeButton(cancelText) { _, _ ->
                            // Send result back to web
                            bridgeManager?.sendToWeb("dialog", "result", mapOf("confirmed" to false))
                        }
                        .show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing confirm dialog", e)
                }
            }
        }
    }
}