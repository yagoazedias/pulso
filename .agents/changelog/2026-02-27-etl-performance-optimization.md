# ETL Performance Optimization

**Date:** 2026-02-27
**Branch:** `etl-performance-optimization`

## Summary

Optimized the ETL pipeline from **57.2 minutes to 4.4 minutes** (13x speedup) for loading a 1.5GB Apple Health XML export with 3.4M+ records. The root cause was ~2M individual `INSERT...RETURNING id` round trips for records with metadata, replaced with batched multi-row inserts.

## Results

```
                BEFORE          AFTER
Records:        3,380,043       3,380,043
Workouts:       1,819           1,819
Activities:     1,687           1,687
Time:           57.2 min        4.4 min
Throughput:     ~985 rec/sec    ~12,800 rec/sec
```

## Changes

### Bug Fix — `batch.clj`

- Fixed ID extraction in `make-returning-batcher` flush path: `(:id r)` did not work because `execute-batch!` returns namespace-qualified keys like `:record/id`. Changed to `(val (first r))`.
- Replaced `prepare/execute-batch!` with a multi-row `INSERT...RETURNING id` via `jdbc/execute!` in new `flush-batch-returning!` function, since `execute-batch!` returns update counts (integers) instead of result maps.

### Records — `records.clj`

- Added `:record-with-meta` returning-batcher for records that have metadata entries.
- Replaced `insert-record-with-metadata!` (individual insert per record) with batched path using the returning-batcher.
- Added `process-returned-metadata!` helper that takes returned `{:id N :metadata [...]}` pairs and feeds metadata entries into the metadata batcher.
- Updated `flush!` to flush returning-batcher first, process returned metadata, then flush remaining batchers.
- Removed `insert-record-with-metadata!` function (no longer needed).
- Removed `next.jdbc` require (no longer used directly).

### Workouts — `workouts.clj`

- Added `:workout` returning-batcher for workout parent rows (previously used inline `jdbc/execute-one!`).
- `process!` now buffers workout rows with all children data (metadata, events, statistics, routes) as associated metadata for the returning-batcher.
- Added `process-returned-children!` helper that distributes children into their respective batchers after workout IDs are returned.
- Updated `flush!` to flush workout batcher first, process children, then flush all child batchers.
- Removed `next.jdbc` require.

### Correlations — `correlations.clj`

- Added `:correlation` returning-batcher for correlation parents.
- Added `:nested-record` returning-batcher for records nested inside correlations (dedicated batcher, separate from top-level records).
- Added `:record-metadata` and `:record-link` batchers for nested record metadata and `correlation_record` join rows.
- Two-phase flush: correlation parents → nested records → metadata + links.
- `flush!` now takes `ds` parameter (needed for lookup resolution during flush).
- Removed `next.jdbc` require.

### ETL Orchestration — `etl.clj`

- Removed `record-batchers` parameter from `correlations/process!` call (correlations now use their own nested-record batcher).
- Updated `correlations/flush!` call to pass `ds`.

### Connection Pool — `db.clj`

- Increased `maximumPoolSize` from 4 to 10.

### Dockerfile

- Updated base image from `clojure:lein-2.11.2-jammy` (no longer available) to `clojure:lein-jammy`.
- Fixed uberjar COPY path from `target/uberjar/` to `target/`.

### Docker Compose — `docker-compose.yml`

- Fixed app command path from `/data/exportar.xml` to `/data/apple_health_export/exportar.xml`.

## Test Changes

### `records_test.clj`

- Updated `process-record-with-metadata` test: records with metadata are now buffered (not inserted immediately), so the test flushes before asserting DB count.

### `correlations_test.clj`

- Removed `records` dependency (correlations now use their own nested-record batcher).
- Updated `process!` and `flush!` calls to match new signatures.

## Test Results

- Unit tests: 18 tests, 95 assertions — all passing
- Integration tests: 19 tests, 83 assertions — all passing
- Full suite: 37 tests, 178 assertions — 0 failures, 0 errors
