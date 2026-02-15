# Pulso

Apple Health XML to PostgreSQL ETL pipeline built in Clojure.

Pulso streams a 1.5GB+ Apple Health XML export and loads it into a normalized PostgreSQL relational model. It handles 3.4M+ health records, 1,800+ workouts, activity summaries, correlations, and user profile data spanning years of health tracking.

## Tech Stack

- **Clojure** 1.12 with Leiningen
- **PostgreSQL** 17 (via Docker)
- **Metabase** — data visualization and analytics on top of PostgreSQL
- **clojure.data.xml** — StAX-based streaming XML parser
- **next.jdbc** + **HikariCP** — database access with connection pooling
- **Migratus** — database migrations
- **Docker** — multi-stage build for production deployment

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose
- An Apple Health XML export file (`exportar.xml`)

For local development without Docker:
- Java 21+
- [Leiningen](https://leiningen.org/)

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

# 2. Run migrations
lein migratus migrate

# 3. Run the ETL
lein run -- --file /path/to/exportar.xml
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

Tests are organized into two profiles, kept separate to enable focused testing:

- **Unit Tests** (`test/unit/`) — Fast, database-independent tests for XML parsing and transformation
  - `pulso.xml.parser-test` — Streaming XML parser with element dispatch
  - `pulso.xml.transform-test` — XML element transformation to Clojure maps

- **Integration Tests** (`test/integration/`) — Database-dependent tests for batch processing, caching, and ETL pipeline
  - `pulso.loader.batch-test` — Batch insert machinery and auto-flush behavior
  - `pulso.loader.lookups-test` — Lookup table caching (source, device, record type, unit)
  - `pulso.loader.profile-test` — User profile insertion
  - `pulso.loader.records-test` — Health records with/without metadata, batching
  - `pulso.loader.workouts-test` — Workouts with child records (metadata, events, statistics, routes)
  - `pulso.loader.correlations-test` — Correlations with nested records
  - `pulso.loader.activity-test` — Activity summary insertion
  - `pulso.etl-test` — End-to-end ETL pipeline execution and idempotency

### Test Dependencies

Integration tests require PostgreSQL and a test database:

```bash
# Start PostgreSQL
docker compose up db

# Create the test database
docker compose exec db psql -U postgres -c "CREATE DATABASE pulso_test;"
```

The test database name can be overridden with `TEST_DB_NAME` environment variable.

### Running Tests

```bash
# Run only unit tests (fast, no database required)
lein with-profile +unit test

# Run only integration tests (requires pulso_test database)
lein with-profile +integration test

# Run all tests (unit + integration)
lein with-profile +unit,+integration test

# Run a specific test namespace
lein with-profile +integration test pulso.loader.batch-test

# Run a specific test
lein with-profile +integration test pulso.loader.batch-test/batcher-flushes-at-batch-size
```

### Test Structure & Patterns

All integration tests follow a **Given-When-Then BDD pattern** for clarity:

```clojure
(deftest process-record-with-metadata
  (testing "Given record batchers and Record XML element with metadata"
    (let [batchers (records/make-batchers @test-ds 10)
          element (apply xml/element :Record {...}
                    [(xml/element :MetadataEntry {...})])]

      (testing "When process! is called"
        (records/process! @test-ds batchers element))

      (testing "Then 1 record inserted immediately"
        (is (= 1 (count-rows @test-ds "record")))))))
```

**Key testing principles:**

- **Test isolation** — Each test is independent; the `:each` fixture truncates tables and resets caches before every test
- **Database transactions** — Tests use the `pulso_test` database to avoid affecting production data
- **Lazy datasource** — The test datasource is initialized lazily on first use
- **Given-When-Then pattern** — Each test clearly shows setup, action, and assertions

### Test Infrastructure

The `pulso.test-helpers` namespace provides shared utilities:

- `test-ds` — HikariCP datasource connected to `pulso_test` database
- `with-db-once` — `:once` fixture that runs migrations once per test run
- `with-db` — `:each` fixture that:
  - Truncates all tables
  - Resets lookup caches (`pulso.loader.lookups/reset-caches!`)
  - Resets export-date atom (`pulso.loader.profile/reset-state!`)
- `count-rows` — Counts rows in a table
- `select-all` — Selects all rows from a table

### Test Results

Current test suite: **37 tests, 176 assertions**

```
Unit Tests:        18 tests
Integration Tests: 19 tests
---
Total:             37 tests
Result:            ✓ All passing
```

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

- **Streaming XML** via `clojure.data.xml/parse` (StAX) — children of the root element are lazy sequences, so only one element is in memory at a time
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

Migrations are managed by Migratus and live in `resources/migrations/`.

## Project Structure

```
pulso/
├── project.clj
├── Dockerfile
├── docker-compose.yml
├── resources/migrations/       # SQL migration files (up/down)
├── src/pulso/
│   ├── core.clj                # CLI entry point
│   ├── config.clj              # DB + app config
│   ├── db.clj                  # Datasource, migrations, truncate
│   ├── etl.clj                 # Orchestrator: parse -> transform -> load
│   ├── xml/
│   │   ├── parser.clj          # Streaming XML parser with element dispatch
│   │   └── transform.clj       # XML elements -> Clojure maps
│   └── loader/
│       ├── batch.clj           # Generic batch insert machinery
│       ├── lookups.clj         # Lookup table cache & upsert
│       ├── records.clj         # Record + metadata loading
│       ├── workouts.clj        # Workout + events + stats + routes
│       ├── correlations.clj    # Correlation + nested records
│       ├── activity.clj        # ActivitySummary loading
│       └── profile.clj         # User profile (Me element)
└── test/
    ├── unit/pulso/
    │   └── xml/
    │       ├── parser_test.clj
    │       └── transform_test.clj
    └── integration/pulso/
        ├── test_helpers.clj          # Shared test infrastructure
        ├── etl_test.clj              # End-to-end pipeline tests
        └── loader/
            ├── batch_test.clj        # Batch processing tests
            ├── lookups_test.clj      # Lookup caching tests
            ├── profile_test.clj      # User profile tests
            ├── records_test.clj      # Record loading tests
            ├── workouts_test.clj     # Workout loading tests
            ├── correlations_test.clj # Correlation tests
            └── activity_test.clj     # Activity summary tests
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
