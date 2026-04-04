# Frontend Unit Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Vitest unit tests for all React frontend components and the `useChartData` hook, plus CI integration.

**Architecture:** Vitest with jsdom + @testing-library/react. Global mock for react-chartjs-2 (avoid canvas). Per-test fetch mocking via vi.fn(). Chart component tests mock useChartData directly. CI runs as a parallel job in existing tests.yml.

**Tech Stack:** Vitest, @testing-library/react, @testing-library/jest-dom, jsdom

---

## File Structure

```
apps/dashboard-django/
├── vite.config.js                              # MODIFY: add test config
├── package.json                                # MODIFY: add test deps + script
├── frontend/src/
│   ├── test-setup.js                           # CREATE: global setup + mocks
│   ├── hooks/useChartData.test.js              # CREATE: hook tests
│   └── components/
│       ├── ChartCard.test.jsx                  # CREATE
│       ├── StatCard.test.jsx                   # CREATE
│       ├── DateRangeSelector.test.jsx          # CREATE
│       ├── EnergyChart.test.jsx                # CREATE
│       ├── WorkoutVolumeChart.test.jsx         # CREATE
│       └── TopRecordTypesChart.test.jsx        # CREATE
.github/workflows/tests.yml                     # MODIFY: add frontend-test job
```

---

### Task 1: Install dependencies and configure Vitest

**Files:**
- Modify: `apps/dashboard-django/package.json`
- Modify: `apps/dashboard-django/vite.config.js`
- Create: `apps/dashboard-django/frontend/src/test-setup.js`

- [ ] **Step 1: Install test dependencies**

Run from `apps/dashboard-django/`:
```bash
npm install --save-dev vitest @testing-library/react @testing-library/jest-dom jsdom
```

- [ ] **Step 2: Add test script to package.json**

In `apps/dashboard-django/package.json`, add to `"scripts"`:
```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 3: Add test config to vite.config.js**

Replace `apps/dashboard-django/vite.config.js` with:
```js
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  root: "frontend",
  build: {
    outDir: "static",
    manifest: true,
    rollupOptions: {
      input: "frontend/src/main.jsx",
    },
  },
  server: {
    port: 5173,
    origin: "http://localhost:5173",
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./frontend/src/test-setup.js"],
    globals: true,
    root: ".",
  },
});
```

The key additions: `test` block with `jsdom` environment, setup file path, `globals: true` (exposes `describe`/`it`/`expect` without imports), and `root: "."` so vitest resolves paths from the dashboard-django directory.

- [ ] **Step 4: Create test setup file**

Create `apps/dashboard-django/frontend/src/test-setup.js`:
```js
import "@testing-library/jest-dom";
import { vi } from "vitest";
import React from "react";

vi.mock("react-chartjs-2", () => ({
  Line: (props) => <canvas data-testid="chart" data-props={JSON.stringify(props)} />,
}));
```

This registers jest-dom matchers (`toBeInTheDocument`, etc.) and globally mocks `react-chartjs-2` so that `<Line>` renders a simple `<canvas>` stub.

- [ ] **Step 5: Verify setup works**

Run from `apps/dashboard-django/`:
```bash
npx vitest run
```

Expected: "No test files found" (no error — config is valid).

- [ ] **Step 6: Commit**

```bash
git add apps/dashboard-django/package.json apps/dashboard-django/package-lock.json apps/dashboard-django/vite.config.js apps/dashboard-django/frontend/src/test-setup.js
git commit -m "chore: configure vitest with jsdom and testing-library"
```

---

### Task 2: Test useChartData hook

**Files:**
- Create: `apps/dashboard-django/frontend/src/hooks/useChartData.test.js`
- Reference: `apps/dashboard-django/frontend/src/hooks/useChartData.js`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/hooks/useChartData.test.js`:
```js
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
```

- [ ] **Step 2: Run tests to verify they pass**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/hooks/useChartData.test.js
```

Expected: 5 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/hooks/useChartData.test.js
git commit -m "test: add useChartData hook tests"
```

---

### Task 3: Test ChartCard component

**Files:**
- Create: `apps/dashboard-django/frontend/src/components/ChartCard.test.jsx`
- Reference: `apps/dashboard-django/frontend/src/components/ChartCard.jsx`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/components/ChartCard.test.jsx`:
```jsx
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
```

- [ ] **Step 2: Run tests**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/components/ChartCard.test.jsx
```

