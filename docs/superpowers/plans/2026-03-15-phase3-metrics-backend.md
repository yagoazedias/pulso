# Phase 3: Metrics Backend + APIs — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 3 chart API endpoints (`energy-vs-goal`, `workout-volume`, `top-record-types`) returning Chart.js-compatible JSON from ETL-owned PostgreSQL tables.

**Architecture:** Repository pattern with `PostgresMetricsRepository` holding all query logic. Plain Django view functions parse date params and return `JsonResponse`. No DRF, no service layer. Tests use SQLite with ORM queries (no raw SQL).

**Tech Stack:** Django 4.2, PostgreSQL (production), SQLite (tests), pytest + pytest-django

**Spec:** `docs/superpowers/specs/2026-03-15-phase3-metrics-backend-design.md`

---

## File Structure

| File | Responsibility |
|------|---------------|
| `apps/analytics/models.py` | Unmanaged Django models mapping ETL tables (`ActivitySummary`, `Workout`, `Record`, `RecordType`) |
| `apps/analytics/repositories.py` | `PostgresMetricsRepository` with 3 query methods returning chart-ready dicts |
| `apps/analytics/views.py` | 3 API view functions + shared `parse_date_params` helper |
| `apps/analytics/urls.py` | URL routing for landing page + 3 API endpoints |
| `conftest.py` | pytest config + shared test fixtures |
| `apps/analytics/tests/test_models.py` | Model meta tests (unmanaged, table names) |
| `apps/analytics/tests/test_repositories.py` | Repository unit tests with fixture data |
| `apps/analytics/tests/test_api.py` | API integration tests (HTTP layer) |

---

## Chunk 1: Models + Fixtures

### Task 1: Add Unmanaged Models

**Files:**
- Modify: `apps/analytics/models.py`
- Modify: `apps/analytics/tests/test_models.py`

- [ ] **Step 1: Write failing tests for new models**

Add to `apps/analytics/tests/test_models.py`:

```python
from apps.analytics.models import ActivitySummary, Workout, Record, RecordType


def test_activity_summary_is_unmanaged():
    assert ActivitySummary._meta.managed is False


def test_activity_summary_table_name():
    assert ActivitySummary._meta.db_table == '"public"."activity_summary"'


def test_workout_is_unmanaged():
    assert Workout._meta.managed is False


def test_workout_table_name():
    assert Workout._meta.db_table == '"public"."workout"'


def test_record_is_unmanaged():
    assert Record._meta.managed is False


def test_record_table_name():
    assert Record._meta.db_table == '"public"."record"'


def test_record_type_is_unmanaged():
    assert RecordType._meta.managed is False


def test_record_type_table_name():
    assert RecordType._meta.db_table == '"public"."record_type"'
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_models.py -v
```

Expected: FAIL — `ImportError: cannot import name 'Workout' from 'apps.analytics.models'`

- [ ] **Step 3: Add models to `apps/analytics/models.py`**

Replace the entire file with:

```python
from django.db import models


class ActivitySummary(models.Model):
    date_components = models.DateField()
    active_energy_burned = models.FloatField(null=True)
    active_energy_burned_goal = models.FloatField(null=True)
    active_energy_burned_unit = models.CharField(max_length=50, null=True)

    class Meta:
        managed = False
        db_table = '"public"."activity_summary"'


class Workout(models.Model):
    activity_type = models.CharField(max_length=255)
    duration = models.FloatField(null=True)
    total_energy_burned = models.FloatField(null=True)
    start_date = models.DateTimeField()
    end_date = models.DateTimeField()

    class Meta:
        managed = False
        db_table = '"public"."workout"'


class Record(models.Model):
    record_type = models.ForeignKey("RecordType", on_delete=models.DO_NOTHING)
    start_date = models.DateTimeField()

    class Meta:
        managed = False
        db_table = '"public"."record"'


class RecordType(models.Model):
    identifier = models.CharField(max_length=255, unique=True)

    class Meta:
        managed = False
        db_table = '"public"."record_type"'
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_models.py -v
```

