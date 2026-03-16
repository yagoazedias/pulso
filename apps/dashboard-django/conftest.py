import datetime

import pytest
from django.conf import settings

# Module-level storage so the autouse fixture can access SQLite table name mappings
# set up by django_db_setup.
_SIMPLE_DB_TABLES = {}   # model -> simple name (for SQLite)
_ORIGINAL_DB_TABLES = {}  # model -> original name (schema-qualified, for production)


def pytest_configure():
    settings.DATABASES["default"] = {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": ":memory:",
    }
    settings.DEBUG = False
    settings.DJANGO_VITE["default"]["dev_mode"] = False


@pytest.fixture(scope="session")
def django_db_setup(django_test_environment, django_db_blocker):
    """Create tables for unmanaged models in test database."""
    from django.apps import apps
    from django.core.management import call_command

    with django_db_blocker.unblock():
        unmanaged = []
        for model in apps.get_models():
            if not model._meta.managed:
                model._meta.managed = True
                unmanaged.append(model)
                # SQLite does not support schema-qualified table names like
                # '"public"."tablename"', so strip the schema prefix for tests.
                _ORIGINAL_DB_TABLES[model] = model._meta.db_table
                if model._meta.db_table.startswith('"public".'):
                    simple_name = model._meta.db_table.split(".")[-1].strip('"')
                    _SIMPLE_DB_TABLES[model] = simple_name
                    model._meta.db_table = simple_name

        try:
            call_command("migrate", "--run-syncdb", verbosity=0)
        finally:
            # Restore original table names so model-level assertions still pass
            # (test_models.py checks the production schema-qualified names).
            # The _patch_db_table_for_sqlite autouse fixture re-applies simple
            # names around each test that accesses the database.
            for model in unmanaged:
                model._meta.managed = False
                model._meta.db_table = _ORIGINAL_DB_TABLES[model]


@pytest.fixture(autouse=True)
def _patch_db_table_for_sqlite(request):
    """Temporarily replace schema-qualified table names with simple names
    for SQLite compatibility whenever a test uses the database."""
    uses_db = (
        "db" in request.fixturenames
        or "django_db" in request.keywords
        or any(
            name in request.fixturenames
            for name in ("energy_data", "workout_data", "record_data")
        )
    )

    if uses_db and _SIMPLE_DB_TABLES:
        for model, name in _SIMPLE_DB_TABLES.items():
            model._meta.db_table = name
        yield
        for model, name in _ORIGINAL_DB_TABLES.items():
            model._meta.db_table = name
    else:
        yield


@pytest.fixture
def energy_data(db):
    """5 ActivitySummary rows with known dates and values."""
    from apps.analytics.models import ActivitySummary

    rows = [
        ActivitySummary(
            date_components=datetime.date(2026, 3, 10),
            active_energy_burned=450,
            active_energy_burned_goal=500,
            active_energy_burned_unit="kcal",
        ),
        ActivitySummary(
            date_components=datetime.date(2026, 3, 11),
            active_energy_burned=520,
            active_energy_burned_goal=500,
            active_energy_burned_unit="kcal",
        ),
        ActivitySummary(
            date_components=datetime.date(2026, 3, 12),
            active_energy_burned=480,
            active_energy_burned_goal=500,
            active_energy_burned_unit="kcal",
        ),
        ActivitySummary(
            date_components=datetime.date(2026, 3, 13),
            active_energy_burned=None,
            active_energy_burned_goal=500,
            active_energy_burned_unit="kcal",
        ),
        ActivitySummary(
            date_components=datetime.date(2026, 3, 14),
            active_energy_burned=600,
            active_energy_burned_goal=500,
            active_energy_burned_unit="kcal",
        ),
    ]
    ActivitySummary.objects.bulk_create(rows)
    return rows


@pytest.fixture
def workout_data(db):
    """6 Workout rows spanning 2 ISO weeks (Mon 2026-03-02 to Sun 2026-03-15)."""
    from apps.analytics.models import Workout

    rows = [
        # Week of 2026-03-02 (Mon)
        Workout(
            activity_type="Running",
            duration=1800.0,
            total_energy_burned=300,
            start_date=datetime.datetime(2026, 3, 2, 8, 0),
            end_date=datetime.datetime(2026, 3, 2, 8, 30),
        ),
        Workout(
            activity_type="Cycling",
            duration=3600.0,
            total_energy_burned=500,
            start_date=datetime.datetime(2026, 3, 4, 17, 0),
            end_date=datetime.datetime(2026, 3, 4, 18, 0),
        ),
        Workout(
            activity_type="Swimming",
            duration=None,
            total_energy_burned=None,
            start_date=datetime.datetime(2026, 3, 5, 7, 0),
            end_date=datetime.datetime(2026, 3, 5, 7, 45),
        ),
        # Week of 2026-03-09 (Mon)
        Workout(
            activity_type="Running",
            duration=2400.0,
            total_energy_burned=400,
            start_date=datetime.datetime(2026, 3, 9, 8, 0),
            end_date=datetime.datetime(2026, 3, 9, 8, 40),
        ),
        Workout(
            activity_type="Yoga",
            duration=3600.0,
            total_energy_burned=200,
            start_date=datetime.datetime(2026, 3, 11, 6, 0),
            end_date=datetime.datetime(2026, 3, 11, 7, 0),
        ),
        Workout(
            activity_type="Running",
            duration=1200.0,
            total_energy_burned=250,
            start_date=datetime.datetime(2026, 3, 14, 18, 0),
            end_date=datetime.datetime(2026, 3, 14, 18, 20),
        ),
    ]
    Workout.objects.bulk_create(rows)
    return rows


@pytest.fixture
def record_data(db):
    """3 RecordType entries + Record rows spanning 2 weeks."""
    from apps.analytics.models import Record, RecordType

    heart_rate = RecordType.objects.create(
        identifier="HKQuantityTypeIdentifierHeartRate"
    )
    step_count = RecordType.objects.create(
        identifier="HKQuantityTypeIdentifierStepCount"
    )
    distance = RecordType.objects.create(
        identifier="HKQuantityTypeIdentifierDistanceWalkingRunning"
    )

    records = [
        *[
            Record(record_type=heart_rate, start_date=datetime.datetime(2026, 3, 2, h, 0))
            for h in range(5)
        ],
        *[
            Record(record_type=step_count, start_date=datetime.datetime(2026, 3, 3, h, 0))
            for h in range(3)
        ],
        Record(record_type=distance, start_date=datetime.datetime(2026, 3, 4, 10, 0)),
        *[
            Record(record_type=heart_rate, start_date=datetime.datetime(2026, 3, 9, h, 0))
            for h in range(4)
        ],
        *[
            Record(record_type=step_count, start_date=datetime.datetime(2026, 3, 10, h, 0))
            for h in range(2)
        ],
    ]
    Record.objects.bulk_create(records)
    return {"types": [heart_rate, step_count, distance], "records": records}
