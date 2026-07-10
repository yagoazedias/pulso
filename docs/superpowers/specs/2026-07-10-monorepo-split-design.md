# Monorepo Split — Design Spec

## 1. Objective

Split the `pulso` monorepo into two standalone GitHub repositories, `pulso-etl` and `pulso-dashboard`, owned by a new GitHub organization (`pulso-health`) instead of the personal `yagoazedias` account, so each app's stack, tooling, CI, and release cadence are fully independent — no shared CI runs, no shared `docker-compose.yml`, no cross-app path filters — and the project has a dedicated home separate from personal repos.

This split executes **after** the ETL Clojure→Python migration (`docs/superpowers/plans/2026-07-09-etl-python-migration.md`) is complete and `apps/etl-clojure` is deleted, so `pulso-etl` is born as a pure Python repo with no Clojure history mixed into new code.

**Status:** the migration precondition is already satisfied — `apps/etl-clojure` was removed and merged to `master` via PR #6 (2026-07-10).

## 2. GitHub Organization

Create a new GitHub organization, **`pulso-health`** (public, free tier), owned by `yagoazedias`. All three repos below live under it:

| Repo | Content | Visibility |
|---|---|---|
| `pulso-health/pulso-etl` | ETL pipeline (Python), Postgres, migrations, Metabase | Public |
| `pulso-health/pulso-dashboard` | Django app + Vite/React frontend | Public |
| `pulso-health/pulso` | The old monorepo, transferred and archived (history/reference only) | Public, archived |

Org creation itself has no `gh`/API path for a personal-account owner — it's a manual step via `https://github.com/organizations/new`. Everything downstream (repo creation, push, transfer, archive) is scriptable with `gh`.

No third "infra" repo. `pulso-etl` and `pulso-dashboard` are each self-sufficient to run standalone.

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
4. Push the filtered history to a new, empty GitHub repo (`pulso-health/pulso-etl` or `pulso-health/pulso-dashboard`).

## 5. CI/CD

Each new repo gets its own `tests.yml` and `docker.yml`, simplified from the current monorepo versions:

- No `working-directory: ./apps/...` — commands run at repo root.
- No `paths:` filters on the `docker.yml` trigger — the whole repo is one app, every push is relevant.
- Job structure (lint → unit → integration → build → artifact) stays the same per app, just de-scoped from the sibling app's steps.

## 6. Org Setup, Repo Creation & Old Repo Archival

1. Create the `pulso-health` organization manually via `https://github.com/organizations/new` (public, free tier), owned by `yagoazedias`.
2. Create `pulso-health/pulso-etl` and `pulso-health/pulso-dashboard` (`gh repo create pulso-health/<name> --public`), push each repo's extracted history (§4).
3. Verify CI is green on both new repos before touching the old one.
4. Transfer the old repo into the org: `gh repo transfer yagoazedias/pulso pulso-health` (requires accepting the transfer as an org owner).
5. Archive `pulso-health/pulso`: replace `README.md` with a short notice ("this project was split into pulso-etl and pulso-dashboard") linking both new repos, commit, then `gh repo archive pulso-health/pulso`.

## 7. Execution Order

1. ~~Complete the ETL Clojure→Python migration~~ — **done** (PR #6 merged 2026-07-10, `apps/etl-clojure` removed).
2. Execute this split: create the org, extract history, create repos, verify CI, transfer and archive the old repo.

This spec is written now; step 2 is ready to execute whenever desired.

## 8. Out of Scope

- No behavior changes to either app — this is a repo/tooling reorganization only.
- No changes to the ETL Clojure→Python migration plan itself.
- No new shared/infra repo.
- No change to Postgres schema, API contracts, or Metabase configuration beyond relocating the compose file.
