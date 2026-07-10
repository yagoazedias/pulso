# Pulso on AWS — Cloud-Native Architecture Design Spec

## 1. Objective

Stop running Pulso only via local `docker-compose` and host it for real, for personal use, on AWS — mapping each existing component (ETL, dashboard, database) to a managed AWS service instead of self-managed containers, while keeping idle cost as close to zero as possible given sporadic, single-user traffic.

This spec covers **architecture and component design only**. Terraform module implementation is a follow-on plan (see §11).

## 2. Context & Constraints

- **Usage pattern:** single user (owner only), ETL runs sporadically (occasional Apple Health export imports), dashboard is checked occasionally — not a daily-active workload.
- **Priority:** minimize cost when idle over minimizing latency or engineering effort at all costs. Prefer scale-to-zero managed services over always-on infrastructure.
- **Existing assets to reuse:** `apps/dashboard-django` (Django + React/Vite, has a `Dockerfile`), `apps/etl-python` (in-progress port of `apps/etl-clojure`, per [[etl-python-migration]], has/will have a `Dockerfile`), the existing Postgres schema, and the existing GitHub Actions CI (`tests.yml`, `docker.yml`).
- **Metabase:** dropped entirely. The Django dashboard already covers the visualization need; running a second BI tool 24/7 for occasional personal use isn't worth the fixed cost.
- **IaC tool:** Terraform (chosen over CDK/CloudFormation — broad module ecosystem, not tied to a single language).
- **Known accepted risk:** the dashboard has no authentication today (confirmed — no `django.contrib.auth` / `login_required` wiring in `apps/dashboard-django`). The user has explicitly chosen to ship without auth for v1 and accept the exposure risk of an unauthenticated public URL (see §8).

## 3. Approach

Three shapes were considered:

1. **Hybrid pragmatic (chosen):** App Runner for the dashboard container as-is, Fargate one-off tasks for ETL, Aurora Serverless v2 for the database. Lowest porting effort (reuses existing Dockerfiles nearly unmodified), each piece is a genuinely managed/cloud-native AWS service, and cost scales down close to zero when idle.
2. **Full serverless (Lambda-first):** Django behind Lambda+API Gateway via Mangum, static frontend on S3+CloudFront. Marginally cheaper at true-zero idle, but requires re-architecting Django's request handling, static/media file serving, and large-file upload path for Lambda's execution model — disproportionate effort for a workload that's already low-traffic under option 1.
3. **Containers-first (closest to today):** Dashboard as an always-on ECS Fargate service behind an ALB. Most familiar operationally, but the ALB (~US$16–20/month fixed) and an always-running task are a fixed cost regardless of how rarely the dashboard is opened — the worst cost profile of the three for this usage pattern.

**Decision: Option 1 (Hybrid pragmatic).**

## 4. Component Architecture

| Today (docker-compose) | AWS service | Role |
|---|---|---|
| `dashboard` service (Django + React) | **App Runner** | Runs the existing container image with minimal changes. Auto-scales down (pause) when idle; managed HTTPS endpoint; no ALB/EC2 to operate. |
| `app` service (etl-clojure → etl-python) | **ECS Fargate**, run as a one-off `RunTask`, not a long-lived service | Batch job triggered per import; pay only for the minutes it runs. |
| ETL trigger/orchestration | **EventBridge** (S3 event notification) → **Step Functions** | S3 upload event starts a state machine that runs the Fargate task and handles success/failure. |
| `db` service (Postgres 17) | **Aurora Serverless v2 (PostgreSQL-compatible)** | Only AWS-managed Postgres option that scales down to near-zero ACU when idle, matching sporadic access. |
| `data/` local XML files | **S3** bucket (`pulso-raw-exports`) | Landing zone for uploaded Apple Health XML exports; source of the ETL trigger event. |
| — (new) | **ECR**, 2 repositories (`pulso-dashboard`, `pulso-etl`) | Image registry for both app containers, integrates with existing GitHub Actions. |
| Env vars (`DB_HOST`, `DB_PASSWORD`, ...) | **SSM Parameter Store** (SecureString) | Same role as today's env vars; free tier covers this scale (Secrets Manager would charge per secret for no added benefit here). |
| `metabase` service | **Removed** | Covered by the Django dashboard; not part of the target architecture. |

## 5. Data Flow

### 5.1 ETL trigger (upload → load)

```
Browser
   │  selects XML in the dashboard's upload UI
   ▼
App Runner (Django) ──generates a presigned S3 PUT URL──▶ Browser
   │                                                          │
   │                                                          │ browser PUTs the file (1.5GB+) directly to S3
   │                                                          ▼
   │                                                   S3 (pulso-raw-exports)
   │                                                          │ ObjectCreated event
   │                                                          ▼
   │                                                   EventBridge rule → Step Functions
   │                                                          │ RunTask
   │                                                          ▼
   │                                                   ECS Fargate task (pulso-etl image)
   │                                                          │ streams object from S3, loads via psycopg2
   ▼                                                          ▼
Aurora Serverless v2 (Postgres) ◀────────────────────────────┘
```

