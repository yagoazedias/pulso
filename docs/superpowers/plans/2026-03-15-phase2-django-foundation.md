# Phase 2: Django Foundation — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap a Django + React + Vite + Chart.js stack in `apps/dashboard-django/` that serves a landing page with a placeholder energy chart, proving the full integration pipeline end-to-end.

**Architecture:** Django serves HTML templates with React "islands" mounted into DOM nodes. Vite bundles React/Chart.js and provides HMR in dev; django-vite bridges the two. Django reads ETL-owned tables via unmanaged models in the `public` schema and stores its own tables in a `dashboard` schema.

**Tech Stack:** Django 5.x, React 18, Vite 6, django-vite, Chart.js 4 + react-chartjs-2, pytest + pytest-django, Gunicorn, Docker multi-stage build.

**Spec:** `docs/superpowers/specs/2026-03-15-phase2-django-foundation-design.md`

---

## Chunk 1: Django Project Bootstrap + Database Configuration

### Task 1: Initialize Django project and analytics app

**Files:**
- Create: `apps/dashboard-django/manage.py`
- Create: `apps/dashboard-django/dashboard_project/__init__.py`
- Create: `apps/dashboard-django/dashboard_project/settings.py`
- Create: `apps/dashboard-django/dashboard_project/urls.py`
- Create: `apps/dashboard-django/dashboard_project/wsgi.py`
- Create: `apps/dashboard-django/dashboard_project/asgi.py`
- Create: `apps/dashboard-django/apps/__init__.py`
- Create: `apps/dashboard-django/apps/analytics/__init__.py`
- Create: `apps/dashboard-django/apps/analytics/views.py`
- Create: `apps/dashboard-django/apps/analytics/urls.py`
- Create: `apps/dashboard-django/apps/analytics/models.py`
- Create: `apps/dashboard-django/apps/analytics/repositories.py`
- Create: `apps/dashboard-django/requirements.txt`

- [ ] **Step 1: Create requirements.txt**

```
apps/dashboard-django/requirements.txt
```

```txt
Django>=5.1,<5.2
psycopg2-binary>=2.9,<3.0
django-vite>=3.0,<4.0
gunicorn>=22.0,<23.0
pytest>=8.0,<9.0
pytest-django>=4.8,<5.0
```

- [ ] **Step 2: Create Django project files**

Create `apps/dashboard-django/manage.py`:

```python
#!/usr/bin/env python
import os
import sys


def main():
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "dashboard_project.settings")
    from django.core.management import execute_from_command_line
    execute_from_command_line(sys.argv)


if __name__ == "__main__":
    main()
```

Create `apps/dashboard-django/dashboard_project/__init__.py` — empty file.

Create `apps/dashboard-django/dashboard_project/settings.py`:

```python
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY = os.environ.get(
    "SECRET_KEY",
    "django-insecure-dev-only-change-in-production",
)

DEBUG = os.environ.get("DEBUG", "true").lower() in ("true", "1", "yes")

ALLOWED_HOSTS = os.environ.get("ALLOWED_HOSTS", "localhost,127.0.0.1").split(",")

INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.staticfiles",
    "django_vite",
    "apps.analytics",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.middleware.common.CommonMiddleware",
]

ROOT_URLCONF = "dashboard_project.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
            ],
        },
    },
]

WSGI_APPLICATION = "dashboard_project.wsgi.application"

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ.get("DB_NAME", "pulso"),
        "USER": os.environ.get("DB_USER", "postgres"),
        "PASSWORD": os.environ.get("DB_PASSWORD", "postgres"),
        "HOST": os.environ.get("DB_HOST", "localhost"),
        "PORT": os.environ.get("DB_PORT", "5432"),
        "OPTIONS": {
            "options": "-c search_path=dashboard,public",
        },
    }
}

STATIC_URL = "/static/"
STATICFILES_DIRS = [
    BASE_DIR / "frontend" / "static",
]
STATIC_ROOT = BASE_DIR / "staticfiles"

DJANGO_VITE = {
    "default": {
        "dev_mode": DEBUG,
        "dev_server_host": "localhost",
        "dev_server_port": 5173,
        "manifest_path": BASE_DIR / "frontend" / "static" / ".vite" / "manifest.json",
    }
}

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
```

