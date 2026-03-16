# Phase 3: Metrics Backend + APIs — Design Spec

## 1. Objective

Implement the 3 chart API endpoints for the Pulso dashboard, backed by SQL queries against ETL-owned tables. Each endpoint returns a Chart.js-compatible JSON response with optional date range filtering.

## 2. Approach

**Repository + Views** — queries live in `PostgresMetricsRepository`, views parse params and return `JsonResponse`. No service layer or DRF — the scope is 3 read-only endpoints with no auth, pagination, or mutation.

## 3. Decisions

| Concern | Choice |
|---------|--------|
| API response contract | Unchanged from V1 spec: `{labels, datasets, meta}` |
| Query parameters | `?start=YYYY-MM-DD&end=YYYY-MM-DD` (both optional) |
| Default windows | Energy: 90 days, Workout/Records: 12 weeks |
| Week bucketing | ISO weeks (Monday–Sunday, `DATE_TRUNC('week')`) |
| Empty ranges | Return empty arrays, not 404 |
| Invalid params | Return 400 with error message |
| Testing | pytest with SQLite fixtures, integration + unit tests |

## 4. Unmanaged Models

Add to `apps/analytics/models.py` alongside existing `ActivitySummary`:

```python
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
    record_type = models.ForeignKey('RecordType', on_delete=models.DO_NOTHING)
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

Only fields needed for the 3 chart queries are mapped.

## 5. Repository Methods

All methods on `PostgresMetricsRepository`. Each accepts `start: str = None, end: str = None` (date strings). If not provided, defaults apply.

### `get_energy_vs_goal(start=None, end=None)`

- **Default window:** last 90 days from today
- **Source:** `activity_summary`
- **Query:** Select `date_components`, `active_energy_burned`, `active_energy_burned_goal` where `date_components BETWEEN start AND end`, ordered by date
- **Response:**
```json
{
  "labels": ["2026-01-01", "2026-01-02", "..."],
  "datasets": [
    {"label": "Active Energy Burned", "data": [450, 520, "..."]},
    {"label": "Goal", "data": [500, 500, "..."]}
  ],
  "meta": {"unit": "kcal", "window": "90d", "last_updated": "2026-03-15T00:00:00Z"}
}
```
- `meta.window` is `"90d"` when using defaults, `"custom"` when params provided
- `meta.last_updated` is the max `date_components` in the result set (or null if empty)

### `get_workout_volume(start=None, end=None)`

- **Default window:** last 12 ISO weeks from today
- **Source:** `workout`
- **Query:** Group by `DATE_TRUNC('week', start_date)`, aggregate `COUNT(*)`, `COALESCE(SUM(duration), 0)`, `COALESCE(SUM(total_energy_burned), 0)`, ordered by week
- **Response:**
```json
{
  "labels": ["2026-01-06", "2026-01-13", "..."],
  "datasets": [
    {"label": "Workouts", "data": [5, 3, "..."]},
    {"label": "Duration (min)", "data": [180, 120, "..."]},
    {"label": "Energy Burned (kcal)", "data": [1200, 800, "..."]}
  ],
  "meta": {"unit": "mixed", "window": "12w", "last_updated": "2026-03-10T00:00:00Z"}
}
```
- Labels are the Monday of each ISO week
- Duration values are in minutes (raw column is seconds — divide by 60)
- `COALESCE` ensures null durations/energy don't produce null sums

### `get_top_record_types(start=None, end=None)`

- **Default window:** last 12 ISO weeks from today
- **Source:** `record` + `record_type`
- **Two-step query:**
  1. Find top 5 record types by total count in the date range
  2. Get weekly counts for those 5 types, grouped by `DATE_TRUNC('week', start_date)` and `record_type.identifier`
- **Response:**
```json
{
  "labels": ["2026-01-06", "2026-01-13", "..."],
  "datasets": [
    {"label": "HKQuantityTypeIdentifierHeartRate", "data": [1200, 1100, "..."]},
    {"label": "HKQuantityTypeIdentifierStepCount", "data": [800, 750, "..."]},
    "..."
  ],
  "meta": {"unit": "count", "window": "12w", "last_updated": "2026-03-10T00:00:00Z"}
}
```
- Returns fewer than 5 datasets if fewer types exist in the range
- Labels are the Monday of each ISO week

## 6. API Views

Three view functions in `apps/analytics/views.py`:

```python
def energy_vs_goal(request):
    # Parse start/end from request.GET
    # Validate date format (YYYY-MM-DD) → 400 if invalid
    # Call repository.get_energy_vs_goal(start, end)
    # Return JsonResponse

