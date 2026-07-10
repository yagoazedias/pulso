# ETL Clojure → Python Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `apps/etl-clojure` with a new `apps/etl-python` app that reproduces the same Apple Health XML → PostgreSQL ETL pipeline in Python, with the same architecture, same schema, and the full test suite (49 tests: 30 unit + 19 integration) ported 1:1.

**Architecture:** Mechanical module-by-module port. Each Clojure namespace maps to one Python module with the same responsibilities: `xml.etree.ElementTree.iterparse` streams the XML file → transform functions turn elements into dicts → loader modules batch-insert into Postgres via `psycopg2`, with a lookup cache for dimension tables. Same CLI shape, same live terminal progress bar, same idempotent truncate-and-reload strategy.

**Tech Stack:** Python (uv-managed via `pyproject.toml`), `psycopg2-binary` for raw SQL/batch inserts (no ORM), `xml.etree.ElementTree` for streaming XML, `pytest` for tests, a small custom SQL-file migration runner (no framework).

## Global Constraints

- New app lives at `apps/etl-python/`, package name `pulso` (same as Clojure), so `apps/etl-python/pulso/...`.
- DB access is plain `psycopg2` + raw SQL — no ORM, no SQLAlchemy.
- Same table/column names as the existing schema (see `apps/etl-clojure/resources/migrations/*.up.sql`) — the Python app writes to the exact same Postgres schema.
- Same env vars: `DB_HOST` (default `localhost`), `DB_PORT` (default `5432`), `DB_NAME` (default `pulso`), `DB_USER` (default `postgres`), `DB_PASSWORD` (default `postgres`), `TEST_DB_NAME` (default `pulso_test`).
- Same CLI flags: `-f/--file`, `-b/--batch-size` (default `5000`), `-p/--progress/--no-progress` (default on), `-h/--help`.
- Same log-progress interval for non-UI mode: every 100,000 records.
- Tests: pytest, unit tests with no `integration` marker (no DB), integration tests marked `@pytest.mark.integration` (require `pulso_test` DB) — mirrors the Leiningen `:unit`/`:integration` profile split.
- Dependency/tooling management via `uv` (`pyproject.toml` + `uv.lock`).
- **Two deviations from the design spec, discovered while reading the Clojure source closely — both keep test parity, so approach A still holds:**
  1. **No `xml/io.py` port.** The Clojure app's DOCTYPE-skip reader (`xml/io.clj`) works around a StAX limitation. Verified empirically that Python's `ElementTree.iterparse` parses the Apple Health DOCTYPE (with its internal `<!ATTLIST>` subset) natively — no workaround needed. `xml/parser.py` and `xml/counter.py` call `iterparse` directly.
  2. **No `rich` dependency for the progress UI.** `ui/terminal.py` is ported directly (ANSI escapes, `threading.Thread`), not rewritten on top of `rich.progress`, because the Clojure test suite unit-tests pure string-producing functions (`format-number`, `progress-bar`, `render-frame`, etc.) that only exist if the renderer is hand-rolled the same way. This keeps the same testable functions and the same 5 ported unit tests.
- `apps/etl-clojure` is only deleted in the final task, gated on the user manually verifying row-count parity against a real export — that step needs the user's own Apple Health data file and cannot be automated by an agent.

---

### Task 1: Scaffold `apps/etl-python` project

**Files:**
- Create: `apps/etl-python/pyproject.toml`
- Create: `apps/etl-python/.python-version`
- Create: `apps/etl-python/pulso/__init__.py`
- Create: `apps/etl-python/pulso/xml/__init__.py`
- Create: `apps/etl-python/pulso/loader/__init__.py`
- Create: `apps/etl-python/pulso/ui/__init__.py`
- Create: `apps/etl-python/tests/__init__.py`
- Create: `apps/etl-python/tests/unit/__init__.py`
- Create: `apps/etl-python/tests/unit/xml/__init__.py`
- Create: `apps/etl-python/tests/unit/ui/__init__.py`
- Create: `apps/etl-python/tests/integration/__init__.py`
- Create: `apps/etl-python/tests/integration/loader/__init__.py`
- Create: `apps/etl-python/tests/fixtures/small-export.xml` (copied from `apps/etl-clojure/test-resources/fixtures/small-export.xml`)
- Modify: `.gitignore` (repo root)

**Interfaces:**
- Produces: a `uv`-managed Python project at `apps/etl-python/`, importable as `pulso.*`, with `pytest` runnable via `uv run pytest`. All later tasks add files under this tree.

- [ ] **Step 1: Install `uv` if not already available**

```bash
command -v uv || pip3 install --user uv
export PATH="$HOME/.local/bin:$PATH"
uv --version
```

Expected: prints a `uv 0.x.y` version string.

- [ ] **Step 2: Create the directory tree and empty `__init__.py` markers**

```bash
cd /home/yagoazedias/github/pulso
mkdir -p apps/etl-python/pulso/xml apps/etl-python/pulso/loader apps/etl-python/pulso/ui
mkdir -p apps/etl-python/tests/unit/xml apps/etl-python/tests/unit/ui
mkdir -p apps/etl-python/tests/integration/loader
mkdir -p apps/etl-python/tests/fixtures
mkdir -p apps/etl-python/migrations
touch apps/etl-python/pulso/__init__.py
touch apps/etl-python/pulso/xml/__init__.py
touch apps/etl-python/pulso/loader/__init__.py
touch apps/etl-python/pulso/ui/__init__.py
touch apps/etl-python/tests/__init__.py
touch apps/etl-python/tests/unit/__init__.py
touch apps/etl-python/tests/unit/xml/__init__.py
touch apps/etl-python/tests/unit/ui/__init__.py
touch apps/etl-python/tests/integration/__init__.py
touch apps/etl-python/tests/integration/loader/__init__.py
cp apps/etl-clojure/test-resources/fixtures/small-export.xml apps/etl-python/tests/fixtures/small-export.xml
```

- [ ] **Step 3: Write `pyproject.toml`**

```toml
[project]
name = "pulso-etl"
version = "0.1.0"
description = "Apple Health XML to PostgreSQL ETL"
requires-python = ">=3.12"
dependencies = [
    "psycopg2-binary>=2.9,<3.0",
]

[project.scripts]
pulso = "pulso.cli:main"

[dependency-groups]
dev = [
    "pytest>=8.0,<9.0",
]

[tool.pytest.ini_options]
testpaths = ["tests"]
markers = [
    "integration: DB-dependent integration tests (requires pulso_test database)",
]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["pulso"]
```

- [ ] **Step 4: Pin the Python version and sync dependencies**

```bash
cd apps/etl-python
echo "3.12" > .python-version
uv sync
```

Expected: `uv` downloads Python 3.12 (if not already available) and creates `.venv/` with `psycopg2-binary` and `pytest` installed. Ends with `Installed N packages`.

- [ ] **Step 5: Verify pytest runs (no tests yet, should report 0 collected)**

```bash
uv run pytest
```

Expected: `no tests ran` (or `collected 0 items`) — exits without error.

- [ ] **Step 6: Add Python-specific ignores for the new app**

The repo root `.gitignore` already has a `# Python` section (`__pycache__/`, `*.pyc`, etc.) that covers this app too. Add venv/uv-specific entries:

Add to `.gitignore` right after the existing `# Python` block (after the `staticfiles/` line):

```
.venv/
uv.lock
```

Wait — do **not** ignore `uv.lock`; it must be committed for reproducible builds. Only add:

```
.venv/
```

- [ ] **Step 7: Commit**

```bash
git add apps/etl-python .gitignore
git commit -m "chore(etl-python): scaffold uv-managed Python project"
```

---

### Task 2: `config.py` — DB configuration

**Files:**
- Create: `apps/etl-python/pulso/config.py`

**Interfaces:**
- Produces: `db_spec() -> dict` with keys `dbname`, `host`, `port`, `user`, `password`; `DEFAULT_BATCH_SIZE: int = 5000`. Used by `db.py`, `cli.py`, `conftest.py`.

- [ ] **Step 1: Write `pulso/config.py`**

```python
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
```

- [ ] **Step 2: Verify manually**

```bash
cd apps/etl-python
uv run python -c "from pulso import config; print(config.db_spec()); print(config.DEFAULT_BATCH_SIZE)"
```

Expected: `{'dbname': 'pulso', 'host': 'localhost', 'port': 5432, 'user': 'postgres', 'password': 'postgres'}` then `5000`.

- [ ] **Step 3: Commit**

```bash
git add apps/etl-python/pulso/config.py
git commit -m "feat(etl-python): port config module"
```

---

### Task 3: `xml/transform.py` — element → dict transforms

**Files:**
- Create: `apps/etl-python/pulso/xml/transform.py`
- Test: `apps/etl-python/tests/unit/xml/test_transform.py`

**Interfaces:**
- Consumes: nothing (pure functions over `xml.etree.ElementTree.Element`).
- Produces: `parse_datetime(s) -> datetime|None`, `parse_date(s) -> date|None`, `parse_dbl(s) -> float|None`, `export_date_element_to_map(el) -> dict`, `me_element_to_map(el, locale) -> dict`, `record_element_to_map(el) -> dict`, `workout_element_to_map(el) -> dict`, `correlation_element_to_map(el) -> dict`, `activity_summary_element_to_map(el) -> dict`. All later loader modules and the parser/counter tests depend on element shapes matching these dict keys.

- [ ] **Step 1: Write the failing test file `tests/unit/xml/test_transform.py`**