Create `apps/dashboard-django/dashboard_project/urls.py`:

```python
from django.urls import include, path

urlpatterns = [
    path("", include("apps.analytics.urls")),
]
```

Create `apps/dashboard-django/dashboard_project/wsgi.py`:

```python
import os

from django.core.wsgi import get_wsgi_application

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "dashboard_project.settings")
application = get_wsgi_application()
```

Create `apps/dashboard-django/dashboard_project/asgi.py`:

```python
import os

from django.core.asgi import get_asgi_application

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "dashboard_project.settings")
application = get_asgi_application()
```

- [ ] **Step 3: Create analytics app files**

Create `apps/dashboard-django/apps/__init__.py` — empty file.

Create `apps/dashboard-django/apps/analytics/__init__.py` — empty file.

Create `apps/dashboard-django/apps/analytics/models.py`:

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
```

Create `apps/dashboard-django/apps/analytics/repositories.py`:

```python
class PostgresMetricsRepository:
    def get_energy_vs_goal(self, days=90):
        """Stub - returns empty list. Implemented in Phase 3."""
        return []
```

Create `apps/dashboard-django/apps/analytics/views.py`:

```python
from django.shortcuts import render


def index(request):
    return render(request, "analytics/index.html")
```

Create `apps/dashboard-django/apps/analytics/urls.py`:

```python
from django.urls import path

from . import views

urlpatterns = [
    path("", views.index, name="index"),
]
```

- [ ] **Step 4: Verify Django project loads**

Run from `apps/dashboard-django/`:

```bash
pip install -r requirements.txt
python manage.py check
```

Expected: `System check identified no issues (0 silenced).`

- [ ] **Step 5: Commit**

```bash
git add apps/dashboard-django/
git commit -m "Bootstrap Django project with analytics app and PostgreSQL schema config"
```

---

### Task 2: Set up pytest and write initial tests

**Files:**
- Create: `apps/dashboard-django/pytest.ini`
- Create: `apps/dashboard-django/conftest.py`
- Create: `apps/dashboard-django/apps/analytics/tests/__init__.py`
- Create: `apps/dashboard-django/apps/analytics/tests/test_views.py`
- Create: `apps/dashboard-django/apps/analytics/tests/test_models.py`
- Create: `apps/dashboard-django/apps/analytics/tests/test_repositories.py`

- [ ] **Step 1: Create pytest configuration**

Create `apps/dashboard-django/pytest.ini`:

```ini
[pytest]
DJANGO_SETTINGS_MODULE = dashboard_project.settings
pythonpath = .
```

Create `apps/dashboard-django/conftest.py` — empty file (placeholder for shared fixtures).

- [ ] **Step 2: Write the smoke test for the landing page**

Create `apps/dashboard-django/apps/analytics/tests/__init__.py` — empty file.

Create `apps/dashboard-django/apps/analytics/tests/test_views.py`:

```python
import pytest
from django.test import Client


@pytest.mark.django_db
def test_index_returns_200():
    client = Client()
    response = client.get("/")
    assert response.status_code == 200


@pytest.mark.django_db
def test_index_contains_chart_root():
    client = Client()
    response = client.get("/")
    assert b'id="chart-root"' in response.content
```

- [ ] **Step 3: Write the model test**

Create `apps/dashboard-django/apps/analytics/tests/test_models.py`:

```python
from apps.analytics.models import ActivitySummary


def test_activity_summary_is_unmanaged():
    assert ActivitySummary._meta.managed is False


def test_activity_summary_table_name():
    assert ActivitySummary._meta.db_table == '"public"."activity_summary"'
