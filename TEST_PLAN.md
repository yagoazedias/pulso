# Pulso — Automated Test Plan

## Context

The Pulso ETL engine is working and successfully loaded 3.38M records. We now need automated tests to ensure correctness and prevent regressions. The codebase has a clear split between pure functions (XML transforms) and side-effectful functions (DB loaders), which maps naturally to unit tests vs integration tests.

**Key design decision:** Unit and integration tests are **fully segregated** — separate directories, separate Leiningen profiles, separate run commands. Unit tests never touch the database and can run anywhere; integration tests require PostgreSQL.

---

## 1. Test Structure

```
test/
  unit/
    pulso/
      xml/
        transform_test.clj        -- Pure transform function tests
        parser_test.clj            -- DOCTYPE stripping + element dispatch
  integration/
    pulso/
      test_helpers.clj             -- DB fixtures, shared utilities
      loader/
        lookups_test.clj           -- Lookup caching + upsert
        profile_test.clj           -- User profile insert
        batch_test.clj             -- Batch insert machinery
        records_test.clj           -- Record + metadata loading
        workouts_test.clj          -- Workout + children
        correlations_test.clj      -- Correlation + nested records
        activity_test.clj          -- Activity summary
      etl_test.clj                 -- End-to-end: full pipeline
test-resources/
  fixtures/
    small-export.xml               -- Minimal XML with all element types
```

---

## 2. Changes to project.clj

Add two profiles with separate source paths and test selectors:

```clojure
:profiles {:uberjar {:aot :all
                      :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
           :unit    {:test-paths ["test/unit"]
                     :resource-paths ["test-resources"]}
           :integration {:test-paths ["test/integration"]
                         :resource-paths ["test-resources"]}}
```

This gives us clean separation:
- `lein with-profile +unit test` — runs **only** unit tests (no DB)
- `lein with-profile +integration test` — runs **only** integration tests (needs PostgreSQL)
- `lein with-profile +unit,+integration test` — runs everything

---

## 3. Test Database Setup

Use a separate `pulso_test` database (same PostgreSQL container, different DB name):

```bash
docker compose exec db psql -U postgres -c "CREATE DATABASE pulso_test;"
```

The test helper overrides `DB_NAME` to `pulso_test` so integration tests never touch the production `pulso` database.

---

## 4. Test Helpers (`test/integration/pulso/test_helpers.clj`)

Only used by integration tests. Provides:
- **`test-ds`** — lazily-initialized HikariCP datasource to `pulso_test`, runs migrations once
- **`with-db-once`** — `:once` fixture that ensures datasource + migrations
- **`with-db`** — `:each` fixture that truncates all tables + resets atom caches
- **`count-rows`** — convenience query `SELECT count(*) FROM <table>`
- **`select-all`** — convenience query `SELECT * FROM <table>`

---

## 5. XML Fixture (`test-resources/fixtures/small-export.xml`)

Shared by both unit and integration tests. Minimal but complete — covers every element type:
- 1 ExportDate
- 1 Me (user profile)
- 2 Records (1 with metadata, 1 without)
- 1 Workout (with event, statistics, route, metadata)
- 1 Correlation (with 2 nested records + metadata)
- 1 ActivitySummary

No DOCTYPE block (tested separately via inline strings in parser tests).

---

## 6. Unit Tests (no DB required)

### 6a. `pulso.xml.transform-test` (~15 tests)

All pure functions:

| Test | Validates |
|---|---|
| `parse-datetime-valid` | Parses `"2025-01-01 10:00:00 -0300"` → correct OffsetDateTime |
| `parse-datetime-nil-and-empty` | nil → nil, "" → nil |
| `parse-date-valid` | `"1998-10-11"` → LocalDate |
| `parse-date-nil-and-empty` | nil/empty → nil |
| `parse-dbl-valid` | `"72.5"` → 72.5 |
| `parse-dbl-invalid` | `"abc"` → nil, nil → nil |
| `export-date-element->map` | Correct OffsetDateTime in result |
| `me-element->map` | All fields mapped, locale passed through |
| `record-element->map-without-metadata` | Correct fields, empty metadata vec |
| `record-element->map-with-metadata` | Metadata entries extracted |
| `workout-element->map-full` | Events, statistics, routes, metadata all populated |
| `workout-element->map-empty-children` | All child vectors empty |
| `correlation-element->map` | Nested records extracted |
| `activity-summary-element->map` | All 10 fields parsed |
| `activity-summary-partial` | Missing attrs → nil without errors |

### 6b. `pulso.xml.parser-test` (~3 tests)