```python
import xml.etree.ElementTree as ET
from datetime import date, timedelta, timezone

from pulso.xml import transform as t


def _el(tag, attrib, children=None):
    element = ET.Element(tag, attrib)
    for child in children or []:
        element.append(child)
    return element


# --- parse_datetime ---

def test_parse_datetime_valid():
    result = t.parse_datetime("2025-01-01 10:00:00 -0300")
    assert result.year == 2025
    assert result.month == 1
    assert result.day == 1
    assert result.hour == 10
    assert result.minute == 0
    assert result.utcoffset() == timedelta(hours=-3)


def test_parse_datetime_nil_and_empty():
    assert t.parse_datetime(None) is None
    assert t.parse_datetime("") is None


# --- parse_date ---

def test_parse_date_valid():
    result = t.parse_date("1998-10-11")
    assert result == date(1998, 10, 11)


def test_parse_date_nil_and_empty():
    assert t.parse_date(None) is None
    assert t.parse_date("") is None


# --- parse_dbl ---

def test_parse_dbl_valid():
    assert t.parse_dbl("72.5") == 72.5
    assert t.parse_dbl("0") == 0.0
    assert t.parse_dbl("-3.14") == -3.14


def test_parse_dbl_invalid():
    assert t.parse_dbl("abc") is None
    assert t.parse_dbl(None) is None
    assert t.parse_dbl("") is None


# --- export_date_element_to_map ---

def test_export_date_element_to_map():
    element = _el("ExportDate", {"value": "2025-06-15 08:30:00 -0300"})
    result = t.export_date_element_to_map(element)
    assert result["export_date"].year == 2025
    assert result["export_date"].month == 6


# --- me_element_to_map ---

def test_me_element_to_map():
    element = _el("Me", {
        "HKCharacteristicTypeIdentifierDateOfBirth": "1998-10-11",
        "HKCharacteristicTypeIdentifierBiologicalSex": "HKBiologicalSexMale",
        "HKCharacteristicTypeIdentifierBloodType": "HKBloodTypeAPositive",
        "HKCharacteristicTypeIdentifierFitzpatrickSkinType": "HKFitzpatrickSkinTypeIII",
        "HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse": "HKCardioFitnessMedicationsUseNone",
    })
    result = t.me_element_to_map(element, "pt_BR")
    assert result["date_of_birth"] == date(1998, 10, 11)
    assert result["biological_sex"] == "HKBiologicalSexMale"
    assert result["blood_type"] == "HKBloodTypeAPositive"
    assert result["fitzpatrick_skin"] == "HKFitzpatrickSkinTypeIII"
    assert result["cardio_fitness_meds"] == "HKCardioFitnessMedicationsUseNone"
    assert result["locale"] == "pt_BR"


# --- record_element_to_map ---

def test_record_element_to_map_without_metadata():
    element = _el("Record", {
        "type": "HKQuantityTypeIdentifierActiveEnergyBurned",
        "sourceName": "Apple Watch",
        "sourceVersion": "10.0",
        "unit": "kcal",
        "value": "15.3",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 11:00:00 -0300",
        "startDate": "2025-01-15 11:00:00 -0300",
        "endDate": "2025-01-15 11:15:00 -0300",
    })
    result = t.record_element_to_map(element)
    assert result["type"] == "HKQuantityTypeIdentifierActiveEnergyBurned"
    assert result["source_name"] == "Apple Watch"
    assert result["source_version"] == "10.0"
    assert result["unit"] == "kcal"
    assert result["value"] == "15.3"
    assert result["creation_date"] is not None
    assert result["start_date"] is not None
    assert result["end_date"] is not None
    assert result["metadata"] == []


def test_record_element_to_map_with_metadata():
    element = _el("Record", {
        "type": "HKQuantityTypeIdentifierHeartRate",
        "sourceName": "Apple Watch",
        "sourceVersion": "10.0",
        "unit": "count/min",
        "value": "72",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 10:30:00 -0300",
        "startDate": "2025-01-15 10:30:00 -0300",
        "endDate": "2025-01-15 10:30:00 -0300",
    }, children=[
        _el("MetadataEntry", {"key": "HKMetadataKeyHeartRateMotionContext", "value": "1"}),
        _el("MetadataEntry", {"key": "HKMetadataKeyHeartRateSensorLocation", "value": "2"}),
    ])
    result = t.record_element_to_map(element)
    assert len(result["metadata"]) == 2
    assert result["metadata"][0]["key"] == "HKMetadataKeyHeartRateMotionContext"
    assert result["metadata"][0]["value"] == "1"
    assert result["metadata"][1]["key"] == "HKMetadataKeyHeartRateSensorLocation"


# --- workout_element_to_map ---

def test_workout_element_to_map_full():
    element = _el("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeRunning",
        "duration": "30.5",
        "durationUnit": "min",
        "totalDistance": "5.2",
        "totalDistanceUnit": "km",
        "totalEnergyBurned": "350.7",
        "totalEnergyBurnedUnit": "kcal",
        "sourceName": "Apple Watch",
        "sourceVersion": "10.0",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 07:00:00 -0300",
        "startDate": "2025-01-15 07:00:00 -0300",
        "endDate": "2025-01-15 07:30:30 -0300",
    }, children=[
        _el("MetadataEntry", {"key": "HKIndoorWorkout", "value": "0"}),
        _el("WorkoutEvent", {
            "type": "HKWorkoutEventTypePause",
            "date": "2025-01-15 07:15:00 -0300",
            "duration": "1.5",
            "durationUnit": "min",
        }),
        _el("WorkoutStatistics", {
            "type": "HKQuantityTypeIdentifierHeartRate",
            "startDate": "2025-01-15 07:00:00 -0300",
            "endDate": "2025-01-15 07:30:30 -0300",
            "average": "155.2",
            "minimum": "120.0",
            "maximum": "185.5",
            "sum": "",
            "unit": "count/min",
        }),
        _el("WorkoutRoute", {
            "sourceName": "Apple Watch",
            "startDate": "2025-01-15 07:00:00 -0300",
            "endDate": "2025-01-15 07:30:30 -0300",
        }, children=[
            _el("FileReference", {"path": "/workout-routes/route.gpx"}),
        ]),
    ])
    result = t.workout_element_to_map(element)
    assert result["activity_type"] == "HKWorkoutActivityTypeRunning"
    assert result["duration"] == 30.5
    assert result["duration_unit"] == "min"
    assert result["total_distance"] == 5.2
    assert result["total_distance_unit"] == "km"
    assert result["total_energy_burned"] == 350.7
    assert result["total_energy_burned_unit"] == "kcal"
    assert result["creation_date"] is not None
    assert len(result["metadata"]) == 1
    assert result["metadata"][0]["key"] == "HKIndoorWorkout"
    assert len(result["events"]) == 1
    assert result["events"][0]["type"] == "HKWorkoutEventTypePause"
    assert result["events"][0]["duration"] == 1.5
    assert len(result["statistics"]) == 1
    stat = result["statistics"][0]
    assert stat["average"] == 155.2
    assert stat["minimum"] == 120.0
    assert stat["maximum"] == 185.5
    assert stat["sum"] is None  # empty string -> None
    assert stat["unit"] == "count/min"
    assert len(result["routes"]) == 1
    assert result["routes"][0]["file_path"] == "/workout-routes/route.gpx"


def test_workout_element_to_map_empty_children():
    element = _el("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeYoga",
        "duration": "60.0",
        "durationUnit": "min",
        "sourceName": "iPhone",
        "creationDate": "2025-01-15 18:00:00 -0300",
        "startDate": "2025-01-15 18:00:00 -0300",
        "endDate": "2025-01-15 19:00:00 -0300",
    })
    result = t.workout_element_to_map(element)
    assert result["activity_type"] == "HKWorkoutActivityTypeYoga"
    assert result["metadata"] == []
    assert result["events"] == []
    assert result["statistics"] == []
    assert result["routes"] == []


# --- correlation_element_to_map ---

def test_correlation_element_to_map():
    element = _el("Correlation", {
        "type": "HKCorrelationTypeIdentifierBloodPressure",
        "sourceName": "Omron",
        "sourceVersion": "3.0",
        "device": "<<HKDevice>>",
        "creationDate": "2025-01-15 08:00:00 -0300",
        "startDate": "2025-01-15 08:00:00 -0300",
        "endDate": "2025-01-15 08:00:00 -0300",
    }, children=[
        _el("MetadataEntry", {"key": "HKMetadataKeyWasUserEntered", "value": "1"}),
        _el("Record", {
            "type": "HKQuantityTypeIdentifierBloodPressureSystolic",
            "sourceName": "Omron", "sourceVersion": "3.0", "unit": "mmHg", "value": "120",
            "device": "<<HKDevice>>",
            "creationDate": "2025-01-15 08:00:00 -0300",
            "startDate": "2025-01-15 08:00:00 -0300",
            "endDate": "2025-01-15 08:00:00 -0300",
        }),
        _el("Record", {
            "type": "HKQuantityTypeIdentifierBloodPressureDiastolic",
            "sourceName": "Omron", "sourceVersion": "3.0", "unit": "mmHg", "value": "80",
            "device": "<<HKDevice>>",
            "creationDate": "2025-01-15 08:00:00 -0300",
            "startDate": "2025-01-15 08:00:00 -0300",
            "endDate": "2025-01-15 08:00:00 -0300",
        }),
    ])
    result = t.correlation_element_to_map(element)
    assert result["type"] == "HKCorrelationTypeIdentifierBloodPressure"
    assert result["source_name"] == "Omron"
    assert len(result["metadata"]) == 1
    assert len(result["records"]) == 2
    assert result["records"][0]["value"] == "120"
    assert result["records"][1]["value"] == "80"


# --- activity_summary_element_to_map ---

def test_activity_summary_element_to_map():
    element = _el("ActivitySummary", {
        "dateComponents": "2025-01-15",
        "activeEnergyBurned": "450.5",
        "activeEnergyBurnedGoal": "500",
        "activeEnergyBurnedUnit": "kcal",
        "appleMoveTime": "35.2",
        "appleMoveTimeGoal": "30",
        "appleExerciseTime": "42.0",
        "appleExerciseTimeGoal": "30",
        "appleStandHours": "10",
        "appleStandHoursGoal": "12",
    })
    result = t.activity_summary_element_to_map(element)
    assert result["date_components"] == date(2025, 1, 15)
    assert result["active_energy_burned"] == 450.5
    assert result["active_energy_burned_goal"] == 500.0
    assert result["active_energy_burned_unit"] == "kcal"
    assert result["apple_move_time"] == 35.2
    assert result["apple_move_time_goal"] == 30.0
    assert result["apple_exercise_time"] == 42.0
    assert result["apple_exercise_time_goal"] == 30.0
    assert result["apple_stand_hours"] == 10.0
    assert result["apple_stand_hours_goal"] == 12.0


def test_activity_summary_partial():
    element = _el("ActivitySummary", {
        "dateComponents": "2025-01-15",
        "activeEnergyBurned": "100",
        "activeEnergyBurnedUnit": "kcal",
    })
    result = t.activity_summary_element_to_map(element)
    assert result["date_components"] == date(2025, 1, 15)
    assert result["active_energy_burned"] == 100.0
    assert result["active_energy_burned_goal"] is None
    assert result["apple_move_time"] is None
    assert result["apple_exercise_time"] is None
    assert result["apple_stand_hours"] is None
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
uv run pytest tests/unit/xml/test_transform.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.xml.transform'` (or collection error).

- [ ] **Step 3: Write `pulso/xml/transform.py`**

```python
from datetime import datetime, date

DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S %z"


def parse_datetime(s):
    if not s:
        return None
    return datetime.strptime(s, DATETIME_FORMAT)


def parse_date(s):
    if not s:
        return None
    return date.fromisoformat(s)


def parse_dbl(s):
    if not s:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _extract_metadata(element):
    return [
        {"key": e.attrib.get("key"), "value": e.attrib.get("value")}
        for e in element.findall("MetadataEntry")
    ]


def export_date_element_to_map(element):
    return {"export_date": parse_datetime(element.attrib.get("value"))}


def me_element_to_map(element, locale):
    a = element.attrib
    return {
        "date_of_birth": parse_date(a.get("HKCharacteristicTypeIdentifierDateOfBirth")),
        "biological_sex": a.get("HKCharacteristicTypeIdentifierBiologicalSex"),
        "blood_type": a.get("HKCharacteristicTypeIdentifierBloodType"),
        "fitzpatrick_skin": a.get("HKCharacteristicTypeIdentifierFitzpatrickSkinType"),
        "cardio_fitness_meds": a.get("HKCharacteristicTypeIdentifierCardioFitnessMedicationsUse"),
        "locale": locale,
    }


def record_element_to_map(element):
    a = element.attrib
    return {
        "type": a.get("type"),
        "source_name": a.get("sourceName"),
        "source_version": a.get("sourceVersion"),
        "unit": a.get("unit"),
        "value": a.get("value"),
        "device": a.get("device"),
        "creation_date": parse_datetime(a.get("creationDate")),
        "start_date": parse_datetime(a.get("startDate")),
        "end_date": parse_datetime(a.get("endDate")),
        "metadata": _extract_metadata(element),
    }


def workout_element_to_map(element):
    a = element.attrib

    events = [
        {
            "type": e.attrib.get("type"),
            "date": parse_datetime(e.attrib.get("date")),
            "duration": parse_dbl(e.attrib.get("duration")),
            "duration_unit": e.attrib.get("durationUnit"),
        }
        for e in element.findall("WorkoutEvent")
    ]

    statistics = [
        {
            "type": s.attrib.get("type"),
            "start_date": parse_datetime(s.attrib.get("startDate")),
            "end_date": parse_datetime(s.attrib.get("endDate")),
            "average": parse_dbl(s.attrib.get("average")),
            "minimum": parse_dbl(s.attrib.get("minimum")),
            "maximum": parse_dbl(s.attrib.get("maximum")),
            "sum": parse_dbl(s.attrib.get("sum")),
            "unit": s.attrib.get("unit"),
        }
        for s in element.findall("WorkoutStatistics")
    ]

    routes = []
    for r in element.findall("WorkoutRoute"):
        file_ref = r.find("FileReference")
        routes.append({
            "source_name": r.attrib.get("sourceName"),
            "start_date": parse_datetime(r.attrib.get("startDate")),
            "end_date": parse_datetime(r.attrib.get("endDate")),
            "file_path": file_ref.attrib.get("path") if file_ref is not None else None,
        })

    return {
        "activity_type": a.get("workoutActivityType"),
        "duration": parse_dbl(a.get("duration")),
        "duration_unit": a.get("durationUnit"),
        "total_distance": parse_dbl(a.get("totalDistance")),
        "total_distance_unit": a.get("totalDistanceUnit"),
        "total_energy_burned": parse_dbl(a.get("totalEnergyBurned")),
        "total_energy_burned_unit": a.get("totalEnergyBurnedUnit"),
        "source_name": a.get("sourceName"),
        "source_version": a.get("sourceVersion"),
        "device": a.get("device"),
        "creation_date": parse_datetime(a.get("creationDate")),
        "start_date": parse_datetime(a.get("startDate")),
        "end_date": parse_datetime(a.get("endDate")),
        "metadata": _extract_metadata(element),
        "events": events,
        "statistics": statistics,
        "routes": routes,
    }


def correlation_element_to_map(element):
    a = element.attrib
    return {
        "type": a.get("type"),
        "source_name": a.get("sourceName"),
        "source_version": a.get("sourceVersion"),
        "device": a.get("device"),
        "creation_date": parse_datetime(a.get("creationDate")),
        "start_date": parse_datetime(a.get("startDate")),
        "end_date": parse_datetime(a.get("endDate")),
        "metadata": _extract_metadata(element),
        "records": [record_element_to_map(r) for r in element.findall("Record")],
    }


def activity_summary_element_to_map(element):
    a = element.attrib
    return {
        "date_components": parse_date(a.get("dateComponents")),
        "active_energy_burned": parse_dbl(a.get("activeEnergyBurned")),
        "active_energy_burned_goal": parse_dbl(a.get("activeEnergyBurnedGoal")),
        "active_energy_burned_unit": a.get("activeEnergyBurnedUnit"),
        "apple_move_time": parse_dbl(a.get("appleMoveTime")),
        "apple_move_time_goal": parse_dbl(a.get("appleMoveTimeGoal")),
        "apple_exercise_time": parse_dbl(a.get("appleExerciseTime")),
        "apple_exercise_time_goal": parse_dbl(a.get("appleExerciseTimeGoal")),
        "apple_stand_hours": parse_dbl(a.get("appleStandHours")),
        "apple_stand_hours_goal": parse_dbl(a.get("appleStandHoursGoal")),
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
uv run pytest tests/unit/xml/test_transform.py -v
```

