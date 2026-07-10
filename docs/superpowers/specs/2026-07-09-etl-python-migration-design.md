# ETL Clojure → Python Migration — Design Spec

## 1. Objective

Replace `apps/etl-clojure` with a new `apps/etl-python` app that reproduces the same Apple Health XML → PostgreSQL ETL pipeline in Python, with full test parity (37 tests: 18 unit + 19 integration). Once `apps/etl-python` passes its full test suite and a manual run against a real export produces matching row counts, `apps/etl-clojure` is deleted and the monorepo has a single, consistent Python tech stack across both apps.

## 2. Approach

**Mechanical 1:1 port, module-by-module.** Each Clojure namespace maps to one Python module, preserving the same function boundaries, same SQL, same table/column names, and the same architecture (streaming parse → transform → batch load → lookup caching). This was chosen over a from-scratch idiomatic rewrite or a big-bang monolithic script because it keeps risk low and verifiability high: every module and every test has a direct, traceable counterpart in the original, which matters for a pipeline handling 3.4M+ real health records.

## 3. Technology Decisions

| Concern | Choice |
|---|---|
| App location | New sibling app: `apps/etl-python/` (not merged into `dashboard-django`) |
| DB access | Plain `psycopg2` + raw SQL / `execute_batch` (mirrors `next.jdbc` + HikariCP) |
| XML streaming | `xml.etree.ElementTree.iterparse`, with `elem.clear()` per top-level child to keep memory constant (mirrors StAX "no head retention") |
| Migrations | Ported `.sql` up/down files + small custom runner (`schema_migrations` table, applies pending files in order at startup) — mirrors Migratus without a heavyweight framework |
| CLI | `argparse`, same flags: `--file/-f`, `--batch-size/-b`, `--progress/--no-progress`, `--help` |
| Progress UI | `rich.progress`, same two-pass (count-then-render) design, stdout suppressed during rendering |
| Python deps | `pyproject.toml` + `uv` (diverges from `dashboard-django`'s plain `requirements.txt`, chosen for better dev ergonomics/lockfile) |
| Testing | pytest, `@pytest.mark.integration` for DB-dependent tests (replaces Leiningen `:unit`/`:integration` profiles) |
| Old app | `apps/etl-clojure` deleted once `apps/etl-python` reaches full parity |

## 4. Module Mapping

| Clojure | Python (`apps/etl-python/pulso/`) | Responsibility |
|---|---|---|
| `core.clj` | `cli.py` | CLI entry point, argument parsing |
| `config.clj` | `config.py` | DB config from env vars |
| `db.clj` | `db.py` | Connection pool, migrations runner, truncate-all |
| `etl.clj` | `etl.py` | Orchestrator: dispatch loop over parsed elements |
| `xml/parser.clj` | `xml_/parser.py` | Streaming element dispatch |
| `xml/transform.clj` | `xml_/transform.py` | XML element → dict |
| `xml/counter.clj` | `xml_/counter.py` | First-pass element counting (progress bar total) |
| `xml/io.clj` | `xml_/io.py` | Low-level XML reading helpers |
| `loader/batch.clj` | `loader/batch.py` | Generic batcher (`add`/`flush`) + returning-batcher for FK ids |
| `loader/lookups.clj` | `loader/lookups.py` | source/device/record_type/unit cache + upsert |
| `loader/records.clj` | `loader/records.py` | Health record + metadata loading |
| `loader/workouts.clj` | `loader/workouts.py` | Workout + events/stats/routes |
| `loader/correlations.clj` | `loader/correlations.py` | Correlation + nested records |
| `loader/activity.clj` | `loader/activity.py` | ActivitySummary loading |
| `loader/profile.clj` | `loader/profile.py` | User profile (`Me` element) |
| `progress.clj` | `progress.py` | Progress state tracking |
| `ui/terminal.clj` | `ui/terminal.py` | Live terminal renderer |

Batchers (`make-batcher`/`make-returning-batcher` closures returning maps of functions in Clojure) become small Python classes — `Batcher` and `ReturningBatcher` — with `add()`/`flush()`/`count()` methods. Same semantics, idiomatic Python instead of mimicking the atom/closure pattern.

## 5. XML Streaming

`xml.etree.ElementTree.iterparse` iterates the root's children. After each top-level child (`Record`, `Workout`, `Correlation`, `ActivitySummary`, `Me`, `ExportDate`) is dispatched and processed, the element is cleared and preceding siblings dropped, keeping memory flat regardless of file size — matching the current constant-memory guarantee for 1.5GB+ exports. The counter pass (for the progress bar total) does a cheap first `iterparse` pass counting tags only, same two-pass design as today.

## 6. Database Access & Migrations

- **Connection pooling:** `psycopg2.pool` (single-threaded ETL; pool kept for parity with the current pooled-datasource shape, not for concurrency).
- **Batch inserts:** `psycopg2.extras.execute_batch` for the plain batcher; `execute_values(..., fetch=True)` with `RETURNING id` for the returning-batcher (workouts/correlations needing child FKs).
- **Migrations:** `resources/migrations/*.sql` up/down files copied into `apps/etl-python/migrations/`. Custom runner tracks applied filenames in a `schema_migrations` table and applies pending `*.up.sql` files in filename order at startup.
- **Truncate-all + idempotent reload:** ported as-is — same table list and CASCADE order as `db/truncate-all!`.

## 7. Testing Strategy

pytest, mirroring the existing unit/integration split via markers instead of Leiningen profiles:

- `tests/unit/` — XML parser/transform tests, no DB (18 tests today)
- `tests/integration/` — loader/batch/ETL tests against a real `pulso_test` Postgres DB (19 tests today), marked `@pytest.mark.integration`
- Same Given/When/Then structure as the Clojure tests, ported 1:1 so each Clojure test has a traceable Python counterpart
- `conftest.py` ports `test-helpers.clj`: a `test_ds` fixture (pool connected to `pulso_test`), a session-scoped fixture that runs migrations once, and a function-scoped fixture that truncates tables and resets lookup/profile caches before each test
- Same `small-export.xml` fixture reused for unit tests
- Run via `pytest -m "not integration"` / `pytest -m integration` / `pytest` (all), matching the three test invocations documented in the current README

## 8. Project Structure

```
apps/etl-python/
├── pyproject.toml          # uv-managed; deps: psycopg2-binary, rich; dev: pytest
├── Dockerfile
├── migrations/*.up.sql, *.down.sql   # ported from etl-clojure
├── pulso/
│   ├── cli.py, config.py, db.py, etl.py, progress.py
│   ├── xml_/{parser,transform,counter,io}.py
│   ├── loader/{batch,lookups,records,workouts,correlations,activity,profile}.py
│   └── ui/terminal.py
└── tests/
    ├── unit/...
    ├── integration/...
    └── conftest.py
```

## 9. CI/CD & Repo-Level Changes

- Root `docker-compose.yml`: swap the `etl` service's build context/command from `apps/etl-clojure` to `apps/etl-python`.
- `.github/workflows/tests.yml`: swap Java/Leiningen setup steps for Python/`uv` setup steps; keep the same job structure (unit → integration → combined → build → artifact).
- `.github/workflows/docker.yml`: point at the new Dockerfile/build context.
- Root `README.md`: update Tech Stack, Quick Start, Testing, and Project Structure sections to describe the Python app; remove Clojure/Leiningen/Java references.

## 10. Cutover Criteria

`apps/etl-clojure` is deleted only after:

1. `apps/etl-python`'s full test suite passes (37 tests: 18 unit + 19 integration).
2. A manual run against a real Apple Health export produces matching row counts (per table) to a reference run of the Clojure app against the same file, in a fresh database.

At that point: delete `apps/etl-clojure/`, update root `README.md` and `docker-compose.yml`, and remove Clojure-specific CI steps.

## 11. Out of Scope

- No behavior changes beyond the language port — same schema, same CLI shape, same idempotent-truncate-and-reload strategy (v1, as noted in the current README's Scope section).
- No changes to `apps/dashboard-django`.
- GPX route files and ECG CSV data remain unsupported, matching current v1 scope.
