import { useState, useEffect } from "react";
import reactLogo from "./assets/react.svg";
import { invoke } from "@tauri-apps/api/core";
import { sendStradaMessage, handleStradaMessage } from "./strada-bridge";
import "./App.css";

function App() {
  const [greetMsg, setGreetMsg] = useState("");
  const [name, setName] = useState("");
  const [num1, setNum1] = useState(0);
  const [num2, setNum2] = useState(0);
  const [sumResult, setSumResult] = useState(null);

  async function greet() {
    // Learn more about Tauri commands at https://tauri.app/develop/calling-rust/
    setGreetMsg(await invoke("greet", { name }));
  }

  async function calculateSum() {
    // Call our new sum_numbers function
    const result = await invoke("sum_numbers", { a: parseInt(num1), b: parseInt(num2) });
    setSumResult(result);
  }

  // State for Strada bridge demo
  const [toastMessage, setToastMessage] = useState("");
  const [dialogResult, setDialogResult] = useState(null);
  const [filePickerResult, setFilePickerResult] = useState(null);

  // Function to show a toast using Strada bridge
  const showToast = async () => {
    await sendStradaMessage("toast", "show", { text: toastMessage || "Hello from React!" });
  };

  // Function to show a dialog using Strada bridge
  const showDialog = async (type) => {
    if (type === "alert") {
      await sendStradaMessage("dialog", "alert", { 
        title: "Alert",
        message: "This is an alert from React",
        buttonText: "OK"
      });
    } else {
      await sendStradaMessage("dialog", "confirm", {
        title: "Confirm",
        message: "Are you sure you want to proceed?",
        confirmText: "Yes",
        cancelText: "No"
      });
    }
  };

  // Function to open file picker using Strada bridge
  const openFilePicker = async () => {
    await sendStradaMessage("filePicker", "open", { type: "text/*" });
  };

  // Listen for Strada events
  useEffect(() => {
    const handleStradaEvent = (event) => {
      const { component, event: eventName, data } = event.detail;
      
      if (component === "dialog" && eventName === "result") {
        setDialogResult(data.confirmed ? "Confirmed" : "Cancelled");
      } else if (component === "filePicker" && eventName === "selected") {
        setFilePickerResult(`${data.fileName} (${data.fileSize} bytes, ${data.mimeType})`);
      }
    };

    document.addEventListener("strada-event", handleStradaEvent);
    return () => {
      document.removeEventListener("strada-event", handleStradaEvent);
    };
  }, []);

  return (
    <main className="container">
      <h1>Welcome to Tauri + React with Strada Bridge</h1>

      <div className="row">
        <a href="https://vitejs.dev" target="_blank">
          <img src="/vite.svg" className="logo vite" alt="Vite logo" />
        </a>
        <a href="https://tauri.app" target="_blank">
          <img src="/tauri.svg" className="logo tauri" alt="Tauri logo" />
        </a>
        <a href="https://reactjs.org" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <p>Click on the Tauri, Vite, and React logos to learn more.</p>

      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          greet();
        }}
      >
        <input
          id="greet-input"
          onChange={(e) => setName(e.currentTarget.value)}
          placeholder="Enter a name..."
        />
        <button type="submit">Greet</button>
      </form>
      <p>{greetMsg}</p>

      <div className="calculator">
        <h2>Number Calculator</h2>
        <div className="row">
          <input
            type="number"
            value={num1}
            onChange={(e) => setNum1(e.currentTarget.value)}
            placeholder="First number"
          />
          <span>+</span>
          <input
            type="number"
            value={num2}
            onChange={(e) => setNum2(e.currentTarget.value)}
            placeholder="Second number"
          />
          <button onClick={calculateSum}>Calculate Sum</button>
        </div>
        {sumResult !== null && (
          <p>
            Sum result: <strong>{sumResult}</strong>
          </p>
        )}
      </div>

      <div className="strada-demo" data-controller="strada-demo">
        <h2>Strada Bridge Demo</h2>
        
        <div className="card">
          <h3>Toast Component</h3>
          <div className="row">
            <input
              value={toastMessage}
              onChange={(e) => setToastMessage(e.currentTarget.value)}
              placeholder="Toast message..."
            />
            <button onClick={showToast}>Show Toast</button>
          </div>
        </div>

        <div className="card">
          <h3>Dialog Component</h3>
          <div className="row">
            <button onClick={() => showDialog("alert")}>Show Alert</button>
            <button onClick={() => showDialog("confirm")}>Show Confirm</button>
          </div>
          {dialogResult && (
            <p>
              Dialog result: <strong>{dialogResult}</strong>
            </p>
          )}
        </div>

        <div className="card">
          <h3>File Picker Component</h3>
          <div className="row">
            <button onClick={openFilePicker}>Open File Picker</button>
          </div>
          {filePickerResult && (
            <p>
              Selected file: <strong>{filePickerResult}</strong>
            </p>
          )}
        </div>
      </div>

      <div className="card">
        <h3>Test Strada Bridge</h3>
        <p>
          To test the Strada bridge with the Stimulus demo site, uncomment the line in MainActivity.kt:
        </p>
        <pre>webView.loadUrl("https://stimulusjs.demo.tebe.ch/")</pre>
        <p>
          Or use our custom test page:
        </p>
        <pre>webView.loadUrl("file:///android_asset/strada-test.html")</pre>
      </div>
    </main>
  );
}

export default App;
