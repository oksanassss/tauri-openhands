package com.tauri_openhands.app

import android.util.Log

/**
 * Generic component that handles messages for components without specific implementations.
 */
class GenericComponent(private val name: String) : NativeComponent {
    private val TAG = "GenericComponent"
    
    override fun handleMessage(message: StradaMessage) {
        Log.d(TAG, "Received message for $name: ${message.event} with data: ${message.data}")
    }
}