```

- [ ] **Step 4: Write the repository test**

Create `apps/dashboard-django/apps/analytics/tests/test_repositories.py`:

```python
from apps.analytics.repositories import PostgresMetricsRepository


def test_get_energy_vs_goal_returns_empty_list():
    repo = PostgresMetricsRepository()
    result = repo.get_energy_vs_goal()
    assert result == []


def test_get_energy_vs_goal_accepts_days_param():
    repo = PostgresMetricsRepository()
    result = repo.get_energy_vs_goal(days=30)
    assert result == []
```

- [ ] **Step 5: Create the template so view tests pass**

Create `apps/dashboard-django/apps/analytics/templates/analytics/index.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Pulso Dashboard</title>
</head>
<body>
    <div id="chart-root"></div>
</body>
</html>
```

Note: This is a minimal template without Vite tags. The `{% load django_vite %}` and asset tags will be added in Task 4 when Vite is configured. This avoids test failures from missing Vite manifest before the frontend is set up.

- [ ] **Step 6: Run tests to verify they pass**

Run from `apps/dashboard-django/`:

```bash
pytest -v
```

Expected: All 6 tests pass.

```
test_views.py::test_index_returns_200 PASSED
test_views.py::test_index_contains_chart_root PASSED
test_models.py::test_activity_summary_is_unmanaged PASSED
test_models.py::test_activity_summary_table_name PASSED
test_repositories.py::test_get_energy_vs_goal_returns_empty_list PASSED
test_repositories.py::test_get_energy_vs_goal_accepts_days_param PASSED
```

- [ ] **Step 7: Commit**

```bash
git add apps/dashboard-django/pytest.ini apps/dashboard-django/conftest.py apps/dashboard-django/apps/analytics/tests/ apps/dashboard-django/apps/analytics/templates/
git commit -m "Add pytest setup and initial tests for views, models, and repository"
```

---

## Chunk 2: Frontend Setup (Vite + React + Chart.js)

### Task 3: Initialize Vite and React frontend

**Files:**
- Create: `apps/dashboard-django/package.json`
- Create: `apps/dashboard-django/vite.config.js`
- Create: `apps/dashboard-django/frontend/src/main.jsx`
- Create: `apps/dashboard-django/frontend/src/components/EnergyChart.jsx`
- Create: `apps/dashboard-django/.gitignore`

- [ ] **Step 1: Create package.json**

Create `apps/dashboard-django/package.json`:

```json
{
  "name": "pulso-dashboard",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build"
  },
  "dependencies": {
    "chart.js": "^4.4.0",
    "react": "^18.3.0",
    "react-chartjs-2": "^5.2.0",
    "react-dom": "^18.3.0"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.0",
    "vite": "^6.0.0"
  }
}
```

- [ ] **Step 2: Create Vite configuration**

Create `apps/dashboard-django/vite.config.js`:

```js
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  root: "frontend",
  build: {
    outDir: "static",
    manifest: true,
    rollupOptions: {
      input: "frontend/src/main.jsx",
    },
  },
  server: {
    port: 5173,
    origin: "http://localhost:5173",
  },
});
```

- [ ] **Step 3: Create React entry point**

Create `apps/dashboard-django/frontend/src/main.jsx`:

```jsx
import React from "react";
import { createRoot } from "react-dom/client";
import EnergyChart from "./components/EnergyChart";

const container = document.getElementById("chart-root");
if (container) {
  const root = createRoot(container);
  root.render(<EnergyChart />);
}
```

- [ ] **Step 4: Create placeholder EnergyChart component**

Create `apps/dashboard-django/frontend/src/components/EnergyChart.jsx`:

```jsx
import React from "react";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

function generateDummyData() {
  const labels = [];
  const burned = [];
  const goal = [];
  const today = new Date();

  for (let i = 89; i >= 0; i--) {
    const date = new Date(today);
    date.setDate(today.getDate() - i);
    labels.push(date.toISOString().split("T")[0]);
    burned.push(Math.round(300 + Math.random() * 400));
    goal.push(500);
  }

  return { labels, burned, goal };
}

