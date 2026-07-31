# Wayfare — Build Plan (Full 6-Service Design)

**Scope:** Full decomposition — 6 services + API Gateway (design doc §2).
**Pace:** ~10 hrs/week. Total effort ≈ **520 hrs ≈ 52 weeks** elapsed.
**Survival rule:** finish one service to **100%** (code + tests + docs) before starting the next.
A stalled year-long project dies at 60%; a *sequence of finished services* is demoable at every step.

---

## The golden path

Each phase ends in something **runnable and demoable**. Never spend weeks with nothing working.

| Phase | Deliverable | Demoable checkpoint | Hrs | ~Weeks |
|---|---|---|---|---|
| **0 — Platform Foundation** ✅ | Monorepo, docker-compose stack, `platform-commons`, gateway skeleton, CI, RS256 keypair, service scaffolds | `docker compose up` boots everything; a spike endpoint writes to Postgres + publishes to Kafka + shows a trace in Jaeger | 60 | 6 |
| **1 — Auth + Gateway** ✅ | Register, login, RS256 JWT + JWKS, refresh-token rotation, gateway routing + JWT validation + Redis rate limit, security test suite | curl: register → login → call a protected route through the gateway — **verified live** | 60 | 6 |
| **2 — User + Catalog** ✅ | Profile + preferences (User), destinations/activities + search + Redis cache + seed data (Catalog), `user.registered` consumer, internal shortlist endpoint | Search returns real seeded data; preferences persist; registration creates a profile via event — **11/11 ITs pass on real Postgres/Redis** | 60 | 6 |
| **3 — Messaging Backbone** ✅ | Kafka topics (auto-created), versioned event envelope, transactional outbox + poller, idempotent consumers (`processed_events`), retry + DLT topics via `@RetryableTopic`, trace propagation over Kafka headers | Publish → consume → redeliver same eventId → no-op; **13/13 ITs pass on real Kafka + Postgres** | 40 | 4 |
| **4 — Trip Service** ✅ | Trip CRUD, itinerary versioning, days/items CRUD, reordering, snapshots, ownership enforcement, saga participation | Create and hand-edit a full trip end to end; user A cannot read user B's trip — **8/8 tests pass on real Postgres + Kafka** | 80 | 8 |
| **5 — Itinerary AI Service** ✅ | Context builder, prompt templates, `LlmClient` port + free-provider adapters (Groq/Gemini/Ollama), structured output, validator + repair loop, async job lifecycle, quota, token/cost accounting, resilience (timeout/retry/circuit breaker), `DEMO_MODE` (real algorithmic builder, not fixtures) | **The money demo** — one request produces a real validated itinerary published to Kafka in the exact shape Trip Service already consumes — **10/10 tests pass on real Postgres + Kafka** | 100 | 10 |
| **6 — Recommendation Service** ✅ | Projection consumers (`user.preferences.updated`, `trip.created`), rule-based scoring engine, endpoints, configurable weights | Suggested destinations and activities, computed with no cross-service call on the request path — **9/9 tests pass on real Postgres + Kafka, no User Service running** | 40 | 4 |
| **7 — Hardening & Handover** 🟡 | Contract-test reasoning (ADR-018), e2e smoke suite, Grafana dashboards + alerts, README rewrite, architecture diagram — **all done, verified live**. Deploy to a VPS, demo recording, literal tracing screenshot — **not done, need the user** (no cloud credentials or screen-recording capability available to the agent) | A live URL (or recording) a stranger can use in under 10 minutes | 80 | 8 |

**Parallelisation note:** if a second developer joins after Phase 3, Trip+Recommendation and Itinerary AI run in parallel, compressing the tail. Solo, keep it strictly sequential.

---

## Milestones (definition of done)

| # | When | Done means |
|---|---|---|
| M1 ✅ | end Phase 0 | Whole stack boots with one command; CI green; spike proves Postgres+Kafka+tracing wired |
| M2 ✅ | end Phase 1 | Client registers, logs in, calls an authenticated route via the gateway |
| M3 ✅ | end Phase 2 | Destinations searchable; preferences persisted; `user.registered` flowing |
| M4 ✅ | end Phase 3 | Outbox → Kafka → idempotent consumer verified end-to-end with tracing |
| M5 ✅ | end Phase 4 | Trips created and hand-edited end to end; ownership suite passes |
| M6 ✅ | end Phase 5 | Real itinerary generated, validated, delivered by event, persisted, editable |
| — ✅ | end Phase 6 | **All 6 services feature-complete.** Only hardening/polish/deployment remain. |
| M7 | end Phase 7 | All scope done, deployed to staging, documented, demo recorded |

---

## Ports & databases (canonical map)

| Service | Port | Database |
|---|---|---|
| api-gateway | 8080 | — |
| auth-service | 8081 | auth_db |
| user-service | 8082 | user_db |
| catalog-service | 8083 | catalog_db |
| trip-service | 8084 | trip_db |
| itinerary-ai-service | 8085 | itinerary_ai_db |
| recommendation-service | 8086 | reco_db |

Infra: Postgres 5432 · Redpanda 19092 (Kafka API) · Redis 6379 · Jaeger UI 16686 / OTLP 4318 ·
Prometheus 9090 · Grafana 3000 · Redpanda Console 8090.