Expected: 15 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/xml/transform.py apps/etl-python/tests/unit/xml/test_transform.py
git commit -m "feat(etl-python): port xml.transform module"
```

---

### Task 4: `xml/parser.py` — streaming element dispatch

**Files:**
- Create: `apps/etl-python/pulso/xml/parser.py`
- Test: `apps/etl-python/tests/unit/xml/test_parser.py`

**Interfaces:**
- Consumes: nothing new.
- Produces: `parse_health_data(xml_file: str, handler_fn: Callable[[Element, str|None], None]) -> None`. Called by `etl.py` (Task 17).

- [ ] **Step 1: Write the failing test file `tests/unit/xml/test_parser.py`**

```python
import os
import xml.etree.ElementTree as ET

from pulso.xml import parser

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "..", "fixtures", "small-export.xml")


def test_parse_health_data_dispatches_all_elements():
    collected = []
    locale_seen = []

    def handler(element, locale):
        locale_seen.append(locale)
        collected.append(element.tag)

    parser.parse_health_data(FIXTURE, handler)

    assert locale_seen[-1] == "pt_BR"
    assert collected == ["ExportDate", "Me", "Record", "Record", "Workout", "Correlation", "ActivitySummary"]


def test_parse_health_data_with_doctype(tmp_path):
    xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE HealthData [
  <!ATTLIST ExportDate value CDATA #REQUIRED>
  <!ATTLIST Record type CDATA #REQUIRED>
]>
<HealthData locale="en_US">
 <ExportDate value="2025-01-01 00:00:00 +0000"/>
 <Record type="HKQuantityTypeIdentifierStepCount"
         sourceName="iPhone"
         value="100"
         creationDate="2025-01-01 12:00:00 +0000"
         startDate="2025-01-01 12:00:00 +0000"
         endDate="2025-01-01 12:30:00 +0000"/>
</HealthData>"""
    xml_path = tmp_path / "health-doctype.xml"
    xml_path.write_text(xml_content)

    tags = []
    parser.parse_health_data(str(xml_path), lambda element, locale: tags.append(element.tag))

    assert tags == ["ExportDate", "Record"]


def test_parse_health_data_skips_whitespace():
    non_element_count = 0

    def handler(element, locale):
        nonlocal non_element_count
        if not isinstance(element, ET.Element):
            non_element_count += 1

    parser.parse_health_data(FIXTURE, handler)

    assert non_element_count == 0
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
uv run pytest tests/unit/xml/test_parser.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.xml.parser'`.

- [ ] **Step 3: Write `pulso/xml/parser.py`**

```python
import logging
import xml.etree.ElementTree as ET

logger = logging.getLogger(__name__)


def parse_health_data(xml_file, handler_fn):
    """Streams the Apple Health XML file and calls handler_fn(element, locale)
    for each direct child of the root element. Uses ElementTree.iterparse so
    the full document is never held in memory: each processed child is
    dropped from the root's children list once handled."""
    logger.info("Parsing XML file: %s", xml_file)
    context = ET.iterparse(xml_file, events=("start", "end"))
    root = None
    locale = None
    depth = 0
    for event, elem in context:
        if event == "start":
            depth += 1
            if root is None:
                root = elem
                locale = elem.attrib.get("locale")
                logger.info("Root tag: %s locale: %s", root.tag, locale)
        else:
            depth -= 1
            if depth == 1:
                handler_fn(elem, locale)
                root.remove(elem)
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
uv run pytest tests/unit/xml/test_parser.py -v
```

Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/xml/parser.py apps/etl-python/tests/unit/xml/test_parser.py
git commit -m "feat(etl-python): port xml.parser module using native iterparse DOCTYPE handling"
```

---

### Task 5: `xml/counter.py` — first-pass element counting

**Files:**
- Create: `apps/etl-python/pulso/xml/counter.py`
- Test: `apps/etl-python/tests/unit/xml/test_counter.py`

**Interfaces:**
- Produces: `count_elements(xml_file: str) -> dict[str, int]`. Used by `cli.py` (Task 18) to compute progress-bar totals.

- [ ] **Step 1: Write the failing test file `tests/unit/xml/test_counter.py`**

```python
import os

from pulso.xml import counter

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "..", "fixtures", "small-export.xml")


def test_count_elements_fixture():
    counts = counter.count_elements(FIXTURE)

    assert counts["Record"] == 2
    assert counts["Workout"] == 1
    assert counts["Correlation"] == 1
    assert counts["ActivitySummary"] == 1
    assert counts["ExportDate"] == 1
    assert counts["Me"] == 1

    assert "MetadataEntry" not in counts
    assert "WorkoutEvent" not in counts
    assert "WorkoutStatistics" not in counts


def test_count_elements_with_doctype(tmp_path):
    xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE HealthData [
  <!ATTLIST ExportDate value CDATA #REQUIRED>
]>
<HealthData locale="en_US">
 <ExportDate value="2025-01-01 00:00:00 +0000"/>
 <Record type="StepCount" value="100"
         creationDate="2025-01-01" startDate="2025-01-01" endDate="2025-01-01"/>
 <Record type="HeartRate" value="72"
         creationDate="2025-01-01" startDate="2025-01-01" endDate="2025-01-01"/>
 <Record type="Distance" value="1.5"
         creationDate="2025-01-01" startDate="2025-01-01" endDate="2025-01-01"/>
</HealthData>"""
    xml_path = tmp_path / "health-count.xml"
    xml_path.write_text(xml_content)

    counts = counter.count_elements(str(xml_path))

    assert counts["Record"] == 3
    assert counts["ExportDate"] == 1
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
uv run pytest tests/unit/xml/test_counter.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.xml.counter'`.

- [ ] **Step 3: Write `pulso/xml/counter.py`**

```python
import logging
import time
import xml.etree.ElementTree as ET
from collections import defaultdict

logger = logging.getLogger(__name__)


def count_elements(xml_file):
    """Counts direct children of the root element (depth 2), without
    retaining parsed subtrees."""
    logger.info("Counting elements in: %s", xml_file)
    start = time.time()
    counts = defaultdict(int)
    context = ET.iterparse(xml_file, events=("start", "end"))
    root = None
    depth = 0
    for event, elem in context:
        if event == "start":
            depth += 1
            if root is None:
                root = elem
            elif depth == 2:
                counts[elem.tag] += 1
        else:
            depth -= 1
            if depth == 1 and elem is not root:
                root.remove(elem)

    elapsed_ms = int((time.time() - start) * 1000)
    result = dict(counts)
    logger.info("Element counting complete counts=%s elapsed_ms=%s", result, elapsed_ms)
    return result
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
uv run pytest tests/unit/xml/test_counter.py -v
```

Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/xml/counter.py apps/etl-python/tests/unit/xml/test_counter.py
git commit -m "feat(etl-python): port xml.counter module"
```

---

### Task 6: `progress.py` — progress state tracking

**Files:**
- Create: `apps/etl-python/pulso/progress.py`
- Test: `apps/etl-python/tests/unit/test_progress.py`

**Interfaces:**
- Produces: `TRACKED_TYPES: list[str]`, `make_state(totals: dict) -> dict`, `record_progress(state: dict, element_type: str) -> None`, `snapshot(state: dict) -> dict`. Used by `cli.py` and `ui/terminal.py` (Task 7, Task 18).

- [ ] **Step 1: Write the failing test file `tests/unit/test_progress.py`**

```python
import time

from pulso import progress


def test_make_state_initializes_correctly():
    totals = {"Record": 1000, "Workout": 50, "Correlation": 20, "ActivitySummary": 365, "Me": 1}
    state = progress.make_state(totals)

    assert set(state["totals"].keys()) == {"Record", "Workout", "Correlation", "ActivitySummary"}
    assert "Me" not in state["totals"]
    assert all(v == 0 for v in state["processed"].values())


def test_record_progress_increments():
    state = progress.make_state({"Record": 100, "Workout": 10, "Correlation": 5, "ActivitySummary": 30})

    progress.record_progress(state, "Record")
    progress.record_progress(state, "Record")
    progress.record_progress(state, "Workout")

    assert state["processed"]["Record"] == 2
    assert state["processed"]["Workout"] == 1
    assert state["processed"]["Correlation"] == 0


def test_record_progress_ignores_untracked():
    state = progress.make_state({"Record": 100})

    progress.record_progress(state, "ExportDate")
    progress.record_progress(state, "Me")

    assert state["processed"]["Record"] == 0


def test_snapshot_calculates_percentages():
    state = {
        "totals": {"Record": 1000, "Workout": 50, "Correlation": 20, "ActivitySummary": 365},
        "processed": {"Record": 500, "Workout": 50, "Correlation": 10, "ActivitySummary": 0},
        "start_ms": time.time() * 1000 - 10000,
    }
    snap = progress.snapshot(state)

    record_info = next(x for x in snap["types"] if x["type"] == "Record")
    assert record_info["processed"] == 500
    assert record_info["total"] == 1000
    assert 49.9 < record_info["pct"] < 50.1

    workout_info = next(x for x in snap["types"] if x["type"] == "Workout")
    assert workout_info["pct"] == 100.0

    assert snap["overall_processed"] == 560
    assert snap["overall_total"] == 1435
    assert 38.0 < snap["overall_pct"] < 40.0

    assert snap["rate"] > 0
    assert snap["eta_secs"] > 0


def test_snapshot_handles_zero_totals():
    state = {
        "totals": {"Record": 0, "Workout": 0, "Correlation": 0, "ActivitySummary": 0},
        "processed": {"Record": 0, "Workout": 0, "Correlation": 0, "ActivitySummary": 0},
        "start_ms": time.time() * 1000,
    }
    snap = progress.snapshot(state)

    assert snap["overall_pct"] == 0.0
    assert snap["eta_secs"] == 0
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
uv run pytest tests/unit/test_progress.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.progress'`.

- [ ] **Step 3: Write `pulso/progress.py`**

```python
import time

TRACKED_TYPES = ["Record", "Workout", "Correlation", "ActivitySummary"]


def make_state(totals):
    return {
        "totals": {t: totals[t] for t in TRACKED_TYPES if t in totals},
        "processed": {t: 0 for t in TRACKED_TYPES},
        "start_ms": time.time() * 1000,
    }


def record_progress(state, element_type):
    if element_type in state["processed"]:
        state["processed"][element_type] += 1


def snapshot(state):
    totals = state["totals"]
    processed = state["processed"]
    now_ms = time.time() * 1000
    elapsed_ms = max(1.0, now_ms - state["start_ms"])
    elapsed_s = elapsed_ms / 1000.0

    types = []
    for t in TRACKED_TYPES:
        proc = processed.get(t, 0)
        tot = totals.get(t, 0)
        pct = (100.0 * proc / tot) if tot > 0 else 0.0
        types.append({"type": t, "processed": proc, "total": tot, "pct": pct})

    overall_processed = sum(x["processed"] for x in types)
    overall_total = sum(x["total"] for x in types)
    overall_pct = (100.0 * overall_processed / overall_total) if overall_total > 0 else 0.0
    rate = int(overall_processed / elapsed_s) if elapsed_s > 0 else 0
    remaining = overall_total - overall_processed
    eta_secs = int(remaining / rate) if rate > 0 else 0

    return {
        "types": types,
        "overall_processed": overall_processed,
        "overall_total": overall_total,
        "overall_pct": overall_pct,
        "rate": rate,
        "eta_secs": eta_secs,
        "elapsed_secs": int(elapsed_s),
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
uv run pytest tests/unit/test_progress.py -v
```

Expected: 5 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/progress.py apps/etl-python/tests/unit/test_progress.py
git commit -m "feat(etl-python): port progress module"
```

---

### Task 7: `ui/terminal.py` — live terminal progress renderer

**Files:**
- Create: `apps/etl-python/pulso/ui/terminal.py`
- Test: `apps/etl-python/tests/unit/ui/test_terminal.py`

**Interfaces:**
- Consumes: snapshot dicts shaped like `progress.snapshot()`'s return value (Task 6).
- Produces: `format_number(n) -> str`, `format_duration(secs) -> str`, `progress_bar(pct, width) -> str`, `render_type_line(type_info: dict) -> str`, `render_frame(snap: dict, filename: str) -> list[str]`, `start_renderer(state, snapshot_fn, filename) -> Renderer` (with `.stop()`). Used by `cli.py` (Task 18).

- [ ] **Step 1: Write the failing test file `tests/unit/ui/test_terminal.py`**

```python
from pulso.ui import terminal


# --- format_number ---

def test_format_number():
    assert terminal.format_number(0) == "0"
    assert terminal.format_number(42) == "42"
    assert terminal.format_number(999) == "999"
    assert terminal.format_number(1000) == "1,000"
    assert terminal.format_number(1234567) == "1,234,567"
    assert terminal.format_number(10000000) == "10,000,000"


# --- format_duration ---

def test_format_duration():
    assert terminal.format_duration(0) == "0:00"
    assert terminal.format_duration(5) == "0:05"
    assert terminal.format_duration(59) == "0:59"
    assert terminal.format_duration(60) == "1:00"
    assert terminal.format_duration(65) == "1:05"
    assert terminal.format_duration(630) == "10:30"


# --- progress_bar ---

def test_progress_bar():
    assert terminal.progress_bar(0, 10) == "[░░░░░░░░░░]"
    assert terminal.progress_bar(100, 10) == "[██████████]"
    assert terminal.progress_bar(50, 10) == "[█████░░░░░]"
    assert terminal.progress_bar(150, 10) == "[██████████]"


