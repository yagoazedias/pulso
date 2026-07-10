import os

DEFAULT_BATCH_SIZE = 5000


def db_spec():
    return {
        "dbname": os.environ.get("DB_NAME", "pulso"),
        "host": os.environ.get("DB_HOST", "localhost"),
        "port": int(os.environ.get("DB_PORT", "5432")),
        "user": os.environ.get("DB_USER", "postgres"),
        "password": os.environ.get("DB_PASSWORD", "postgres"),
    }