const { labels, burned, goal } = generateDummyData();

const data = {
  labels,
  datasets: [
    {
      label: "Active Energy Burned (kcal)",
      data: burned,
      borderColor: "rgb(255, 99, 132)",
      backgroundColor: "rgba(255, 99, 132, 0.1)",
      tension: 0.3,
    },
    {
      label: "Goal (kcal)",
      data: goal,
      borderColor: "rgb(75, 192, 192)",
      backgroundColor: "rgba(75, 192, 192, 0.1)",
      borderDash: [5, 5],
      tension: 0.3,
    },
  ],
};

const options = {
  responsive: true,
  plugins: {
    title: {
      display: true,
      text: "Daily Active Energy vs Goal (Last 90 Days)",
    },
    legend: {
      position: "top",
    },
  },
  scales: {
    y: {
      beginAtZero: true,
      title: {
        display: true,
        text: "kcal",
      },
    },
  },
};

export default function EnergyChart() {
  return <Line data={data} options={options} />;
}
```

- [ ] **Step 5: Create .gitignore for the dashboard app**

Create `apps/dashboard-django/.gitignore`:

```
node_modules/
frontend/static/
staticfiles/
__pycache__/
*.pyc
.venv/
db.sqlite3
```

- [ ] **Step 6: Install npm dependencies and verify Vite builds**

Run from `apps/dashboard-django/`:

```bash
npm install
npm run build
```

Expected: Build succeeds, files appear in `frontend/static/` including `.vite/manifest.json`.

- [ ] **Step 7: Commit**

```bash
git add apps/dashboard-django/package.json apps/dashboard-django/vite.config.js apps/dashboard-django/frontend/src/ apps/dashboard-django/.gitignore
git commit -m "Add Vite + React + Chart.js frontend with placeholder energy chart"
```

---

### Task 4: Wire Django template to Vite assets

**Files:**
- Modify: `apps/dashboard-django/apps/analytics/templates/analytics/index.html`

- [ ] **Step 1: Update the template with django-vite tags**

Replace `apps/dashboard-django/apps/analytics/templates/analytics/index.html` with:

```html
{% load django_vite %}
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Pulso Dashboard</title>
    {% vite_hmr_client %}
</head>
<body>
    <div id="chart-root"></div>
    {% vite_asset "src/main.jsx" %}
</body>
</html>
```

- [ ] **Step 2: Build Vite assets (required for tests)**

The `{% vite_asset %}` tag requires either dev mode (Vite dev server running) or the built manifest. Build the assets so tests can run with `DEBUG=false`.

Run from `apps/dashboard-django/`:

```bash
npm run build
ls frontend/static/.vite/manifest.json
```

Expected: Build succeeds and `manifest.json` exists.

- [ ] **Step 3: Run tests to verify nothing broke**

Run from `apps/dashboard-django/`:

```bash
DEBUG=false pytest -v
```

Note: `DEBUG=false` is required so `django-vite` reads the built manifest instead of trying to reach the Vite dev server. All subsequent test runs should also use `DEBUG=false` unless the Vite dev server is running.

Expected: All 6 tests still pass.

- [ ] **Step 4: Commit**

```bash
git add apps/dashboard-django/apps/analytics/templates/analytics/index.html
git commit -m "Wire Django template to Vite assets with django-vite tags"
```

---

## Chunk 3: Docker & Docker Compose

### Task 5: Create Dockerfile for the Django app

**Files:**
- Create: `apps/dashboard-django/Dockerfile`

- [ ] **Step 1: Create the multi-stage Dockerfile**

Create `apps/dashboard-django/Dockerfile`:

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

RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
COPY --from=frontend /app/frontend/static frontend/static

ENV DJANGO_SETTINGS_MODULE=dashboard_project.settings
RUN SECRET_KEY=build-placeholder python manage.py collectstatic --noinput

EXPOSE 8000
CMD ["gunicorn", "dashboard_project.wsgi:application", "--bind", "0.0.0.0:8000"]
```

