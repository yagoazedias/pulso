from pulso import db

_source_cache = {}
_device_cache = {}
_record_type_cache = {}
_unit_cache = {}


def reset_caches():
    _source_cache.clear()
    _device_cache.clear()
    _record_type_cache.clear()
    _unit_cache.clear()


def _upsert_id(pool, sql, params, cache, cache_key):
    if cache_key in cache:
        return cache[cache_key]
    with db.get_conn(pool) as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            row_id = cur.fetchone()[0]
        conn.commit()
    cache[cache_key] = row_id
    return row_id


def ensure_source_id(pool, source_name, source_version):
    if source_name is None:
        return None
    cache_key = (source_name, source_version)
    return _upsert_id(
        pool,
        """INSERT INTO source (name, version) VALUES (%s, %s)
           ON CONFLICT (name, version) DO UPDATE SET name = EXCLUDED.name
           RETURNING id""",
        (source_name, source_version),
        _source_cache,
        cache_key,
    )


def ensure_device_id(pool, raw_text):
    if raw_text is None:
        return None
    return _upsert_id(
        pool,
        """INSERT INTO device (raw_text) VALUES (%s)
           ON CONFLICT (raw_text) DO UPDATE SET raw_text = EXCLUDED.raw_text
           RETURNING id""",
        (raw_text,),
        _device_cache,
        raw_text,
    )


def ensure_record_type_id(pool, identifier):
    if identifier is None:
        return None
    return _upsert_id(
        pool,
        """INSERT INTO record_type (identifier) VALUES (%s)
           ON CONFLICT (identifier) DO UPDATE SET identifier = EXCLUDED.identifier
           RETURNING id""",
        (identifier,),
        _record_type_cache,
        identifier,
    )


def ensure_unit_id(pool, unit_name):
    if unit_name is None:
        return None
    return _upsert_id(
        pool,
        """INSERT INTO unit (name) VALUES (%s)
           ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
           RETURNING id""",
        (unit_name,),
        _unit_cache,
        unit_name,
    )
