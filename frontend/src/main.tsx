import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

console.log("CODE BREAK: main.tsx loaded");

const rootElement = document.getElementById("root");

if (!rootElement) {
    throw new Error("CODE BREAK: #root element not found");
}

ReactDOM.createRoot(rootElement).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>
);