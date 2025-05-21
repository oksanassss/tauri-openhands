# Strada Bridge for Tauri WebView

This document explains how the Strada bridge is integrated with the WebView in a Tauri Android application.

## Overview

The Strada bridge enables communication between web content and native Android components. It's specifically designed to work with Hotwire/Stimulus pages, allowing web components to trigger native functionality.

## WebView Integration

The WebView integration consists of the following components:

1. **StradaBridgeManager**: Manages the communication between web content and native components
2. **WebView Extensions**: Kotlin extension functions to simplify WebView setup
3. **Stimulus Adapter**: JavaScript adapter for Stimulus controllers

## How It Works

1. The WebView is configured with JavaScript enabled and a JavaScript interface added
2. The Strada bridge JavaScript is injected into the WebView
3. The bridge scans for `data-controller` attributes in the DOM
4. When a controller is found, a corresponding Strada component is registered
5. Web content can send messages to native components using `Strada.componentName.send(event, data)`
6. Native components can send messages back to web content using `sendToWeb(component, event, data)`

## Usage in MainActivity

```kotlin
class MainActivity : TauriActivity() {
    private lateinit var bridgeManager: StradaBridgeManager
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridgeManager = StradaBridgeManager(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        
        // Get the WebView from Tauri
        webView = findViewById(getWebViewId())
        if (webView != null) {
            // Enable Strada bridge support
            bridgeManager = webView.enableStradaBridge(this)
            
            // Load a URL with Strada bridge support
            webView.loadUrlWithStrada("https://stimulusjs.demo.tebe.ch/", bridgeManager)
        }
    }
}
```

## WebView Extension Functions

The following extension functions are provided to simplify WebView setup:

### enableStradaBridge

```kotlin
fun WebView.enableStradaBridge(context: Context): StradaBridgeManager
```

Enables Strada bridge support for a WebView. This function configures the WebView with the necessary settings and injects the Strada bridge JavaScript.

### enableAutoStradaBridge

```kotlin
fun WebView.enableAutoStradaBridge(context: Context): StradaBridgeManager
```

Detects Hotwire/Stimulus pages and automatically enables Strada bridge support. This function sets a WebViewClient that checks for Stimulus-specific elements and injects the Strada bridge.

### loadUrlWithStrada

```kotlin
fun WebView.loadUrlWithStrada(url: String, bridgeManager: StradaBridgeManager)
```

Loads a URL and ensures Strada bridge is enabled. This function loads the specified URL and ensures the Strada bridge is initialized.

## Stimulus Integration

The Strada bridge includes a Stimulus adapter that:

1. Automatically registers Strada components for Stimulus controllers
2. Forwards events between Strada and Stimulus controllers
3. Adds a `strada` method to Stimulus controllers for sending messages to native components

### Usage in Stimulus Controllers

```javascript
// In a Stimulus controller
export default class extends Controller {
  connect() {
    // Send a message to the native component
    this.strada("connect", { controllerId: this.element.id });
  }
  
  showToast() {
    // Send a message to the toast component
    this.strada("show", { text: "Hello from Stimulus!" });
  }
  
  // Handle messages from native components
  handleNativeEvent(event) {
    const data = event.detail;
    console.log("Received native event:", data);
  }
}
```

## Testing with Stimulus Demo Site

The Strada bridge is configured to work with the Stimulus demo site at https://stimulusjs.demo.tebe.ch/. This site provides a good test environment for the bridge as it includes various Stimulus controllers.

To test the bridge:

1. Load the Stimulus demo site in the WebView
2. Open the browser console to see bridge initialization messages
3. Interact with the Stimulus controllers to trigger native functionality

## Customizing the Bridge

To customize the bridge for your application:

1. Add new native components to the `StradaBridgeManager` class
2. Update the `getOrCreateComponent` method to return your custom components
3. Implement the `handleMessage` method in your custom components
4. Use the `sendToWeb` method to send messages back to web content