| Test | Validates |
|---|---|
| `parse-health-data-dispatches-all-elements` | Handler receives all 7 elements in order, locale is correct |
| `parse-health-data-with-doctype` | DOCTYPE-containing XML parses without errors |
| `parse-health-data-skips-whitespace` | Only map elements dispatched (no text nodes) |

---

## 7. Integration Tests (require PostgreSQL)

### 7a. `pulso.loader.lookups-test` (~5 tests)

| Test | Validates |
|---|---|
| `ensure-source-id-creates-and-caches` | First call inserts, second returns cached ID |
| `ensure-source-id-different-versions` | Different versions → different IDs |
| `ensure-source-id-nil-returns-nil` | nil name → nil, no insert |
| `ensure-device-id-creates-and-caches` | Insert + cache cycle |
| `ensure-unit-id-creates-and-caches` | Insert + cache cycle |

### 7b. `pulso.loader.batch-test` (~3 tests)

| Test | Validates |
|---|---|
| `batcher-flushes-at-batch-size` | Auto-flush when buffer reaches threshold |
| `batcher-flush-partial` | Manual flush of partial buffer |
| `batcher-empty-flush-noop` | Flush with no rows → no error |

### 7c. `pulso.loader.profile-test` (~2 tests)

| Test | Validates |
|---|---|
| `save-export-date-stores-value` | Export date atom populated |
| `save-profile-inserts-row` | user_profile row with correct DOB, sex, locale |

### 7d. `pulso.loader.records-test` (~3 tests)

| Test | Validates |
|---|---|
| `process-record-without-metadata` | Batched insert, no metadata rows |
| `process-record-with-metadata` | Individual insert + metadata rows linked |
| `flush-clears-pending` | All pending records flushed to DB |

### 7e. `pulso.loader.workouts-test` (~2 tests)

| Test | Validates |
|---|---|
| `process-workout-all-children` | workout + metadata + event + statistics + route rows |
| `process-workout-no-children` | Only workout row, child tables empty |

### 7f. `pulso.loader.correlations-test` (~1 test)

| Test | Validates |
|---|---|
| `process-correlation-with-nested-records` | correlation + correlation_record + record rows + metadata |

### 7g. `pulso.loader.activity-test` (~1 test)

| Test | Validates |
|---|---|
| `process-activity-summary` | activity_summary row with correct fields |

### 7h. `pulso.etl-test` (~2 tests, end-to-end)

| Test | Validates |
|---|---|
| `execute-full-pipeline` | Run ETL on fixture, verify returned counts and all table row counts |
| `execute-is-idempotent` | Run twice, counts identical (truncate-and-reload) |

---

## 8. Implementation Order

**Phase 1 — Unit tests (no DB dependency):**
1. `project.clj` — add `:unit` and `:integration` profiles
2. `test-resources/fixtures/small-export.xml` — XML fixture
3. `test/unit/pulso/xml/transform_test.clj` — pure transform tests
4. `test/unit/pulso/xml/parser_test.clj` — parser tests
5. Verify: `lein with-profile +unit test` passes

**Phase 2 — Integration tests (requires PostgreSQL):**
6. `test/integration/pulso/test_helpers.clj` — DB fixtures
7. `test/integration/pulso/loader/batch_test.clj` — foundational
8. `test/integration/pulso/loader/lookups_test.clj` — foundational
9. `test/integration/pulso/loader/profile_test.clj`
10. `test/integration/pulso/loader/records_test.clj`
11. `test/integration/pulso/loader/workouts_test.clj`
12. `test/integration/pulso/loader/correlations_test.clj`
13. `test/integration/pulso/loader/activity_test.clj`
14. `test/integration/pulso/etl_test.clj` — end-to-end
15. Verify: `lein with-profile +integration test` passes

---

## 9. Running Tests

```bash
# Unit tests only — no Docker/DB needed
lein with-profile +unit test

# Integration tests only — requires PostgreSQL
docker compose up -d db
docker compose exec db psql -U postgres -c "CREATE DATABASE pulso_test;"
lein with-profile +integration test

# All tests
lein with-profile +unit,+integration test

# Single namespace
lein with-profile +integration test pulso.etl-test
```

---

## 10. Verification

- `lein with-profile +unit test` passes with 0 failures, **no PostgreSQL required**
- `lein with-profile +integration test` passes with 0 failures, uses `pulso_test` database
- Unit and integration tests are in completely separate directories and profiles
- Integration tests never touch the production `pulso` database
- All table counts match expected values in ETL end-to-end test