Key decisions:
- **The large file never transits through App Runner/Django.** Django's only job in the upload path is issuing a presigned URL; the browser uploads straight to S3. This avoids App Runner request-size/timeout limits and keeps the app container stateless and small.
- **No new state-tracking infrastructure.** The Fargate task writes its outcome (status, row counts, timestamp, error message on failure) into an `etl_run` table in the existing Postgres schema. The dashboard shows "last import: succeeded, 3.4M records, 2 days ago" via a normal query against a table it already has a connection to — no SQS/DynamoDB/extra polling mechanism needed for a single-user tool.
- **Step Functions**, not a bare EventBridge→Lambda→RunTask call, because it gives free retry/failure-state handling for the batch job (e.g., mark the `etl_run` row as `failed` with an error message if the Fargate task exits non-zero) without hand-rolling that logic.

### 5.2 Dashboard read path

App Runner reaches Aurora over a private connection via an **App Runner VPC Connector** — traffic never leaves the VPC, and Aurora has no public endpoint.

## 6. Networking

- Single VPC, two private subnets (different AZs) for Aurora and the ETL Fargate task's ENIs; App Runner attaches via its VPC Connector into the same private subnets to reach Aurora.
- **No NAT Gateway.** Nothing in this architecture needs outbound access to the public internet: Django doesn't call external APIs, and the ETL task only talks to S3 and Aurora. An **S3 Gateway VPC Endpoint** (no hourly charge) covers S3 access from inside the VPC. Skipping the NAT Gateway avoids its ~US$32/month fixed cost — the single largest avoidable fixed cost in a small VPC, and the main reason a naive "just lift the docker-compose setup into a VPC" design would end up more expensive than necessary.
- Security groups: Aurora's SG allows inbound Postgres (5432) only from the App Runner VPC Connector's SG and the ETL Fargate task's SG. No component gets a public IP except App Runner's managed public endpoint and S3 (already public by design, access controlled via bucket policy + presigned URLs).

## 7. CI/CD & Deployment

Extends the existing GitHub Actions setup rather than replacing it:

- **`tests.yml`** (existing): unchanged — still runs unit/integration tests for both apps on every push/PR.
- **`docker.yml`** (existing, extended): on merge to `master`, additionally build and push both images (`pulso-dashboard`, `pulso-etl`) to their ECR repositories, tagged with the commit SHA.
- **New deploy step:** after a successful ECR push,
  - trigger an App Runner deployment (`aws apprunner start-deployment`) so it picks up the new `pulso-dashboard` image, and
  - register a new ECS task definition revision pointing at the new `pulso-etl` image tag, which Step Functions is configured to always run at `$LATEST` revision.
- **Terraform** is applied out-of-band (manually, or via a separate `infra` workflow gated on manual approval) since infra changes are far less frequent than app deploys and warrant a human look before `apply`.

## 8. Security & Access Control (accepted risk)

The dashboard ships in v1 **without authentication**. App Runner's default endpoint is a public HTTPS URL reachable by anyone who has it; there is no `django.contrib.auth` login gate, IP allow-listing, or WAF in front of it.

This was discussed explicitly and is a **deliberate, accepted trade-off for v1** to keep initial scope small, not an oversight. It's called out here so it isn't silently forgotten:

- **Risk:** personal health data (workouts, heart rate, activity history) is reachable by anyone who discovers or guesses the App Runner URL.
- **Mitigation deferred to future work** (see §12): the lowest-effort fix, if/when this is revisited, is adding `django.contrib.auth` with a single account and gating views with `login_required` — no new AWS infrastructure required.

## 9. Cost Considerations (rough order of magnitude)

Not a committed budget, but the design decisions driving cost down:

- **Aurora Serverless v2**: scales down when idle; cost is dominated by storage (a few GB, ~cents/month) plus compute only during the brief windows the dashboard or ETL is actually querying it.
- **App Runner**: bills for provisioned+active compute; with auto-pause on idle, cost approaches its per-request/build cost rather than a 24/7 instance. (Verify auto-pause availability in the target region at implementation time — treat "always-on minimum instance" as the fallback cost assumption if unavailable.)
- **Fargate ETL task**: pay-per-run only, on the order of cents per import (a handful of vCPU-minutes).
- **No NAT Gateway**: avoids the ~US$32/month fixed cost that would otherwise dominate the bill for a low-traffic personal setup.
- **Parameter Store over Secrets Manager**: avoids per-secret monthly charges for config that doesn't need automatic rotation.
- **S3, ECR, CloudWatch Logs**: negligible at this scale.

Expect a low-double-digit US$/month bill dominated by Aurora and App Runner baseline costs, not by data transfer or request volume.

## 10. Out of Scope

- Terraform module implementation (separate follow-on plan, per §1).
- Authentication/authorization for the dashboard (see §8 — explicitly deferred).
- Custom domain (Route 53 + ACM) — App Runner's default `*.awsapprunner.com` URL is sufficient for v1.
- Multi-user/multi-tenant support, high availability (Aurora read replicas, multi-AZ App Runner beyond its default), and Metabase reinstatement — all explicitly out of scope given single-user personal use.
- Changes to the ETL/dashboard application code beyond what's needed to run in these services (e.g., presigned-URL upload endpoint, `etl_run` status table) — functional behavior of the pipeline itself is unchanged from [[etl-python-migration]].

## 11. Future Work

- Add dashboard authentication (`django.contrib.auth` + `login_required`) — lowest-effort fix for the accepted risk in §8.
- Terraform implementation plan: module breakdown (network, database, ecr, etl, dashboard), state backend (S3+DynamoDB lock table), variable/environment structure.
- Custom domain + ACM certificate once the URL needs to be memorable/shared.
- Revisit Metabase (or Amazon QuickSight) if ad-hoc exploratory querying beyond the Django dashboard's fixed views becomes a real need.
