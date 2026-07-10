# Monorepo Split — Design Spec

## 1. Objective

Split the `pulso` monorepo into two standalone GitHub repositories, `pulso-etl` and `pulso-dashboard`, so each app's stack, tooling, CI, and release cadence are fully independent — no shared CI runs, no shared `docker-compose.yml`, no cross-app path filters.

This split executes **after** the ETL Clojure→Python migration (`docs/superpowers/plans/2026-07-09-etl-python-migration.md`) is complete and `apps/etl-clojure` is deleted, so `pulso-etl` is born as a pure Python repo with no Clojure history mixed into new code.

## 2. Target Repositories

| Repo | Content | GitHub visibility |
|---|---|---|
| `pulso-etl` | ETL pipeline (Python), Postgres, migrations, Metabase | Public |
| `pulso-dashboard` | Django app + Vite/React frontend | Public |

No third "infra" repo. Each repo is self-sufficient to run standalone.

### Ownership boundary

`pulso-etl` owns the Postgres schema and instance for local dev: its `docker-compose.yml` runs `db` + `app` (etl) + `metabase`, same shape as today's root compose minus the dashboard service. `pulso-dashboard`'s `docker-compose.yml` runs only the `dashboard` service, connecting to an already-running Postgres via env vars (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) — no `db` service of its own for the "real data" workflow.

Local dev workflow to run the full stack: start `pulso-etl`'s compose first (brings up Postgres + runs migrations + loads data), then start `pulso-dashboard`'s compose pointing at that Postgres.

## 3. File Mapping

### `pulso-etl`

Extracted from `apps/etl-python/` (post-migration) plus these root-level files:

- Code, tests, `Dockerfile`, migrations of the ETL app → repo root
- New `docker-compose.yml`: `db` + `app` + `metabase` (ported from current root compose)
- `data/` (gitignored, recreated locally per-clone), `examples/*.png` (Metabase screenshots)
- `PLAN.md`, `TEST_PLAN.md`, `AGENTS.md` (currently at monorepo root; content is ETL-specific)
- `.agents/plans/etl-performance-optimization.md`, `.agents/plans/etl-progress-bar.md`, `.agents/changelog/*.md`
- `docs/superpowers/specs/2026-07-09-etl-python-migration-design.md` and `docs/superpowers/plans/2026-07-09-etl-python-migration.md`
- `.github/workflows/tests.yml`, `.github/workflows/docker.yml` — trimmed to ETL-only jobs, no `working-directory`/path-filter indirection

### `pulso-dashboard`

Extracted from `apps/dashboard-django/` plus these root-level files:

- Code (Django + `frontend/`), `Dockerfile` → repo root
- New `docker-compose.yml`: `dashboard` service only, Postgres connection via env vars
- `docs/superpowers/specs/2026-03-15-phase2-django-foundation-design.md`, `2026-03-15-phase3-metrics-backend-design.md`, `2026-04-04-frontend-tests-design.md` and their corresponding plans
- `docs/specs/dashboard-v1.md`, `DASHBOARD_MONOREPO_PLAN.md`, `.agents/plans/DASHBOARD_MONOREPO_PLAN.md` (+ `.agents/plans/images/Screenshot 2026-02-27 201514.png`)
- `.github/workflows/tests.yml`, `.github/workflows/docker.yml` — trimmed to Dashboard-only jobs

### Left behind

`.idea/` (IDE config, should not have been tracked), the current root `docker-compose.yml` (replaced by the two new ones), root `README.md` (replaced by the archive notice in the old repo, per §6).

## 4. History Extraction

For each target repo:

1. Clone the monorepo into a temp working copy.
2. Run `git filter-repo` with one `--path` per file/directory that belongs to that repo (per §3), using `--path-rename apps/<app>/:` to flatten the app subdirectory to repo root, plus explicit `--path`/`--path-rename` entries for the root-level files (`AGENTS.md`, `PLAN.md`, `TEST_PLAN.md`, `DASHBOARD_MONOREPO_PLAN.md`, etc.) since those live outside `apps/*` and need individual handling.
3. This preserves `git log`/`git blame` for everything that ever lived under the kept paths, including commits from before the monorepo reorganization (when the ETL app lived at the repo root).
4. Push the filtered history to a new, empty GitHub repo (`pulso-etl` or `pulso-dashboard`).

## 5. CI/CD

Each new repo gets its own `tests.yml` and `docker.yml`, simplified from the current monorepo versions:

- No `working-directory: ./apps/...` — commands run at repo root.
- No `paths:` filters on the `docker.yml` trigger — the whole repo is one app, every push is relevant.
- Job structure (lint → unit → integration → build → artifact) stays the same per app, just de-scoped from the sibling app's steps.

## 6. Repo Creation & Old Repo Archival

1. Create `pulso-etl` and `pulso-dashboard` on GitHub (public), push extracted history to each.
2. Verify CI is green on both new repos before touching the old one.
3. Archive `yagoazedias/pulso`: replace `README.md` with a short notice ("this project was split into pulso-etl and pulso-dashboard") linking both new repos, commit, then `gh repo archive`.

## 7. Execution Order

1. Complete the ETL Clojure→Python migration in the current monorepo (existing plan, independent of this spec) — `apps/etl-clojure` deleted, `apps/etl-python` is the only ETL app.
2. Execute this split: extract history, create repos, verify CI, archive the old repo.

This spec is written now and executed once step 1 is done.

## 8. Out of Scope

- No behavior changes to either app — this is a repo/tooling reorganization only.
- No changes to the ETL Clojure→Python migration plan itself.
- No new shared/infra repo.
- No change to Postgres schema, API contracts, or Metabase configuration beyond relocating the compose file.
