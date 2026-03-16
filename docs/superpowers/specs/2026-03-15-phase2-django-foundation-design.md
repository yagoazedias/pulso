# Phase 2: Django Foundation — Design Spec

## 1. Objective

Bootstrap the Django dashboard application inside the Pulso monorepo with a working end-to-end stack: Django serving a page with an embedded React chart component, built with Vite, rendering a placeholder Chart.js line chart. This phase proves the full integration pipeline before real data queries are added in Phase 3.

## 2. Approach

**Minimal Vertical Slice** — build only what's needed to prove the full stack works. One Django app, one unmanaged model, one repository stub, one React chart component with dummy data. Defer abstraction and real queries to later phases.

## 3. Technology Decisions

| Concern | Choice |
|---------|--------|
| Framework | Django (server-rendered pages) |
| Frontend | React islands embedded in Django templates (not SPA) |
| JS Bundler | Vite + `django-vite` |
| Charting | Chart.js + `react-chartjs-2` |
| Python deps | pip + `requirements.txt` |
| Testing | pytest + pytest-django |
| WSGI server | Gunicorn (production) |
| DB strategy | Same PostgreSQL instance, separate schemas (`public` for ETL, `dashboard` for Django) |
| Django models | Unmanaged (`managed = False`) for ETL-owned tables |

## 4. Project Structure

```
apps/dashboard-django/
├── manage.py
├── requirements.txt
├── vite.config.js
├── package.json
├── Dockerfile
├── dashboard_project/
│   ├── __init__.py
│   ├── settings.py
│   ├── urls.py
│   ├── wsgi.py
│   └── asgi.py
├── apps/
│   └── analytics/
│       ├── __init__.py
│       ├── models.py
│       ├── repositories.py
│       ├── views.py
│       ├── urls.py
│       ├── templates/
│       │   └── analytics/
│       │       └── index.html
│       └── tests/
│           ├── __init__.py
│           └── test_views.py
├── frontend/
│   └── src/
│       ├── main.jsx
│       └── components/
│           └── EnergyChart.jsx
├── pytest.ini
└── conftest.py
```

### Key layout decisions

- `frontend/` at the dashboard root separates JS concerns from Python.
- Vite builds into `frontend/static/`, `django-vite` injects script tags into templates.
- Tests live inside each Django app (`apps/analytics/tests/`).
- Nested `apps/` directory inside the Django project for future extensibility.

## 5. Database Schema Strategy

Django and the ETL share the same PostgreSQL database but use different schemas:

- **`public` schema** — ETL-owned tables (`activity_summary`, `workout`, `record`, `record_type`, etc.). Django reads from these via unmanaged models.
- **`dashboard` schema** — Django-owned tables (`django_migrations`, `django_session`, etc.). Created and managed by Django's migration system.

### Configuration

- Django's `DATABASES.default` sets `OPTIONS: { "options": "-c search_path=dashboard,public" }` so Django's own tables are created in `dashboard` while ETL tables in `public` remain accessible.
- Unmanaged models explicitly reference `db_table = '"public"."activity_summary"'`.

### Unmanaged model (Phase 2)

```python
class ActivitySummary(models.Model):
    date_components = models.DateField()
    active_energy_burned = models.FloatField()
    active_energy_burned_goal = models.FloatField()
    active_energy_burned_unit = models.CharField()

    class Meta:
        managed = False
        db_table = '"public"."activity_summary"'
```

Only `ActivitySummary` is defined in Phase 2. Additional unmanaged models (`Workout`, `Record`, `RecordType`) will be added in Phase 3 when their queries are implemented.

## 6. Repository Layer

```python
class PostgresMetricsRepository:
    def get_energy_vs_goal(self, days=90):
        """Stub - returns empty list. Implemented in Phase 3."""
        return []
```

- Plain class, no abstract base class yet.
- ABC / interface extraction deferred to Phase 6 (NoSQL readiness) when a second implementation is needed.

## 7. Frontend Wiring

### Django template (`analytics/index.html`)

```html
{% load django_vite %}
<!DOCTYPE html>
<html>
<head>
    <title>Pulso Dashboard</title>
    {% vite_hmr_client %}
</head>
<body>
    <div id="chart-root"></div>
    {% vite_asset 'src/main.jsx' %}
</body>
</html>
```

