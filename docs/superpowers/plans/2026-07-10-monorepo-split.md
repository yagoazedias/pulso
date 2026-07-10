# Monorepo Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the `pulso` monorepo into two standalone, history-preserving GitHub repositories (`pulso-etl`, `pulso-dashboard`) under the new `pulso-health-tracker` organization, then transfer and archive the old monorepo.

**Architecture:** For each target repo: clone the monorepo (master only), run `git filter-repo` to keep only that app's files (rewriting root-level files into place, flattening the `apps/<x>/` prefix to repo root), rewrite the repo-local docs/CI/compose files to remove monorepo assumptions, then publish via `gh repo create --source=. --push`. Finish by transferring the old repo into the org and archiving it with a pointer README.

**Tech Stack:** `git-filter-repo` (installed via `uv tool install`), GitHub CLI (`gh`, already authenticated as `yagoazedias` with `repo` + `read:org` scopes).

## Global Constraints

- Org: `pulso-health-tracker` (already created, public, owned by `yagoazedias`).
- New repos: `pulso-health-tracker/pulso-etl`, `pulso-health-tracker/pulso-dashboard` — both public.
- History must be preserved via `git filter-repo` (not squashed) per `docs/superpowers/specs/2026-07-10-monorepo-split-design.md` §4.
- No behavior changes to either app — this is a repo/tooling reorganization only (spec §8).
- Old repo `yagoazedias/pulso` is transferred into the org and archived, not deleted (spec §6).
- Extraction scope is `master` only — other branches (`feature/etl-python-migration`, etc.) are not carried over; `master` already has the ETL Python migration merged (PR #6), so it is the correct source.
- The plan's Bash steps assume the working directory starts at `/home/yagoazedias/github/pulso` (the monorepo) unless a step explicitly `cd`s elsewhere.

---

### Task 1: Preflight — tooling and access checks

**Files:** none (no repo changes; this task only verifies the environment).

- [ ] **Step 1: Confirm the monorepo working tree is clean and on `master`**

Run:
```bash
cd /home/yagoazedias/github/pulso
git status --short
git branch --show-current
```
Expected: no output from `git status --short` (clean tree), and `master` printed by the second command. If the tree is dirty or on another branch, stop and resolve before continuing — do not run the extraction steps against uncommitted or wrong-branch state.

- [ ] **Step 2: Install `git-filter-repo`**

Run:
```bash
uv tool install git-filter-repo
git filter-repo --version
```
Expected: a version string is printed (e.g. `git-filter-repo <version>`), confirming the `git filter-repo` subcommand is on `PATH`.

- [ ] **Step 3: Verify `gh` can see the target organization**

Run:
```bash
gh api orgs/pulso-health-tracker --jq '.login'
```
Expected: `pulso-health-tracker`. If this errors with 404/403, the org slug is wrong or the authenticated account lacks access — stop and confirm the org name before continuing.

- [ ] **Step 4: Record the source commit**

Run:
```bash
git -C /home/yagoazedias/github/pulso rev-parse master
```
Expected: a 40-character SHA. Note it down (referred to as `<SOURCE_SHA>` below) — both extractions in Tasks 2 and 3 should clone from this exact commit so they're extracted from the same point in history.

---

### Task 2: Build and publish `pulso-etl`

**Files:**
- Create (in a scratch clone, not the monorepo): `docker-compose.yml`, `README.md`, `AGENTS.md` (rewritten), `.gitignore`, `.github/workflows/tests.yml`, `.github/workflows/docker.yml`
- Everything else in this repo comes from `git filter-repo` extraction of `apps/etl-python/`, `infra/postgres-init/`, and the ETL-specific root docs.

**Interfaces:** none — this is a standalone repo, no code dependencies on Task 3.

- [ ] **Step 1: Clone the monorepo (master only) into a scratch directory**

Run:
```bash
rm -rf /tmp/pulso-split/pulso-etl
mkdir -p /tmp/pulso-split
git clone --single-branch --branch master /home/yagoazedias/github/pulso /tmp/pulso-split/pulso-etl
cd /tmp/pulso-split/pulso-etl
git log --oneline -1
```
Expected: the last command prints the commit at `<SOURCE_SHA>` from Task 1 Step 4.

- [ ] **Step 2: Run `git filter-repo` to keep only ETL-relevant paths**

Run (from `/tmp/pulso-split/pulso-etl`):
```bash
git filter-repo --force \
  --path apps/etl-python/ --path-rename apps/etl-python/: \
  --path infra/postgres-init/ --path-rename infra/postgres-init/:postgres-init/ \
  --path PLAN.md \
  --path TEST_PLAN.md \
  --path AGENTS.md \
  --path .agents/plans/etl-performance-optimization.md \
  --path .agents/plans/etl-progress-bar.md \
  --path .agents/changelog/2026-02-27-etl-performance-optimization.md \
  --path .agents/changelog/2026-02-28-etl-progress-bar.md \
  --path docs/superpowers/specs/2026-07-09-etl-python-migration-design.md \
  --path docs/superpowers/plans/2026-07-09-etl-python-migration.md \
  --path examples/
```
Expected: `git filter-repo` prints a summary ending in `New size:` (smaller than the original) and removes the `origin` remote (this is expected safety behavior — confirm with `git remote -v`, which should print nothing).

- [ ] **Step 3: Verify the extracted file tree and history**

Run:
```bash
find . -maxdepth 1 -not -path './.git' | sort
ls pulso/ migrations/ postgres-init/ examples/
git log --oneline -- PLAN.md | tail -1
git log --oneline | wc -l
```
Expected:
- Top-level listing includes `pulso`, `migrations`, `tests`, `postgres-init`, `examples`, `pyproject.toml`, `uv.lock`, `Dockerfile`, `PLAN.md`, `TEST_PLAN.md`, `AGENTS.md`, `.agents`, `docs`, `.python-version` — no `apps/` or `infra/` directories (they were flattened/renamed away).
- `pulso/`, `migrations/`, `postgres-init/`, `examples/` each list files without error.
- `git log --oneline -- PLAN.md | tail -1` prints `0e3f8ec Initial commit: Pulso - Apple Health XML to PostgreSQL ETL` — this confirms history predating the monorepo reorganization was preserved.
- The commit count is fewer than 92 (the full monorepo's commit count) but greater than 21 (the `apps/etl-python`-only commit count from before this filter ran) — this confirms root-level file history (PLAN.md, AGENTS.md, etc.) was merged in alongside the app's own commits.

- [ ] **Step 4: Write the new root `docker-compose.yml`**

Write to `/tmp/pulso-split/pulso-etl/docker-compose.yml`:
```yaml
services:
  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: pulso
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./postgres-init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "postgres"]
      interval: 5s
      timeout: 3s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_HOST: db
      DB_PORT: "5432"
      DB_NAME: pulso
      DB_USER: postgres
      DB_PASSWORD: postgres
    volumes:
      - ./data:/data
    command: ["--file", "/data/apple_health_export/exportar.xml"]

  metabase:
    image: metabase/metabase:latest
    depends_on:
      db:
        condition: service_healthy
    environment:
      MB_DB_TYPE: postgres
      MB_DB_DBNAME: metabase
      MB_DB_PORT: "5432"
      MB_DB_USER: postgres
      MB_DB_PASS: postgres
      MB_DB_HOST: db
    ports:
      - "3000:3000"

volumes:
  pgdata:
```

- [ ] **Step 5: Rewrite `AGENTS.md` for the Python/uv toolchain**

The current `AGENTS.md` (carried over by the filter-repo extraction) still describes the old Clojure/Leiningen setup — it's stale since the ETL migration to Python. Replace `/tmp/pulso-split/pulso-etl/AGENTS.md` entirely with:
```markdown
# Repository Guidelines

## Project Structure & Module Organization
- `pulso/`: application code.
- `pulso/xml/`: streaming XML parsing and transformation.
- `pulso/loader/`: ETL loaders (records, workouts, correlations, activity, profile, batch helpers).
- `tests/unit/`: fast parser/transform tests with no DB dependency.
- `tests/integration/`: PostgreSQL-backed ETL/loader tests.
- `migrations/`: SQL migration files, applied by a custom runner at startup.
- `tests/fixtures/`: test fixtures (for example `small-export.xml`).
- `data/`: local input XML files used during manual runs.

## Build, Test, and Development Commands
- `docker compose up --build`: build and run the full stack (app + PostgreSQL + Metabase).
- `docker compose up db`: start only PostgreSQL for local development.
- `uv sync`: install dependencies.
- `uv run python -m pulso.cli --file data/exportar.xml`: execute ETL against an XML file (migrations run automatically on startup).
- `uv run pytest -m "not integration"`: run unit tests only.
- `uv run pytest -m integration`: run integration tests only (requires `pulso_test` database).
- `uv run pytest`: run the full suite.

## Coding Style & Naming Conventions
- Language: Python 3.12, managed with `uv`.
- Indentation: 4 spaces, standard PEP 8 formatting.
- Module naming: snake_case, matching paths (for example `pulso.loader.batch` -> `pulso/loader/batch.py`).
- Prefer descriptive, data-oriented function names for DB-writing operations (`process`, `load`).

## Testing Guidelines
- Use `pytest` and keep tests isolated.
- Integration tests use fixtures from `tests/integration/conftest.py` (`test_ds`, table truncation, cache resets between tests) and follow Given/When/Then structure.
- Name tests by behavior (`test_execute_is_idempotent`, `test_process_record_with_metadata`).
- Ensure new features include unit tests and integration coverage when DB writes are involved.

## Commit & Pull Request Guidelines
- Follow existing commit style: short imperative subject lines (for example `Add ...`, `Fix ...`, `Implement ...`).
- Keep commits focused and logically scoped; avoid mixing refactors with behavior changes.
- PRs should include:
  - Clear summary of behavior changes.
  - Linked issue/task when applicable.
  - Test evidence (commands run and results).
  - Schema/migration notes for DB changes.
```

- [ ] **Step 6: Write the new root `README.md`**

Write to `/tmp/pulso-split/pulso-etl/README.md`:
```markdown
# Pulso ETL

Apple Health XML to PostgreSQL ETL pipeline built in Python.

Pulso streams a 1.5GB+ Apple Health XML export and loads it into a normalized PostgreSQL relational model. It handles 3.4M+ health records, 1,800+ workouts, activity summaries, correlations, and user profile data spanning years of health tracking.

This repo is the ETL half of the Pulso project. The companion analytics dashboard lives at [pulso-health-tracker/pulso-dashboard](https://github.com/pulso-health-tracker/pulso-dashboard) — it reads from the same PostgreSQL database this ETL populates.

## Tech Stack

- **Python** 3.12, managed with **uv**
- **PostgreSQL** 17 (via Docker)
- **Metabase** — data visualization and analytics on top of PostgreSQL
- **xml.etree.ElementTree** — streaming (iterparse) XML parser
- **psycopg2** — database access with connection pooling
- Custom SQL-file migration runner — tracks applied migrations in a `schema_migrations` table
- **Docker** — multi-stage build for production deployment

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose
- An Apple Health XML export file (`exportar.xml`)

For local development without Docker:
- Python 3.12+
- [uv](https://docs.astral.sh/uv/)

## Quick Start

### With Docker (recommended)

```bash
# 1. Place your Apple Health export in the data/ directory
mkdir -p data && cp /path/to/exportar.xml data/

# 2. Start PostgreSQL and run the ETL
docker compose up --build
```

This will:
- Start a PostgreSQL 17 instance
- Build the Pulso Docker image
- Run database migrations
- Stream and load the entire XML export

### Local Development

```bash
# 1. Start PostgreSQL only
docker compose up db

# 2. Install dependencies
uv sync

# 3. Run the ETL (migrations run automatically on startup)
uv run python -m pulso.cli --file /path/to/exportar.xml
```

## Usage

```
Pulso - Apple Health XML to PostgreSQL ETL

Usage: pulso [options]

Options:
  -f, --file FILE          Path to Apple Health XML export file (required)
  -b, --batch-size SIZE    Batch insert size (default: 5000)
  -h, --help               Show help
```

## Configuration

Database connection is configured via environment variables, with sensible defaults for local development:

| Variable      | Default     | Description         |
|---------------|-------------|---------------------|
| `DB_HOST`     | `localhost` | PostgreSQL host     |
| `DB_PORT`     | `5432`      | PostgreSQL port     |
| `DB_NAME`     | `pulso`     | Database name        |
| `DB_USER`     | `postgres`  | Database user        |
| `DB_PASSWORD` | `postgres`  | Database password    |

## Testing

Pulso includes comprehensive unit and integration tests to verify XML parsing, data transformation, and end-to-end ETL correctness.

### Test Organization

Tests are organized into two groups, kept separate via pytest markers to enable focused testing:

- **Unit Tests** (`tests/unit/`) — Fast, database-independent tests for XML parsing and transformation
- **Integration Tests** (`tests/integration/`, marked `@pytest.mark.integration`) — Database-dependent tests for batch processing, caching, and ETL pipeline

### Test Dependencies

Integration tests require PostgreSQL and a test database:

```bash
# Start PostgreSQL
docker compose up db

# Create the test database
docker compose exec db psql -U postgres -c "CREATE DATABASE pulso_test;"
```

The test database name can be overridden with the `TEST_DB_NAME` environment variable.

### Running Tests

```bash
# Run only unit tests (fast, no database required)
uv run pytest -m "not integration"

# Run only integration tests (requires pulso_test database)
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest -m integration

# Run all tests (unit + integration)
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest

# Run a specific test file
uv run pytest tests/integration/loader/test_batch.py -v
```

### Test Infrastructure

`tests/integration/conftest.py` provides shared fixtures:

- `test_ds` — connection pool fixture that truncates all tables and resets lookup/profile caches before each test
- `count_rows` — counts rows in a table
- `select_all` — selects all rows from a table

## Continuous Integration & Deployment

Pulso uses GitHub Actions to automatically build, test, and verify code quality on every push and pull request.

### Workflows

**Build and Test** (`.github/workflows/tests.yml`)
- Runs on: Push to `master`, `main`, `develop` and all pull requests
- Steps: install `uv`, install dependencies, create the PostgreSQL test database, run unit tests, run integration tests, run the combined suite, comment results on the PR.

**Docker Build** (`.github/workflows/docker.yml`)
- Runs on: Push to `master`, `main` and pull requests
- Builds the ETL Docker image and validates `docker-compose.yml`.

### Status Badges

```markdown
![Build and Test](https://github.com/pulso-health-tracker/pulso-etl/actions/workflows/tests.yml/badge.svg)
![Docker Build](https://github.com/pulso-health-tracker/pulso-etl/actions/workflows/docker.yml/badge.svg)
```

## Architecture

Pulso uses a **single-pass streaming** approach to keep memory usage constant regardless of file size:

```
              XML File (1.5GB+)
                   |
        StAX Streaming Parser (lazy)
                   |
        Iterate root children (no head retention)
                   |
      +--------+---+---+-----------+--------------+
     <Me>  <Record>  <Workout>  <Correlation>  <ActivitySummary>
      |       |         |           |               |
  transform  transform  transform  transform    transform
      |       |         |           |               |
   INSERT   BATCH     INSERT+     INSERT+        BATCH
    (1)    (5000)     children    children       (1000)
```

**Key design decisions:**

- **Streaming XML** via `xml.etree.ElementTree.iterparse` — each processed top-level child is removed from the root element's children list, so only one element is retained in memory at a time
- **Lookup caching** — source, device, record type, and unit tables are cached in module-level dicts (~50-100 unique values). Cache misses trigger `INSERT ON CONFLICT ... RETURNING id`
- **Batch inserts** — records are accumulated in a buffer and flushed via `psycopg2.extras.execute_batch` every 5,000 rows
- **Idempotent loads** — all tables are truncated before each run (v1 strategy)

## Database Schema

The schema is normalized into lookup/dimension tables, fact tables, and child tables:

- **Lookup tables:** `source`, `device`, `record_type`, `unit`
- **User profile:** `user_profile`
- **Records:** `record`, `record_metadata` (3.4M+ rows)
- **Workouts:** `workout`, `workout_metadata`, `workout_event`, `workout_statistics`, `workout_route`
- **Correlations:** `correlation`, `correlation_metadata`, `correlation_record`
- **Activity:** `activity_summary`

Migrations are plain SQL files applied by a small custom runner (tracked in a `schema_migrations` table) and live in `migrations/`.

The [pulso-dashboard](https://github.com/pulso-health-tracker/pulso-dashboard) repo reads from this same schema — its Postgres connection env vars must point at the database this ETL populates.

## Project Structure

```
pulso-etl/
├── pyproject.toml
├── uv.lock
├── Dockerfile
├── docker-compose.yml
├── postgres-init/                  # Postgres bootstrap SQL (metabase DB, dashboard schema)
├── migrations/                     # SQL migration files (up only)
├── pulso/
│   ├── cli.py                      # CLI entry point
│   ├── config.py                   # DB + app config
│   ├── db.py                       # Pool, migrations runner, truncate
│   ├── etl.py                      # Orchestrator: parse -> transform -> load
│   ├── progress.py                 # Progress state tracking
│   ├── xml/
│   │   ├── parser.py               # Streaming XML parser with element dispatch
│   │   ├── counter.py              # Fast element counter for progress totals
│   │   └── transform.py            # XML elements -> Python dicts
│   ├── loader/
│   │   ├── batch.py                # Generic batch insert machinery
│   │   ├── lookups.py              # Lookup table cache & upsert
│   │   ├── records.py              # Record + metadata loading
│   │   ├── workouts.py             # Workout + events + stats + routes
│   │   ├── correlations.py         # Correlation + nested records
│   │   ├── activity.py             # ActivitySummary loading
│   │   └── profile.py              # User profile (Me element)
│   └── ui/
│       └── terminal.py             # Live terminal progress renderer
└── tests/
    ├── unit/                       # Fast, no-DB tests
    └── integration/                # DB-backed tests (@pytest.mark.integration)
        └── conftest.py             # Shared test infrastructure
```

## Analytics & Visualization with Metabase

Pulso includes **Metabase**, an open-source business intelligence tool that allows you to explore and visualize your Apple Health data without writing SQL.

### Running Metabase

When you run `docker compose up`, Metabase is automatically started and accessible at:

```
http://localhost:3000
```

**Initial setup:**
1. Navigate to http://localhost:3000
2. Create an admin account on the welcome screen
3. Connect to the Pulso database:
   - Database type: PostgreSQL
   - Host: `db`
   - Port: `5432`
   - Database: `pulso`
   - Username: `postgres`
   - Password: `postgres`

### Example Dashboards

**Active Energy Burned vs Goal Energy Burned**
![Active Energy Burned vs Goal](examples/Metabase-Active%20Energy%20Burned%20%20Vs%20Goal%20Energy%20Burned-15_02_2026%2C%2008_45_29.png)

**Weekdays when Workout Routes were Added**
![Workout Routes by Weekday](examples/Metabase-Weekdays%20when%20Workout%20Route%20were%20added-15_02_2026%2C%2008_47_36.png)

## Verification

After a successful load, you can verify the data:

```sql
-- Total health records
SELECT count(*) FROM record;

-- Workout count
SELECT count(*) FROM workout;

-- User profile
SELECT * FROM user_profile;

-- Records by type
SELECT rt.identifier, count(*)
FROM record r
JOIN record_type rt ON r.record_type_id = rt.id
GROUP BY rt.identifier
ORDER BY count(*) DESC;
```

## Scope

**v1** processes the main Apple Health XML export only. GPX route files and ECG CSV data are not yet supported.

## License

MIT
```

- [ ] **Step 7: Write the new `.gitignore`**

Write to `/tmp/pulso-split/pulso-etl/.gitignore`:
```
# User health data (SENSITIVE — never commit)
data/
*.xml
!tests/fixtures/*.xml
*.zip
*.gpx
*.csv

# Python
__pycache__/
*.pyc
*.pyo
*.egg-info/
dist/
build/
.venv/
.pytest_cache/

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
```

- [ ] **Step 8: Write the trimmed `.github/workflows/tests.yml`**

Write to `/tmp/pulso-split/pulso-etl/.github/workflows/tests.yml`:
```yaml
name: Build and Test

on:
  push:
    branches: [ master, main, develop ]
  pull_request:
    branches: [ master, main, develop ]

permissions:
  pull-requests: write

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:17-alpine
        env:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: pulso
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Install uv
        uses: astral-sh/setup-uv@v3
        with:
          version: "latest"

      - name: Set up Python
        run: uv python install

      - name: Install dependencies
        run: uv sync

      - name: Create test database
        env:
          PGHOST: localhost
          PGUSER: postgres
          PGPASSWORD: postgres
        run: |
          psql -c "CREATE DATABASE pulso_test;"

      - name: Run unit tests
        run: uv run pytest -m "not integration" -v

      - name: Run integration tests
        env:
          DB_HOST: localhost
          DB_USER: postgres
          DB_PASSWORD: postgres
        run: uv run pytest -m integration -v

      - name: Run all tests (unit + integration)
        env:
          DB_HOST: localhost
          DB_USER: postgres
          DB_PASSWORD: postgres
        run: uv run pytest -v

      - name: Comment test results
        if: always() && github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          script: |
            const comment = '## Build and Test Results\n\n' +
              '- Unit tests: Passed\n' +
              '- Integration tests: Passed\n';
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: comment
            });
```

- [ ] **Step 9: Write the trimmed `.github/workflows/docker.yml`**

Write to `/tmp/pulso-split/pulso-etl/.github/workflows/docker.yml`:
```yaml
name: Docker Build

on:
  push:
    branches: [ master, main ]
  pull_request:
    branches: [ master, main ]

jobs:
  docker-etl:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build ETL Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: false
          tags: pulso-etl:latest
          cache-from: type=gha,scope=etl
          cache-to: type=gha,mode=max,scope=etl

  docker-compose:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Validate docker-compose.yml
        run: docker compose config -q

      - name: Build all services
        run: docker compose build

      - name: Verify services
        run: docker compose ps -a || true
```

- [ ] **Step 10: Commit the rewritten files**

Run (from `/tmp/pulso-split/pulso-etl`):
```bash
git add docker-compose.yml README.md AGENTS.md .gitignore .github/workflows/tests.yml .github/workflows/docker.yml
git status --short
```
Expected: all six paths listed as staged (`A`/`M`). Then commit:
```bash
git commit -m "$(cat <<'EOF'
chore: adapt repo for standalone pulso-etl split

Rewrites docker-compose.yml, README.md, AGENTS.md, .gitignore, and CI
workflows to drop monorepo-specific paths (apps/etl-python/ ->
repo root, infra/postgres-init/ -> postgres-init/) now that this repo
only contains the ETL app.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
git log --oneline -3
```
Expected: the new commit appears at the top of `git log --oneline -3`, on top of the filtered history (the second line should still be an ETL-related commit, not a dashboard one).

- [ ] **Step 11: Create the GitHub repo and push**

Run (from `/tmp/pulso-split/pulso-etl`):
```bash
gh repo create pulso-health-tracker/pulso-etl --public --source=. --remote=origin --push
```
Expected: output ending with `✓ Created repository pulso-health-tracker/pulso-etl on GitHub` and a push summary. Verify:
```bash
gh repo view pulso-health-tracker/pulso-etl --json name,visibility,defaultBranchRef --jq '{name, visibility, branch: .defaultBranchRef.name}'
```
Expected: `{"name":"pulso-etl","visibility":"PUBLIC","branch":"master"}`.

- [ ] **Step 12: Verify CI is green**

Run:
```bash
sleep 15
gh run list --repo pulso-health-tracker/pulso-etl --limit 5
```
Expected: two workflow runs listed (`Build and Test`, `Docker Build`) triggered by the push. If status is `in_progress`, wait and re-check:
```bash
gh run watch --repo pulso-health-tracker/pulso-etl $(gh run list --repo pulso-health-tracker/pulso-etl --limit 1 --json databaseId --jq '.[0].databaseId') --exit-status
```
Expected: exits 0 with `✓` next to each job (`test`, `docker-etl`, `docker-compose`). Repeat for the second workflow run if `gh run watch` only tracked one. If either run fails, open the failing job's log with `gh run view --repo pulso-health-tracker/pulso-etl --log-failed <run-id>` and fix the underlying cause (most likely a leftover `apps/etl-python/` path reference) before proceeding to Task 3.

---

### Task 3: Build and publish `pulso-dashboard`

**Files:**
- Create (in a scratch clone, not the monorepo): `docker-compose.yml`, `README.md`, `.gitignore`, `.github/workflows/tests.yml`, `.github/workflows/docker.yml`
- Everything else in this repo comes from `git filter-repo` extraction of `apps/dashboard-django/` and the dashboard-specific root/docs files.

**Interfaces:** none — standalone repo, independent of Task 2's outcome (only cross-references it in prose/README links).

- [ ] **Step 1: Clone the monorepo (master only) into a scratch directory**

Run:
```bash
rm -rf /tmp/pulso-split/pulso-dashboard
git clone --single-branch --branch master /home/yagoazedias/github/pulso /tmp/pulso-split/pulso-dashboard
cd /tmp/pulso-split/pulso-dashboard
git log --oneline -1
```
Expected: prints the same `<SOURCE_SHA>` commit as Task 2 Step 1.

- [ ] **Step 2: Run `git filter-repo` to keep only dashboard-relevant paths**

Run (from `/tmp/pulso-split/pulso-dashboard`):
```bash
git filter-repo --force \
  --path apps/dashboard-django/ --path-rename apps/dashboard-django/: \
  --path docs/superpowers/specs/2026-03-15-phase2-django-foundation-design.md \
  --path docs/superpowers/specs/2026-03-15-phase3-metrics-backend-design.md \
  --path docs/superpowers/specs/2026-04-04-frontend-tests-design.md \
  --path docs/superpowers/plans/2026-03-15-phase2-django-foundation.md \
  --path docs/superpowers/plans/2026-03-15-phase3-metrics-backend.md \
  --path docs/superpowers/plans/2026-04-04-frontend-tests.md \
  --path docs/specs/dashboard-v1.md \
  --path DASHBOARD_MONOREPO_PLAN.md \
  --path .agents/plans/DASHBOARD_MONOREPO_PLAN.md \
  --path ".agents/plans/images/Screenshot 2026-02-27 201514.png"
```
Expected: `git filter-repo` prints a summary ending in `New size:` and removes the `origin` remote (`git remote -v` prints nothing).

- [ ] **Step 3: Verify the extracted file tree and history**

Run:
```bash
find . -maxdepth 1 -not -path './.git' | sort
ls apps/analytics frontend/src
git log --oneline -- apps/analytics/models.py | tail -1
git log --oneline | wc -l
```
Expected:
- Top-level listing includes `apps`, `frontend`, `dashboard_project`, `manage.py`, `requirements.txt`, `package.json`, `package-lock.json`, `vite.config.js`, `Dockerfile`, `conftest.py`, `pytest.ini`, `.dockerignore`, `.gitignore`, `docs`, `DASHBOARD_MONOREPO_PLAN.md`, `.agents` — no top-level `apps/dashboard-django/` (flattened).
- `apps/analytics` and `frontend/src` list files without error.
- `git log --oneline -- apps/analytics/models.py | tail -1` prints a commit at or after `2db41ad Bootstrap Django project with analytics app and PostgreSQL schema config` (that's the earliest commit touching the Django app).
- Commit count is smaller than 92 (full monorepo) but includes the dashboard's app-history plus the merged-in root docs.

- [ ] **Step 4: Write the new root `docker-compose.yml`**

Write to `/tmp/pulso-split/pulso-dashboard/docker-compose.yml`:
```yaml
services:
  dashboard:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      DB_HOST: ${DB_HOST:-host.docker.internal}
      DB_PORT: ${DB_PORT:-5432}
      DB_NAME: ${DB_NAME:-pulso}
      DB_USER: ${DB_USER:-postgres}
      DB_PASSWORD: ${DB_PASSWORD:-postgres}
      DJANGO_SETTINGS_MODULE: dashboard_project.settings
      SECRET_KEY: ${SECRET_KEY:-change-me-in-production}
      DEBUG: ${DEBUG:-false}
    ports:
      - "8000:8000"
    extra_hosts:
      - "host.docker.internal:host-gateway"
```
Note: `extra_hosts` makes `host.docker.internal` resolve on Linux too (it's automatic on Docker Desktop for Mac/Windows), so the default `DB_HOST` reaches a Postgres started by `pulso-etl`'s compose on the host's port 5432.

- [ ] **Step 5: Write the new root `README.md`**

Write to `/tmp/pulso-split/pulso-dashboard/README.md`:
```markdown
# Pulso Dashboard

Django-based analytics dashboard for [Pulso](https://github.com/pulso-health-tracker/pulso-etl) — renders health metrics (active energy vs goal, workout volume, top record types) from the PostgreSQL database that the [pulso-etl](https://github.com/pulso-health-tracker/pulso-etl) pipeline populates.

This repo only contains the read-side dashboard. It does **not** run its own Postgres for real data — it expects `pulso-etl`'s database to already be running and migrated. See Prerequisites below.

## Tech Stack

- **Django** 4.2, **psycopg2**, **django-vite**, **gunicorn**, **whitenoise**
- **React** 18 + **Chart.js** (via `react-chartjs-2`), bundled with **Vite**
- **pytest** + **pytest-django** for backend tests, **vitest** + **testing-library** for frontend tests

## Prerequisites

- [pulso-etl](https://github.com/pulso-health-tracker/pulso-etl) running locally (`docker compose up db` at minimum) — this dashboard reads from its `pulso` database and `dashboard` schema.
- Docker and Docker Compose, or Python 3.12+ and Node 20+ for local (non-Docker) development.

## Quick Start

### With Docker (recommended)

```bash
# 1. Make sure pulso-etl's docker compose is already running (provides Postgres on localhost:5432)
# 2. Build and start the dashboard, pointing at that Postgres:
DB_HOST=host.docker.internal docker compose up --build
```

Dashboard is then available at http://localhost:8000.

### Local Development (no Docker)

```bash
# 1. Install backend dependencies
pip install -r requirements.txt

# 2. Install frontend dependencies
npm ci

# 3. In one terminal, run the Vite dev server
npm run dev

# 4. In another terminal, run Django (pointing at pulso-etl's Postgres)
DB_HOST=localhost DEBUG=true python manage.py runserver
```

## Configuration

| Variable                 | Default                          | Description                                   |
|---------------------------|-----------------------------------|------------------------------------------------|
| `DB_HOST`                 | `localhost`                      | PostgreSQL host (point this at `pulso-etl`'s DB) |
| `DB_PORT`                 | `5432`                           | PostgreSQL port                                 |
| `DB_NAME`                 | `pulso`                          | Database name                                   |
| `DB_USER`                 | `postgres`                       | Database user                                   |
| `DB_PASSWORD`              | `postgres`                       | Database password                               |
| `SECRET_KEY`               | `django-insecure-dev-only-...`   | Django secret key — set a real value in production |
| `DEBUG`                    | `true`                           | Django debug mode                               |
| `ALLOWED_HOSTS`            | `localhost,127.0.0.1`            | Comma-separated allowed hosts                   |

Django reads from the Postgres `search_path` `dashboard,public` — `dashboard` (its own schema) falls back to `public` (where `pulso-etl`'s tables live). Both schemas are created by `pulso-etl`'s `postgres-init/` scripts.

## API Endpoints

- `GET /` — dashboard HTML page
- `GET /api/metrics/energy-vs-goal` — daily active energy vs goal, last 90 days
- `GET /api/metrics/workout-volume` — weekly workout count/duration/energy
- `GET /api/metrics/top-record-types` — top 5 record types by volume, weekly

## Testing

```bash
# Backend tests (pytest + pytest-django)
pip install -r requirements.txt
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres pytest

# Frontend tests (vitest)
npm ci
npm test
```

## Continuous Integration

**Build and Test** (`.github/workflows/tests.yml`) runs frontend tests (`npm ci && npm test`) on every push and pull request.

**Docker Build** (`.github/workflows/docker.yml`) builds the Dashboard Docker image and validates `docker-compose.yml`.

### Status Badges

```markdown
![Build and Test](https://github.com/pulso-health-tracker/pulso-dashboard/actions/workflows/tests.yml/badge.svg)
![Docker Build](https://github.com/pulso-health-tracker/pulso-dashboard/actions/workflows/docker.yml/badge.svg)
```

## Project Structure

```
pulso-dashboard/
├── manage.py
├── requirements.txt
├── package.json
├── vite.config.js
├── Dockerfile
├── docker-compose.yml
├── conftest.py
├── pytest.ini
├── dashboard_project/          # Django settings, urls, wsgi/asgi
├── apps/
│   └── analytics/
│       ├── models.py            # Unmanaged models over pulso-etl's tables
│       ├── repositories.py      # Query layer for the 3 metrics
│       ├── views.py             # index + 3 JSON metric endpoints
│       ├── urls.py
│       ├── templates/analytics/index.html
│       └── tests/
└── frontend/
    └── src/
        ├── components/          # Dashboard, ChartCard, StatCard, 3 chart components, DateRangeSelector
        ├── hooks/useChartData.js
        └── main.jsx
```
```

- [ ] **Step 6: Write the new `.gitignore`**

Write to `/tmp/pulso-split/pulso-dashboard/.gitignore`:
```
node_modules/
frontend/static/
staticfiles/
__pycache__/
*.pyc
.venv/
db.sqlite3

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
```

- [ ] **Step 7: Write the trimmed `.github/workflows/tests.yml`**

Write to `/tmp/pulso-split/pulso-dashboard/.github/workflows/tests.yml`:
```yaml
name: Build and Test

on:
  push:
    branches: [ master, main, develop ]
  pull_request:
    branches: [ master, main, develop ]

jobs:
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
          cache-dependency-path: package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Run frontend tests
        run: npm test
```
Note: this matches the current monorepo's CI exactly — there is no Django/pytest job in `tests.yml` today (only the `frontend-test` job covers this app), so none is added here. That gap is pre-existing and out of scope for this split (spec §8: no behavior changes).

- [ ] **Step 8: Write the trimmed `.github/workflows/docker.yml`**

Write to `/tmp/pulso-split/pulso-dashboard/.github/workflows/docker.yml`:
```yaml
name: Docker Build

on:
  push:
    branches: [ master, main ]
  pull_request:
    branches: [ master, main ]

jobs:
  docker-dashboard:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build Dashboard Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: false
          tags: pulso-dashboard:latest
          cache-from: type=gha,scope=dashboard
          cache-to: type=gha,mode=max,scope=dashboard

  docker-compose:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Validate docker-compose.yml
        run: docker compose config -q
```

- [ ] **Step 9: Commit the rewritten files**

Run (from `/tmp/pulso-split/pulso-dashboard`):
```bash
git add docker-compose.yml README.md .gitignore .github/workflows/tests.yml .github/workflows/docker.yml
git status --short
```
Expected: all five paths staged. Then commit:
```bash
git commit -m "$(cat <<'EOF'
chore: adapt repo for standalone pulso-dashboard split

Rewrites docker-compose.yml, README.md, .gitignore, and CI workflows
to drop monorepo-specific paths (apps/dashboard-django/ -> repo root)
and to connect to an external Postgres (pulso-etl's) via env vars
instead of a bundled db service.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
git log --oneline -3
```
Expected: the new commit on top; the commit below it should be dashboard-related, not ETL-related.

- [ ] **Step 10: Create the GitHub repo and push**

Run (from `/tmp/pulso-split/pulso-dashboard`):
```bash
gh repo create pulso-health-tracker/pulso-dashboard --public --source=. --remote=origin --push
```
Expected: output ending with `✓ Created repository pulso-health-tracker/pulso-dashboard on GitHub`. Verify:
```bash
gh repo view pulso-health-tracker/pulso-dashboard --json name,visibility,defaultBranchRef --jq '{name, visibility, branch: .defaultBranchRef.name}'
```
Expected: `{"name":"pulso-dashboard","visibility":"PUBLIC","branch":"master"}`.

- [ ] **Step 11: Verify CI is green**

Run:
```bash
sleep 15
gh run list --repo pulso-health-tracker/pulso-dashboard --limit 5
```
Expected: two workflow runs listed. Watch the most recent one to completion:
```bash
gh run watch --repo pulso-health-tracker/pulso-dashboard $(gh run list --repo pulso-health-tracker/pulso-dashboard --limit 1 --json databaseId --jq '.[0].databaseId') --exit-status
```
Expected: exits 0. Repeat for the other run if needed. If a run fails, inspect with `gh run view --repo pulso-health-tracker/pulso-dashboard --log-failed <run-id>` and fix (most likely a leftover `apps/dashboard-django/` path reference) before proceeding to Task 4.

---

### Task 4: Transfer and archive the old monorepo

**Files:** `README.md` in `yagoazedias/pulso` (rewritten in place, via a local clone).

- [ ] **Step 1: Transfer `yagoazedias/pulso` into the org**

Run:
```bash
gh repo transfer yagoazedias/pulso pulso-health-tracker
```
Expected: a confirmation prompt (`gh` will ask you to type the repo name to confirm) — type `pulso` to confirm. On success, output confirms the transfer. Since `yagoazedias` is an owner of `pulso-health-tracker`, the transfer completes immediately without needing a separate acceptance step.

- [ ] **Step 2: Verify the transfer**

Run:
```bash
gh repo view pulso-health-tracker/pulso --json owner,isArchived --jq '{owner: .owner.login, archived: .isArchived}'
```
Expected: `{"owner":"pulso-health-tracker","archived":false}`.

- [ ] **Step 3: Clone the transferred repo and replace its README with a pointer notice**

Run:
```bash
rm -rf /tmp/pulso-split/pulso-old
git clone git@github.com:pulso-health-tracker/pulso.git /tmp/pulso-split/pulso-old
cd /tmp/pulso-split/pulso-old
```

Write to `/tmp/pulso-split/pulso-old/README.md` (replacing the entire existing file):
```markdown
# Pulso (archived)

This monorepo has been split into two standalone repositories:

- **ETL pipeline:** [pulso-health-tracker/pulso-etl](https://github.com/pulso-health-tracker/pulso-etl)
- **Analytics dashboard:** [pulso-health-tracker/pulso-dashboard](https://github.com/pulso-health-tracker/pulso-dashboard)

This repository is kept archived for historical reference (commit history, past issues/PRs). No further changes will be made here — please open issues and PRs against the new repos instead.
```

- [ ] **Step 4: Commit and push the README change**

Run (from `/tmp/pulso-split/pulso-old`):
```bash
git add README.md
git commit -m "$(cat <<'EOF'
docs: point to the split pulso-etl and pulso-dashboard repos

This monorepo has been split; see pulso-health-tracker/pulso-etl and
pulso-health-tracker/pulso-dashboard for active development.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
git push
```
Expected: push succeeds against `pulso-health-tracker/pulso`'s `master` branch.

- [ ] **Step 5: Archive the repo**

Run:
```bash
gh repo archive pulso-health-tracker/pulso --yes
```
Expected: confirmation output that the repo is archived. Verify:
```bash
gh repo view pulso-health-tracker/pulso --json isArchived --jq '.isArchived'
```
Expected: `true`.

- [ ] **Step 6: Update the local monorepo's remote (optional but recommended)**

The local working copy at `/home/yagoazedias/github/pulso` still points `origin` at `yagoazedias/pulso`, which now redirects to `pulso-health-tracker/pulso` (GitHub preserves a redirect after transfer) but is archived (read-only). Run:
```bash
cd /home/yagoazedias/github/pulso
git remote set-url origin git@github.com:pulso-health-tracker/pulso.git
git remote -v
```
Expected: both `fetch` and `push` URLs now show `pulso-health-tracker/pulso`. Note: pushes will fail since the repo is archived — this local copy becomes read-only/historical too. Future work happens by cloning `pulso-health-tracker/pulso-etl` and `pulso-health-tracker/pulso-dashboard` fresh.

---

## Post-Plan Verification

- [ ] `https://github.com/pulso-health-tracker/pulso-etl` — public, CI green, README renders correctly, `postgres-init/`, `pulso/`, `migrations/`, `tests/`, `examples/` all present.
- [ ] `https://github.com/pulso-health-tracker/pulso-dashboard` — public, CI green, README renders correctly, `apps/analytics/`, `frontend/src/` all present.
- [ ] `https://github.com/pulso-health-tracker/pulso` — archived, README shows the pointer notice.
- [ ] `git log --oneline -- PLAN.md` in the `pulso-etl` clone still shows `0e3f8ec Initial commit: Pulso - Apple Health XML to PostgreSQL ETL` as its oldest entry (history preserved end-to-end).
