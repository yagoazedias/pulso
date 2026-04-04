import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import TopRecordTypesChart from "./TopRecordTypesChart";

vi.mock("../hooks/useChartData", () => ({
  default: vi.fn(),
}));

import useChartData from "../hooks/useChartData";

describe("TopRecordTypesChart", () => {
  it("strips HKQuantityTypeIdentifier prefix from labels", () => {
    useChartData.mockReturnValue({
      data: {
        labels: ["W1", "W2"],
        datasets: [
          { label: "HKQuantityTypeIdentifierHeartRate", data: [100, 200] },
          { label: "HKQuantityTypeIdentifierStepCount", data: [50, 80] },
        ],
        meta: {},
      },
      loading: false,
      error: null,
    });
    render(<TopRecordTypesChart startDate="2026-01-01" endDate="2026-03-31" />);
    const chart = screen.getByTestId("chart");
    const props = JSON.parse(chart.dataset.props);
    expect(props.data.datasets[0].label).toBe("HeartRate");
    expect(props.data.datasets[1].label).toBe("StepCount");
  });

  it("uses 5-color palette", () => {
    const palette = ["#36a2eb", "#ff6384", "#ff9f40", "#4bc0c0", "#9966ff"];
    useChartData.mockReturnValue({
      data: {
        labels: ["W1"],
        datasets: palette.map((_, i) => ({
          label: `Type${i}`,
          data: [i * 10],
        })),
        meta: {},
      },
      loading: false,
      error: null,
    });
    render(<TopRecordTypesChart startDate="2026-01-01" endDate="2026-03-31" />);
    const chart = screen.getByTestId("chart");
    const props = JSON.parse(chart.dataset.props);
    props.data.datasets.forEach((ds, i) => {
      expect(ds.borderColor).toBe(palette[i]);
    });
  });
});