# --- render_type_line ---

def test_render_type_line():
    line = terminal.render_type_line({"type": "Record", "processed": 500, "total": 1000, "pct": 50.0})
    assert "Record" in line
    assert "50.0%" in line
    assert "500" in line
    assert "1,000" in line


# --- render_frame ---

def test_render_frame():
    snap = {
        "types": [
            {"type": "Record", "processed": 500, "total": 1000, "pct": 50.0},
            {"type": "Workout", "processed": 10, "total": 20, "pct": 50.0},
        ],
        "overall_processed": 510,
        "overall_total": 1020,
        "overall_pct": 50.0,
        "rate": 100,
        "eta_secs": 5,
        "elapsed_secs": 65,
    }
    lines = terminal.render_frame(snap, "export.xml")

    assert isinstance(lines, list)
    assert all(isinstance(line, str) for line in lines)
    assert "export.xml" in lines[0]
    assert any("─" in line for line in lines)
    assert any("Record" in line for line in lines)
    assert any("Workout" in line for line in lines)
    summary = lines[-1]
    assert "50.0%" in summary
    assert "1:05" in summary
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
uv run pytest tests/unit/ui/test_terminal.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.ui.terminal'`.

- [ ] **Step 3: Write `pulso/ui/terminal.py`**

```python
import sys
import threading
import time

BAR_WIDTH = 30
RENDER_INTERVAL_S = 0.1


def format_number(n):
    return f"{int(n):,}"


def format_duration(secs):
    secs = int(secs)
    m, s = divmod(secs, 60)
    return f"{m}:{s:02d}"


def progress_bar(pct, width):
    filled = min(int((pct / 100.0) * width), width)
    empty = width - filled
    return "[" + ("█" * filled) + ("░" * empty) + "]"


def render_type_line(type_info):
    label = f"{type_info['type']:<15}"
    bar = progress_bar(type_info["pct"], BAR_WIDTH)
    pct_str = f"{type_info['pct']:5.1f}%"
    nums = f"{int(type_info['processed']):,} / {int(type_info['total']):,}"
    return f"  {label} {bar} {pct_str}  {nums}"


def render_frame(snap, filename):
    header = f"Pulso ETL - Processing {filename}"
    divider = "─" * 74
    type_lines = [render_type_line(t) for t in snap["types"]]
    summary = (
        f"  Overall: {snap['overall_pct']:5.1f}%  |  "
        f"{format_number(snap['overall_processed'])} / {format_number(snap['overall_total'])}  |  "
        f"{format_number(snap['rate'])}/s  |  ETA {snap['eta_secs']}s  |  "
        f"{format_duration(snap['elapsed_secs'])}"
    )
    return [header, divider, *type_lines, divider, summary]


def _move_cursor_up(n):
    return f"\033[{n}A"


def _clear_line():
    return "\033[2K\r"


def _hide_cursor():
    return "\033[?25l"


def _show_cursor():
    return "\033[?25h"


class Renderer:
    """Runs a daemon thread that redraws progress bars at ~10 FPS using
    cursor-up + overwrite for flicker-free in-place updates."""

    def __init__(self, state, snapshot_fn, filename):
        self._state = state
        self._snapshot_fn = snapshot_fn
        self._filename = filename
        self._running = threading.Event()
        self._running.set()
        self._lines_printed = 0
        self._thread = threading.Thread(
            target=self._run, daemon=True, name="pulso-progress-renderer"
        )
        self._thread.start()

    def _print_frame(self, lines):
        if self._lines_printed > 0:
            sys.stdout.write(_move_cursor_up(self._lines_printed))
        for line in lines:
            sys.stdout.write(_clear_line() + line + "\n")
        sys.stdout.flush()
        self._lines_printed = len(lines)

    def _run(self):
        sys.stdout.write(_hide_cursor())
        sys.stdout.flush()
        try:
            while self._running.is_set():
                snap = self._snapshot_fn(self._state)
                self._print_frame(render_frame(snap, self._filename))
                time.sleep(RENDER_INTERVAL_S)
        finally:
            sys.stdout.write(_show_cursor())
            sys.stdout.flush()

    def stop(self):
        self._running.clear()
        self._thread.join(timeout=0.5)
        snap = self._snapshot_fn(self._state)
        self._print_frame(render_frame(snap, self._filename))


def start_renderer(state, snapshot_fn, filename):
    return Renderer(state, snapshot_fn, filename)
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
uv run pytest tests/unit/ui/test_terminal.py -v
```

Expected: 5 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/ui/terminal.py apps/etl-python/tests/unit/ui/test_terminal.py
git commit -m "feat(etl-python): port ui.terminal module (direct ANSI port, no rich dependency)"
```

---

### Task 8: Migrations + `db.py` — connection pool, migration runner, truncate

**Files:**
- Create: `apps/etl-python/migrations/20260214160000-create-lookup-tables.up.sql`
- Create: `apps/etl-python/migrations/20260214160100-create-user-profile.up.sql`
- Create: `apps/etl-python/migrations/20260214160200-create-record-tables.up.sql`
- Create: `apps/etl-python/migrations/20260214160300-create-workout-tables.up.sql`
- Create: `apps/etl-python/migrations/20260214160400-create-correlation-tables.up.sql`
- Create: `apps/etl-python/migrations/20260214160500-create-activity-summary.up.sql`
- Create: `apps/etl-python/pulso/db.py`

**Interfaces:**
- Produces: `create_pool(db_spec, minconn=1, maxconn=10) -> SimpleConnectionPool`, `get_conn(pool) -> context manager yielding a connection`, `migrate(pool, migrations_dir) -> None`, `truncate_all(pool) -> None`, `MIGRATIONS_DIR: Path` (resolved relative to this file, pointing at `apps/etl-python/migrations/`). Used by every loader module (Tasks 10–17), `conftest.py` (Task 9), and `cli.py` (Task 18).

- [ ] **Step 1: Copy the migration SQL files as-is (same schema, same filenames)**

```bash
cd /home/yagoazedias/github/pulso
cp apps/etl-clojure/resources/migrations/20260214160000-create-lookup-tables.up.sql apps/etl-python/migrations/
cp apps/etl-clojure/resources/migrations/20260214160100-create-user-profile.up.sql apps/etl-python/migrations/
cp apps/etl-clojure/resources/migrations/20260214160200-create-record-tables.up.sql apps/etl-python/migrations/
cp apps/etl-clojure/resources/migrations/20260214160300-create-workout-tables.up.sql apps/etl-python/migrations/
cp apps/etl-clojure/resources/migrations/20260214160400-create-correlation-tables.up.sql apps/etl-python/migrations/
cp apps/etl-clojure/resources/migrations/20260214160500-create-activity-summary.up.sql apps/etl-python/migrations/
ls apps/etl-python/migrations/
```

Expected: 6 `.up.sql` files listed. (Down-migrations are not ported — the runner is forward-only, matching how `db.clj` only ever calls `migratus/migrate`, never `rollback`.)

- [ ] **Step 2: Write `pulso/db.py`**

```python
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
```

- [ ] **Step 3: Verify the module imports cleanly**

```bash
cd apps/etl-python
uv run python -c "from pulso import db; print(db.MIGRATIONS_DIR)"
```

Expected: prints the absolute path to `apps/etl-python/migrations`, no errors. (No dedicated test file for `db.py` — the Clojure `db.clj` has none either; it's exercised through the integration test fixtures in Task 9 onward.)

- [ ] **Step 4: Commit**

```bash
git add apps/etl-python/migrations apps/etl-python/pulso/db.py
git commit -m "feat(etl-python): port migrations and db module (pool, migration runner, truncate)"
```

---

### Task 9: `conftest.py` — integration test infrastructure

**Files:**
- Create: `apps/etl-python/tests/integration/conftest.py`

**Interfaces:**
- Consumes: `db.create_pool`, `db.migrate`, `db.truncate_all`, `db.get_conn` (Task 8); `config.db_spec` (Task 2).
- Produces: pytest fixtures `test_ds` (truncates tables, yields the pool), `count_rows` (fixture factory), `select_all` (fixture factory). Used by every integration test from Task 10 onward. **Note:** Tasks 11 and 12 each extend this same file to also reset the `lookups` cache and `profile` state respectively — they add that wiring when those modules are created, since `conftest.py` can't import modules that don't exist yet.

- [ ] **Step 1: Write `tests/integration/conftest.py`**

```python
import os

import psycopg2.extras
import pytest

from pulso import config, db

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
    """Per-test: truncates all tables before handing back the connection
    pool. Tasks 11/12 extend this to also reset the lookups cache and
    profile state once those modules exist."""
    pool = _get_pool()
    db.truncate_all(pool)
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
```

`table` in `count_rows`/`select_all` is always a hardcoded literal from test code, never external input — same trust boundary as the Clojure `test-helpers.clj` version, which builds the same kind of string-concatenated SQL.

- [ ] **Step 2: Add `PGHOST`-independent local Postgres for verification, then run the (still-empty) integration suite to confirm fixtures wire up**

This step needs a real `pulso_test` Postgres database. If you don't already have one running locally:

```bash
docker run -d --name pulso-test-db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=pulso_test -p 5432:5432 postgres:17-alpine
# wait for it to be healthy
until docker exec pulso-test-db pg_isready -U postgres; do sleep 1; done
```

Then run:

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration -v
```

Expected: `no tests ran` (0 collected — no integration test files exist yet) but **no import errors**, and no connection errors (confirms the session-scoped `_migrate_once` fixture successfully connected and ran the 6 migrations against `pulso_test`). If this fails to connect, fix the DB connection before proceeding — every remaining integration task depends on it.

- [ ] **Step 3: Commit**

```bash
git add apps/etl-python/tests/integration/conftest.py
git commit -m "feat(etl-python): port test-helpers as pytest fixtures (test_ds, count_rows, select_all)"
```

---

### Task 10: `loader/batch.py` — generic batch insert machinery

**Files:**
- Create: `apps/etl-python/pulso/loader/batch.py`
- Test: `apps/etl-python/tests/integration/loader/test_batch.py`

**Interfaces:**
- Consumes: `db.get_conn` (Task 8); `test_ds`, `count_rows` fixtures (Task 9).
- Produces: `Batcher(pool, table, columns, batch_size)` with `.add(row: tuple)`, `.flush()`, `.count() -> int`; `ReturningBatcher(pool, table, columns, batch_size)` with `.add(row: tuple, metadata) -> list[dict]|None`, `.flush() -> list[dict]|None`, `.count() -> int` where each returned dict is `{"id": <generated id>, "metadata": <metadata passed to add>}`. Used by `loader/records.py`, `loader/workouts.py`, `loader/correlations.py`, `loader/activity.py` (Tasks 13, 14, 15, 16).

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_batch.py`**

```python
from datetime import date

import pytest

from pulso.loader.batch import Batcher

pytestmark = pytest.mark.integration


def test_batcher_flushes_at_batch_size(test_ds, count_rows):
    batcher = Batcher(test_ds, "activity_summary", ["date_components", "active_energy_burned"], 2)

    batcher.add((date(2025, 1, 1), 100.0))
    batcher.add((date(2025, 1, 2), 200.0))
    batcher.add((date(2025, 1, 3), 300.0))

    assert count_rows("activity_summary") == 2
    assert batcher.count() == 3


def test_batcher_flush_partial(test_ds, count_rows):
    batcher = Batcher(test_ds, "activity_summary", ["date_components", "active_energy_burned"], 5)
    batcher.add((date(2025, 1, 1), 100.0))

    batcher.flush()

    assert count_rows("activity_summary") == 1
    assert batcher.count() == 1


def test_batcher_empty_flush_noop(test_ds, count_rows):
    batcher = Batcher(test_ds, "activity_summary", ["date_components", "active_energy_burned"], 5)

    batcher.flush()

    assert count_rows("activity_summary") == 0
    assert batcher.count() == 0
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_batch.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.batch'`.

- [ ] **Step 3: Write `pulso/loader/batch.py`**

```python
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_batch.py -v
```

Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/loader/batch.py apps/etl-python/tests/integration/loader/test_batch.py
git commit -m "feat(etl-python): port loader.batch module (Batcher, ReturningBatcher)"
```

---

### Task 11: `loader/lookups.py` — dimension table cache & upsert

**Files:**
- Create: `apps/etl-python/pulso/loader/lookups.py`
- Modify: `apps/etl-python/tests/integration/conftest.py`
- Test: `apps/etl-python/tests/integration/loader/test_lookups.py`

**Interfaces:**
- Consumes: `db.get_conn` (Task 8); `test_ds`, `count_rows` fixtures (Task 9).
- Produces: `reset_caches() -> None`, `ensure_source_id(pool, source_name, source_version) -> int|None`, `ensure_device_id(pool, raw_text) -> int|None`, `ensure_record_type_id(pool, identifier) -> int|None`, `ensure_unit_id(pool, unit_name) -> int|None`. Used by `loader/records.py`, `loader/workouts.py`, `loader/correlations.py` (Tasks 13–15). This task also wires `reset_caches()` into the shared `test_ds` fixture so every later integration test gets cache isolation.

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_lookups.py`**

