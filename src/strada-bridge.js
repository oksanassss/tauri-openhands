// strada-bridge.js
// This file provides integration between Tauri's JavaScript API and the Strada bridge

import { invoke, event } from '@tauri-apps/api';

// Initialize the Strada bridge
export function initStradaBridge() {
  console.log('Initializing Strada bridge');
  
  // Listen for strada-event events from Rust
  event.listen('strada-event', (event) => {
    console.log('Received strada-event from Rust:', event);
    
    // Forward the event to the Strada bridge
    if (window.StradaReceiveMessage) {
      const message = {
        component: event.payload.component,
        event: event.payload.event,
        data: event.payload.data
      };
      
      window.StradaReceiveMessage(JSON.stringify(message));
    }
  });
}

// Send a message to the Strada bridge from Rust
export async function sendStradaMessage(component, event, data = {}) {
  console.log(`Sending Strada message: ${component}.${event}`, data);
  
  try {
    await invoke('send_strada_message', {
      message: {
        component,
        event,
        data
      }
    });
    return true;
  } catch (error) {
    console.error('Error sending Strada message:', error);
    return false;
  }
}

// Handle a Strada message in Rust
export async function handleStradaMessage(component, event, data = {}) {
  console.log(`Handling Strada message in Rust: ${component}.${event}`, data);
  
  try {
    await invoke('handle_strada_message', {
      message: {
        component,
        event,
        data
      }
    });
    return true;
  } catch (error) {
    console.error('Error handling Strada message:', error);
    return false;
  }
}

// Export a function to register Strada components
export function registerStradaComponent(name) {
  if (!window.Strada) {
    console.error('Strada is not initialized');
    return null;
  }
  
  return window.Strada.registerComponent(name);
}

// Initialize the bridge when the module is imported
initStradaBridge();