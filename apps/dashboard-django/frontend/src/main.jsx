import React from "react";
import { createRoot } from "react-dom/client";
import EnergyChart from "./components/EnergyChart";

const container = document.getElementById("chart-root");
if (container) {
  const root = createRoot(container);
  root.render(<EnergyChart />);
}