Expected: 6 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/components/ChartCard.test.jsx
git commit -m "test: add ChartCard component tests"
```

---

### Task 4: Test StatCard component

**Files:**
- Create: `apps/dashboard-django/frontend/src/components/StatCard.test.jsx`
- Reference: `apps/dashboard-django/frontend/src/components/StatCard.jsx`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/components/StatCard.test.jsx`:
```jsx
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
```

- [ ] **Step 2: Run tests**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/components/StatCard.test.jsx
```

Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/components/StatCard.test.jsx
git commit -m "test: add StatCard component tests"
```

---

### Task 5: Test DateRangeSelector component

**Files:**
- Create: `apps/dashboard-django/frontend/src/components/DateRangeSelector.test.jsx`
- Reference: `apps/dashboard-django/frontend/src/components/DateRangeSelector.jsx`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/components/DateRangeSelector.test.jsx`:
```jsx
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
```

- [ ] **Step 2: Run tests**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/components/DateRangeSelector.test.jsx
```

Expected: 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/components/DateRangeSelector.test.jsx
git commit -m "test: add DateRangeSelector component tests"
```

---

### Task 6: Test EnergyChart component

**Files:**
- Create: `apps/dashboard-django/frontend/src/components/EnergyChart.test.jsx`
- Reference: `apps/dashboard-django/frontend/src/components/EnergyChart.jsx`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/components/EnergyChart.test.jsx`:
```jsx
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
```

- [ ] **Step 2: Run tests**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/components/EnergyChart.test.jsx
```

Expected: 3 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/components/EnergyChart.test.jsx
git commit -m "test: add EnergyChart component tests"
```

---

### Task 7: Test WorkoutVolumeChart component

**Files:**
- Create: `apps/dashboard-django/frontend/src/components/WorkoutVolumeChart.test.jsx`
- Reference: `apps/dashboard-django/frontend/src/components/WorkoutVolumeChart.jsx`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/components/WorkoutVolumeChart.test.jsx`:
```jsx
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
```

- [ ] **Step 2: Run tests**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/components/WorkoutVolumeChart.test.jsx
```

Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/components/WorkoutVolumeChart.test.jsx
git commit -m "test: add WorkoutVolumeChart component tests"
```

---

### Task 8: Test TopRecordTypesChart component

**Files:**
- Create: `apps/dashboard-django/frontend/src/components/TopRecordTypesChart.test.jsx`
- Reference: `apps/dashboard-django/frontend/src/components/TopRecordTypesChart.jsx`

- [ ] **Step 1: Write the tests**

Create `apps/dashboard-django/frontend/src/components/TopRecordTypesChart.test.jsx`:
```jsx
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
```

- [ ] **Step 2: Run tests**

```bash
cd apps/dashboard-django && npx vitest run frontend/src/components/TopRecordTypesChart.test.jsx
```

Expected: 2 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/frontend/src/components/TopRecordTypesChart.test.jsx
git commit -m "test: add TopRecordTypesChart component tests"
```

---

### Task 9: Add frontend-test job to CI

**Files:**
- Modify: `.github/workflows/tests.yml`

- [ ] **Step 1: Add the frontend-test job**

Add a new job to `.github/workflows/tests.yml` at the same level as the existing `test` job (after line 1, inside `jobs:`):

```yaml
  frontend-test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: "20"
          cache: "npm"
          cache-dependency-path: apps/dashboard-django/package-lock.json

      - name: Install dependencies
        working-directory: ./apps/dashboard-django
        run: npm ci

      - name: Run frontend tests
        working-directory: ./apps/dashboard-django
        run: npm test
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/tests.yml
git commit -m "ci: add frontend test job to CI workflow"
```

---

### Task 10: Run all tests and verify

- [ ] **Step 1: Run frontend tests**

```bash
cd apps/dashboard-django && npm test
```

Expected: ~22 tests across 7 files, all PASS.

- [ ] **Step 2: Run ETL unit tests**

```bash
cd apps/etl-clojure && lein with-profile +unit test
```

Expected: 18 tests, 95 assertions, all PASS.

- [ ] **Step 3: Run Vite production build**

```bash
cd apps/dashboard-django && npm run build
```

Expected: Build succeeds, no errors.