Expected: 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add apps/dashboard-django/apps/analytics/models.py apps/dashboard-django/apps/analytics/tests/test_models.py
git commit -m "feat: add unmanaged Workout, Record, RecordType models"
```

### Task 2: Update conftest.py with Test Fixtures

The tests use SQLite (set in `conftest.py`). Since models are `managed = False`, Django won't create their tables in the test DB. We need to override this for testing by making them temporarily managed.

**Files:**
- Modify: `conftest.py`

- [ ] **Step 1: Add fixture setup to `conftest.py`**

Replace the entire file with:

```python
import datetime

import pytest
from django.conf import settings


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
        try:
            call_command("migrate", "--run-syncdb", verbosity=0)
        finally:
            for model in unmanaged:
                model._meta.managed = False


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
```

- [ ] **Step 2: Run existing tests to verify nothing breaks**

```bash
cd apps/dashboard-django && python -m pytest -v
```

Expected: All existing tests PASS (8 model tests + 2 view tests + 2 repo tests)

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/conftest.py
git commit -m "feat: add test fixtures for energy, workout, and record data"
```

---

## Chunk 2: Repository Implementation

### Task 3: Implement `get_energy_vs_goal`

**Files:**
- Modify: `apps/analytics/repositories.py`
- Modify: `apps/analytics/tests/test_repositories.py`

- [ ] **Step 1: Write failing tests**

Replace `apps/analytics/tests/test_repositories.py` with:

```python
import datetime

import pytest

from apps.analytics.repositories import PostgresMetricsRepository


@pytest.fixture
def repo():
    return PostgresMetricsRepository()


class TestGetEnergyVsGoal:
    @pytest.mark.django_db
    def test_returns_correct_structure(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-10", end="2026-03-14")
        assert "labels" in result
        assert "datasets" in result
        assert "meta" in result
        assert len(result["datasets"]) == 2
        assert result["datasets"][0]["label"] == "Active Energy Burned"
        assert result["datasets"][1]["label"] == "Goal"

    @pytest.mark.django_db
    def test_returns_correct_values(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-10", end="2026-03-14")
        assert result["labels"] == [
            "2026-03-10", "2026-03-11", "2026-03-12", "2026-03-13", "2026-03-14"
        ]
        assert result["datasets"][0]["data"] == [450, 520, 480, None, 600]
        assert result["datasets"][1]["data"] == [500, 500, 500, 500, 500]

    @pytest.mark.django_db
    def test_date_filtering(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-11", end="2026-03-12")
        assert result["labels"] == ["2026-03-11", "2026-03-12"]
        assert result["datasets"][0]["data"] == [520, 480]

    @pytest.mark.django_db
    def test_empty_range_returns_empty_arrays(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2025-01-01", end="2025-01-02")
        assert result["labels"] == []
        assert result["datasets"][0]["data"] == []
        assert result["datasets"][1]["data"] == []

    @pytest.mark.django_db
    def test_meta_fields(self, repo, energy_data):
        result = repo.get_energy_vs_goal(start="2026-03-10", end="2026-03-14")
        assert result["meta"]["unit"] == "kcal"
        assert result["meta"]["window"] == "custom"
        assert result["meta"]["last_updated"] == "2026-03-14"

    @pytest.mark.django_db
    def test_default_window_meta(self, repo, energy_data):
        result = repo.get_energy_vs_goal()
        assert result["meta"]["window"] == "90d"

    @pytest.mark.django_db
    def test_empty_result_last_updated_is_none(self, repo):
        result = repo.get_energy_vs_goal(start="2025-01-01", end="2025-01-02")
        assert result["meta"]["last_updated"] is None
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py -v
```

Expected: FAIL — `TypeError` or `AttributeError` (old stub returns list, not dict)

- [ ] **Step 3: Implement `get_energy_vs_goal` in `repositories.py`**

Replace `apps/analytics/repositories.py` with:

