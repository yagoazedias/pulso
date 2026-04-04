import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import ChartCard from "./ChartCard";

describe("ChartCard", () => {
  it("shows spinner when loading", () => {
    const { container } = render(<ChartCard title="Test" loading={true} />);
    expect(container.querySelector(".spinner")).toBeInTheDocument();
  });

  it("shows error message when error is set", () => {
    render(<ChartCard title="Test" error="Something broke" />);
    expect(screen.getByText("Something broke")).toBeInTheDocument();
  });

  it("shows no data message when empty", () => {
    render(<ChartCard title="Test" empty={true} />);
    expect(screen.getByText("No data available")).toBeInTheDocument();
  });

  it("renders children when data is present", () => {
    render(
      <ChartCard title="Test">
        <p>Chart content</p>
      </ChartCard>
    );
    expect(screen.getByText("Chart content")).toBeInTheDocument();
  });

  it("displays title", () => {
    render(<ChartCard title="Energy Chart" />);
    expect(screen.getByText("Energy Chart")).toBeInTheDocument();
  });

  it("displays meta when provided", () => {
    render(<ChartCard title="Test" meta="Unit: kcal" />);
    expect(screen.getByText("Unit: kcal")).toBeInTheDocument();
  });
});
