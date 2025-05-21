/**
 * Strada Stimulus Adapter
 * 
 * This script provides integration between the Strada bridge and Stimulus controllers.
 * It automatically registers Strada components for Stimulus controllers and forwards
 * events between them.
 */

(function() {
  // Wait for both Strada and Stimulus to be available
  function waitForDependencies(callback) {
    if (window.Strada && window.Stimulus) {
      callback();
    } else {
      setTimeout(() => waitForDependencies(callback), 100);
    }
  }

  waitForDependencies(() => {
    console.log('Strada Stimulus Adapter: Initializing');
    
    // Store original Stimulus controller registration
    const originalRegister = window.Stimulus.register;
    
    // Override Stimulus.register to automatically register Strada components
    window.Stimulus.register = function(name, controller) {
      // Register with Stimulus
      const result = originalRegister.call(this, name, controller);
      
      // Register with Strada
      if (window.Strada && !window.Strada[name]) {
        window.Strada.registerComponent(name);
        console.log('Strada Stimulus Adapter: Registered component for controller', name);
        
        // Listen for Strada events and dispatch them to Stimulus controllers
        window.addEventListener("StradaReceiveMessage", function(event) {
          if (!event.detail) return;
          
          const { component, event: eventName, data } = event.detail;
          
          // Only process events for this component
          if (component !== name) return;
          
          console.log(`Strada event received for ${name}: ${eventName}`, data);
          
          // Find all instances of this controller
          const elements = document.querySelectorAll(`[data-controller~="${name}"]`);
          elements.forEach(element => {
            // Dispatch a custom event to the element
            const customEvent = new CustomEvent(`strada:${name}:${eventName}`, {
              detail: data,
              bubbles: true
            });
            element.dispatchEvent(customEvent);
            
            // Also dispatch a simpler event for compatibility
            const simpleEvent = new CustomEvent(`strada:${eventName}`, {
              detail: data,
              bubbles: true
            });
            element.dispatchEvent(simpleEvent);
          });
        });
      }
      
      return result;
    };
    
    // Patch existing controllers
    if (window.Stimulus.application) {
      const controllerNames = Object.keys(window.Stimulus.application.controllers);
      controllerNames.forEach(name => {
        if (!window.Strada[name]) {
          window.Strada.registerComponent(name);
          console.log('Strada Stimulus Adapter: Registered component for existing controller', name);
        }
      });
    }
    
    // Add helper to Stimulus controllers
    window.Stimulus.Controller.prototype.strada = function(event, data) {
      const controllerName = this.identifier;
      if (window.Strada && window.Strada[controllerName]) {
        window.Strada[controllerName].send(event, data);
        return true;
      }
      return false;
    };
    
    console.log('Strada Stimulus Adapter: Initialized');
  });
})();