```python
import datetime


class PostgresMetricsRepository:
    def get_energy_vs_goal(self, start=None, end=None):
        from apps.analytics.models import ActivitySummary

        today = datetime.date.today()
        is_default = start is None and end is None

        if start is None:
            start = (today - datetime.timedelta(days=90)).isoformat()
        if end is None:
            end = today.isoformat()

        rows = (
            ActivitySummary.objects.filter(
                date_components__gte=start,
                date_components__lte=end,
            )
            .order_by("date_components")
            .values_list(
                "date_components",
                "active_energy_burned",
                "active_energy_burned_goal",
            )
        )

        labels = []
        energy_data = []
        goal_data = []
        last_date = None

        for date_val, energy, goal in rows:
            labels.append(date_val.isoformat())
            energy_data.append(energy)
            goal_data.append(goal)
            last_date = date_val

        return {
            "labels": labels,
            "datasets": [
                {"label": "Active Energy Burned", "data": energy_data},
                {"label": "Goal", "data": goal_data},
            ],
            "meta": {
                "unit": "kcal",
                "window": "90d" if is_default else "custom",
                "last_updated": last_date.isoformat() if last_date else None,
            },
        }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py -v
```

Expected: All `TestGetEnergyVsGoal` tests PASS

- [ ] **Step 5: Commit**

```bash
git add apps/dashboard-django/apps/analytics/repositories.py apps/dashboard-django/apps/analytics/tests/test_repositories.py
git commit -m "feat: implement get_energy_vs_goal repository method"
```

### Task 4: Implement `get_workout_volume`

**Files:**
- Modify: `apps/analytics/repositories.py`
- Modify: `apps/analytics/tests/test_repositories.py`

- [ ] **Step 1: Write failing tests**

Add to `apps/analytics/tests/test_repositories.py`:

```python
class TestGetWorkoutVolume:
    @pytest.mark.django_db
    def test_returns_correct_structure(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        assert "labels" in result
        assert "datasets" in result
        assert "meta" in result
        assert len(result["datasets"]) == 3
        assert result["datasets"][0]["label"] == "Workouts"
        assert result["datasets"][1]["label"] == "Duration (min)"
        assert result["datasets"][2]["label"] == "Energy Burned (kcal)"

    @pytest.mark.django_db
    def test_weekly_aggregation(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        # Week 2026-03-02: 3 workouts, duration (1800+3600+0)/60=90 min, energy 300+500+0=800
        # Week 2026-03-09: 3 workouts, duration (2400+3600+1200)/60=120 min, energy 400+200+250=850
        assert result["datasets"][0]["data"] == [3, 3]
        assert result["datasets"][1]["data"] == [90.0, 120.0]
        assert result["datasets"][2]["data"] == [800, 850]

    @pytest.mark.django_db
    def test_labels_are_week_mondays(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        assert result["labels"] == ["2026-03-02", "2026-03-09"]

    @pytest.mark.django_db
    def test_null_duration_energy_dont_break_sums(self, repo, workout_data):
        """The Swimming workout has null duration and energy — sums should still work."""
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-08")
        # Week has 3 workouts: Running (1800s, 300kcal), Cycling (3600s, 500kcal), Swimming (null, null)
        assert result["datasets"][0]["data"] == [3]
        assert result["datasets"][1]["data"] == [90.0]  # (1800+3600)/60, null excluded
        assert result["datasets"][2]["data"] == [800]  # 300+500, null excluded

    @pytest.mark.django_db
    def test_empty_range(self, repo, workout_data):
        result = repo.get_workout_volume(start="2025-01-01", end="2025-01-07")
        assert result["labels"] == []
        assert result["datasets"][0]["data"] == []

    @pytest.mark.django_db
    def test_meta_fields(self, repo, workout_data):
        result = repo.get_workout_volume(start="2026-03-02", end="2026-03-15")
        assert result["meta"]["unit"] == "mixed"
        assert result["meta"]["window"] == "custom"

    @pytest.mark.django_db
    def test_default_window_meta(self, repo, workout_data):
        result = repo.get_workout_volume()
        assert result["meta"]["window"] == "12w"
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py::TestGetWorkoutVolume -v
```

