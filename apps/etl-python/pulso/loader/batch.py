import psycopg2.extras

from pulso import db


class Batcher:
    """Accumulates rows and flushes via a plain batch INSERT once batch_size
    is reached, or on explicit flush()."""

    def __init__(self, pool, table, columns, batch_size):
        self._pool = pool
        self._table = table
        self._columns = columns
        self._batch_size = batch_size
        self._buffer = []
        self._total = 0

    def add(self, row):
        self._buffer.append(row)
        self._total += 1
        if len(self._buffer) >= self._batch_size:
            self._flush_batch()

    def flush(self):
        self._flush_batch()

    def count(self):
        return self._total

    def _flush_batch(self):
        if not self._buffer:
            return
        cols = ", ".join(self._columns)
        placeholders = "(" + ", ".join(["%s"] * len(self._columns)) + ")"
        sql = f"INSERT INTO {self._table} ({cols}) VALUES {placeholders}"
        with db.get_conn(self._pool) as conn:
            with conn.cursor() as cur:
                psycopg2.extras.execute_batch(cur, sql, self._buffer)
            conn.commit()
        self._buffer = []


class ReturningBatcher:
    """Like Batcher, but flushes via a multi-row INSERT ... RETURNING id and
    pairs each generated id back up with the metadata passed to add()."""

    def __init__(self, pool, table, columns, batch_size):
        self._pool = pool
        self._table = table
        self._columns = columns
        self._batch_size = batch_size
        self._rows = []
        self._metadata = []
        self._total = 0

    def add(self, row, metadata):
        self._rows.append(row)
        self._metadata.append(metadata)
        self._total += 1
        if len(self._rows) >= self._batch_size:
            return self._flush_batch()
        return None

    def flush(self):
        return self._flush_batch()

    def count(self):
        return self._total

    def _flush_batch(self):
        if not self._rows:
            return None
        cols = ", ".join(self._columns)
        template = "(" + ", ".join(["%s"] * len(self._columns)) + ")"
        sql = f"INSERT INTO {self._table} ({cols}) VALUES %s RETURNING id"
        with db.get_conn(self._pool) as conn:
            with conn.cursor() as cur:
                ids = psycopg2.extras.execute_values(
                    cur, sql, self._rows, template=template, fetch=True
                )
            conn.commit()
        metas = self._metadata
        self._rows = []
        self._metadata = []
        return [{"id": row_id, "metadata": meta} for (row_id,), meta in zip(ids, metas)]
