// strada-bridge.js
// This file provides integration between Tauri's JavaScript API and the Strada bridge

import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';

// Global olarak StradaBridge'i tanımla
window.StradaBridge = {
  initialized: false,
  messageQueue: [],
  
  init: function() {
    if (!window.StradaNativeBridge) {
      return false;
    }
    
    this.initialized = true;
    console.log('StradaBridge initialized successfully');
    this.processQueue();
    return true;
  },

  processQueue: function() {
    while (this.messageQueue.length > 0) {
      const message = this.messageQueue.shift();
      this.processMessage(message);
    }
  },

  processMessage: function(message) {
    document.dispatchEvent(new CustomEvent('strada-event', {
      detail: message
    }));
  },

  receiveFromRust: function(message) {
    if (!this.initialized) {
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
    return true;
  }
  
  if (window._stradaRetryCount === undefined) {
    window._stradaRetryCount = 0;
  }
  
  if (window._stradaRetryCount > 20) {
    console.error('StradaNativeBridge not initialized');
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