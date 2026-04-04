import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import WorkoutVolumeChart from "./WorkoutVolumeChart";

const COLORS = [
  { border: "#36a2eb", bg: "rgba(54, 162, 235, 0.08)" },
  { border: "#ff9f40", bg: "rgba(255, 159, 64, 0.08)" },
  { border: "#ff6384", bg: "rgba(255, 99, 132, 0.08)" },
];

vi.mock("../hooks/useChartData", () => ({
  default: vi.fn(),
}));

import useChartData from "../hooks/useChartData";

describe("WorkoutVolumeChart", () => {
  it("maps datasets with correct color palette", () => {
    useChartData.mockReturnValue({
      data: {
        labels: ["W1", "W2"],
        datasets: [
          { label: "Count", data: [3, 5] },
          { label: "Duration", data: [120, 180] },
          { label: "Energy", data: [800, 1200] },
        ],
        meta: {},
      },
      loading: false,
      error: null,
    });
    render(<WorkoutVolumeChart startDate="2026-01-01" endDate="2026-03-31" />);
    const chart = screen.getByTestId("chart");
    const props = JSON.parse(chart.dataset.props);
    expect(props.data.datasets[0].borderColor).toBe(COLORS[0].border);
    expect(props.data.datasets[1].borderColor).toBe(COLORS[1].border);
    expect(props.data.datasets[2].borderColor).toBe(COLORS[2].border);
  });

  it("handles empty datasets", () => {
    useChartData.mockReturnValue({
      data: {
        labels: [],
        datasets: [{ label: "Count", data: [] }],
        meta: {},
      },
      loading: false,
      error: null,
    });
    render(<WorkoutVolumeChart startDate="2026-01-01" endDate="2026-03-31" />);
    expect(screen.getByText("No data available")).toBeInTheDocument();
  });
});