Expected: FAIL — `AttributeError: 'PostgresMetricsRepository' object has no attribute 'get_workout_volume'`

- [ ] **Step 3: Implement `get_workout_volume`**

Add to `apps/analytics/repositories.py` inside `PostgresMetricsRepository`:

```python
    def get_workout_volume(self, start=None, end=None):
        from apps.analytics.models import Workout

        today = datetime.date.today()
        is_default = start is None and end is None

        if start is None:
            start = (today - datetime.timedelta(weeks=12)).isoformat()
        if end is None:
            end = today.isoformat()

        workouts = Workout.objects.filter(
            start_date__date__gte=start,
            start_date__date__lte=end,
        ).order_by("start_date")

        # Group by ISO week (Monday)
        weeks = {}
        for w in workouts:
            # Get the Monday of this workout's week
            dt = w.start_date.date() if hasattr(w.start_date, "date") else w.start_date
            monday = dt - datetime.timedelta(days=dt.weekday())
            key = monday.isoformat()

            if key not in weeks:
                weeks[key] = {"count": 0, "duration": 0.0, "energy": 0.0}

            weeks[key]["count"] += 1
            if w.duration is not None:
                weeks[key]["duration"] += w.duration / 60.0
            if w.total_energy_burned is not None:
                weeks[key]["energy"] += w.total_energy_burned

        sorted_keys = sorted(weeks.keys())
        labels = sorted_keys
        counts = [weeks[k]["count"] for k in sorted_keys]
        durations = [weeks[k]["duration"] for k in sorted_keys]
        energies = [weeks[k]["energy"] for k in sorted_keys]

        last_date = sorted_keys[-1] if sorted_keys else None

        return {
            "labels": labels,
            "datasets": [
                {"label": "Workouts", "data": counts},
                {"label": "Duration (min)", "data": durations},
                {"label": "Energy Burned (kcal)", "data": energies},
            ],
            "meta": {
                "unit": "mixed",
                "window": "12w" if is_default else "custom",
                "last_updated": last_date,
            },
        }
```

