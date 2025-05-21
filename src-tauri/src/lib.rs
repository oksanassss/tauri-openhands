// Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
mod strada_bridge;

#[tauri::command]
fn greet(name: &str) -> String {
    format!("Hello, {}! You've been greeted from Rust!", name)
}

// New function to sum two numbers
#[tauri::command]
fn sum_numbers(a: i32, b: i32) -> i32 {
    a + b
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            greet, 
            sum_numbers,
            strada_bridge::handle_strada_message,
            strada_bridge::send_strada_message
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
