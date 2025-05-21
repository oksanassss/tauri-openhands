import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./strada-bridge"; // Import the Strada bridge

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
