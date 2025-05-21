# Tauri Android WebView Bridge Integration

This document describes the integration of the Strada-style bridge for Tauri Android applications, which enables communication between web content and native Android components.

## Overview

The bridge allows web applications to communicate with native Android components through a JavaScript API. This is particularly useful for Hotwire/Stimulus applications that need to access native functionality.

## Components

The bridge consists of the following components:

1. **StradaBridgeManager**: A Kotlin class that manages the bridge between web and native components.
2. **WebViewExtensions**: Extension functions for WebView to enable the bridge.
3. **Strada JavaScript API**: A JavaScript API that allows web applications to communicate with native components.
4. **Stimulus Adapter**: A JavaScript adapter that integrates the bridge with Stimulus controllers.

## Native Components

The following native components are available:

- **file-picker**: File selection using the native Android file picker
- **toast**: Display native Android toast messages
- **dialog**: Show native Android dialogs (alert and confirm)
- **share**: Share content using the Android share sheet
- **camera**: Access the device camera (placeholder implementation)
- **notification**: Show native Android notifications (placeholder implementation)
- **location**: Access device location (placeholder implementation)

## Usage in Web Applications

### Basic Usage

```javascript
// Check if the bridge is available
if (window.Strada && window.Strada.toast) {
    // Send a message to the native component
    window.Strada.toast.send("show", {
        message: "Hello from web!",
        duration: "short"
    });
}

// Listen for messages from native components
document.addEventListener("strada:toast:shown", function(event) {
    console.log("Toast was shown:", event.detail);
});
```

### With Stimulus

```html
<div data-controller="file-picker">
    <button data-action="click->file-picker#openFilePicker">Select File</button>
    <div data-file-picker-target="result"></div>
</div>

<script>
    application.register("file-picker", class extends Stimulus.Controller {
        static targets = ["result"];

        connect() {
            // Listen for native events
            document.addEventListener("strada:file-picker:result", this.handleFileResult.bind(this));
        }

        openFilePicker() {
            // Send message to native bridge
            if (window.Strada && window.Strada["file-picker"]) {
                window.Strada["file-picker"].send("open", {
                    mimeTypes: ["image/*", "application/pdf"],
                    multiple: false
                });
            }
        }

        handleFileResult(event) {
            const fileData = event.detail;
            if (fileData.files && fileData.files.length > 0) {
                const file = fileData.files[0];
                this.resultTarget.textContent = `Selected file: ${file.name}`;
            }
        }
    });
</script>
```

## Native Component API Reference

### File Picker

**Component name:** `file-picker` or `filePicker`

**Events:**

- `open`: Open the file picker
  - Parameters:
    - `mimeTypes`: Array of MIME types to filter (e.g., `["image/*", "application/pdf"]`)
    - `mimeType`: Single MIME type to filter (e.g., `"image/*"`)
    - `multiple`: Boolean indicating whether multiple files can be selected

**Responses:**

- `result`: Sent when files are selected
  - Data:
    - `files`: Array of file objects with properties:
      - `name`: File name
      - `size`: File size in bytes
      - `type`: MIME type
      - `uri`: File URI
- `cancelled`: Sent when the file picker is cancelled

### Toast

**Component name:** `toast`

**Events:**

- `show`: Show a toast message
  - Parameters:
    - `message`: The message to display
    - `duration`: Duration (`"short"` or `"long"`)

**Responses:**

- `shown`: Sent when the toast is shown
  - Data:
    - `success`: Boolean indicating success

### Dialog

**Component name:** `dialog`

**Events:**

- `alert`: Show an alert dialog
  - Parameters:
    - `title`: Dialog title
    - `message`: Dialog message
    - `buttonText`: Text for the button (default: "OK")
- `confirm`: Show a confirmation dialog
  - Parameters:
    - `title`: Dialog title
    - `message`: Dialog message
    - `positiveButton`: Text for the positive button (default: "OK")
    - `negativeButton`: Text for the negative button (default: "Cancel")

**Responses:**

- `result`: Sent when the dialog is dismissed
  - Data:
    - `type`: Dialog type (`"alert"` or `"confirm"`)
    - `confirmed`: Boolean indicating whether the positive button was clicked (for confirm dialogs)

### Share

**Component name:** `share`

**Events:**

- `share`: Share content
  - Parameters:
    - `text`: Text to share
    - `url`: URL to share
    - `title`: Share sheet title

**Responses:**

- `result`: Sent when sharing is complete
  - Data:
    - `success`: Boolean indicating success

### Location

**Component name:** `location`

**Events:**

- `get`: Get the current location
  - Parameters:
    - `accuracy`: Desired accuracy (`"high"` or `"low"`)

**Responses:**

- `result`: Sent when location is available
  - Data:
    - `latitude`: Latitude
    - `longitude`: Longitude
    - `accuracy`: Accuracy in meters
    - `error`: Error message (if an error occurred)

## Integration with Tauri

The bridge is integrated with Tauri by:

1. Adding the bridge manager to the MainActivity
2. Setting up the WebView with the bridge
3. Handling activity results for components like the file picker

## Extending the Bridge

To add a new native component:

1. Create a new inner class in `StradaBridgeManager.kt` that extends `NativeComponent`
2. Implement the `handleMessage` method to handle messages from web
3. Add the component to the `getOrCreateComponent` method
4. Create a corresponding Stimulus controller in your web application

## Security Considerations

- The bridge allows web content to access native functionality, so it should only be used with trusted web content.
- Consider adding validation and permission checks for sensitive operations.
- Limit the functionality exposed through the bridge to what is necessary for your application.