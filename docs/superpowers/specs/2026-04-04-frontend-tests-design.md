# Frontend Unit Tests Design

## Goal

Add unit tests for all React frontend components and the `useChartData` hook in the dashboard app. Focus on isolated, fast tests that verify rendering logic and data transformations.

## Setup

- **Test runner:** Vitest (native Vite integration)
- **Rendering:** `@testing-library/react` with `jsdom` environment
- **Mocks:**
  - `react-chartjs-2` mocked globally (avoid canvas/WebGL dependency)
  - `fetch` mocked per test via `vi.fn()`

### Dependencies to Add

```
vitest
@testing-library/react
@testing-library/jest-dom
jsdom
```

### Configuration

- Add `test` block to `vite.config.js` with `environment: "jsdom"` and global setup file
- Setup file registers `@testing-library/jest-dom` matchers
- Mock `react-chartjs-2` in setup to export a stub `Line` component
- Add `"test": "vitest run"` script to `package.json`

## Test Files

All test files live alongside their source in `frontend/src/`.

### `hooks/useChartData.test.js`

Tests for the custom data-fetching hook:

1. Returns `loading: true` initially
2. Sets `data` on successful fetch response
3. Sets `error` on failed fetch (non-2xx status)
4. Builds correct URL with `start` and `end` query params
5. Re-fetches when params change

### `components/ChartCard.test.jsx`

Tests for the loading/error/empty/content state rendering:

1. Shows spinner (`div.spinner`) when `loading` is true
2. Shows error message when `error` is set
3. Shows "No data available" when `empty` is true
4. Renders children when data is present (no loading/error/empty)
5. Displays title in header
6. Displays meta text when provided

### `components/StatCard.test.jsx`

Tests for simple presentational rendering:

1. Renders label, value, and subtitle
2. Omits subtitle element when `sub` prop is not provided

### `components/DateRangeSelector.test.jsx`

Tests for date inputs and callback behavior:

1. Renders with provided `startDate` and `endDate` values
2. Calls `onChange` with updated `startDate` when start input changes
3. Calls `onChange` with updated `endDate` when end input changes

### `components/EnergyChart.test.jsx`

Tests for API data transformation into Chart.js format:

1. Passes `/api/metrics/energy-vs-goal` endpoint to `useChartData`
2. Transforms API response into correct Chart.js dataset format (coral color, fill, dashed goal line)
3. Delegates loading/error/empty states to `ChartCard`

### `components/WorkoutVolumeChart.test.jsx`

Tests for workout data transformation:

1. Maps API datasets with correct 3-color palette (blue/orange/coral)
2. Handles empty datasets gracefully

### `components/TopRecordTypesChart.test.jsx`

Tests for record types data transformation:

1. Strips `HKQuantityTypeIdentifier` prefix from dataset labels
2. Uses 5-color palette for dataset lines

## Total

~20 tests across 7 test files. All unit-level, no integration or E2E tests.

## Mocking Strategy

### `react-chartjs-2`

Global mock in setup file that exports a stub `Line` component rendering a `<canvas data-testid="chart" />`. Chart tests verify the `data` and `options` props passed to this stub.

### `fetch`

Each test file mocks `globalThis.fetch` via `vi.fn()`. Tests provide controlled JSON responses matching the API contract:

```json
{
  "labels": ["2026-01-01", "2026-01-02"],
  "datasets": [{"label": "Energy", "data": [300, 400]}],
  "meta": {"unit": "kcal"}
}
```

### `useChartData` (in chart component tests)

Chart component tests mock the `useChartData` hook via `vi.mock("../hooks/useChartData")` to return controlled states (loading, error, data) without needing to mock fetch.

## CI/CD

Add a `frontend-test` job to `.github/workflows/tests.yml` that runs alongside the existing ETL test job.

### Job: `frontend-test`

```yaml
frontend-test:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-node@v4
      with:
        node-version: "20"
        cache: "npm"
        cache-dependency-path: apps/dashboard-django/package-lock.json
    - run: npm ci
      working-directory: ./apps/dashboard-django
    - run: npm test
      working-directory: ./apps/dashboard-django
```

- No database or services needed — all tests are mocked
- Runs in parallel with the existing `test` (ETL) job
- Triggered on the same branches: `master`, `main`, `develop`
- Include frontend test results in the PR comment (update the existing `actions/github-script` step)
