// strada-bridge.js
// This file provides integration between Tauri's JavaScript API and the Strada bridge

import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';

// Global olarak StradaBridge'i tanımla
window.StradaBridge = {
  initialized: false,
  messageQueue: [],
  
  init: function() {
    console.log('StradaBridge.init() called, StradaNativeBridge exists:', !!window.StradaNativeBridge);
    
    if (!window.StradaNativeBridge) {
      console.warn('StradaNativeBridge not available during init, will retry later');
      
      // Schedule a retry
      setTimeout(() => {
        console.log('Retrying StradaBridge initialization');
        this.init();
      }, 1000);
      
      return false;
    }
    
    this.initialized = true;
    console.log('StradaBridge initialized successfully');
    
    // Process any queued messages
    this.processQueue();
    
    // Dispatch an event to notify that the bridge is ready
    document.dispatchEvent(new CustomEvent('strada-bridge-ready'));
    
    return true;
  },

  processQueue: function() {
    console.log(`Processing message queue, ${this.messageQueue.length} messages pending`);
    while (this.messageQueue.length > 0) {
      const message = this.messageQueue.shift();
      this.processMessage(message);
    }
  },

  processMessage: function(message) {
    console.log('Processing message:', message);
    document.dispatchEvent(new CustomEvent('strada-event', {
      detail: message
    }));
  },

  receiveFromRust: function(message) {
    console.log('Received message from Rust:', message);
    
    if (!this.initialized) {
      console.log('Bridge not initialized, queueing message');
      this.messageQueue.push(message);
      return;
    }
    
    this.processMessage(message);
  }
};

// Native bridge hazır olduğunda başlat
document.addEventListener('strada-native-ready', () => {
  console.log('Native bridge ready event received');
  window.StradaBridge.init();
});

// Sayfa yüklendiğinde de kontrol et
document.addEventListener('DOMContentLoaded', () => {
  console.log('DOM loaded, checking bridge status');
  window.StradaBridge.init();
});

async function sendStradaMessage(component, event, data = {}) {
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

async function handleStradaMessage(component, event, data = {}) {
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

function registerStradaComponent(name) {
  if (!window.Strada) {
    console.error('Strada is not initialized');
    return null;
  }
  
  return window.Strada.registerComponent(name);
}

function checkStradaBridge() {
  if (window.StradaNativeBridge) {
    console.log('StradaNativeBridge is available!');
    
    // If bridge is found, initialize it
    if (window.StradaBridge && !window.StradaBridge.initialized) {
      console.log('Initializing StradaBridge from checkStradaBridge');
      window.StradaBridge.init();
    }
    
    return true;
  }
  
  if (window._stradaRetryCount === undefined) {
    window._stradaRetryCount = 0;
    console.log('Starting StradaNativeBridge check process');
  }
  
  // Increase retry count to 50 (25 seconds)
  if (window._stradaRetryCount > 50) {
    console.error('StradaNativeBridge not initialized after 50 attempts');
    
    // As a fallback, try to create a mock bridge for testing
    if (!window.StradaNativeBridge) {
      console.warn('Creating mock StradaNativeBridge for testing');
      window.StradaNativeBridge = {
        receiveMessage: function(message) {
          console.log('Mock bridge received message:', message);
          // Just log the message for now
        }
      };
      
      // Notify that the bridge is ready (mock version)
      document.dispatchEvent(new CustomEvent('strada-native-ready'));
      
      if (window.StradaBridge && !window.StradaBridge.initialized) {
        window.StradaBridge.init();
      }
    }
    
    return false;
  }
  
  window._stradaRetryCount++;
  console.warn('StradaNativeBridge not ready, retrying... Attempt: ' + window._stradaRetryCount);
  setTimeout(checkStradaBridge, 500);
  return false;
}

// DOMContentLoaded'da tekrar dene
document.addEventListener('DOMContentLoaded', checkStradaBridge);

// Export functions
export { 
  registerStradaComponent,
  sendStradaMessage,
  handleStradaMessage 
};