def workout_volume(request):
    # Same pattern

def top_record_types(request):
    # Same pattern
```

**Shared param parsing logic:** Extract into a helper function `parse_date_params(request)` that returns `(start, end)` or raises `ValueError` on invalid format. Views catch `ValueError` and return 400.

## 7. URL Routing

Add to `apps/analytics/urls.py`:

```python
urlpatterns = [
    path("", views.index, name="index"),
    path("api/metrics/energy-vs-goal", views.energy_vs_goal, name="energy-vs-goal"),
    path("api/metrics/workout-volume", views.workout_volume, name="workout-volume"),
    path("api/metrics/top-record-types", views.top_record_types, name="top-record-types"),
]
```

## 8. Testing

### Test fixtures (in `conftest.py`)

- `energy_data` — 5 `ActivitySummary` rows with known dates and values
- `workout_data` — 6 `Workout` rows spanning 2 ISO weeks
- `record_data` — 3 `RecordType` entries + `Record` rows spanning 2 weeks with varied counts

### API integration tests (`test_api.py`)

**Energy vs goal:**
- Returns 200 with correct JSON structure (labels, datasets, meta)
- Default range returns data from fixture
- `?start=...&end=...` filters correctly
- Empty range returns empty arrays
- Invalid date format returns 400

**Workout volume:**
- Returns 200 with 3 datasets (count, duration, energy)
- Weekly aggregation is correct
- Date range filtering works
- Empty range returns empty arrays
- Null duration/energy don't break sums

**Top record types:**
- Returns 200 with datasets per type
- Returns types ordered by volume (most first)
- Weekly bucketing is correct
- Date range filtering works
- Returns fewer than 5 if fewer types exist

### Repository unit tests (extend `test_repositories.py`)

- Each method returns correct dict structure with fixture data
- Date filtering at repository level
- Null-safe aggregations work correctly

## 9. Files Changed

| File | Action |
|------|--------|
| `apps/analytics/models.py` | Add `Workout`, `Record`, `RecordType` models |
| `apps/analytics/repositories.py` | Implement 3 query methods |
| `apps/analytics/views.py` | Add 3 API view functions + param parser |
| `apps/analytics/urls.py` | Add 3 API routes |
| `conftest.py` | Add test fixtures |
| `apps/analytics/tests/test_api.py` | Create API integration tests |
| `apps/analytics/tests/test_repositories.py` | Extend with query tests |

## 10. Acceptance Criteria

- [ ] `GET /api/metrics/energy-vs-goal` returns contract-compliant JSON from `activity_summary`
- [ ] `GET /api/metrics/workout-volume` returns weekly aggregations from `workout`
- [ ] `GET /api/metrics/top-record-types` returns top 5 types with weekly counts
- [ ] All endpoints accept optional `?start=...&end=...` query params
- [ ] Invalid date params return 400 with error message
- [ ] Empty date ranges return empty arrays (not 404)
- [ ] Null values in duration/energy don't break aggregations
- [ ] All tests pass with `pytest -v`
- [ ] Query time < 1s for default windows (validated manually against real data)

## 11. What This Phase Does NOT Include

- Frontend chart components consuming these APIs (Phase 4)
- Date-range selector UI (Phase 4)
- Loading/empty/error states in UI (Phase 4)
- CI workflow for Django (Phase 5)
- Repository interface / ABC (Phase 6)
