import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import StatCard from "./StatCard";

describe("StatCard", () => {
  it("renders label, value, and subtitle", () => {
    render(<StatCard label="Energy" value="350 kcal" sub="Most recent day" />);
    expect(screen.getByText("Energy")).toBeInTheDocument();
    expect(screen.getByText("350 kcal")).toBeInTheDocument();
    expect(screen.getByText("Most recent day")).toBeInTheDocument();
  });

  it("omits subtitle when sub is not provided", () => {
    const { container } = render(<StatCard label="Energy" value="350 kcal" />);
    expect(container.querySelector(".stat-card-sub")).not.toBeInTheDocument();
  });
});
