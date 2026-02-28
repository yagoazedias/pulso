# Rich Terminal Progress Bar for ETL Pipeline

**Date:** 2026-02-28
**Branch:** etl-performance-optimization

## Goal

Replace the basic log-every-100k-records progress reporting with a rich terminal UI showing per-type progress bars, throughput, and ETA. Requires a fast counting pre-pass to know totals upfront.

## Approach

**Two-pass pipeline** with a **custom ANSI-based terminal UI** (no external dependencies).

1. **Pass 1 (Counting):** Fast StAX-based scan counting top-level XML element tags by type
2. **Pass 2 (Processing):** Existing ETL pipeline, instrumented with a per-element callback driving the progress UI

## Files Created

| File | Purpose |
|------|---------|
| `src/pulso/xml/io.clj` | Shared XML reader utilities (chained-reader, skip-doctype-reader) |
| `src/pulso/xml/counter.clj` | Fast StAX element counter |
| `src/pulso/progress.clj` | Progress state management (atom + snapshot) |
| `src/pulso/ui/terminal.clj` | ANSI terminal renderer (10 FPS daemon thread) |
| `test/unit/pulso/xml/counter_test.clj` | Counter tests |
| `test/unit/pulso/progress_test.clj` | Progress calculation tests |

## Files Modified

| File | Changes |
|------|---------|
| `src/pulso/xml/parser.clj` | Use `xml.io` instead of inline reader fns |
| `src/pulso/etl.clj` | Accept `opts` map with `:on-element` callback |
| `src/pulso/core.clj` | `--[no-]progress` CLI flag, wire two-pass pipeline |

## Terminal UI Layout

```
Pulso ETL - Processing export.xml
──────────────────────────────────────────────────────────────────────────
  Record          [████████████████████░░░░░░░░░░]  67.3%  258,412 / 385,421
  Workout         [██████████████████████████████] 100.0%      312 / 312
  Correlation     [████████████████████████░░░░░░]  80.9%       72 / 89
  ActivitySummary [░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░]   0.0%        0 / 365
──────────────────────────────────────────────────────────────────────────
  Overall: 67.1%  |  258,796 / 386,187  |  12,483/s  |  ETA 10s  |  0:42
```

## Implementation Order

1. Extract shared XML I/O (`xml/io.clj`) + update `parser.clj`
2. Implement counting pass (`xml/counter.clj`) + tests
3. Implement progress state (`progress.clj`) + tests
4. Implement terminal UI (`ui/terminal.clj`)
5. Modify `etl.clj` to accept `on-element` callback
6. Wire everything in `core.clj` with `--progress` flag
