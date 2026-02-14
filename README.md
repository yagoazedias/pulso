# Pulso

Apple Health XML to PostgreSQL ETL pipeline built in Clojure.

Pulso streams a 1.5GB+ Apple Health XML export and loads it into a normalized PostgreSQL relational model. It handles 3.4M+ health records, 1,800+ workouts, activity summaries, correlations, and user profile data spanning years of health tracking.

## Tech Stack

- **Clojure** 1.12 with Leiningen
- **PostgreSQL** 17 (via Docker)
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
└── test/pulso/
    ├── xml/
    │   ├── parser_test.clj
    │   └── transform_test.clj
    └── loader/
        └── batch_test.clj
```

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
