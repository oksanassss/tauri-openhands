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
    
    // As a fallback, create a functional mock bridge
    if (!window.StradaNativeBridge) {
      console.warn('Creating functional mock StradaNativeBridge');
      
      // Create a mock UI for alerts and dialogs
      const createMockDialog = (options) => {
        console.log('Creating mock dialog with options:', options);
        
        // Create dialog container
        const dialogContainer = document.createElement('div');
        dialogContainer.style.position = 'fixed';
        dialogContainer.style.top = '0';
        dialogContainer.style.left = '0';
        dialogContainer.style.width = '100%';
        dialogContainer.style.height = '100%';
        dialogContainer.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
        dialogContainer.style.display = 'flex';
        dialogContainer.style.justifyContent = 'center';
        dialogContainer.style.alignItems = 'center';
        dialogContainer.style.zIndex = '9999';
        
        // Create dialog box
        const dialogBox = document.createElement('div');
        dialogBox.style.backgroundColor = 'white';
        dialogBox.style.borderRadius = '8px';
        dialogBox.style.padding = '20px';
        dialogBox.style.maxWidth = '80%';
        dialogBox.style.boxShadow = '0 4px 8px rgba(0, 0, 0, 0.2)';
        
        // Create title
        if (options.title) {
          const title = document.createElement('h3');
          title.textContent = options.title;
          title.style.margin = '0 0 10px 0';
          dialogBox.appendChild(title);
        }
        
        // Create message
        if (options.message) {
          const message = document.createElement('p');
          message.textContent = options.message;
          message.style.margin = '0 0 20px 0';
          dialogBox.appendChild(message);
        }
        
        // Create buttons container
        const buttonsContainer = document.createElement('div');
        buttonsContainer.style.display = 'flex';
        buttonsContainer.style.justifyContent = 'flex-end';
        
        // For confirm dialogs
        if (options.confirmText && options.cancelText) {
          // Cancel button
          const cancelButton = document.createElement('button');
          cancelButton.textContent = options.cancelText;
          cancelButton.style.padding = '8px 16px';
          cancelButton.style.marginRight = '10px';
          cancelButton.style.border = '1px solid #ccc';
          cancelButton.style.borderRadius = '4px';
          cancelButton.style.backgroundColor = '#f5f5f5';
          cancelButton.onclick = () => {
            document.body.removeChild(dialogContainer);
            if (options.onCancel) options.onCancel();
          };
          buttonsContainer.appendChild(cancelButton);
          
          // Confirm button
          const confirmButton = document.createElement('button');
          confirmButton.textContent = options.confirmText;
          confirmButton.style.padding = '8px 16px';
          confirmButton.style.border = 'none';
          confirmButton.style.borderRadius = '4px';
          confirmButton.style.backgroundColor = '#4CAF50';
          confirmButton.style.color = 'white';
          confirmButton.onclick = () => {
            document.body.removeChild(dialogContainer);
            if (options.onConfirm) options.onConfirm();
          };
          buttonsContainer.appendChild(confirmButton);
        } 
        // For alert dialogs
        else if (options.buttonText) {
          const button = document.createElement('button');
          button.textContent = options.buttonText;
          button.style.padding = '8px 16px';
          button.style.border = 'none';
          button.style.borderRadius = '4px';
          button.style.backgroundColor = '#4CAF50';
          button.style.color = 'white';
          button.onclick = () => {
            document.body.removeChild(dialogContainer);
            if (options.onDismiss) options.onDismiss();
          };
          buttonsContainer.appendChild(button);
        }
        
        dialogBox.appendChild(buttonsContainer);
        dialogContainer.appendChild(dialogBox);
        document.body.appendChild(dialogContainer);
      };
      
      // Create a mock toast function
      const createMockToast = (message) => {
        console.log('Creating mock toast with message:', message);
        
        const toast = document.createElement('div');
        toast.textContent = message;
        toast.style.position = 'fixed';
        toast.style.bottom = '20px';
        toast.style.left = '50%';
        toast.style.transform = 'translateX(-50%)';
        toast.style.backgroundColor = 'rgba(0, 0, 0, 0.8)';
        toast.style.color = 'white';
        toast.style.padding = '10px 20px';
        toast.style.borderRadius = '4px';
        toast.style.zIndex = '9999';
        
        document.body.appendChild(toast);
        
        // Auto-remove after 3 seconds
        setTimeout(() => {
          if (document.body.contains(toast)) {
            document.body.removeChild(toast);
          }
        }, 3000);
      };
      
      // Create the mock bridge with UI functionality
      window.StradaNativeBridge = {
        receiveMessage: function(message) {
          console.log('Mock bridge received message:', message);
          
          try {
            // Handle different component types
            if (message.component === 'dialog') {
              if (message.event === 'alert') {
                createMockDialog({
                  title: message.data.title,
                  message: message.data.message,
                  buttonText: message.data.buttonText || 'OK'
                });
              } else if (message.event === 'confirm') {
                createMockDialog({
                  title: message.data.title,
                  message: message.data.message,
                  confirmText: message.data.confirmText || 'Yes',
                  cancelText: message.data.cancelText || 'No',
                  onConfirm: () => {
                    console.log('Confirm dialog: User clicked Yes');
                  },
                  onCancel: () => {
                    console.log('Confirm dialog: User clicked No');
                  }
                });
              }
            } else if (message.component === 'toast') {
              // Handle both message formats (direct message or text property)
              const toastMessage = message.data.message || message.data.text || 'Toast notification';
              createMockToast(toastMessage);
            } else if (message.component === 'filePicker') {
              console.log('Mock file picker opened with type:', message.data.type);
              // We can't fully mock file picker, but we can show a dialog
              createMockDialog({
                title: 'File Picker',
                message: 'This is a mock file picker. In a real app, you would see the native file picker here.',
                buttonText: 'Close'
              });
            } else {
              console.log('Unhandled component type in mock bridge:', message.component);
            }
          } catch (error) {
            console.error('Error in mock bridge:', error);
          }
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