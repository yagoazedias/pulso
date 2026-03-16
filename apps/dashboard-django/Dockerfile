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
