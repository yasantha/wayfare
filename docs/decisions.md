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

<!-- Add new decisions above this line as you build. -->
