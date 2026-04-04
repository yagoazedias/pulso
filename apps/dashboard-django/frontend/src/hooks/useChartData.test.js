import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import useChartData from "./useChartData";

const mockResponse = {
  labels: ["2026-01-01", "2026-01-02"],
  datasets: [{ label: "Energy", data: [300, 400] }],
  meta: { unit: "kcal" },
};

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("useChartData", () => {
  it("returns loading true initially", () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {}));
    const { result } = renderHook(() => useChartData("/api/test"));
    expect(result.current.loading).toBe(true);
    expect(result.current.data).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it("sets data on successful fetch", async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({ ok: true, json: () => Promise.resolve(mockResponse) })
    );
    const { result } = renderHook(() => useChartData("/api/test"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual(mockResponse);
    expect(result.current.error).toBeNull();
  });

  it("sets error on non-ok response", async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) })
    );
    const { result } = renderHook(() => useChartData("/api/test"));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe("HTTP 500");
    expect(result.current.data).toBeNull();
  });

  it("builds URL with date params", async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({ ok: true, json: () => Promise.resolve(mockResponse) })
    );
    renderHook(() => useChartData("/api/test", "2026-01-01", "2026-03-31"));
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/test?start=2026-01-01&end=2026-03-31"
    );
  });

  it("calls endpoint without params when dates are absent", async () => {
    globalThis.fetch = vi.fn(() =>
      Promise.resolve({ ok: true, json: () => Promise.resolve(mockResponse) })
    );
    renderHook(() => useChartData("/api/test"));
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    expect(globalThis.fetch).toHaveBeenCalledWith("/api/test");
  });
});
