use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Runtime, Window};

#[derive(Debug, Serialize, Deserialize)]
pub struct StradaMessage {
    pub component: String,
    pub event: String,
    #[serde(default)]
    pub data: serde_json::Value,
}

/// Response structure for Strada bridge messages
#[derive(Debug, Serialize, Deserialize)]
pub struct StradaResponse {
    #[serde(rename = "sendToWeb")]
    pub send_to_web: bool,
    pub data: Option<StradaMessage>,
}

/// Handles messages from the Strada bridge
#[tauri::command]
pub fn handle_strada_message<R: Runtime>(
    _app: AppHandle<R>,
    window: Window<R>,
    message: StradaMessage,
) -> Result<StradaResponse, String> {
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
            let response_message = StradaMessage {
                component: "filePicker".to_string(),
                event: "result".to_string(),
                data: serde_json::json!({
                    "fileName": "example.txt",
                    "fileSize": 1024,
                    "mimeType": "text/plain",
                }),
            };
            
            // Also emit an event for other parts of the app to listen to
            window
                .emit("strada-event", &response_message)
                .map_err(|e| e.to_string())?;
                
            // Return the response to be sent back to the WebView
            Ok(StradaResponse {
                send_to_web: true,
                data: Some(response_message),
            })
        }
        ("toast", "show") => {
            // Handle toast request
            // In a real implementation, you would show a toast
            // For now, just log the message
            println!("Toast message: {:?}", message.data);
            
            // No need to send anything back to the WebView
            Ok(StradaResponse {
                send_to_web: false,
                data: None,
            })
        }
        ("dialog", event) => {
            // Handle dialog request
            // In a real implementation, you would show a dialog
            // and return the result to the web content
            println!("Dialog event: {}, data: {:?}", event, message.data);
            
            // Create a response message
            let response_message = StradaMessage {
                component: "dialog".to_string(),
                event: "result".to_string(),
                data: serde_json::json!({
                    "confirmed": true,
                }),
            };
            
            // Also emit an event for other parts of the app to listen to
            window
                .emit("strada-event", &response_message)
                .map_err(|e| e.to_string())?;
                
            // Return the response to be sent back to the WebView
            Ok(StradaResponse {
                send_to_web: true,
                data: Some(response_message),
            })
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
            
            // Create a response message
            let response_message = StradaMessage {
                component: "custom".to_string(),
                event: "rust-result".to_string(),
                data: rust_data,
            };
            
            // Also emit an event for other parts of the app to listen to
            window
                .emit("strada-event", &response_message)
                .map_err(|e| e.to_string())?;
                
            // Return the response to be sent back to the WebView
            Ok(StradaResponse {
                send_to_web: true,
                data: Some(response_message),
            })
        }
        _ => {
            println!("Unknown Strada message: {:?}", message);
            
            // No need to send anything back to the WebView
            Ok(StradaResponse {
                send_to_web: false,
                data: None,
            })
        }
    }
}

/// Forwards a Strada message from Rust to the WebView
#[tauri::command]
pub fn send_strada_message<R: Runtime>(
    window: Window<R>,
    message: StradaMessage,
) -> Result<(), String> {
    // Emit an event to the frontend
    window
        .emit("strada-message", &message)
        .map_err(|e| e.to_string())?;
    
    // Also inject JavaScript to call the StradaNativeBridge.receiveFromRust method
    let message_json = serde_json::to_string(&message)
        .map_err(|e| e.to_string())?;
    
    // Escape single quotes in the JSON string
    let escaped_json = message_json.replace('\'', "\\'");
    
    // Create JavaScript to call the receiveFromRust method
    let js = format!(
        "if (window.StradaNativeBridge && window.StradaNativeBridge.receiveFromRust) {{ 
            window.StradaNativeBridge.receiveFromRust('{}'); 
        }} else {{ 
            console.error('StradaNativeBridge.receiveFromRust not available'); 
        }}",
        escaped_json
    );
    
    // Execute the JavaScript using eval
    let window_clone = window.clone();
    window
        .run_on_main_thread(move || {
            let _ = window_clone.webviews().first().map(|webview| {
                let _ = webview.eval(&js);
            });
        })
        .map_err(|e| e.to_string())?;

    Ok(())
}