**Note on SQLite compatibility:** The spec says `DATE_TRUNC('week', start_date)` for PostgreSQL. Since tests run on SQLite (which lacks `DATE_TRUNC`), we use Python-level grouping via the ORM. This works identically for both databases.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py::TestGetWorkoutVolume -v
```

Expected: All `TestGetWorkoutVolume` tests PASS

- [ ] **Step 5: Commit**

```bash
git add apps/dashboard-django/apps/analytics/repositories.py apps/dashboard-django/apps/analytics/tests/test_repositories.py
git commit -m "feat: implement get_workout_volume repository method"
```

### Task 5: Implement `get_top_record_types`

**Files:**
- Modify: `apps/analytics/repositories.py`
- Modify: `apps/analytics/tests/test_repositories.py`

- [ ] **Step 1: Write failing tests**

Add to `apps/analytics/tests/test_repositories.py`:

```python
class TestGetTopRecordTypes:
    @pytest.mark.django_db
    def test_returns_correct_structure(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert "labels" in result
        assert "datasets" in result
        assert "meta" in result

    @pytest.mark.django_db
    def test_types_ordered_by_volume(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        # HeartRate: 9 total, StepCount: 5 total, Distance: 1 total
        assert result["datasets"][0]["label"] == "HKQuantityTypeIdentifierHeartRate"
        assert result["datasets"][1]["label"] == "HKQuantityTypeIdentifierStepCount"
        assert result["datasets"][2]["label"] == "HKQuantityTypeIdentifierDistanceWalkingRunning"

    @pytest.mark.django_db
    def test_weekly_bucketing(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert result["labels"] == ["2026-03-02", "2026-03-09"]
        # HeartRate: week1=5, week2=4
        assert result["datasets"][0]["data"] == [5, 4]
        # StepCount: week1=3, week2=2
        assert result["datasets"][1]["data"] == [3, 2]
        # Distance: week1=1, week2=0
        assert result["datasets"][2]["data"] == [1, 0]

    @pytest.mark.django_db
    def test_returns_max_5_types(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert len(result["datasets"]) <= 5

    @pytest.mark.django_db
    def test_fewer_than_5_types(self, repo, record_data):
        """Fixture only has 3 types — should return 3 datasets."""
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert len(result["datasets"]) == 3

    @pytest.mark.django_db
    def test_empty_range(self, repo, record_data):
        result = repo.get_top_record_types(start="2025-01-01", end="2025-01-07")
        assert result["labels"] == []
        assert result["datasets"] == []

    @pytest.mark.django_db
    def test_date_range_filtering(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-08")
        assert result["labels"] == ["2026-03-02"]

    @pytest.mark.django_db
    def test_meta_fields(self, repo, record_data):
        result = repo.get_top_record_types(start="2026-03-02", end="2026-03-15")
        assert result["meta"]["unit"] == "count"
        assert result["meta"]["window"] == "custom"

    @pytest.mark.django_db
    def test_default_window_meta(self, repo, record_data):
        result = repo.get_top_record_types()
        assert result["meta"]["window"] == "12w"
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py::TestGetTopRecordTypes -v
```

Expected: FAIL — `AttributeError: 'PostgresMetricsRepository' object has no attribute 'get_top_record_types'`

- [ ] **Step 3: Implement `get_top_record_types`**

Add to `apps/analytics/repositories.py` inside `PostgresMetricsRepository`:

```python
    def get_top_record_types(self, start=None, end=None):
        from apps.analytics.models import Record

        today = datetime.date.today()
        is_default = start is None and end is None

        if start is None:
            start = (today - datetime.timedelta(weeks=12)).isoformat()
        if end is None:
            end = today.isoformat()

        records = list(
            Record.objects.filter(
                start_date__date__gte=start,
                start_date__date__lte=end,
            ).select_related("record_type").order_by("start_date")
        )

        # Step 1: Count totals per type to find top 5
        type_counts = {}
        for r in records:
            identifier = r.record_type.identifier
            type_counts[identifier] = type_counts.get(identifier, 0) + 1

        top_types = sorted(type_counts, key=type_counts.get, reverse=True)[:5]

        if not top_types:
            return {
                "labels": [],
                "datasets": [],
                "meta": {
                    "unit": "count",
                    "window": "12w" if is_default else "custom",
                    "last_updated": None,
                },
            }

        # Step 2: Build weekly counts for top types
        # Collect all weeks and per-type-per-week counts
        week_type_counts = {}  # {monday_iso: {identifier: count}}
        all_weeks = set()

        for r in records:
            identifier = r.record_type.identifier
            if identifier not in top_types:
                continue
            dt = r.start_date.date() if hasattr(r.start_date, "date") else r.start_date
            monday = dt - datetime.timedelta(days=dt.weekday())
            key = monday.isoformat()
            all_weeks.add(key)

            if key not in week_type_counts:
                week_type_counts[key] = {}
            week_type_counts[key][identifier] = (
                week_type_counts[key].get(identifier, 0) + 1
            )

        sorted_weeks = sorted(all_weeks)
        last_date = sorted_weeks[-1] if sorted_weeks else None

        datasets = []
        for type_id in top_types:
            data = [week_type_counts.get(w, {}).get(type_id, 0) for w in sorted_weeks]
            datasets.append({"label": type_id, "data": data})

        return {
            "labels": sorted_weeks,
            "datasets": datasets,
            "meta": {
                "unit": "count",
                "window": "12w" if is_default else "custom",
                "last_updated": last_date,
            },
        }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py::TestGetTopRecordTypes -v
```

Expected: All `TestGetTopRecordTypes` tests PASS

- [ ] **Step 5: Run all repository tests**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_repositories.py -v
```

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add apps/dashboard-django/apps/analytics/repositories.py apps/dashboard-django/apps/analytics/tests/test_repositories.py
git commit -m "feat: implement get_top_record_types repository method"
```

---

## Chunk 3: API Views + Integration Tests

### Task 6: Add API Views and URL Routes

**Files:**
- Modify: `apps/analytics/views.py`
- Modify: `apps/analytics/urls.py`

- [ ] **Step 1: Implement views and param parser**

Replace `apps/analytics/views.py` with:

```python
import datetime

from django.http import JsonResponse
from django.shortcuts import render
from django.views.decorators.http import require_GET

from apps.analytics.repositories import PostgresMetricsRepository


def index(request):
    return render(request, "analytics/index.html")


def parse_date_params(request):
    """Extract and validate start/end date params from request.

    Returns (start, end) as strings or None.
    Raises ValueError if format is invalid.
    """
    start = request.GET.get("start")
    end = request.GET.get("end")

    for value, name in [(start, "start"), (end, "end")]:
        if value is not None:
            try:
                datetime.date.fromisoformat(value)
            except ValueError:
                raise ValueError(
                    f"Invalid date format for '{name}': '{value}'. Expected YYYY-MM-DD."
                )

    return start, end


@require_GET
def energy_vs_goal(request):
    try:
        start, end = parse_date_params(request)
    except ValueError as e:
        return JsonResponse({"error": str(e)}, status=400)

    repo = PostgresMetricsRepository()
    data = repo.get_energy_vs_goal(start=start, end=end)
    return JsonResponse(data)


@require_GET
def workout_volume(request):
    try:
        start, end = parse_date_params(request)
    except ValueError as e:
        return JsonResponse({"error": str(e)}, status=400)

    repo = PostgresMetricsRepository()
    data = repo.get_workout_volume(start=start, end=end)
    return JsonResponse(data)


@require_GET
def top_record_types(request):
    try:
        start, end = parse_date_params(request)
    except ValueError as e:
        return JsonResponse({"error": str(e)}, status=400)

    repo = PostgresMetricsRepository()
    data = repo.get_top_record_types(start=start, end=end)
    return JsonResponse(data)
```

- [ ] **Step 2: Add URL routes**

Replace `apps/analytics/urls.py` with:

```python
from django.urls import path

from . import views

urlpatterns = [
    path("", views.index, name="index"),
    path("api/metrics/energy-vs-goal", views.energy_vs_goal, name="energy-vs-goal"),
    path("api/metrics/workout-volume", views.workout_volume, name="workout-volume"),
    path("api/metrics/top-record-types", views.top_record_types, name="top-record-types"),
]
```

- [ ] **Step 3: Run existing tests to verify nothing breaks**

```bash
cd apps/dashboard-django && python -m pytest -v
```

Expected: All existing tests PASS

- [ ] **Step 4: Commit**

```bash
git add apps/dashboard-django/apps/analytics/views.py apps/dashboard-django/apps/analytics/urls.py
git commit -m "feat: add API views and URL routes for metrics endpoints"
```

### Task 7: Write API Integration Tests

**Files:**
- Create: `apps/analytics/tests/test_api.py`

- [ ] **Step 1: Write integration tests**

Create `apps/analytics/tests/test_api.py`:

```python
import json

import pytest
from django.test import Client


@pytest.fixture
def client():
    return Client()


class TestEnergyVsGoalAPI:
    @pytest.mark.django_db
    def test_returns_200(self, client, energy_data):
        response = client.get("/api/metrics/energy-vs-goal")
        assert response.status_code == 200

    @pytest.mark.django_db
    def test_returns_correct_json_structure(self, client, energy_data):
        response = client.get("/api/metrics/energy-vs-goal")
        data = json.loads(response.content)
        assert "labels" in data
        assert "datasets" in data
        assert "meta" in data
        assert len(data["datasets"]) == 2

    @pytest.mark.django_db
    def test_date_range_filtering(self, client, energy_data):
        response = client.get(
            "/api/metrics/energy-vs-goal?start=2026-03-11&end=2026-03-12"
        )
        data = json.loads(response.content)
        assert data["labels"] == ["2026-03-11", "2026-03-12"]

    @pytest.mark.django_db
    def test_empty_range_returns_empty_arrays(self, client, energy_data):
        response = client.get(
            "/api/metrics/energy-vs-goal?start=2025-01-01&end=2025-01-02"
        )
        data = json.loads(response.content)
        assert data["labels"] == []
        assert data["datasets"][0]["data"] == []

    @pytest.mark.django_db
    def test_invalid_date_returns_400(self, client):
        response = client.get("/api/metrics/energy-vs-goal?start=not-a-date")
        assert response.status_code == 400
        data = json.loads(response.content)
        assert "error" in data

    @pytest.mark.django_db
    def test_post_not_allowed(self, client):
        response = client.post("/api/metrics/energy-vs-goal")
        assert response.status_code == 405


class TestWorkoutVolumeAPI:
    @pytest.mark.django_db
    def test_returns_200(self, client, workout_data):
        response = client.get("/api/metrics/workout-volume")
        assert response.status_code == 200

    @pytest.mark.django_db
    def test_returns_3_datasets(self, client, workout_data):
        response = client.get("/api/metrics/workout-volume")
        data = json.loads(response.content)
        assert len(data["datasets"]) == 3

    @pytest.mark.django_db
    def test_date_range_filtering(self, client, workout_data):
        response = client.get(
            "/api/metrics/workout-volume?start=2026-03-02&end=2026-03-08"
        )
        data = json.loads(response.content)
        assert len(data["labels"]) == 1

    @pytest.mark.django_db
    def test_empty_range_returns_empty_arrays(self, client, workout_data):
        response = client.get(
            "/api/metrics/workout-volume?start=2025-01-01&end=2025-01-07"
        )
        data = json.loads(response.content)
        assert data["labels"] == []

    @pytest.mark.django_db
    def test_invalid_date_returns_400(self, client):
        response = client.get("/api/metrics/workout-volume?end=bad")
        assert response.status_code == 400

    @pytest.mark.django_db
    def test_post_not_allowed(self, client):
        response = client.post("/api/metrics/workout-volume")
        assert response.status_code == 405


class TestTopRecordTypesAPI:
    @pytest.mark.django_db
    def test_returns_200(self, client, record_data):
        response = client.get("/api/metrics/top-record-types")
        assert response.status_code == 200

    @pytest.mark.django_db
    def test_returns_datasets_per_type(self, client, record_data):
        response = client.get("/api/metrics/top-record-types")
        data = json.loads(response.content)
        assert len(data["datasets"]) == 3

    @pytest.mark.django_db
    def test_types_ordered_by_volume(self, client, record_data):
        response = client.get(
            "/api/metrics/top-record-types?start=2026-03-02&end=2026-03-15"
        )
        data = json.loads(response.content)
        assert data["datasets"][0]["label"] == "HKQuantityTypeIdentifierHeartRate"

    @pytest.mark.django_db
    def test_date_range_filtering(self, client, record_data):
        response = client.get(
            "/api/metrics/top-record-types?start=2026-03-02&end=2026-03-08"
        )
        data = json.loads(response.content)
        assert len(data["labels"]) == 1

    @pytest.mark.django_db
    def test_invalid_date_returns_400(self, client):
        response = client.get("/api/metrics/top-record-types?start=2026-13-01")
        assert response.status_code == 400

    @pytest.mark.django_db
    def test_post_not_allowed(self, client):
        response = client.post("/api/metrics/top-record-types")
        assert response.status_code == 405
```

- [ ] **Step 2: Run integration tests**

```bash
cd apps/dashboard-django && python -m pytest apps/analytics/tests/test_api.py -v
```

Expected: All 18 API tests PASS

- [ ] **Step 3: Run full test suite**

```bash
cd apps/dashboard-django && python -m pytest -v
```

Expected: All tests PASS (models + repositories + views + API)

- [ ] **Step 4: Commit**

```bash
git add apps/dashboard-django/apps/analytics/tests/test_api.py
git commit -m "feat: add API integration tests for all metrics endpoints"
```
