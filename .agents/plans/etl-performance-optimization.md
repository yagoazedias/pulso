# ETL Performance Optimization Plan

## Context

Loading a 1.5GB+ Apple Health XML export with 3.4M+ records is taking too long. The root cause: **records with metadata (~60% of 3.4M) each do an individual `INSERT...RETURNING id` + transaction**, resulting in ~2M single-row round trips instead of batched inserts. Workouts (1800) and correlations also use individual inserts but have negligible absolute impact.

## Bug Fix (Prerequisite)

**File:** `src/pulso/loader/batch.clj`

`make-returning-batcher` has an ID extraction inconsistency:
- Line 67 (auto-flush in `add!`): uses `(val (first r))` — correct, table-agnostic
- Line 77 (explicit `flush!`): uses `(:id r)` — **wrong**, should be `(val (first r))` since `execute-batch!` returns namespace-qualified keys like `:record/id`

Fix: change line 77 to use `(val (first r))`.

## Optimization 1 — Batch Records with Metadata (Critical)

**Impact:** Reduces ~2M individual round trips to ~400 batch flushes. Estimated 50-100x improvement on the dominant code path.

**File:** `src/pulso/loader/records.clj`

### Changes:

1. **`make-batchers`** — add a `make-returning-batcher` for records-with-metadata:
   ```clojure
   {:record          (batch/make-batcher ...)           ;; unchanged, for no-metadata records
    :record-with-meta (batch/make-returning-batcher ...) ;; NEW, for records with metadata
    :metadata         (batch/make-batcher ...)}          ;; unchanged
   ```

2. **`process!`** — replace `insert-record-with-metadata!` with the returning-batcher's `add!`:
   - Records with metadata → `(:add! (:record-with-meta batchers))` passing metadata entries as associated data
   - When auto-flush fires, process returned ID+metadata pairs into the metadata batcher
   - Records without metadata → unchanged batch path

3. **Add `process-returned-metadata!`** helper — iterates over returned `{:id N :metadata [...]}` pairs and feeds each metadata entry into the metadata batcher

4. **`flush!`** — flush returning-batcher first, process its returned pairs into metadata batcher, then flush metadata batcher last

5. **Remove `insert-record-with-metadata!`** — no longer needed

### FK Integrity:
`make-returning-batcher` does `execute-batch!` with `:return-keys true` inside a transaction. PostgreSQL returns IDs in insertion order, matched positionally with the metadata buffer. Metadata batcher flushes after record batcher → FK constraints satisfied.

## Optimization 2 — Batch Workout Parents (Low absolute impact, consistency)

**Impact:** Reduces ~1800 individual inserts to ~1 batch flush. Small absolute gain but makes the pattern consistent.

**File:** `src/pulso/loader/workouts.clj`

### Changes:

1. **`make-batchers`** — add a `make-returning-batcher` for workout parents (currently workouts have no batcher, just inline `jdbc/execute-one!`)

2. **`process!`** — package workout row + all children data (metadata, events, statistics, routes) as associated metadata for the returning-batcher. When auto-flush fires, process returned IDs to feed children into their respective batchers.

3. **Add `process-returned-children!`** helper — iterates over returned `{:id workout-id :metadata children-map}` pairs and adds each child entry to the correct batcher

4. **`flush!`** — flush workout returning-batcher first, process children, then flush all child batchers

## Optimization 3 — Batch Correlation Inserts (Low absolute impact, consistency)

**File:** `src/pulso/loader/correlations.clj`

### Changes:

1. **`make-batchers`** — add returning-batchers for both correlation parents AND nested records (separate batcher from the top-level records to avoid cross-batcher complexity)

2. **`process!`** — pre-resolve all lookup FKs for nested records, then package everything as associated metadata for the correlation returning-batcher

3. **Two-phase flush:** flush correlation parents → process nested records into nested-record returning-batcher → flush nested records → process metadata and correlation_record links → flush remaining batchers

4. **`flush!` signature change** — no longer needs `record-batchers` parameter since correlations use their own dedicated nested-record batcher

**File:** `src/pulso/etl.clj` — update `correlations/process!` call (no longer passes `record-batchers`) and `correlations/flush!` call

## Optimization 4 — Increase Connection Pool

**File:** `src/pulso/db.clj`

Change `:maximumPoolSize` from `4` to `10`. Only meaningful with future parallelism but removes the constraint now.

## Test Changes

**File:** `test/integration/pulso/loader/records_test.clj`
- `process-record-with-metadata` test currently asserts 1 record in DB immediately after `process!`. With batching, the record is buffered. Update test to flush before asserting DB count.

**All other tests** call `flush!` before asserting → should pass without changes.

**End-to-end test** (`etl_test.clj`) calls flush at the end → no changes needed.

## Implementation Order

| Step | What | File(s) | Risk |
|------|------|---------|------|
| 1 | Fix `make-returning-batcher` ID bug | `batch.clj` | Very Low |
| 2 | Batch records with metadata | `records.clj` | Low |
| 3 | Update records test | `records_test.clj` | Very Low |
| 4 | Run full test suite | — | — |
| 5 | Batch workout parents | `workouts.clj` | Low |
| 6 | Batch correlation inserts | `correlations.clj`, `etl.clj` | Medium |
| 7 | Increase connection pool | `db.clj` | Very Low |
| 8 | Run full test suite | — | — |

## Verification

1. `lein with-profile +unit test` — unit tests pass
2. `lein with-profile +integration test` — integration tests pass (requires PostgreSQL + `pulso_test` DB)
3. `lein with-profile +unit,+integration test` — all 37 tests, 176 assertions pass
4. Manual load test with real XML export to measure improvement