### React entry point (`main.jsx`)

Mounts a single `<EnergyChart />` component into `#chart-root`.

### Placeholder chart (`EnergyChart.jsx`)

- Uses `react-chartjs-2` `<Line />` component.
- Renders hardcoded dummy data (90 days of fake energy vs goal values).
- No API calls — proves the rendering pipeline works.

### Vite configuration

- `@vitejs/plugin-react` for JSX support.
- `build.outDir` → `frontend/static/`.
- `build.manifest` → `true` (required by `django-vite`).
- Dev server on port 5173 with HMR.

## 8. Dockerfile

Multi-stage production build:

**Stage 1 — Node builder:**
- `node:20-alpine`
- Install npm deps (`npm ci`)
- Build Vite assets (`npm run build`)

**Stage 2 — Python runner:**
- `python:3.12-slim`
- Install pip deps
- Copy built frontend assets from Stage 1
- Run `collectstatic`
- Serve with Gunicorn on port 8000

```dockerfile
# Stage 1: Build frontend assets
FROM node:20-alpine AS frontend
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY frontend/ frontend/
COPY vite.config.js ./
RUN npm run build

# Stage 2: Django + Gunicorn
FROM python:3.12-slim AS runner
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
COPY --from=frontend /app/frontend/static frontend/static
ENV DJANGO_SETTINGS_MODULE=dashboard_project.settings
RUN SECRET_KEY=build-placeholder python manage.py collectstatic --noinput
EXPOSE 8000
CMD ["gunicorn", "dashboard_project.wsgi:application", "--bind", "0.0.0.0:8000"]
```

## 9. Docker Compose Integration

Add `dashboard` service to the root `docker-compose.yml`:

```yaml
dashboard:
  build: ./apps/dashboard-django
  depends_on:
    db:
      condition: service_healthy
  environment:
    - DB_HOST=db
    - DB_NAME=pulso
    - DB_USER=postgres
    - DB_PASSWORD=postgres
    - DJANGO_SETTINGS_MODULE=dashboard_project.settings
    - DEBUG=false
  ports:
    - "8000:8000"
```

## 10. Testing

### Framework

pytest + pytest-django. Configuration in `pytest.ini` with `DJANGO_SETTINGS_MODULE`. Shared fixtures in `conftest.py`.

### Phase 2 test scope

1. **Smoke test** — GET `/` returns 200 and response contains `#chart-root` div.
2. **Model test** — `ActivitySummary._meta.managed` is `False`.
3. **Repository test** — `PostgresMetricsRepository().get_energy_vs_goal()` returns empty list.

## 11. Local Development Workflow

```bash
cd apps/dashboard-django

# 1. Install dependencies
pip install -r requirements.txt
npm install

# 2. Start PostgreSQL (from repo root)
docker compose up db

# 3. Create dashboard schema
psql -h localhost -U postgres -d pulso -c "CREATE SCHEMA IF NOT EXISTS dashboard;"

# 4. Run Django migrations (creates tables in dashboard schema)
python manage.py migrate

# 5. Run Django dev server
python manage.py runserver

# 6. Run Vite dev server (separate terminal)
npm run dev

# 7. Open http://localhost:8000 — placeholder chart with HMR

# 8. Run tests
pytest
```

## 12. Acceptance Criteria

- [ ] Django app starts locally and serves the landing page.
- [ ] Vite HMR works in development (edit React component → browser updates).
- [ ] Placeholder Chart.js line chart renders dummy energy vs goal data.
- [ ] `ActivitySummary` unmanaged model is defined and references the correct table.
- [ ] `PostgresMetricsRepository` skeleton exists with stub method.
- [ ] Django's own tables live in the `dashboard` schema.
- [ ] `pytest` runs and all 3 tests pass.
- [ ] Dockerfile builds successfully and serves the app via Gunicorn.
- [ ] `dashboard` service added to `docker-compose.yml`.

## 13. What This Phase Does NOT Include

- Real data queries (Phase 3).
- Additional unmanaged models beyond `ActivitySummary` (Phase 3).
- API endpoints returning JSON (Phase 3).
- Additional chart components (Phase 4).
- Date-range selectors or loading states (Phase 4).
- CI workflow for Django (Phase 5).
- Repository interface / ABC (Phase 6).
