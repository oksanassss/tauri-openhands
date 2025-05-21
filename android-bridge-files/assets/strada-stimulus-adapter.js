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
        document.addEventListener(`strada:${name}:*`, function(event) {
          const eventName = event.type.split(':')[2];
          const detail = event.detail;
          
          // Find all instances of this controller
          const elements = document.querySelectorAll(`[data-controller~="${name}"]`);
          elements.forEach(element => {
            // Dispatch a custom event to the element
            const customEvent = new CustomEvent(`strada:${eventName}`, {
              detail: detail,
              bubbles: true
            });
            element.dispatchEvent(customEvent);
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