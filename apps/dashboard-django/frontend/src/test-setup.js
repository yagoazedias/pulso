import "@testing-library/jest-dom";
import { vi } from "vitest";
import React from "react";

vi.mock("react-chartjs-2", () => ({
  Line: (props) => <canvas data-testid="chart" data-props={JSON.stringify(props)} />,
}));
