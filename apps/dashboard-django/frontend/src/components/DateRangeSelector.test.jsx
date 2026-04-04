import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import DateRangeSelector from "./DateRangeSelector";

describe("DateRangeSelector", () => {
  it("renders with provided dates", () => {
    render(
      <DateRangeSelector
        startDate="2026-01-01"
        endDate="2026-03-31"
        onChange={() => {}}
      />
    );
    const inputs = screen.getAllByDisplayValue(/2026/);
    expect(inputs).toHaveLength(2);
    expect(inputs[0].value).toBe("2026-01-01");
    expect(inputs[1].value).toBe("2026-03-31");
  });

  it("calls onChange with updated startDate", () => {
    const onChange = vi.fn();
    render(
      <DateRangeSelector
        startDate="2026-01-01"
        endDate="2026-03-31"
        onChange={onChange}
      />
    );
    const startInput = screen.getByDisplayValue("2026-01-01");
    fireEvent.change(startInput, { target: { value: "2026-02-01" } });
    expect(onChange).toHaveBeenCalledWith({
      startDate: "2026-02-01",
      endDate: "2026-03-31",
    });
  });

  it("calls onChange with updated endDate", () => {
    const onChange = vi.fn();
    render(
      <DateRangeSelector
        startDate="2026-01-01"
        endDate="2026-03-31"
        onChange={onChange}
      />
    );
    const endInput = screen.getByDisplayValue("2026-03-31");
    fireEvent.change(endInput, { target: { value: "2026-04-30" } });
    expect(onChange).toHaveBeenCalledWith({
      startDate: "2026-01-01",
      endDate: "2026-04-30",
    });
  });
});
