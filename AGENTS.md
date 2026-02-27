# Repository Guidelines

## Project Structure & Module Organization
- `src/pulso/`: application code.
- `src/pulso/xml/`: streaming XML parsing and transformation.
- `src/pulso/loader/`: ETL loaders (records, workouts, correlations, activity, profile, batch helpers).
- `test/unit/`: fast parser/transform tests with no DB dependency.
- `test/integration/`: PostgreSQL-backed ETL/loader tests.
- `resources/migrations/`: SQL migrations managed by Migratus.
- `test-resources/fixtures/`: test fixtures (for example `small-export.xml`).
- `data/`: local input XML files used during manual runs.

## Build, Test, and Development Commands
- `docker compose up --build`: build and run the full ETL stack (app + PostgreSQL).
- `docker compose up db`: start only PostgreSQL for local development.
- `lein migratus migrate`: apply DB migrations locally.
- `lein run -- --file data/exportar.xml`: execute ETL against an XML file.
- `lein check`: syntax and project checks (run before PR).
- `lein with-profile +unit test`: run unit tests only.
- `lein with-profile +integration test`: run integration tests only (requires `pulso_test`).
- `lein with-profile +unit,+integration test`: run full suite.
- `lein uberjar`: produce standalone artifact in `target/`.

## Coding Style & Naming Conventions
- Language: Clojure 1.12, Leiningen project layout.
- Indentation: standard Clojure formatting (2-space aligned forms, idiomatic threading/`let` alignment).
- Namespace/file naming: kebab-case with matching paths (for example `pulso.loader.batch` -> `src/pulso/loader/batch.clj`).
- Prefer descriptive, data-oriented function names ending with `!` for side effects (`process!`, `execute!`).

## Testing Guidelines
- Use `clojure.test` and keep tests isolated.
- Integration tests should use fixtures from `pulso.test-helpers` (`with-db-once`, `with-db`) and follow Given/When/Then blocks.
- Name tests by behavior (`execute-is-idempotent`, `process-record-with-metadata`).
- Ensure new features include unit tests and integration coverage when DB writes are involved.

## Commit & Pull Request Guidelines
- Follow existing commit style: short imperative subject lines (for example `Add ...`, `Fix ...`, `Implement ...`).
- Keep commits focused and logically scoped; avoid mixing refactors with behavior changes.
- PRs should include:
  - Clear summary of behavior changes.
  - Linked issue/task when applicable.
  - Test evidence (commands run and results).
  - Schema/migration notes for DB changes.
  - Screenshots only when UI/Metabase output changes are relevant.
