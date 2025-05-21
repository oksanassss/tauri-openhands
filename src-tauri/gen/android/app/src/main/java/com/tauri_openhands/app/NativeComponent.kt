package com.tauri_openhands.app

/**
 * Interface for native components that can handle messages from the Strada bridge.
 */
interface NativeComponent {
    /**
     * Handle a message from the Strada bridge.
     */
    fun handleMessage(message: StradaMessage)
}

/**
 * Data class representing a message from the Strada bridge.
 */
data class StradaMessage(
    val component: String,
    val event: String,
    val data: Map<String, Any>
)