```python
import pytest

from pulso.loader import lookups

pytestmark = pytest.mark.integration


def test_ensure_source_id_creates_and_caches(test_ds, count_rows):
    id1 = lookups.ensure_source_id(test_ds, "iPhone", "15.0")
    id2 = lookups.ensure_source_id(test_ds, "iPhone", "15.0")

    assert id1 == id2
    assert count_rows("source") == 1


def test_ensure_source_id_different_versions(test_ds, count_rows):
    id1 = lookups.ensure_source_id(test_ds, "iPhone", "15.0")
    id2 = lookups.ensure_source_id(test_ds, "iPhone", "16.0")

    assert id1 != id2
    assert count_rows("source") == 2


def test_ensure_source_id_nil_returns_nil(test_ds, count_rows):
    result = lookups.ensure_source_id(test_ds, None, "15.0")

    assert result is None
    assert count_rows("source") == 0


def test_ensure_device_id_creates_and_caches(test_ds, count_rows):
    id1 = lookups.ensure_device_id(test_ds, "iPhone (User's Device)")
    id2 = lookups.ensure_device_id(test_ds, "iPhone (User's Device)")

    assert id1 == id2
    assert count_rows("device") == 1


def test_ensure_unit_id_creates_and_caches(test_ds, count_rows):
    id1 = lookups.ensure_unit_id(test_ds, "count/min")
    id2 = lookups.ensure_unit_id(test_ds, "count/min")

    assert id1 == id2
    assert count_rows("unit") == 1
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_lookups.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.lookups'`.

- [ ] **Step 3: Write `pulso/loader/lookups.py`**

```python
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
```

- [ ] **Step 4: Wire cache reset into `tests/integration/conftest.py`**

In `apps/etl-python/tests/integration/conftest.py`, add the import:

```python
from pulso import config, db
from pulso.loader import lookups
```

And update the `test_ds` fixture body:

```python
@pytest.fixture
def test_ds():
    """Per-test: truncates all tables and resets the lookups cache before
    handing back the connection pool."""
    pool = _get_pool()
    db.truncate_all(pool)
    lookups.reset_caches()
    yield pool
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_lookups.py -v
```

Expected: 5 passed.

- [ ] **Step 6: Commit**

```bash
git add apps/etl-python/pulso/loader/lookups.py apps/etl-python/tests/integration/loader/test_lookups.py apps/etl-python/tests/integration/conftest.py
git commit -m "feat(etl-python): port loader.lookups module, wire cache reset into test fixtures"
```

---

### Task 12: `loader/profile.py` — user profile + export date

**Files:**
- Create: `apps/etl-python/pulso/loader/profile.py`
- Modify: `apps/etl-python/tests/integration/conftest.py`
- Test: `apps/etl-python/tests/integration/loader/test_profile.py`

**Interfaces:**
- Consumes: `db.get_conn` (Task 8); `xml.transform.export_date_element_to_map`, `xml.transform.me_element_to_map` (Task 3); `test_ds`, `count_rows`, `select_all` fixtures (Task 9).
- Produces: `reset_state() -> None`, `save_export_date(element) -> None`, `save_profile(pool, element, locale) -> None`. Used by `etl.py` (Task 17). This task also wires `reset_state()` into the shared `test_ds` fixture, matching the Clojure `with-db` fixture's belt-and-suspenders reset (each test also calls `reset_state()` itself, same as the original tests call `profile/reset-state!` directly).

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_profile.py`**

```python
import xml.etree.ElementTree as ET
from datetime import timezone

import pytest

from pulso.loader import profile

pytestmark = pytest.mark.integration


def test_save_export_date_stores_value(test_ds, count_rows, select_all):
    profile.reset_state()
    export_element = ET.Element("ExportDate", {"value": "2025-01-15 12:00:00 -0300"})

    profile.save_export_date(export_element)
    me_element = ET.Element("Me", {
        "HKCharacteristicTypeIdentifierDateOfBirth": "1990-05-15",
        "HKCharacteristicTypeIdentifierBiologicalSex": "HKBiologicalSexMale",
    })
    profile.save_profile(test_ds, me_element, "pt_BR")

    assert count_rows("user_profile") == 1
    row = select_all("user_profile")[0]
    export_date_utc = row["export_date"].astimezone(timezone.utc)
    assert export_date_utc.year == 2025
    assert export_date_utc.month == 1
    assert export_date_utc.day == 15


def test_save_profile_inserts_row(test_ds, count_rows, select_all):
    profile.reset_state()
    export_element = ET.Element("ExportDate", {"value": "2025-01-15 12:00:00 -0300"})
    me_element = ET.Element("Me", {
        "HKCharacteristicTypeIdentifierDateOfBirth": "1990-05-15",
        "HKCharacteristicTypeIdentifierBiologicalSex": "HKBiologicalSexMale",
    })

    profile.save_export_date(export_element)
    profile.save_profile(test_ds, me_element, "pt_BR")

    assert count_rows("user_profile") == 1
    row = select_all("user_profile")[0]
    assert str(row["date_of_birth"]) == "1990-05-15"
    assert row["biological_sex"] == "HKBiologicalSexMale"
    assert row["locale"] == "pt_BR"
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_profile.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.profile'`.

- [ ] **Step 3: Write `pulso/loader/profile.py`**

```python
import logging

from pulso import db
from pulso.xml import transform

logger = logging.getLogger(__name__)

_export_date = None


def reset_state():
    global _export_date
    _export_date = None


def save_export_date(element):
    global _export_date
    data = transform.export_date_element_to_map(element)
    _export_date = data["export_date"]
    logger.info("Export date: %s", _export_date)


def save_profile(pool, element, locale):
    data = transform.me_element_to_map(element, locale)
    logger.info("Saving user profile - DOB: %s Sex: %s", data["date_of_birth"], data["biological_sex"])
    with db.get_conn(pool) as conn:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO user_profile
                       (date_of_birth, biological_sex, blood_type,
                        fitzpatrick_skin, cardio_fitness_meds,
                        export_date, locale)
                   VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                (
                    data["date_of_birth"],
                    data["biological_sex"],
                    data["blood_type"],
                    data["fitzpatrick_skin"],
                    data["cardio_fitness_meds"],
                    _export_date,
                    data["locale"],
                ),
            )
        conn.commit()
```

- [ ] **Step 4: Wire profile state reset into `tests/integration/conftest.py`**

In `apps/etl-python/tests/integration/conftest.py`, add the import:

```python
from pulso import config, db
from pulso.loader import lookups, profile
```

And update the `test_ds` fixture body:

```python
@pytest.fixture
def test_ds():
    """Per-test: truncates all tables and resets the lookups cache and
    profile state before handing back the connection pool."""
    pool = _get_pool()
    db.truncate_all(pool)
    lookups.reset_caches()
    profile.reset_state()
    yield pool
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_profile.py -v
```

Expected: 2 passed.

- [ ] **Step 6: Commit**

```bash
git add apps/etl-python/pulso/loader/profile.py apps/etl-python/tests/integration/loader/test_profile.py apps/etl-python/tests/integration/conftest.py
git commit -m "feat(etl-python): port loader.profile module, wire state reset into test fixtures"
```

---

### Task 13: `loader/records.py` — health record + metadata loading

**Files:**
- Create: `apps/etl-python/pulso/loader/records.py`
- Test: `apps/etl-python/tests/integration/loader/test_records.py`

**Interfaces:**
- Consumes: `xml.transform.record_element_to_map` (Task 3); `loader.batch.Batcher`, `loader.batch.ReturningBatcher` (Task 10); `loader.lookups.ensure_*_id` (Task 11).
- Produces: `make_batchers(pool, batch_size) -> dict`, `process(pool, batchers, element) -> dict`, `flush(batchers) -> None`. Used by `loader/correlations.py` (Task 15, for nested records) and `etl.py` (Task 17).

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_records.py`**

```python
import xml.etree.ElementTree as ET

import pytest

from pulso.loader import records

pytestmark = pytest.mark.integration


def test_process_record_without_metadata(test_ds, count_rows):
    batchers = records.make_batchers(test_ds, 10)
    element = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierStepCount",
        "sourceName": "iPhone",
        "sourceVersion": "15.0",
        "value": "1234",
        "unit": "count",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })

    records.process(test_ds, batchers, element)

    assert count_rows("record") == 0
    assert batchers["record"].count() == 1


def test_process_record_with_metadata(test_ds, count_rows):
    batchers = records.make_batchers(test_ds, 10)
    element = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierHeartRate",
        "sourceName": "Apple Watch",
        "sourceVersion": "8.0",
        "value": "72",
        "unit": "count/min",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyHeartRateMotionContext", "value": "0"}))
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyExternalUUID", "value": "abc-123"}))

    records.process(test_ds, batchers, element)

    assert count_rows("record") == 0
    assert batchers["record_with_meta"].count() == 1

    records.flush(batchers)

    assert count_rows("record") == 1
    assert count_rows("record_metadata") == 2


