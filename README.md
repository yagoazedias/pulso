# Pulso

Apple Health XML to PostgreSQL ETL pipeline built in Python, with Django-based analytics dashboard (in development).

Pulso streams a 1.5GB+ Apple Health XML export and loads it into a normalized PostgreSQL relational model. It handles 3.4M+ health records, 1,800+ workouts, activity summaries, correlations, and user profile data spanning years of health tracking. The Django dashboard provides real-time analytics on top of this normalized data.

## Monorepo Structure

This is a monorepo containing both the ETL pipeline and analytics dashboard:

- **`apps/etl-python/`** — Pulso ETL (Python + uv)
- **`apps/dashboard-django/`** — Django analytics dashboard (in development for Phase 2+)
- **`infra/`** — Shared infrastructure (Docker, compose configs)
- **`docs/`** — Project documentation
- **`scripts/`** — Shared utility scripts

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
- Build the Pulso uberjar
- Run database migrations
- Stream and load the entire XML export

### Local Development

```bash
# 1. Start PostgreSQL only
docker compose up db

# 2. Navigate to the ETL app and install dependencies
cd apps/etl-python
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
| `DB_NAME`     | `pulso`     | Database name       |
| `DB_USER`     | `postgres`  | Database user       |
| `DB_PASSWORD` | `postgres`  | Database password   |

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
cd apps/etl-python

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

### Test Results

Current test suite: **49 tests** (30 unit + 19 integration), all passing.

## Continuous Integration & Deployment

Pulso uses GitHub Actions to automatically build, test, and verify code quality on every push and pull request.

### Workflows

**Build and Test** (`.github/workflows/tests.yml`)
- Runs on: Push to `master`, `main`, `develop` and all pull requests
- Steps:
  1. Checkout code
  2. Set up Java 21
  3. Create PostgreSQL test database
  4. Run syntax checks with `lein check`
  5. Run unit tests (`lein with-profile +unit test`)
  6. Run integration tests (`lein with-profile +integration test`)
  7. Run combined test suite (`lein with-profile +unit,+integration test`)
  8. Build uberjar
  9. Upload build artifacts
  10. Comment test results on pull requests

**Docker Build** (`.github/workflows/docker.yml`)
- Runs on: Push to `master`, `main` and pull requests when Docker files change
- Steps:
  1. Build Docker image with caching
  2. Validate `docker-compose.yml`
  3. Build all Docker services
  4. Verify service configuration

### Status Badges

Add this to your README to display workflow status:

```markdown
![Build and Test](https://github.com/YOUR_USERNAME/pulso/actions/workflows/tests.yml/badge.svg)
![Docker Build](https://github.com/YOUR_USERNAME/pulso/actions/workflows/docker.yml/badge.svg)
```

### Local Workflow Testing

To test workflows locally before pushing, you can use [act](https://github.com/nektos/act):

```bash
# Install act
brew install act

# Run the tests workflow locally
act -j test

# Run the docker workflow locally
act -j docker
```

## Architecture

Pulso uses a **single-pass streaming** approach to keep memory usage constant regardless of file size (runs with `-Xmx512m`):

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
- **Lookup caching** — source, device, record type, and unit tables are cached in atoms (~50-100 unique values). Cache misses trigger `INSERT ON CONFLICT ... RETURNING id`
- **Batch inserts** — records are accumulated in a buffer and flushed via `next.jdbc/execute-batch!` every 5,000 rows
- **Idempotent loads** — all tables are truncated before each run (v1 strategy)

## Database Schema

The schema is normalized into lookup/dimension tables, fact tables, and child tables:

- **Lookup tables:** `source`, `device`, `record_type`, `unit`
- **User profile:** `user_profile`
- **Records:** `record`, `record_metadata` (3.4M+ rows)
- **Workouts:** `workout`, `workout_metadata`, `workout_event`, `workout_statistics`, `workout_route`
- **Correlations:** `correlation`, `correlation_metadata`, `correlation_record`
- **Activity:** `activity_summary`

Migrations are plain SQL files applied by a small custom runner (tracked in a `schema_migrations` table) and live in `apps/etl-python/migrations/`.

## Project Structure

```
pulso/
├── apps/
│   ├── etl-python/                     # Pulso ETL pipeline (Python)
│   │   ├── pyproject.toml
│   │   ├── Dockerfile
│   │   ├── migrations/                 # SQL migration files (up only)
│   │   ├── pulso/
│   │   │   ├── cli.py                  # CLI entry point
│   │   │   ├── config.py               # DB + app config
│   │   │   ├── db.py                   # Pool, migrations runner, truncate
│   │   │   ├── etl.py                  # Orchestrator: parse -> transform -> load
│   │   │   ├── progress.py             # Progress state tracking
│   │   │   ├── xml/
│   │   │   │   ├── parser.py           # Streaming XML parser with element dispatch
│   │   │   │   ├── counter.py          # Fast element counter for progress totals
│   │   │   │   └── transform.py        # XML elements -> Python dicts
│   │   │   ├── loader/
│   │   │   │   ├── batch.py            # Generic batch insert machinery
│   │   │   │   ├── lookups.py          # Lookup table cache & upsert
│   │   │   │   ├── records.py          # Record + metadata loading
│   │   │   │   ├── workouts.py         # Workout + events + stats + routes
│   │   │   │   ├── correlations.py     # Correlation + nested records
│   │   │   │   ├── activity.py         # ActivitySummary loading
│   │   │   │   └── profile.py          # User profile (Me element)
│   │   │   └── ui/
│   │   │       └── terminal.py         # Live terminal progress renderer
│   │   └── tests/
│   │       ├── unit/                   # Fast, no-DB tests
│   │       └── integration/            # DB-backed tests (@pytest.mark.integration)
│   │           └── conftest.py         # Shared test infrastructure
│   └── dashboard-django/               # Django analytics dashboard (coming in Phase 2)
│
├── infra/
│   ├── docker/
│   └── compose/
│
├── docs/
│   ├── specs/
│   │   └── dashboard-v1.md             # Dashboard V1 specification
│   └── ...
│
├── scripts/
│   └── (shared utility scripts)
│
├── docker-compose.yml                  # Root compose file
├── README.md
└── ...
```

## Analytics & Visualization with Metabase

Pulso includes **Metabase**, an open-source business intelligence tool that allows you to explore and visualize your Apple Health data without writing SQL.

### What is Metabase?

Metabase provides an intuitive web interface to:
- **Create interactive dashboards** — visualize trends, patterns, and metrics
- **Explore data visually** — build queries with a point-and-click interface
- **Ask questions** — compose complex queries without SQL knowledge
- **Share insights** — create shareable reports and dashboards

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

Here are some examples of what you can visualize with your Apple Health data:

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
