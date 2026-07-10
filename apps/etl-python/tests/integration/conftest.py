import os

import psycopg2.extras
import pytest

from pulso import config, db
from pulso.loader import lookups, profile

TEST_DB_NAME = os.environ.get("TEST_DB_NAME", "pulso_test")

_pool = None


def _test_db_spec():
    spec = config.db_spec()
    spec["dbname"] = TEST_DB_NAME
    return spec


def _get_pool():
    global _pool
    if _pool is None:
        _pool = db.create_pool(_test_db_spec())
    return _pool


@pytest.fixture(scope="session", autouse=True)
def _migrate_once():
    """Once-per-session: ensure the pool exists and migrations are applied."""
    pool = _get_pool()
    db.migrate(pool)
    yield


@pytest.fixture
def test_ds():
    """Per-test: truncates all tables and resets the lookups cache and
    profile state before handing back the connection pool."""
    pool = _get_pool()
    db.truncate_all(pool)
    lookups.reset_caches()
    profile.reset_state()
    yield pool


@pytest.fixture
def count_rows(test_ds):
    def _count(table):
        with db.get_conn(test_ds) as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT COUNT(*) FROM {table}")
                return cur.fetchone()[0]
    return _count


@pytest.fixture
def select_all(test_ds):
    def _select(table):
        with db.get_conn(test_ds) as conn:
            with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
                cur.execute(f"SELECT * FROM {table}")
                return cur.fetchall()
    return _select
