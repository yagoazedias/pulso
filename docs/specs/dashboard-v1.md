# Django Analytics Monorepo Plan

## 1. Objective
Build a Django-based analytics application inside this repository to replace the Metabase-first workflow for core product views, while keeping Pulso ETL as the ingestion pipeline.

Initial dashboard scope is intentionally limited to 3 charts:
1. Daily Active Energy vs Goal
2. Workout Volume Trend
3. Top Record Types Over Time

This document is the implementation spec and phased plan.

## 2. Scope
### In Scope (V1)
- Monorepo folder reorganization.
- New Django project for dashboard + JSON metric APIs.
- PostgreSQL-backed metrics repository.
- Main page with Chart.js rendering the 3 approved charts.
- Frontend architecture that is as componentized as possible (reusable UI and chart modules).
- Django automated tests (unit + API/integration level for metrics endpoints).
- Dedicated GitHub Actions workflow to build and test the Django project.
- Basic documentation and local developer workflow.

### Out of Scope (V1)
- NoSQL implementation (planned for later phases).
- Full Metabase parity.
- Auth/permissions beyond local/internal use.
- Mobile app or external public API.

## 2.1 UI Reference (V1)
Use the following image as the visual reference for the first dashboard implementation:

- `.agents/plans/images/Screenshot 2026-02-27 201514.png`

Guidance:
- Match layout hierarchy, visual density, and chart grouping from the reference.
- Preserve V1 functional scope (only the 3 approved charts), even if the reference shows additional widgets.
- Treat this image as a direction for UI composition, not as a strict pixel-perfect requirement.

## 3. Proposed Monorepo Structure
```text
.
├── apps/
│   ├── etl-clojure/                 # Current Pulso project (moved from repo root)
│   │   ├── src/
│   │   ├── resources/
│   │   ├── test/
│   │   ├── project.clj
│   │   └── Dockerfile
│   └── dashboard-django/
│       ├── manage.py
│       ├── pyproject.toml
│       ├── dashboard_project/       # settings, urls, wsgi/asgi
│       └── apps/
│           └── analytics/
│               ├── views.py
│               ├── urls.py
│               ├── services/
│               ├── repositories/
│               ├── templates/
│               └── static/
├── infra/
│   ├── docker/
│   └── compose/
├── data/
├── docs/
│   └── specs/
│       └── dashboard-v1.md
├── scripts/
└── README.md
```

Notes:
- Keep `resources/migrations` with ETL app (`apps/etl-clojure/resources/migrations`).
- Centralize cross-app scripts in `/scripts`.
- Move this plan into `docs/specs/dashboard-v1.md` during Phase 1.

## 4. Architecture Decisions
- ETL remains source of truth for loading and normalizing health data.
- Django acts as read/query layer for UI analytics.
- Frontend should maximize componentization: shared layout components, reusable chart wrapper, reusable metric cards, and isolated data-fetch modules.
- Use repository pattern in Django:
  - `MetricsRepository` (interface)
  - `PostgresMetricsRepository` (V1)
  - `NoSqlMetricsRepository` (future)
- Keep API response contracts stable so storage backend can change without frontend rewrites.

## 5. Chart Specifications (V1)
### Chart A: Daily Active Energy vs Goal
- Type: Line chart (2 datasets).
- Grain: Daily.
- Source: `activity_summary`.
- Fields: `date_components`, `active_energy_burned`, `active_energy_burned_goal`, `active_energy_burned_unit`.
- Default window: last 90 days.

### Chart B: Workout Volume Trend
- Type: Multi-line or combo (count + duration + total energy).
- Grain: Weekly.
- Source: `workout`.
- Fields: `start_date`, `duration`, `total_energy_burned`.
- Metrics:
  - workouts per week
  - total duration per week
  - total energy burned per week

