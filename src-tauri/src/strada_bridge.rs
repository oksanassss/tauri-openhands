use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, Runtime, Window};

#[derive(Debug, Serialize, Deserialize)]
pub struct StradaMessage {
    pub component: String,
    pub event: String,
    #[serde(default)]
    pub data: serde_json::Value,
}

/// Handles messages from the Strada bridge
#[tauri::command]
pub fn handle_strada_message<R: Runtime>(
    app: AppHandle<R>,
    window: Window<R>,
    message: StradaMessage,
) -> Result<(), String> {
    println!(
        "Received Strada message: component={}, event={}",
        message.component, message.event
    );

    // Process the message based on component and event
    match (message.component.as_str(), message.event.as_str()) {
        ("filePicker", "open") => {
            // Handle file picker request
            // In a real implementation, you would open a file dialog
            // and return the result to the web content
            
            // For now, just emit an event back to the frontend
            window
                .emit(
                    "strada-event",
                    StradaMessage {
                        component: "filePicker".to_string(),
                        event: "selected".to_string(),
                        data: serde_json::json!({
                            "fileName": "example.txt",
                            "fileSize": 1024,
                            "mimeType": "text/plain",
                        }),
                    },
                )
                .map_err(|e| e.to_string())?;
        }
        ("toast", "show") => {
            // Handle toast request
            // In a real implementation, you would show a toast
            // For now, just log the message
            println!("Toast message: {:?}", message.data);
        }
        ("dialog", event) => {
            // Handle dialog request
            // In a real implementation, you would show a dialog
            // and return the result to the web content
            println!("Dialog event: {}, data: {:?}", event, message.data);
            
            // For now, just emit an event back to the frontend
            window
                .emit(
                    "strada-event",
                    StradaMessage {
                        component: "dialog".to_string(),
                        event: "result".to_string(),
                        data: serde_json::json!({
                            "confirmed": true,
                        }),
                    },
                )
                .map_err(|e| e.to_string())?;
        }
        ("custom", "action") => {
            // Handle custom action
            println!("Custom action received: {:?}", message.data);
            
            // Get the timestamp from the message data
            let timestamp = message.data.get("timestamp")
                .and_then(|v| v.as_str())
                .unwrap_or("unknown");
            
            // Process in Rust backend
            let rust_data = serde_json::json!({
                "processedBy": "Rust Backend",
                "rustTimestamp": chrono::Utc::now().to_rfc3339(),
                "originalTimestamp": timestamp,
                "message": "This message was processed by the Tauri Rust backend!",
                "randomValue": rand::random::<u32>()
            });
            
            // Send a reply back
            window
                .emit(
                    "strada-event",
                    StradaMessage {
                        component: "custom".to_string(),
                        event: "rust-result".to_string(),
                        data: rust_data,
                    },
                )
                .map_err(|e| e.to_string())?;
        }
        _ => {
            println!("Unknown Strada message: {:?}", message);
        }
    }

    Ok(())
}

/// Forwards a Strada message from Rust to the WebView
#[tauri::command]
pub fn send_strada_message<R: Runtime>(
    window: Window<R>,
    message: StradaMessage,
) -> Result<(), String> {
    // Emit an event to the frontend
    window
        .emit("strada-message", message)
        .map_err(|e| e.to_string())?;

    Ok(())
}