# Tauri Android with Strada Bridge

This project demonstrates how to integrate a Hotwire Strada-like bridge in a Tauri Android application. The bridge enables communication between web content and native Android components.

## Features

- Native bridge for communication between web content and Android components
- Support for detecting `data-controller` attributes in the DOM
- JavaScript API for sending messages from web to native components
- Native components for toast, dialog, and file picker
- Full round-trip communication: Web ⟷ Kotlin ⟷ Rust

## Architecture

The bridge consists of the following components:

1. **JavaScript API**: Provides a `window.Strada` object for web content to send messages to native components.
2. **Kotlin Bridge**: Handles messages from JavaScript and routes them to the appropriate native components.
3. **Native Components**: Implement native functionality like toast, dialog, and file picker.
4. **Rust Backend**: Receives forwarded messages from Kotlin and can send messages back to the web content.

## Implementation Details

### JavaScript API

The JavaScript API is injected into the WebView and provides the following functionality:

```javascript
// Send a message to a native component
window.Strada.componentName.send('eventName', { /* data */ });

// Listen for messages from native components
document.addEventListener('strada:componentName:eventName', (event) => {
  const data = event.detail;
  // Handle the message
});
```

### Kotlin Bridge

The Kotlin bridge is implemented in `StradaBridgeManager.kt` and provides the following functionality:

- Detects `data-controller` attributes in the DOM
- Routes messages from JavaScript to the appropriate native components
- Sends messages from native components back to the web content

### Native Components

The following native components are implemented:

- **Toast**: Shows toast messages
- **Dialog**: Shows alert and confirm dialogs
- **FilePicker**: Opens the native file picker

### Rust Backend

The Rust backend is implemented in `strada_bridge.rs` and provides the following functionality:

- Receives messages from the Kotlin bridge
- Processes messages and sends responses back to the web content
- Provides a Tauri command API for handling Strada messages

## Usage

### Loading a Hotwire-compatible Web Page

To load a Hotwire-compatible web page, update the `MainActivity.kt` file:

```kotlin
// Load the Stimulus demo site
webView.loadUrl("https://stimulusjs.demo.tebe.ch/")
```

### Using the Bridge in Web Content

To use the bridge in web content, add `data-controller` attributes to your HTML elements:

```html
<div data-controller="toast">
  <button id="showToast">Show Toast</button>
</div>
```

Then, use the JavaScript API to send messages to native components:

```javascript
document.getElementById('showToast').addEventListener('click', () => {
  window.Strada.toast.send('show', { text: 'Hello from Strada!' });
});
```

### Adding New Native Components

To add a new native component, create a new class that extends `NativeComponent` in `StradaBridgeManager.kt`:

```kotlin
class MyComponent(private val context: Context) : NativeComponent("myComponent") {
    override fun handleMessage(message: Message) {
        when (message.event) {
            "myEvent" -> {
                // Handle the message
            }
            else -> {
                Log.w(TAG, "Unknown event: ${message.event}")
            }
        }
    }
}
```

Then, register the component in `StradaBridgeManager.kt`:

```kotlin
private fun getOrCreateComponent(name: String): NativeComponent {
    return components[name] ?: when (name) {
        "toast" -> ToastComponent(context)
        "dialog" -> DialogComponent(context, activity)
        "filePicker" -> FilePickerComponent(context, activity)
        "myComponent" -> MyComponent(context) // Add your component here
        else -> {
            Log.w(TAG, "Unknown component: $name, creating generic component")
            GenericComponent(name)
        }
    }.also {
        components[name] = it
    }
}
```

## Testing

The project includes a test HTML file (`strada-test.html`) that demonstrates the bridge functionality. To use it, update the `MainActivity.kt` file:

```kotlin
// Load the test HTML file
webView.loadUrl("file:///android_asset/strada-test.html")
```

## Development Setup

- [VS Code](https://code.visualstudio.com/) + [Tauri](https://marketplace.visualstudio.com/items?itemName=tauri-apps.tauri-vscode) + [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer)