def test_flush_clears_pending(test_ds, count_rows):
    batchers = records.make_batchers(test_ds, 10)

    records.process(test_ds, batchers, ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierStepCount",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "1000", "unit": "count",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    }))
    records.process(test_ds, batchers, ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierActiveEnergyBurned",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "100.5", "unit": "kcal",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    }))

    records.flush(batchers)

    assert count_rows("record") == 2
    assert batchers["record"].count() == 2
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_records.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.records'`.

- [ ] **Step 3: Write `pulso/loader/records.py`**

```python
from pulso.loader import lookups
from pulso.loader.batch import Batcher, ReturningBatcher
from pulso.xml import transform

RECORD_COLUMNS = [
    "record_type_id", "source_id", "device_id", "unit_id",
    "value", "creation_date", "start_date", "end_date",
]


def make_batchers(pool, batch_size):
    return {
        "record": Batcher(pool, "record", RECORD_COLUMNS, batch_size),
        "record_with_meta": ReturningBatcher(pool, "record", RECORD_COLUMNS, batch_size),
        "metadata": Batcher(pool, "record_metadata", ["record_id", "key", "value"], batch_size),
    }


def _process_returned_metadata(metadata_batcher, pairs):
    if not pairs:
        return
    for pair in pairs:
        for m in pair["metadata"]:
            metadata_batcher.add((pair["id"], m["key"], m["value"]))


def process(pool, batchers, element):
    """Transforms a Record element and inserts it. Records with metadata
    use a ReturningBatcher so generated ids can be matched with their
    metadata; records without metadata use a plain batch insert."""
    data = transform.record_element_to_map(element)
    record_type_id = lookups.ensure_record_type_id(pool, data["type"])
    source_id = lookups.ensure_source_id(pool, data["source_name"], data["source_version"])
    device_id = lookups.ensure_device_id(pool, data["device"])
    unit_id = lookups.ensure_unit_id(pool, data["unit"])
    row = (
        record_type_id, source_id, device_id, unit_id,
        data["value"], data["creation_date"], data["start_date"], data["end_date"],
    )

    if data["metadata"]:
        pairs = batchers["record_with_meta"].add(row, data["metadata"])
        _process_returned_metadata(batchers["metadata"], pairs)
    else:
        batchers["record"].add(row)

    return data


def flush(batchers):
    """Flushes the returning-batcher first so record ids are available for
    their metadata, then the plain batchers."""
    pairs = batchers["record_with_meta"].flush()
    _process_returned_metadata(batchers["metadata"], pairs)
    batchers["record"].flush()
    batchers["metadata"].flush()
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_records.py -v
```

Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/loader/records.py apps/etl-python/tests/integration/loader/test_records.py
git commit -m "feat(etl-python): port loader.records module"
```

---

### Task 14: `loader/workouts.py` — workout + events/stats/routes loading

**Files:**
- Create: `apps/etl-python/pulso/loader/workouts.py`
- Test: `apps/etl-python/tests/integration/loader/test_workouts.py`

**Interfaces:**
- Consumes: `xml.transform.workout_element_to_map` (Task 3); `loader.batch.Batcher`, `loader.batch.ReturningBatcher` (Task 10); `loader.lookups.ensure_source_id`, `ensure_device_id` (Task 11).
- Produces: `make_batchers(pool, batch_size) -> dict`, `process(pool, batchers, element) -> None`, `flush(batchers) -> None`. Used by `etl.py` (Task 17).

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_workouts.py`**

```python
import xml.etree.ElementTree as ET

import pytest

from pulso.loader import workouts

pytestmark = pytest.mark.integration


def test_process_workout_all_children(test_ds, count_rows):
    batchers = workouts.make_batchers(test_ds, 10)
    element = ET.Element("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeRunning",
        "duration": "60.0", "durationUnit": "min",
        "totalDistance": "10.5", "totalDistanceUnit": "km",
        "totalEnergyBurned": "500.0", "totalEnergyBurnedUnit": "kcal",
        "sourceName": "iPhone", "sourceVersion": "15.0", "device": "iPhone (User's Device)",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyWorkoutBrandName", "value": "Apple"}))
    element.append(ET.Element("WorkoutEvent", {
        "type": "HKWorkoutEventTypePause", "date": "2025-01-15 09:30:00 -0300",
        "duration": "5.0", "durationUnit": "min",
    }))
    element.append(ET.Element("WorkoutStatistics", {
        "type": "HKQuantityTypeIdentifierHeartRateVariabilitySDNN",
        "startDate": "2025-01-15 09:00:00 -0300", "endDate": "2025-01-15 10:00:00 -0300",
        "average": "45.0", "minimum": "30.0", "maximum": "60.0", "sum": "45.0", "unit": "ms",
    }))
    route = ET.Element("WorkoutRoute", {
        "sourceName": "Apple Watch",
        "startDate": "2025-01-15 09:00:00 -0300", "endDate": "2025-01-15 10:00:00 -0300",
    })
    route.append(ET.Element("FileReference", {"path": "/path/to/route.gpx"}))
    element.append(route)

    workouts.process(test_ds, batchers, element)
    workouts.flush(batchers)

    assert count_rows("workout") == 1
    assert count_rows("workout_metadata") == 1
    assert count_rows("workout_event") == 1
    assert count_rows("workout_statistics") == 1
    assert count_rows("workout_route") == 1


def test_process_workout_no_children(test_ds, count_rows):
    batchers = workouts.make_batchers(test_ds, 10)
    element = ET.Element("Workout", {
        "workoutActivityType": "HKWorkoutActivityTypeCycling",
        "duration": "30.0", "durationUnit": "min",
        "sourceName": "iPhone", "sourceVersion": "15.0", "device": "iPhone (User's Device)",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 09:30:00 -0300",
    })

    workouts.process(test_ds, batchers, element)
    workouts.flush(batchers)

    assert count_rows("workout") == 1
    assert count_rows("workout_metadata") == 0
    assert count_rows("workout_event") == 0
    assert count_rows("workout_statistics") == 0
    assert count_rows("workout_route") == 0
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_workouts.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.workouts'`.

- [ ] **Step 3: Write `pulso/loader/workouts.py`**

```python
from pulso.loader import lookups
from pulso.loader.batch import Batcher, ReturningBatcher
from pulso.xml import transform

WORKOUT_COLUMNS = [
    "activity_type", "duration", "duration_unit",
    "total_distance", "total_distance_unit",
    "total_energy_burned", "total_energy_burned_unit",
    "source_id", "device_id",
    "creation_date", "start_date", "end_date",
]


def make_batchers(pool, batch_size):
    return {
        "workout": ReturningBatcher(pool, "workout", WORKOUT_COLUMNS, batch_size),
        "metadata": Batcher(pool, "workout_metadata", ["workout_id", "key", "value"], batch_size),
        "event": Batcher(
            pool, "workout_event",
            ["workout_id", "type", "date", "duration", "duration_unit"], batch_size,
        ),
        "statistics": Batcher(
            pool, "workout_statistics",
            ["workout_id", "type", "start_date", "end_date",
             "average", "minimum", "maximum", "sum", "unit"], batch_size,
        ),
        "route": Batcher(
            pool, "workout_route",
            ["workout_id", "source_name", "start_date", "end_date", "file_path"], batch_size,
        ),
    }


def _process_returned_children(batchers, pairs):
    if not pairs:
        return
    for pair in pairs:
        workout_id = pair["id"]
        children = pair["metadata"]
        for m in children["metadata_entries"]:
            batchers["metadata"].add((workout_id, m["key"], m["value"]))
        for e in children["events"]:
            batchers["event"].add((workout_id, e["type"], e["date"], e["duration"], e["duration_unit"]))
        for s in children["statistics"]:
            batchers["statistics"].add((
                workout_id, s["type"], s["start_date"], s["end_date"],
                s["average"], s["minimum"], s["maximum"], s["sum"], s["unit"],
            ))
        for r in children["routes"]:
            batchers["route"].add(
                (workout_id, r["source_name"], r["start_date"], r["end_date"], r["file_path"])
            )


def process(pool, batchers, element):
    data = transform.workout_element_to_map(element)
    source_id = lookups.ensure_source_id(pool, data["source_name"], data["source_version"])
    device_id = lookups.ensure_device_id(pool, data["device"])
    row = (
        data["activity_type"], data["duration"], data["duration_unit"],
        data["total_distance"], data["total_distance_unit"],
        data["total_energy_burned"], data["total_energy_burned_unit"],
        source_id, device_id,
        data["creation_date"], data["start_date"], data["end_date"],
    )
    children = {
        "metadata_entries": data["metadata"],
        "events": data["events"],
        "statistics": data["statistics"],
        "routes": data["routes"],
    }
    pairs = batchers["workout"].add(row, children)
    _process_returned_children(batchers, pairs)


def flush(batchers):
    pairs = batchers["workout"].flush()
    _process_returned_children(batchers, pairs)
    batchers["metadata"].flush()
    batchers["event"].flush()
    batchers["statistics"].flush()
    batchers["route"].flush()
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_workouts.py -v
```

Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/loader/workouts.py apps/etl-python/tests/integration/loader/test_workouts.py
git commit -m "feat(etl-python): port loader.workouts module"
```

---

### Task 15: `loader/correlations.py` — correlation + nested records loading

**Files:**
- Create: `apps/etl-python/pulso/loader/correlations.py`
- Test: `apps/etl-python/tests/integration/loader/test_correlations.py`

**Interfaces:**
- Consumes: `xml.transform.correlation_element_to_map` (Task 3); `loader.batch.Batcher`, `loader.batch.ReturningBatcher` (Task 10); `loader.lookups.ensure_*_id` (Task 11).
- Produces: `make_batchers(pool, batch_size) -> dict`, `process(pool, batchers, element) -> None`, `flush(pool, batchers) -> None`. Used by `etl.py` (Task 17).

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_correlations.py`**

```python
import xml.etree.ElementTree as ET

import pytest

from pulso.loader import correlations

pytestmark = pytest.mark.integration


def test_process_correlation_with_nested_records(test_ds, count_rows):
    batchers = correlations.make_batchers(test_ds, 10)
    element = ET.Element("Correlation", {
        "type": "HKCorrelationTypeIdentifierBloodPressure",
        "sourceName": "iPhone", "sourceVersion": "15.0",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    element.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeyGroupName", "value": "TestGroup"}))

    systolic = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierBloodPressureSystolic",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "120", "unit": "mmHg",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    systolic.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeySourceSecondaryID", "value": "source-1"}))
    element.append(systolic)

    diastolic = ET.Element("Record", {
        "type": "HKQuantityTypeIdentifierBloodPressureDiastolic",
        "sourceName": "iPhone", "sourceVersion": "15.0", "value": "80", "unit": "mmHg",
        "creationDate": "2025-01-15 10:00:00 -0300",
        "startDate": "2025-01-15 09:00:00 -0300",
        "endDate": "2025-01-15 10:00:00 -0300",
    })
    diastolic.append(ET.Element("MetadataEntry", {"key": "HKMetadataKeySourceSecondaryID", "value": "source-2"}))
    element.append(diastolic)

    correlations.process(test_ds, batchers, element)
    correlations.flush(test_ds, batchers)

    assert count_rows("correlation") == 1
    assert count_rows("record") == 2
    assert count_rows("correlation_record") == 2
    assert count_rows("correlation_metadata") == 1
    assert count_rows("record_metadata") == 2
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_correlations.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.correlations'`.

- [ ] **Step 3: Write `pulso/loader/correlations.py`**

```python
from pulso.loader import lookups
from pulso.loader.batch import Batcher, ReturningBatcher
from pulso.xml import transform

CORRELATION_COLUMNS = ["type", "source_id", "device_id", "creation_date", "start_date", "end_date"]
RECORD_COLUMNS = [
    "record_type_id", "source_id", "device_id", "unit_id",
    "value", "creation_date", "start_date", "end_date",
]


def make_batchers(pool, batch_size):
    """A dedicated nested-record ReturningBatcher avoids cross-batcher
    complexity with top-level records.py."""
    return {
        "correlation": ReturningBatcher(pool, "correlation", CORRELATION_COLUMNS, batch_size),
        "metadata": Batcher(pool, "correlation_metadata", ["correlation_id", "key", "value"], batch_size),
        "nested_record": ReturningBatcher(pool, "record", RECORD_COLUMNS, batch_size),
        "record_metadata": Batcher(pool, "record_metadata", ["record_id", "key", "value"], batch_size),
        "record_link": Batcher(pool, "correlation_record", ["correlation_id", "record_id"], batch_size),
    }


def _process_returned_nested_records(batchers, pairs):
    if not pairs:
        return
    for pair in pairs:
        record_id = pair["id"]
        meta = pair["metadata"]
        batchers["record_link"].add((meta["corr_id"], record_id))
        for m in meta["record_metadata"]:
            batchers["record_metadata"].add((record_id, m["key"], m["value"]))


def _process_returned_correlations(pool, batchers, pairs):
    if not pairs:
        return
    for pair in pairs:
        corr_id = pair["id"]
        meta = pair["metadata"]
        for m in meta["corr_metadata"]:
            batchers["metadata"].add((corr_id, m["key"], m["value"]))
        for record_map in meta["records"]:
            record_type_id = lookups.ensure_record_type_id(pool, record_map["type"])
            source_id = lookups.ensure_source_id(pool, record_map["source_name"], record_map["source_version"])
            device_id = lookups.ensure_device_id(pool, record_map["device"])
            unit_id = lookups.ensure_unit_id(pool, record_map["unit"])
            row = (
                record_type_id, source_id, device_id, unit_id,
                record_map["value"], record_map["creation_date"],
                record_map["start_date"], record_map["end_date"],
            )
            nested_pairs = batchers["nested_record"].add(
                row, {"corr_id": corr_id, "record_metadata": record_map["metadata"]}
            )
            _process_returned_nested_records(batchers, nested_pairs)


def process(pool, batchers, element):
    data = transform.correlation_element_to_map(element)
    source_id = lookups.ensure_source_id(pool, data["source_name"], data["source_version"])
    device_id = lookups.ensure_device_id(pool, data["device"])
    row = (data["type"], source_id, device_id, data["creation_date"], data["start_date"], data["end_date"])
    children = {"corr_metadata": data["metadata"], "records": data["records"]}
    pairs = batchers["correlation"].add(row, children)
    _process_returned_correlations(pool, batchers, pairs)


def flush(pool, batchers):
    """Three-phase flush: correlation parents -> nested records -> metadata + links."""
    corr_pairs = batchers["correlation"].flush()
    _process_returned_correlations(pool, batchers, corr_pairs)

    nested_pairs = batchers["nested_record"].flush()
    _process_returned_nested_records(batchers, nested_pairs)

    batchers["metadata"].flush()
    batchers["record_metadata"].flush()
    batchers["record_link"].flush()
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_correlations.py -v
```

Expected: 1 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/loader/correlations.py apps/etl-python/tests/integration/loader/test_correlations.py
git commit -m "feat(etl-python): port loader.correlations module"
```

---

### Task 16: `loader/activity.py` — activity summary loading

**Files:**
- Create: `apps/etl-python/pulso/loader/activity.py`
- Test: `apps/etl-python/tests/integration/loader/test_activity.py`

**Interfaces:**
- Consumes: `xml.transform.activity_summary_element_to_map` (Task 3); `loader.batch.Batcher` (Task 10).
- Produces: `make_batcher(pool, batch_size) -> Batcher`, `process(batcher, element) -> None`. Used by `etl.py` (Task 17).

- [ ] **Step 1: Write the failing test file `tests/integration/loader/test_activity.py`**

```python
import xml.etree.ElementTree as ET

import pytest

from pulso.loader import activity

pytestmark = pytest.mark.integration


def test_process_activity_summary(test_ds, count_rows, select_all):
    batcher = activity.make_batcher(test_ds, 10)
    element = ET.Element("ActivitySummary", {
        "dateComponents": "2025-01-15",
        "activeEnergyBurned": "450.5", "activeEnergyBurnedGoal": "420.0", "activeEnergyBurnedUnit": "kcal",
        "appleMoveTime": "30.5", "appleMoveTimeGoal": "30.0",
        "appleExerciseTime": "25.0", "appleExerciseTimeGoal": "30.0",
        "appleStandHours": "10.0", "appleStandHoursGoal": "12.0",
    })

    activity.process(batcher, element)
    batcher.flush()

    assert count_rows("activity_summary") == 1
    row = select_all("activity_summary")[0]
    assert str(row["date_components"]) == "2025-01-15"
    assert row["active_energy_burned"] == 450.5
    assert row["active_energy_burned_goal"] == 420.0
    assert row["active_energy_burned_unit"] == "kcal"
    assert row["apple_move_time"] == 30.5
    assert row["apple_move_time_goal"] == 30.0
    assert row["apple_exercise_time"] == 25.0
    assert row["apple_exercise_time_goal"] == 30.0
    assert row["apple_stand_hours"] == 10.0
    assert row["apple_stand_hours_goal"] == 12.0
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_activity.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.loader.activity'`.

- [ ] **Step 3: Write `pulso/loader/activity.py`**

```python
from pulso.loader.batch import Batcher
from pulso.xml import transform

ACTIVITY_COLUMNS = [
    "date_components",
    "active_energy_burned", "active_energy_burned_goal", "active_energy_burned_unit",
    "apple_move_time", "apple_move_time_goal",
    "apple_exercise_time", "apple_exercise_time_goal",
    "apple_stand_hours", "apple_stand_hours_goal",
]


def make_batcher(pool, batch_size):
    return Batcher(pool, "activity_summary", ACTIVITY_COLUMNS, batch_size)


def process(batcher, element):
    data = transform.activity_summary_element_to_map(element)
    batcher.add((
        data["date_components"],
        data["active_energy_burned"], data["active_energy_burned_goal"], data["active_energy_burned_unit"],
        data["apple_move_time"], data["apple_move_time_goal"],
        data["apple_exercise_time"], data["apple_exercise_time_goal"],
        data["apple_stand_hours"], data["apple_stand_hours_goal"],
    ))
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/loader/test_activity.py -v
```

Expected: 1 passed.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/loader/activity.py apps/etl-python/tests/integration/loader/test_activity.py
git commit -m "feat(etl-python): port loader.activity module"
```

---

### Task 17: `etl.py` — pipeline orchestrator

**Files:**
- Create: `apps/etl-python/pulso/etl.py`
- Test: `apps/etl-python/tests/integration/test_etl.py`

**Interfaces:**
- Consumes: `db.truncate_all` (Task 8); `xml.parser.parse_health_data` (Task 4); `loader.profile.*` (Task 12), `loader.records.*` (Task 13), `loader.workouts.*` (Task 14), `loader.correlations.*` (Task 15), `loader.activity.*` (Task 16), `loader.lookups.reset_caches` (Task 11).
- Produces: `execute(pool, xml_file, batch_size, on_element=None) -> dict` returning `{"records": int, "workouts": int, "correlations": int, "activities": int}`. Used by `cli.py` (Task 18).

- [ ] **Step 1: Write the failing test file `tests/integration/test_etl.py`**

```python
import os

import pytest

from pulso import etl

pytestmark = pytest.mark.integration

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "fixtures", "small-export.xml")


def test_execute_full_pipeline(test_ds, count_rows):
    result = etl.execute(test_ds, FIXTURE, 5000)

    # Fixture contains 2 top-level records (nested correlation records not counted here)
    assert result["records"] == 2
    assert result["workouts"] == 1
    assert result["correlations"] == 1
    assert result["activities"] == 1

    # Records: 2 top-level + 2 nested in correlation = 4 total
    assert count_rows("record") == 4
    # Record metadata: 2 from HeartRate, 0 from ActiveEnergyBurned, 0 from nested records
    assert count_rows("record_metadata") == 2
    assert count_rows("workout") == 1
    assert count_rows("workout_metadata") == 1
    assert count_rows("workout_event") == 1
    assert count_rows("workout_statistics") == 1
    assert count_rows("workout_route") == 1
    assert count_rows("correlation") == 1
    assert count_rows("correlation_metadata") == 1
    assert count_rows("correlation_record") == 2
    assert count_rows("activity_summary") == 1
    assert count_rows("user_profile") == 1


def test_execute_is_idempotent(test_ds, count_rows):
    result1 = etl.execute(test_ds, FIXTURE, 5000)
    result2 = etl.execute(test_ds, FIXTURE, 5000)

    assert result1 == result2
    assert count_rows("record") == 4
    assert count_rows("workout") == 1
    assert count_rows("correlation") == 1
    assert count_rows("activity_summary") == 1
    assert count_rows("user_profile") == 1
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/etl-python
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/test_etl.py -v
```

Expected: `ModuleNotFoundError: No module named 'pulso.etl'`.

- [ ] **Step 3: Write `pulso/etl.py`**

```python
import logging
import time

from pulso import db
from pulso.loader import activity, correlations, lookups, profile, records, workouts
from pulso.xml import parser

logger = logging.getLogger(__name__)

PROGRESS_EVERY = 100000


def execute(pool, xml_file, batch_size, on_element=None):
    """Runs the full ETL pipeline: parse XML -> transform -> load into
    PostgreSQL. When on_element is provided, the old log-every-100k
    progress reporting is disabled (the caller is driving its own UI)."""
    logger.info("Starting ETL pipeline file=%s batch_size=%s", xml_file, batch_size)
    start = time.time()
    use_log_progress = on_element is None

    db.truncate_all(pool)
    lookups.reset_caches()
    profile.reset_state()

    record_batchers = records.make_batchers(pool, batch_size)
    workout_batchers = workouts.make_batchers(pool, batch_size)
    corr_batchers = correlations.make_batchers(pool, batch_size)
    activity_batcher = activity.make_batcher(pool, batch_size)
    counters = {"records": 0, "workouts": 0, "correlations": 0, "activities": 0}

    def handler(element, locale):
        tag = element.tag
        if tag == "ExportDate":
            profile.save_export_date(element)
            if on_element:
                on_element("ExportDate")
        elif tag == "Me":
            profile.save_profile(pool, element, locale)
            if on_element:
                on_element("Me")
        elif tag == "Record":
            records.process(pool, record_batchers, element)
            counters["records"] += 1
            if use_log_progress and counters["records"] % PROGRESS_EVERY == 0:
                logger.info("Progress: %s records processed", counters["records"])
            if on_element:
                on_element("Record")
        elif tag == "Workout":
            workouts.process(pool, workout_batchers, element)
            counters["workouts"] += 1
            if on_element:
                on_element("Workout")
        elif tag == "Correlation":
            correlations.process(pool, corr_batchers, element)
            counters["correlations"] += 1
            if on_element:
                on_element("Correlation")
        elif tag == "ActivitySummary":
            activity.process(activity_batcher, element)
            counters["activities"] += 1
            if on_element:
                on_element("ActivitySummary")
        else:
            logger.debug("Skipping element tag=%s", tag)

    parser.parse_health_data(xml_file, handler)

    records.flush(record_batchers)
    workouts.flush(workout_batchers)
    correlations.flush(pool, corr_batchers)
    activity_batcher.flush()

    elapsed_ms = int((time.time() - start) * 1000)
    logger.info(
        "ETL complete! %s elapsed_ms=%s elapsed_min=%.1f",
        counters, elapsed_ms, elapsed_ms / 60000.0,
    )
    return counters
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest tests/integration/test_etl.py -v
```

Expected: 2 passed.

- [ ] **Step 5: Run the full test suite (unit + integration) to confirm nothing regressed**

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest -v
uv run pytest -m "not integration" -v
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres uv run pytest -m integration -v
```

Expected: full run 49 passed; unit-only run 30 passed; integration-only run 19 passed.

- [ ] **Step 6: Commit**

```bash
git add apps/etl-python/pulso/etl.py apps/etl-python/tests/integration/test_etl.py
git commit -m "feat(etl-python): port etl orchestrator, full pipeline test parity reached"
```

---

### Task 18: `cli.py` — command-line entry point

**Files:**
- Create: `apps/etl-python/pulso/cli.py`

**Interfaces:**
- Consumes: `config.db_spec`, `config.DEFAULT_BATCH_SIZE` (Task 2); `db.create_pool`, `db.migrate` (Task 8); `etl.execute` (Task 17); `xml.counter.count_elements` (Task 5); `progress.make_state`, `progress.record_progress`, `progress.snapshot` (Task 6); `ui.terminal.start_renderer` (Task 7).
- Produces: `main(argv=None) -> None`, registered as the `pulso` console script entry point in `pyproject.toml` (Task 1). No automated tests — `core.clj` has none either; verified manually below.

- [ ] **Step 1: Write `pulso/cli.py`**

```python
import argparse
import logging
import os
import sys

from pulso import config, db, etl, progress
from pulso.ui import terminal
from pulso.xml import counter

logger = logging.getLogger(__name__)


def _build_arg_parser():
    parser = argparse.ArgumentParser(
        prog="pulso",
        description="Pulso - Apple Health XML to PostgreSQL ETL",
        add_help=False,
    )
    parser.add_argument("-f", "--file", help="Path to Apple Health XML export file")
    parser.add_argument(
        "-b", "--batch-size", type=int, default=config.DEFAULT_BATCH_SIZE, help="Batch insert size"
    )
    parser.add_argument(
        "-p", "--progress", dest="progress", action="store_true", default=True,
        help="Show progress bar (default: true)",
    )
    parser.add_argument("--no-progress", dest="progress", action="store_false")
    parser.add_argument("-h", "--help", dest="show_help", action="store_true", help="Show help")
    return parser


def _detach_console_handler():
    """Removes the root logger's stdout handler so log lines don't
    interfere with the progress UI, mirroring the Clojure app's
    detach-console-appender!."""
    root = logging.getLogger()
    for handler in list(root.handlers):
        if isinstance(handler, logging.StreamHandler) and handler.stream is sys.stdout:
            root.removeHandler(handler)
            return handler
    return None


def _reattach_console_handler(handler):
    if handler:
        logging.getLogger().addHandler(handler)


def _run_with_progress(pool, xml_file, batch_size):
    filename = os.path.basename(xml_file)
    print(f"Pulso ETL - Counting elements in {filename}...")
    totals = counter.count_elements(xml_file)
    state = progress.make_state(totals)
    detached = _detach_console_handler()
    renderer = terminal.start_renderer(state, progress.snapshot, filename)
    try:
        result = etl.execute(
            pool, xml_file, batch_size,
            on_element=lambda element_type: progress.record_progress(state, element_type),
        )
        renderer.stop()
        _reattach_console_handler(detached)
        print()
        logger.info("Final counts: %s", result)
        return result
    except Exception:
        renderer.stop()
        _reattach_console_handler(detached)
        raise


def main(argv=None):
    arg_parser = _build_arg_parser()
    args = arg_parser.parse_args(argv)

    if args.show_help:
        arg_parser.print_help()
        sys.exit(0)

    if not args.file:
        print("Error: --file is required")
        arg_parser.print_help()
        sys.exit(1)

    if not os.path.exists(args.file):
        print("Error: File does not exist")
        sys.exit(1)

    db_spec = config.db_spec()
    pool = db.create_pool(db_spec)
    logger.info(
        "Connecting to database host=%s port=%s dbname=%s",
        db_spec["host"], db_spec["port"], db_spec["dbname"],
    )
    db.migrate(pool)

    if args.progress:
        _run_with_progress(pool, args.file, args.batch_size)
    else:
        result = etl.execute(pool, args.file, args.batch_size)
        logger.info("Final counts: %s", result)

    sys.exit(0)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Manual verification — help text**

```bash
cd apps/etl-python
uv run python -m pulso.cli --help
```

Expected: prints usage text listing `-f/--file`, `-b/--batch-size`, `-p/--progress`, `--no-progress`, `-h/--help`, exit code 0.

- [ ] **Step 3: Manual verification — missing required flag**

```bash
uv run python -m pulso.cli
echo "exit code: $?"
```

Expected: prints `Error: --file is required` and usage, exit code 1.

- [ ] **Step 4: Manual verification — full run against the fixture, with progress UI**

Requires the same `pulso_test`-style Postgres reachable via `DB_*` env vars (point it at a scratch `pulso` DB, not `pulso_test`, so it doesn't collide with the test suite):

```bash
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres DB_NAME=pulso_cli_check \
  uv run python -c "
import psycopg2
conn = psycopg2.connect(host='localhost', user='postgres', password='postgres', dbname='postgres')
conn.autocommit = True
cur = conn.cursor()
cur.execute('DROP DATABASE IF EXISTS pulso_cli_check')
cur.execute('CREATE DATABASE pulso_cli_check')
"
DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres DB_NAME=pulso_cli_check \
  uv run python -m pulso.cli --file tests/fixtures/small-export.xml
```

Expected: prints "Pulso ETL - Counting elements in small-export.xml...", then a live-updating progress bar for Record/Workout/Correlation/ActivitySummary that completes at 100%, exit code 0.

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/pulso/cli.py
git commit -m "feat(etl-python): port cli entry point"
```

---

### Task 19: Dockerfile + `docker-compose.yml`

**Files:**
- Create: `apps/etl-python/Dockerfile`
- Modify: `docker-compose.yml:19-34` (the `app` service)

**Interfaces:**
- Produces: a Docker image for `apps/etl-python` runnable the same way as the current `app` service; `docker-compose.yml`'s `app` service now builds from `apps/etl-python`.

- [ ] **Step 1: Write `apps/etl-python/Dockerfile`**

```dockerfile
FROM python:3.12-slim AS builder
WORKDIR /app
RUN pip install --no-cache-dir uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

FROM python:3.12-slim
WORKDIR /app
COPY --from=builder /app/.venv /app/.venv
COPY pulso ./pulso
COPY migrations ./migrations
ENV PATH="/app/.venv/bin:$PATH"
ENTRYPOINT ["python", "-m", "pulso.cli"]
```

- [ ] **Step 2: Update `docker-compose.yml`'s `app` service**

In `docker-compose.yml`, change:

```yaml
  app:
    build:
      context: ./apps/etl-clojure
      dockerfile: Dockerfile
```

to:

```yaml
  app:
    build:
      context: ./apps/etl-python
      dockerfile: Dockerfile
```

Leave the rest of the `app` service (environment, volumes, command, depends_on) unchanged — same env vars, same `/data` volume mount, same `["--file", "/data/apple_health_export/exportar.xml"]` command.

Also update the `db` service's postgres-init volume mount, since `resources/postgres-init/` currently lives under `apps/etl-clojure/`. Move that directory so it's not tied to the app being deleted:

```bash
cd /home/yagoazedias/github/pulso
mkdir -p infra/postgres-init
cp apps/etl-clojure/resources/postgres-init/01-create-metabase-db.sh infra/postgres-init/
cp apps/etl-clojure/resources/postgres-init/02-create-dashboard-schema.sql infra/postgres-init/
```

Then in `docker-compose.yml`, change:

```yaml
      - ./apps/etl-clojure/resources/postgres-init:/docker-entrypoint-initdb.d:ro
```

to:

```yaml
      - ./infra/postgres-init:/docker-entrypoint-initdb.d:ro
```

- [ ] **Step 3: Validate the compose file**

```bash
cd /home/yagoazedias/github/pulso
docker compose config -q
```

Expected: no output, exit code 0 (valid YAML + resolvable build contexts).

- [ ] **Step 4: Build the new image**

```bash
docker compose build app
```

Expected: image builds successfully (multi-stage `uv sync` then slim runtime layer).

- [ ] **Step 5: Commit**

```bash
git add apps/etl-python/Dockerfile docker-compose.yml infra/postgres-init
git commit -m "feat(etl-python): add Dockerfile, point docker-compose at Python ETL"
```

---

### Task 20: CI workflows

**Files:**
- Modify: `.github/workflows/tests.yml`
- Modify: `.github/workflows/docker.yml`

**Interfaces:**
- Produces: CI that builds/tests `apps/etl-python` instead of `apps/etl-clojure`, same job structure (unit → integration → combined → build → artifact).

- [ ] **Step 1: Replace the `test` job's Clojure/Leiningen steps in `.github/workflows/tests.yml`**

Replace the whole `test` job (everything from `test:` through the end of the `Comment test results` step) with:

```yaml
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:17-alpine
        env:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: pulso
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Install uv
        uses: astral-sh/setup-uv@v3
        with:
          version: "latest"

      - name: Set up Python
        working-directory: ./apps/etl-python
        run: uv python install

      - name: Install dependencies
        working-directory: ./apps/etl-python
        run: uv sync

      - name: Create test database
        env:
          PGHOST: localhost
          PGUSER: postgres
          PGPASSWORD: postgres
        run: |
          psql -c "CREATE DATABASE pulso_test;"

      - name: Run unit tests
        working-directory: ./apps/etl-python
        run: uv run pytest -m "not integration" -v

      - name: Run integration tests
        working-directory: ./apps/etl-python
        env:
          DB_HOST: localhost
          DB_USER: postgres
          DB_PASSWORD: postgres
        run: uv run pytest -m integration -v

      - name: Run all tests (unit + integration)
        working-directory: ./apps/etl-python
        env:
          DB_HOST: localhost
          DB_USER: postgres
          DB_PASSWORD: postgres
        run: uv run pytest -v

      - name: Comment test results
        if: always() && github.event_name == 'pull_request'
        uses: actions/github-script@v7
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          script: |
            const comment = '## Build and Test Results\n\n' +
              '- Unit tests: Passed\n' +
              '- Integration tests: Passed\n';
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: comment
            });
```

This drops the `lein check` syntax-check step (no direct Python equivalent required by this migration), the uberjar build/upload steps (the app now ships as a Docker image, built by the separate `docker.yml` workflow, not a standalone jar artifact), and the Cloverage coverage report (out of scope for this migration — coverage tooling can be added later as its own change).

- [ ] **Step 2: Update `.github/workflows/docker.yml`'s `docker-etl` job**

Change:

```yaml
      - name: Build ETL Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./apps/etl-clojure
          push: false
          tags: pulso-etl:latest
          cache-from: type=gha,scope=etl
          cache-to: type=gha,mode=max,scope=etl
```

to:

```yaml
      - name: Build ETL Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./apps/etl-python
          push: false
          tags: pulso-etl:latest
          cache-from: type=gha,scope=etl
          cache-to: type=gha,mode=max,scope=etl
```

Also update the `paths:` filters at the top of the file (both `push` and `pull_request` triggers) — replace:

```yaml
      - 'apps/etl-clojure/Dockerfile'
      ...
      - 'apps/etl-clojure/src/**'
      - 'apps/etl-clojure/resources/**'
```

with:

```yaml
      - 'apps/etl-python/Dockerfile'
      ...
      - 'apps/etl-python/pulso/**'
      - 'apps/etl-python/migrations/**'
```

(keep the `apps/dashboard-django/**`, `docker-compose.yml`, and `.github/workflows/docker.yml` entries unchanged).

- [ ] **Step 3: Verify workflow YAML is well-formed**

```bash
cd /home/yagoazedias/github/pulso
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/tests.yml'))" && echo OK
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/docker.yml'))" && echo OK
```

Expected: `OK` printed twice.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/tests.yml .github/workflows/docker.yml
git commit -m "ci: switch tests.yml and docker.yml from Clojure/Leiningen to Python/uv"
```

---

### Task 21: README update

**Files:**
- Modify: `README.md`

**Interfaces:**
- Produces: README describing the Python ETL app instead of the Clojure one — Tech Stack, Quick Start, Testing, Project Structure sections.

- [ ] **Step 1: Update the intro and Tech Stack sections**

Replace:

```markdown
Apple Health XML to PostgreSQL ETL pipeline built in Clojure, with Django-based analytics dashboard (in development).
```

with:

```markdown
Apple Health XML to PostgreSQL ETL pipeline built in Python, with Django-based analytics dashboard (in development).
```

Replace the `## Monorepo Structure` bullet:

```markdown
- **`apps/etl-clojure/`** — Pulso ETL (Clojure + Leiningen)
```

with:

```markdown
- **`apps/etl-python/`** — Pulso ETL (Python + uv)
```

Replace the entire `## Tech Stack` section:

```markdown
## Tech Stack

- **Python** 3.12, managed with **uv**
- **PostgreSQL** 17 (via Docker)
- **Metabase** — data visualization and analytics on top of PostgreSQL
- **xml.etree.ElementTree** — streaming (iterparse) XML parser
- **psycopg2** — database access with connection pooling
- Custom SQL-file migration runner — tracks applied migrations in a `schema_migrations` table
- **Docker** — multi-stage build for production deployment
```

- [ ] **Step 2: Update Prerequisites and Quick Start**

Replace:

```markdown
For local development without Docker:
- Java 21+
- [Leiningen](https://leiningen.org/)
```

with:

```markdown
For local development without Docker:
- Python 3.12+
- [uv](https://docs.astral.sh/uv/)
```

Replace the `### Local Development` block:

```markdown
### Local Development

```bash
# 1. Start PostgreSQL only
docker compose up db

# 2. Navigate to the ETL app and run migrations
cd apps/etl-clojure
lein migratus migrate

# 3. Run the ETL
lein run -- --file /path/to/exportar.xml
```
```

with:

```markdown
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
```

- [ ] **Step 3: Update the Testing section**

Replace the whole `## Testing` section body (test organization, dependencies, running tests, structure/patterns, infrastructure, results) with:

```markdown
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
```

- [ ] **Step 4: Update the Architecture and Project Structure sections**

In `## Architecture`, replace:

```markdown
- **Streaming XML** via `clojure.data.xml/parse` (StAX) — children of the root element are lazy sequences, so only one element is in memory at a time
```

with:

```markdown
- **Streaming XML** via `xml.etree.ElementTree.iterparse` — each processed top-level child is removed from the root element's children list, so only one element is retained in memory at a time
```

Replace the last line of `## Database Schema`:

```markdown
Migrations are managed by Migratus and live in `resources/migrations/`.
```

with:

```markdown
Migrations are plain SQL files applied by a small custom runner (tracked in a `schema_migrations` table) and live in `apps/etl-python/migrations/`.
```

Replace the whole `## Project Structure` tree's `etl-clojure` block:

```markdown
│   ├── etl-clojure/                    # Pulso ETL pipeline (Clojure)
│   │   ├── project.clj
│   │   ├── Dockerfile
│   │   ├── resources/migrations/       # SQL migration files (up/down)
│   │   ├── src/pulso/
│   │   │   ├── core.clj                # CLI entry point
│   │   │   ├── config.clj              # DB + app config
│   │   │   ├── db.clj                  # Datasource, migrations, truncate
│   │   │   ├── etl.clj                 # Orchestrator: parse -> transform -> load
│   │   │   ├── xml/
│   │   │   │   ├── parser.clj          # Streaming XML parser with element dispatch
│   │   │   │   └── transform.clj       # XML elements -> Clojure maps
│   │   │   └── loader/
│   │   │       ├── batch.clj           # Generic batch insert machinery
│   │   │       ├── lookups.clj         # Lookup table cache & upsert
│   │   │       ├── records.clj         # Record + metadata loading
│   │   │       ├── workouts.clj        # Workout + events + stats + routes
│   │   │       ├── correlations.clj    # Correlation + nested records
│   │   │       ├── activity.clj        # ActivitySummary loading
│   │   │       └── profile.clj         # User profile (Me element)
│   │   └── test/
│   │       ├── unit/pulso/
│   │       │   └── xml/
│   │       │       ├── parser_test.clj
│   │       │       └── transform_test.clj
│   │       └── integration/pulso/
│   │           ├── test_helpers.clj    # Shared test infrastructure
│   │           ├── etl_test.clj        # End-to-end pipeline tests
│   │           └── loader/
│   │               ├── batch_test.clj  # Batch processing tests
│   │               ├── lookups_test.clj # Lookup caching tests
│   │               ├── profile_test.clj # User profile tests
│   │               ├── records_test.clj # Record loading tests
│   │               ├── workouts_test.clj # Workout loading tests
│   │               ├── correlations_test.clj # Correlation tests
│   │               └── activity_test.clj # Activity summary tests
```

with:

```markdown
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
│   │       ├── conftest.py             # Shared test infrastructure
│   │       ├── unit/                   # Fast, no-DB tests
│   │       └── integration/            # DB-backed tests (@pytest.mark.integration)
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: update README for Python ETL app"
```

---

### Task 22: Manual cutover verification + delete `apps/etl-clojure`

**Files:**
- Delete: `apps/etl-clojure/` (entire directory)

**Interfaces:**
- N/A — this is the final acceptance gate, not a code change.

This task cannot be done by an automated worker alone: it requires **your** real Apple Health export file, and a side-by-side comparison run of both the old and new apps. Do not delete `apps/etl-clojure` until both checks below pass.

- [ ] **Step 1: Run the old Clojure app against your real export, record table counts**

```bash
cd /home/yagoazedias/github/pulso
mkdir -p data && cp /path/to/your/exportar.xml data/
docker compose up db -d
until docker compose exec db pg_isready -U postgres; do sleep 1; done
docker compose exec db psql -U postgres -c "CREATE DATABASE pulso_reference;" 2>/dev/null || true
cd apps/etl-clojure
DB_NAME=pulso_reference DB_HOST=localhost lein migratus migrate
DB_NAME=pulso_reference DB_HOST=localhost lein run -- --file ../../data/exportar.xml --no-progress
```

Record the row counts it reports (or query them directly):

```bash
docker compose exec db psql -U postgres -d pulso_reference -c "
SELECT 'record', count(*) FROM record
UNION ALL SELECT 'workout', count(*) FROM workout
UNION ALL SELECT 'correlation', count(*) FROM correlation
UNION ALL SELECT 'activity_summary', count(*) FROM activity_summary
UNION ALL SELECT 'user_profile', count(*) FROM user_profile;"
```

- [ ] **Step 2: Run the new Python app against the same export, into a fresh database**

```bash
docker compose exec db psql -U postgres -c "CREATE DATABASE pulso_verify;" 2>/dev/null || true
cd ../etl-python
DB_NAME=pulso_verify DB_HOST=localhost DB_USER=postgres DB_PASSWORD=postgres \
  uv run python -m pulso.cli --file ../../data/exportar.xml --no-progress
```

```bash
docker compose exec db psql -U postgres -d pulso_verify -c "
SELECT 'record', count(*) FROM record
UNION ALL SELECT 'workout', count(*) FROM workout
UNION ALL SELECT 'correlation', count(*) FROM correlation
UNION ALL SELECT 'activity_summary', count(*) FROM activity_summary
UNION ALL SELECT 'user_profile', count(*) FROM user_profile;"
```

- [ ] **Step 3: Compare the two count sets**

Every row must match exactly between `pulso_reference` and `pulso_verify`. If any table's count differs, stop — do not proceed to deletion. File the discrepancy as a bug against the specific loader module (the table name tells you which one) and fix it before continuing.

- [ ] **Step 4: Clean up the temporary comparison databases**

```bash
docker compose exec db psql -U postgres -c "DROP DATABASE pulso_reference;"
docker compose exec db psql -U postgres -c "DROP DATABASE pulso_verify;"
```

- [ ] **Step 5: Delete `apps/etl-clojure` and commit**

```bash
cd /home/yagoazedias/github/pulso
git rm -r apps/etl-clojure
git commit -m "chore: remove apps/etl-clojure, Python ETL has full parity

Verified row-count parity against a real Apple Health export across
record, workout, correlation, activity_summary, and user_profile."
```

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-09-etl-python-migration.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
