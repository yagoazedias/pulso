import logging
from contextlib import contextmanager
from pathlib import Path

from psycopg2 import pool as pg_pool

logger = logging.getLogger(__name__)

MIGRATIONS_DIR = Path(__file__).resolve().parent.parent / "migrations"

_MIGRATIONS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS schema_migrations (
        filename TEXT PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
"""

TRUNCATE_SQL = """
    TRUNCATE
        correlation_record, correlation_metadata, correlation,
        workout_route, workout_statistics, workout_event, workout_metadata, workout,
        record_metadata, record,
        activity_summary, user_profile,
        source, device, record_type, unit
    CASCADE
"""


def create_pool(db_spec, minconn=1, maxconn=10):
    return pg_pool.SimpleConnectionPool(
        minconn,
        maxconn,
        host=db_spec["host"],
        port=db_spec["port"],
        dbname=db_spec["dbname"],
        user=db_spec["user"],
        password=db_spec["password"],
    )


@contextmanager
def get_conn(pool):
    conn = pool.getconn()
    try:
        yield conn
    finally:
        pool.putconn(conn)


def migrate(pool, migrations_dir=MIGRATIONS_DIR):
    """Applies pending *.up.sql files in filename order. Tracks applied
    filenames in a schema_migrations table (mirrors Migratus's migrate,
    forward-only)."""
    logger.info("Running database migrations...")
    migrations_dir = Path(migrations_dir)
    with get_conn(pool) as conn:
        with conn.cursor() as cur:
            cur.execute(_MIGRATIONS_TABLE_SQL)
        conn.commit()

        with conn.cursor() as cur:
            cur.execute("SELECT filename FROM schema_migrations")
            applied = {row[0] for row in cur.fetchall()}

        for path in sorted(migrations_dir.glob("*.up.sql")):
            if path.name in applied:
                continue
            sql = path.read_text().replace("--;;", "")
            with conn.cursor() as cur:
                cur.execute(sql)
                cur.execute(
                    "INSERT INTO schema_migrations (filename) VALUES (%s)",
                    (path.name,),
                )
            conn.commit()
    logger.info("Migrations complete.")


def truncate_all(pool):
    logger.info("Truncating all tables...")
    with get_conn(pool) as conn:
        with conn.cursor() as cur:
            cur.execute(TRUNCATE_SQL)
        conn.commit()
    logger.info("All tables truncated.")
