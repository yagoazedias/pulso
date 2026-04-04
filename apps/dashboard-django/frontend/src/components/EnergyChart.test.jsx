import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import EnergyChart from "./EnergyChart";

const mockData = {
  labels: ["2026-01-01", "2026-01-02"],
  datasets: [
    { label: "Active Energy Burned", data: [300, 400] },
    { label: "Goal", data: [500, 500] },
  ],
  meta: { unit: "kcal" },
};

vi.mock("../hooks/useChartData", () => ({
  default: vi.fn(),
}));

import useChartData from "../hooks/useChartData";

describe("EnergyChart", () => {
  it("passes correct endpoint to useChartData", () => {
    useChartData.mockReturnValue({ data: null, loading: true, error: null });
    render(<EnergyChart startDate="2026-01-01" endDate="2026-03-31" />);
    expect(useChartData).toHaveBeenCalledWith(
      "/api/metrics/energy-vs-goal",
      "2026-01-01",
      "2026-03-31"
    );
  });

  it("renders chart with correct data transformation", () => {
    useChartData.mockReturnValue({ data: mockData, loading: false, error: null });
    render(<EnergyChart startDate="2026-01-01" endDate="2026-03-31" />);
    const chart = screen.getByTestId("chart");
    const props = JSON.parse(chart.dataset.props);
    expect(props.data.labels).toEqual(["2026-01-01", "2026-01-02"]);
    expect(props.data.datasets[0].borderColor).toBe("#ff6384");
    expect(props.data.datasets[0].fill).toBe(true);
    expect(props.data.datasets[1].borderColor).toBe("#4bc0c0");
    expect(props.data.datasets[1].borderDash).toEqual([5, 5]);
  });

  it("shows loading state via ChartCard", () => {
    useChartData.mockReturnValue({ data: null, loading: true, error: null });
    const { container } = render(<EnergyChart startDate="2026-01-01" endDate="2026-03-31" />);
    expect(container.querySelector(".spinner")).toBeInTheDocument();
  });
});
