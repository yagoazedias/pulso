import React from "react";
import Dashboard from "./Dashboard";

export default function App() {
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-icon">P</div>
          <span className="sidebar-brand-name">Pulso</span>
        </div>
        <ul className="sidebar-nav">
          <li className="sidebar-nav-item active">Dashboard</li>
        </ul>
      </aside>
      <main className="main">
        <Dashboard />
      </main>
    </div>
  );
}
