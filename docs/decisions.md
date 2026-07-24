# Architecture Decision Log

> Three sentences per decision: what I chose, what I rejected, why.
> This file becomes the best section of the README. Keep it current from day one.

---

## ADR-001 — Full six-service decomposition (not the merged portfolio scope)
**Chosen:** Six services — Auth, User, Catalog, Trip, Itinerary AI, Recommendation — plus an API Gateway, each owning a private database.
**Rejected:** The four-service merged scope (User→Auth, Recommendation→Catalog).
**Why:** Building the full decomposition to demonstrate independent data ownership and the complete microservice syllabus; the trade-off is a much longer timeline, mitigated by finishing one service to 100% before starting the next so any stopping point is a complete, demoable subset.

## ADR-002 — Redpanda as the local Kafka broker
**Chosen:** Redpanda (Kafka-API compatible) in docker-compose.
**Rejected:** Full Apache Kafka + ZooKeeper/KRaft locally.
**Why:** Kafka-API compatible so application code is unchanged, but a far smaller memory footprint for single-machine development; production can swap to managed Kafka with no code change.

## ADR-003 — Database-per-service on one Postgres instance
**Chosen:** One Postgres container, six databases, per-service credentials, no cross-database reads.
**Rejected:** Separate Postgres instances per service now; or a single shared schema.
**Why:** The access pattern is already correct (no service reads another's tables), so splitting onto separate instances later is a pure infrastructure change with zero code impact — MVP economy without compromising the boundary.

## ADR-004 — RS256 JWT with JWKS (not HS256)
**Chosen:** Asymmetric RS256; Auth holds the private key, all others verify via a cached JWKS endpoint.
**Rejected:** HS256 with a shared secret distributed to every service.
**Why:** No signing secret is copied across six services, so a compromise of any verifying service cannot forge tokens; this is the single strongest edge-security signal in the project.

## ADR-005 — Free LLM provider behind the LlmClient port (not OpenAI)
**Chosen:** Provider-agnostic `LlmClient` port with a free provider — Groq (OpenAI-compatible, free, fast) as the live default — plus `DEMO_MODE=true` fixtures as the deployed default; Gemini and Ollama as alternative adapters.
**Rejected:** OpenAI as the sole provider (paid); hardcoding any single vendor's SDK into the service.
**Why:** No budget for a paid API, and the design already isolates the provider behind one adapter class — so a free provider is a config/one-class swap with zero architectural impact; `DEMO_MODE` fixtures mean the public demo spends nothing and never hits a rate limit, while the validator + repair loop backstops any provider's JSON. Provider-agnosticism is a stronger interview signal than a single-vendor dependency.

## ADR-006 — Java 25 + Spring Boot 3.5.16, and no Lombok
**Chosen:** Target Java 25 (LTS); Spring Boot 3.5.16 (latest 3.x, Spring Framework 6.2) with Spring Cloud 2025.0.3; drop Lombok, use plain hand-written accessors on entities. Mockito runs with `-Dnet.bytebuddy.experimental=true`.
**Rejected:** Java 21 (works with Boot 3.4.1 but not the requested runtime); Spring Boot 4.x (Spring Framework 7 — a much larger migration); keeping Lombok (its annotation processor failed silently under JDK 25).
**Why:** Java 25 requires tooling that understands class-file major version 69 — Boot 3.4.1's repackage plugin does not, Boot 3.5.16 does; staying on the 3.5 line keeps all existing Spring 6 code unchanged. Dropping Lombok removes a recurring bleeding-edge-JDK failure mode for the cost of a few explicit getters. Whole reactor builds green on JDK 25.

<!-- Add new decisions above this line as you build. -->
