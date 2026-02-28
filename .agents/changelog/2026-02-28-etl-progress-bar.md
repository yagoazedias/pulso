# Rich Terminal Progress Bar for ETL Pipeline

**Date:** 2026-02-28
**Branch:** `feature/etl-progress-bar`

## Summary

Added a rich terminal progress UI to the ETL pipeline, replacing the basic log-every-100k-records reporting. Uses a two-pass approach: a fast StAX counting pre-pass to determine totals, followed by the existing ETL processing instrumented with per-element callbacks driving an ANSI-based progress display at 10 FPS.

## Results

```
Pulso ETL - Processing exportar.xml
──────────────────────────────────────────────────────────────────────────
  Record          [████████████████████░░░░░░░░░░]  67.3%  2,274,649 / 3,380,043
  Workout         [██████████████████████████████] 100.0%      1,819 / 1,819
  Correlation     [██████████████████████████████] 100.0%          0 / 0
  ActivitySummary [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░]   0.0%        0 / 1,687
──────────────────────────────────────────────────────────────────────────
  Overall: 67.3%  |  2,276,468 / 3,383,549  |  12,055/s  |  ETA 91s  |  3:08
```

- Counting pre-pass: ~29ms on test fixture, fast on real exports
- Rendering: 10 FPS daemon thread with cursor-up overwrite (no flicker)
- Throughput: ~12,000 elements/s (no measurable overhead from progress tracking)

## New Files

### `src/pulso/xml/io.clj` — Shared XML reader utilities
- Extracted `chained-reader` and `skip-doctype-reader` from `parser.clj`
- Reused by both the parser and the new counter

### `src/pulso/xml/counter.clj` — Fast element counter
- Uses Java StAX `XMLStreamReader` directly
- Counts `START_ELEMENT` events at depth 2 (direct children of `<HealthData>`)
- No attribute parsing, no object allocation per element
- Returns `{:Record 3380043, :Workout 1819, ...}`

### `src/pulso/progress.clj` — Progress state management
- `make-state [totals]` → atom with totals, processed counts, start time
- `record-progress! [state-atom element-type]` → increments count
- `snapshot [state]` → derives per-type %, overall %, rate (elem/s), ETA

### `src/pulso/ui/terminal.clj` — ANSI terminal renderer
- Background daemon thread rendering at 10 FPS
- Per-type progress bars with filled/empty block characters
- Overall summary line with throughput, ETA, elapsed time
- Hides/shows cursor during rendering

### `test/unit/pulso/xml/counter_test.clj` — Counter tests
- Fixture-based counting (verifies depth-2-only counting)
- DOCTYPE handling test

### `test/unit/pulso/progress_test.clj` — Progress calculation tests
- State initialization, increment, snapshot math, zero-total edge case

## Modified Files

### `src/pulso/xml/parser.clj`
- Removed inline `chained-reader` and `skip-doctype-reader`
- Now requires `pulso.xml.io` for shared reader utilities

### `src/pulso/etl.clj`
- Added 4-arity `execute!` accepting `opts` map with `:on-element` callback
- Old log-every-100k progress reporting disabled when callback is provided
- 3-arity delegates to 4-arity with empty opts (backward compatible)

### `src/pulso/core.clj`
- Added `--[no-]progress` CLI flag (default: `true`)
- When enabled: counting pass → progress state → start renderer → ETL with callback → stop renderer
- When disabled: runs ETL as before with traditional log output
- Suppresses console logging during progress UI (detaches/reattaches logback STDOUT appender)

## CLI Usage

```bash
# With progress bar (default)
lein run -- --file export.xml

# Without progress bar
lein run -- --file export.xml --no-progress
```

## Test Results

- Unit tests: 25 tests, 124 assertions — all passing
- 0 failures, 0 errors