- [ ] **Step 2: Verify Docker build succeeds**

Run from the repository root:

```bash
docker build -t pulso-dashboard apps/dashboard-django/
```

Expected: Build completes successfully.

- [ ] **Step 3: Commit**

```bash
git add apps/dashboard-django/Dockerfile
git commit -m "Add multi-stage Dockerfile with Gunicorn for Django dashboard"
```

---

### Task 6: Add dashboard service to docker-compose.yml

**Files:**
- Modify: `docker-compose.yml` (repository root)

- [ ] **Step 1: Add dashboard service**

Add the following service to `docker-compose.yml`, after the `metabase` service and before the `volumes:` section:

```yaml
  dashboard:
    build:
      context: ./apps/dashboard-django
      dockerfile: Dockerfile
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_HOST: db
      DB_PORT: "5432"
      DB_NAME: pulso
      DB_USER: postgres
      DB_PASSWORD: postgres
      DJANGO_SETTINGS_MODULE: dashboard_project.settings
      SECRET_KEY: change-me-in-production
      DEBUG: "false"
    ports:
      - "8000:8000"
```

- [ ] **Step 2: Add postgres-init script for dashboard schema**

Create `apps/etl-clojure/resources/postgres-init/02-create-dashboard-schema.sql`:

```sql
CREATE SCHEMA IF NOT EXISTS dashboard;
```

This is picked up by the `db` service's init volume mount (`./apps/etl-clojure/resources/postgres-init:/docker-entrypoint-initdb.d:ro`) and runs automatically on first database creation.

- [ ] **Step 3: Verify docker-compose config is valid**

Run from the repository root:

```bash
docker compose config --quiet
```

Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml apps/etl-clojure/resources/postgres-init/02-create-dashboard-schema.sql
git commit -m "Add dashboard service to docker-compose and auto-create dashboard schema"
```

---

### Task 7: End-to-end verification

This task is a manual verification step — no new files.

- [ ] **Step 1: Verify local dev workflow**

Run from `apps/dashboard-django/`:

```bash
# Terminal 1: Start PostgreSQL
docker compose up db

# Terminal 2: Create schema + migrate
psql -h localhost -U postgres -d pulso -c "CREATE SCHEMA IF NOT EXISTS dashboard;"
cd apps/dashboard-django
python manage.py migrate

# Terminal 3: Start Django
cd apps/dashboard-django
python manage.py runserver

# Terminal 4: Start Vite
cd apps/dashboard-django
npm run dev
```

Open `http://localhost:8000` — verify the placeholder energy chart renders with HMR.

- [ ] **Step 2: Verify Docker Compose workflow**

Run from the repository root:

```bash
docker compose up --build dashboard
```

Open `http://localhost:8000` — verify the chart renders via Gunicorn.

- [ ] **Step 3: Run full test suite**

Run from `apps/dashboard-django/`:

```bash
DEBUG=false pytest -v
```

Expected: All 6 tests pass.

- [ ] **Step 4: Verify Django tables are in the dashboard schema**

After running `python manage.py migrate`, verify Django's own tables were created in the `dashboard` schema:

```bash
psql -h localhost -U postgres -d pulso -c "SELECT schemaname, tablename FROM pg_tables WHERE schemaname='dashboard';"
```

Expected: Django tables like `django_migrations`, `django_content_type` appear under the `dashboard` schema.

- [ ] **Step 5: Verify Vite HMR works**

With both Django (`python manage.py runserver`) and Vite (`npm run dev`) running:

1. Open `http://localhost:8000` in the browser
2. Edit `frontend/src/components/EnergyChart.jsx` — change the chart title text
3. Observe the browser updates automatically without a full page reload

- [ ] **Step 6: Final commit (if any fixes were needed)**

```bash
git add apps/dashboard-django/ docker-compose.yml
git commit -m "Fix issues found during end-to-end verification"
```

Only commit if fixes were needed. Skip if everything passed cleanly.
