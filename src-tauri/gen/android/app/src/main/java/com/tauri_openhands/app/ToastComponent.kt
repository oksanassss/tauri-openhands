package com.tauri_openhands.app

import android.content.Context
import android.widget.Toast

/**
 * Native component that handles toast messages.
 */
class ToastComponent(private val context: Context) : NativeComponent {
    override fun handleMessage(message: StradaMessage) {
        when (message.event) {
            "show" -> {
                val text = message.data["text"] as? String ?: "Toast message"
                val duration = if (message.data["long"] == true) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(context, text, duration).show()
            }
        }
    }
}