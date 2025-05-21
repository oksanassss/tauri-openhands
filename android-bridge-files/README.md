# Strada Bridge for Tauri Android

This directory contains the implementation of a Strada-style bridge for Tauri Android applications, enabling communication between web content and native Android components.

## Overview

The Strada Bridge allows web content to communicate with native Android components using a simple messaging protocol. It's based on the [Hotwire Strada](https://github.com/hotwired/strada-android) pattern but adapted for use with Tauri's WebView.

## Implementation Details

### Native Components (Kotlin)

- `StradaBridgeManager.kt`: The main bridge manager that handles communication between web and native components.
- `MainActivity.kt`: Modified to initialize and use the bridge manager.

### JavaScript API

The bridge injects a JavaScript API into the WebView that provides the following functionality:

```javascript
// Register a component
Strada.registerComponent('componentName');

// Send a message to a native component
Strada.componentName.send('eventName', { key: 'value' });

// Listen for messages from native components
document.addEventListener('strada:componentName:eventName', (event) => {
  const data = event.detail;
  // Handle the message
});
```

### Available Components

The following native components are available:

1. **Toast**: Show native Android toast messages
   ```javascript
   Strada.toast.send('show', { text: 'Hello World', long: false });
   ```

2. **Dialog**: Show native Android dialogs
   ```javascript
   Strada.dialog.send('alert', { title: 'Alert', text: 'Message' });
   Strada.dialog.send('confirm', { title: 'Confirm', text: 'Are you sure?' });
   ```

3. **FilePicker**: Open the native Android file picker
   ```javascript
   Strada.filePicker.send('open', { mimeType: '*/*', multiple: false });
   ```

4. **Share**: Share content using the native Android share sheet
   ```javascript
   Strada.share.send('text', { text: 'Content to share', title: 'Share via' });
   ```

5. **Custom**: A custom component for demonstrating the bridge extensibility
   ```javascript
   Strada.custom.send('action', { timestamp: new Date().toISOString() });
   ```

Additional components with placeholder implementations:
- Camera
- Notification
- Location

## Usage in HTML

To use the Strada Bridge in your HTML, add the `data-controller` attribute to your elements:

```html
<div data-controller="toast">
  <button id="showToast">Show Toast</button>
</div>

<script>
  document.getElementById('showToast').addEventListener('click', () => {
    Strada.toast.send('show', { text: 'Hello from Strada!' });
  });
  
  document.addEventListener('strada:toast:shown', (event) => {
    console.log('Toast shown!');
  });
</script>
```

## Demo

A demo HTML file is included at `assets/strada-bridge-demo.html` that demonstrates all the available components.

## Integration with Tauri

To integrate the Strada Bridge with your Tauri Android application:

1. Copy the Kotlin files to your Android project's source directory
2. Initialize the `StradaBridgeManager` in your `MainActivity`
3. Set the WebView reference in the bridge manager
4. Add the demo HTML file to your assets directory for testing

## Extending the Bridge

To add a new component:

1. Create a new inner class in `StradaBridgeManager.kt` that extends `NativeComponent`
2. Add the component to the `getOrCreateComponent` method
3. Implement the `handleMessage` method to handle messages from web content
4. Use the `sendToWeb` method to send messages back to web content

Example:

```kotlin
inner class MyComponent(private val context: Context) : NativeComponent("myComponent") {
    override fun handleMessage(message: Message) {
        when (message.event) {
            "connect" -> {
                sendToWeb(name, "connected", mapOf("status" to "ready"))
            }
            "myAction" -> {
                // Handle the action
                sendToWeb(name, "result", mapOf("success" to true))
            }
            else -> {
                Log.w(TAG, "Unknown event: ${message.event}")
                sendToWeb(name, "error", mapOf("error" to "Unknown event: ${message.event}"))
            }
        }
    }
}
```

Then in your HTML:

```html
<div data-controller="myComponent">
  <button id="myButton">My Action</button>
</div>

<script>
  document.getElementById('myButton').addEventListener('click', () => {
    Strada.myComponent.send('myAction', { param: 'value' });
  });
  
  document.addEventListener('strada:myComponent:result', (event) => {
    console.log('Result:', event.detail);
  });
</script>
```