---

## The three cross-cutting rules (apply from day one, not retrofitted)

1. **Snapshots + projections, never cross-service DB reads.** Trip stores `destination_snapshot`/`activity_snapshot`; Recommendation maintains its own projection tables. This keeps sync calls to the three in design §3.1.
2. **Outbox for every state-change-plus-event.** One local transaction writes the row and the outbox entry; a poller publishes. This is your strongest single technical signal — never publish directly from app code after commit.
3. **Correlation ID + trace context on every hop, including Kafka headers.** Wire the logging pattern and OTel in Phase 0. One screenshot of a trace spanning gateway → AI → Kafka → Trip is worth three README paragraphs.

---

## Risk watch (the four that actually cause slippage)

| Risk | Mitigation baked into this plan |
|---|---|
| Infra complexity eats the schedule | Phases 0 & 3 explicitly budgeted, not absorbed into features |
| Distributed debugging slows everything | Tracing built in Phase 0, before real code depends on it |
| LLM cost overrun on a public demo | `DEMO_MODE=true` default + per-user quota + hard spend cap set **before** first live call |
| `platform-commons` becomes a distributed monolith | Restricted to error model, correlation filter, JWT utils. **No domain entities.** Enforced in review |

---

## Phase 7 checklist (current)

Everything the agent can do without cloud credentials or a screen recorder is
done and verified live (all 7 services healthy, smoke test 13/13, Prometheus
scraping all services, Grafana dashboard rendering real data, a real Jaeger
trace confirmed spanning `api-gateway`→`trip-service`). What's left needs you:

- [x] End-to-end smoke suite (`scripts/smoke-test.sh`) — run against the real stack, 13/13
- [x] Micrometer metrics for generation outcomes (design §9.1) — success/fail rate, latency percentiles, LLM spend
- [x] Prometheus scrape config labeled per service; `infra/prometheus/alerts.yml` (7 rules)
- [x] Grafana dashboard auto-provisioned (`infra/grafana/provisioning/`) — verified rendering real data
- [x] Contract-testing decision reasoned and documented (ADR-018) rather than bolted on for its own sake
- [x] README rewritten with real numbers throughout, no placeholders
- [x] `docs/architecture.md` — diagrams matching the system as built, including the 2 points it diverged from the source design doc
- [ ] **You:** deploy to a VPS (Hetzner/DigitalOcean/Oracle free tier/Fly.io — portfolio-build-plan §5) — needs credentials the agent doesn't have
- [ ] **You:** record a short demo (screen recording) — needs your screen, not the agent's
- [ ] **You:** a literal screenshot of a Jaeger trace for the README — the agent confirmed the trace exists and spans correctly via Jaeger's API, but can't capture a browser screenshot
- [ ] **You:** decide `DEMO_MODE` policy for the public deployment (recommended: stay `true` by default per portfolio-build-plan §4 — bring-your-own-key for a live LLM call)

---

## Immediate next steps (Phase 0 checklist)

- [x] `.gitignore` in the first commit (secrets never enter history)
- [x] `.env.example` committed; `.env` gitignored
- [x] docker-compose infra stack (Postgres · Redpanda · Redis · Jaeger · Prometheus · Grafana)
- [x] Postgres init script creating all six databases
- [x] RS256 keypair generated into `secrets/`
- [x] Build `platform-commons` (ProblemDetail, correlation filter, user context, pagination)
- [x] Scaffold the 7 Spring Boot services (monorepo Maven reactor)
- [x] CI matrix (build → test per service via reactor)
- [ ] **You:** install Maven + Docker Desktop on your machine (neither detected)
- [ ] **You:** set the free LLM provider key + spend/rate awareness (Groq); keep `DEMO_MODE=true`
- [ ] **You:** verify `docker compose up` is green
- [ ] **You:** `mvn -B verify` at the repo root builds all modules green
- [ ] Phase 0 spike: one throwaway endpoint that writes to Postgres + publishes to Kafka + appears in Jaeger, then delete it

### Service scaffolding (Maven · Java 25 · Spring Boot 3.5.16)

| Service | Dependencies |
|---|---|
| api-gateway | Gateway, Spring Security, Data Redis Reactive, Actuator |
| auth-service | Web, Security, JPA, PostgreSQL, Flyway, Validation, Kafka, Actuator, Lombok |
| user-service | Web, Security, JPA, PostgreSQL, Flyway, Validation, Kafka, Actuator, Lombok |
| catalog-service | Web, JPA, PostgreSQL, Flyway, Validation, Data Redis, Actuator, Lombok |
| trip-service | Web, Security, JPA, PostgreSQL, Flyway, Validation, Kafka, Actuator, Lombok |
| itinerary-ai-service | Web, Security, JPA, PostgreSQL, Flyway, Validation, Kafka, WebFlux, Actuator, Lombok |
| recommendation-service | Web, JPA, PostgreSQL, Flyway, Validation, Kafka, Actuator, Lombok |

Add to every service: `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`,
`micrometer-registry-prometheus`, and (test scope) `spring-boot-testcontainers` + `testcontainers:postgresql`.

Every service uses the same hexagonal layout: `api/ · application/ · domain/ · infrastructure/{persistence,messaging,client}`.