### Chart C: Top Record Types Over Time
- Type: Multi-series line chart (top N record types).
- Grain: Weekly.
- Source: `record` + `record_type`.
- Fields: `record.start_date`, `record_type.identifier`.
- Rule: top 5 record types by volume in selected range.

## 6. API Contract (Stable)
Each chart endpoint returns:
```json
{
  "labels": ["2026-01-01", "2026-01-02"],
  "datasets": [{"label": "...", "data": [1, 2]}],
  "meta": {"unit": "kcal", "window": "90d", "last_updated": "..."}
}
```

Initial endpoints:
- `GET /api/metrics/energy-vs-goal`
- `GET /api/metrics/workout-volume`
- `GET /api/metrics/top-record-types`

## 7. Development Phases
### Phase 0: Planning & Alignment
- Confirm naming, ownership, and delivery milestones.
- Freeze V1 chart and API scope to the 3 charts listed above.

### Phase 1: Monorepo Reorganization (First Implementation Phase)
- Move current Clojure app into `apps/etl-clojure/`.
- Introduce `apps/dashboard-django/`, `infra/`, `docs/specs/`, and `scripts/`.
- Update paths in Docker, CI, and README.
- Add root-level task shortcuts (for example `make etl-test`, `make dashboard-run`).

Acceptance criteria:
- Existing ETL tests still pass from new location.
- Existing Docker workflow still runs.
- Repository docs reflect new structure.

### Phase 2: Django Foundation
- Bootstrap Django project and `analytics` app.
- Add PostgreSQL connectivity and settings management.
- Implement repository interface and postgres repository skeleton.
- Add landing page with Chart.js assets wired.
- Set up Django test framework and base test structure.

Acceptance criteria:
- Django app starts locally.
- Placeholder chart data renders on main page.
- `python -m pytest` or `python manage.py test` runs successfully with baseline tests.

### Phase 3: Metrics Backend + APIs
- Implement SQL queries for the 3 charts.
- Add service layer + endpoint serializers.
- Handle timezone, empty ranges, and null-safe aggregations.

Acceptance criteria:
- Endpoints return contract-compliant JSON.
- Query time acceptable for default windows (< 1s local target).

### Phase 4: Frontend Charts (Chart.js)
- Implement 3 production chart components.
- Add date-range selector and loading/empty/error states.
- Add chart-level metadata (units, latest update).
- Refactor shared UI patterns into reusable components (no duplicated chart setup logic across views).

Acceptance criteria:
- All 3 charts render from live API data.
- Main dashboard is usable on desktop and mobile.
- Chart rendering/configuration logic is centralized in reusable components/modules.

### Phase 5: Quality, Ops, and Release
- Add tests for services/repositories/API views.
- Add lint/format/type-check steps for Django app.
- Add CI jobs for ETL and Django paths.
- Add a dedicated workflow (for example `.github/workflows/django-tests.yml`) that installs dependencies, builds static assets if needed, and runs Django tests on pushes/PRs.
- Prepare release notes and migration guide.

Acceptance criteria:
- CI is green for both apps.
- Documented runbook for local setup and troubleshooting.
- Django workflow runs independently and blocks merge when build/tests fail.

### Phase 6: NoSQL Readiness (Post-V1)
- Introduce denormalized metric document model.
- Implement `NoSqlMetricsRepository` behind feature flag.
- Run dual-read validation against PostgreSQL outputs.

Acceptance criteria:
- API contract unchanged.
- Delta checks for chart outputs are within agreed tolerance.

## 8. Risks and Mitigations
- Repo move breaks scripts/CI: mitigate with path-by-path migration checklist and incremental PRs.
- Heavy aggregation queries: mitigate with indexes/materialized rollups in later iteration.
- Data semantics drift across backends: mitigate with contract tests and dual-read validation.

## 9. Definition of Done (V1)
- Monorepo structure adopted and documented.
- Django dashboard shipped with 3 charts (A/B/C).
- PostgreSQL-backed metrics APIs are stable and tested.
- CI covers ETL and Django changes.
