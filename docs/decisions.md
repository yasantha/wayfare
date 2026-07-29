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

## ADR-007 — One repository interface per file, never nested
**Chosen:** Every Spring Data repository is its own top-level interface file (`UserProfileRepository.java`, etc.), matching the pattern already used in auth-service.
**Rejected:** Grouping related repositories as nested interfaces inside one holder class (tried first in user-service — `Repositories.UserProfileRepository`).
**Why:** Nested repository interfaces failed Spring Data JPA's component scan (`NoSuchBeanDefinitionException` at context startup) even though they're implicitly public static; caught immediately by the Testcontainers integration test rather than at runtime in a later phase — exactly why every service gets an IT before being called done.

## ADR-008 — Nullable JPQL filter parameters need explicit casts (or native SQL)
**Chosen:** For a nullable string filter reused in both an `is null` check and a function call (e.g. `lower(...)`), wrap every use in `cast(:x as string)`. For a nullable numeric filter, don't fight Hibernate's HQL cast — use a native query with a plain SQL cast instead.
**Rejected:** Trusting Hibernate to infer the parameter type from context; using `cast(:x as java.math.BigDecimal)` (a Java class name — HQL wants its own type tokens like `big_decimal`, and even the correct token still mis-bound to bytea against real Postgres in this Hibernate 6.6.53 build).
**Why:** An untyped null Postgres bind parameter used in an `IS NULL` check alongside a function call resolves ambiguously and can default to `bytea`, breaking `lower(bytea)` and `cast(bytea as numeric)` — caught only by running the query against a real database (Testcontainers), not by compiling or mocking. `cast(:x as string)` fixed the destination search; the equivalent numeric HQL cast did not, so the activity shortlist query is native SQL instead. Re-check this pattern with a real Postgres instance any time a new optional filter parameter is added in Trip/AI/Recommendation.

## ADR-009 — @ConditionalOnBean in a custom auto-configuration needs @AutoConfigureAfter
**Chosen:** `WayfareCommonsAutoConfiguration`'s `OutboxPoller` bean is annotated `@AutoConfigureAfter({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, KafkaAutoConfiguration.class})` alongside its `@ConditionalOnBean({JdbcTemplate.class, KafkaTemplate.class})`.
**Rejected:** `@ConditionalOnBean` alone, assuming Spring Boot would naturally process third-party auto-configurations after the framework's own ones.
**Why:** `@ConditionalOnBean` only sees beans registered *before* the annotated configuration class runs; without an explicit ordering hint, `WayfareCommonsAutoConfiguration` could be processed before Boot's own Datasource/Jdbc/Kafka auto-configurations register their beans, silently skipping `OutboxPoller` creation with no error — the outbox rows would just accumulate unpublished forever. Caught only by `OutboxToKafkaIT` (a real Kafka Testcontainers test asserting a message actually arrives), not by unit tests, mocks, or compilation. Reinforces [[wayfare-project]]'s running theme: cross-service infrastructure code needs a real-infra integration test before it's trusted.

## ADR-010 — Retry/DLT topology via Spring Kafka's @RetryableTopic, not hand-rolled topics
**Chosen:** `@RetryableTopic(attempts=4, backoff=@Backoff(delay=2000, multiplier=2.0))` on `UserRegisteredConsumer`, with a `@DltHandler` for the terminal failure case.
**Rejected:** Manually creating `.retry.5s`/`.retry.1m`/`.retry.10m`/`.dlq` topics and routing failures between them by hand, as design §3.2 describes at the infrastructure level.
**Why:** Spring Kafka auto-creates the retry/DLT topic chain (`user.registered-retry-0`, `-retry-1`, ..., `-dlt`) and handles the routing/backoff itself — same observable behavior (exponential backoff, eventual dead-letter) for a fraction of the code, and it's the idiomatic Spring Kafka mechanism an interviewer would expect to see used rather than reimplemented.

## ADR-011 — Generation is triggered by direct HTTP to Itinerary AI, not a Trip-produced event
**Chosen:** The client calls `POST /trips/{id}/itinerary:generate` directly on Itinerary AI Service (already wired at the gateway in Phase 1); Trip Service's role in the saga is purely consuming `itinerary.generation.succeeded`/`failed` (design §4.4 steps 3-4), plus a small internal endpoint (`PATCH /internal/trips/{id}/status`) Itinerary AI can call to flip a trip to GENERATING before starting work.
**Rejected:** Having Trip Service itself produce an `itinerary.generation.requested` event for Itinerary AI to consume, as the design doc's §3.2 event catalogue literally states ("Producer: Trip").
**Why:** The design doc is internally inconsistent here — §3.2's table says Trip produces the request event, but §5.2's endpoint table places the generate-trigger endpoint on **Itinerary AI Service**, and §7.1's sequence diagram shows the client hitting AI service directly, with AI persisting its own `generation_request` before ever touching Kafka. The sequence diagram is the more detailed, concrete source and is what the Phase 1 gateway routing already implements (`/api/v1/trips/*/itinerary:generate` → AI service directly) — so it's treated as authoritative. No functionality is lost: Trip still knows about every generation via the succeeded/failed events it consumes.

## ADR-012 — Spring Data JPA derives query properties from the getter, not the field name
**Chosen:** `ItineraryRepository.findByTripIdAndActiveTrue` — matching the entity's actual JavaBean property (`active`, exposed via `isActive()`).
**Rejected:** `findByTripIdAndIsActiveTrue`, written first by analogy to the column name `is_active`.
**Why:** A boolean field named `active` with a conventional `isActive()` getter has the JavaBean *property* name `active`, not `isActive` — Spring Data's method-name query derivation failed at application-context startup with `PropertyReferenceException: No property 'isActive' found for type 'Itinerary'`. Caught by `TripServiceIT`, not compilation (interfaces don't type-check their derived-query strings). The method turned out to be unused elsewhere in the codebase and was deleted rather than fixed-and-kept — YAGNI.

## ADR-013 — Integration test fixtures must never use hardcoded literal dates
**Chosen:** `TripServiceIT` computes all trip dates as `LocalDate.now().plusDays(N)`.
**Rejected:** Hardcoded ISO date strings (`"2026-03-01"`, etc.), written when those dates were safely in the future.
**Why:** `CreateTripRequest.startDate` has `@FutureOrPresent`. A multi-day session between Phase 3 and Phase 4 pushed the real wall-clock date past several of those hardcoded literals, so every trip-creation test started failing with 400 instead of 201 — a false regression with a one-line cause once traced (`TRIP_EXIT` went from a clean prior run to universal 400s). Any test asserting date-relative validation must derive its fixture dates from `now()`, never a literal.

## ADR-014 — Never dispatch async work from inside the transactional method that persists its precondition
**Chosen:** `GenerationService.requestGeneration()` is plain (no `@Transactional`); it calls `GenerationOutcomeService.createPending()` (a separate, `@Transactional` bean) and only dispatches `GenerationWorker.process()` (`@Async`) *after* that call returns.
**Rejected:** The original design — one `@Transactional` method that both persisted the `PENDING` row and called the async worker in the same method body.
**Why:** `@Transactional` commits when the proxied method *returns*, not when the last repository call inside it executes. Dispatching the async worker before that return point is a race: virtual threads start near-instantly, and the worker's first query (`findById`) can run — and fail with `NoSuchElementException` — before the outer transaction has committed the row it's looking for. Caught by `GenerationDemoModeIT` failing with a 27-second timeout (the async method threw immediately, invisible to the caller since `@Async` swallows exceptions into a handler, not a stack trace at the call site) — not by code review, since the bug is only visible under real transaction-boundary timing, never in a unit test with mocks. General rule for this codebase: any method that both writes state in a transaction *and* triggers async/event work needs the dispatch to happen strictly after the transactional call returns, on a different bean than the one holding `@Transactional`.

## ADR-015 — Map.of(...) throwing on a null value is a silent-failure trap behind a broad catch block
**Chosen:** Build optional-valued request bodies with a mutable `LinkedHashMap`, adding a key only when its value is non-null; log a `WARN` inside every "degrade gracefully" catch block (design §3.4's resilience pattern) rather than swallowing silently.
**Rejected:** `Map.of("k1", v1, "k2", possiblyNullValue, ...)` inside a broad `catch (Exception e) { return List.of(); }` handler.
**Why:** `Map.of` throws `NullPointerException` on any null value, and the design's own "degrade, don't fail" resilience pattern (§3.4 — proceed ungrounded if a downstream service is unreachable) means that exception gets caught and silently discarded exactly like a real network failure would be. The result: `CatalogClient.fetchShortlist` always returned an empty list whenever `maxCostUsd` was null (the normal case), producing an itinerary of nothing but `FREE_TIME` items with zero indication anything was wrong — caught only because `GenerationDemoModeIT` explicitly asserts at least one item is catalog-grounded, not because an error surfaced anywhere. A resilience catch block that hides bugs as indistinguishable from real outages is itself a bug; every such block now logs before degrading.

## ADR-016 — OutboxPoller needs an explicit per-service opt-out, not just a bean-presence check
**Chosen:** `@ConditionalOnProperty(prefix = "wayfare.outbox", name = "enabled", matchIfMissing = true)` added alongside the existing `@ConditionalOnBean({JdbcTemplate, KafkaTemplate})` check; `recommendation-service` sets `wayfare.outbox.enabled: false`.
**Rejected:** Trusting the bean-presence check alone to mean "this service owns an outbox table."
**Why:** Spring Boot auto-configures a `KafkaTemplate` bean the moment `spring-kafka` is on the classpath, regardless of whether the service ever calls `.send()` — true for recommendation-service, which only consumes (`user.preferences.updated`, `trip.created`) and is never listed as a producer anywhere in the design's event catalogue. Without the explicit opt-out, `OutboxPoller` would have activated and polled a `select ... from outbox` against `reco_db`, which has no such table — a startup-time-invisible, every-500ms runtime failure that only a real integration test surfaces (a unit test with mocks never boots the real auto-configuration graph).

## ADR-017 — Activity scoring formula intentionally diverges from the design doc, documented not silent
**Chosen:** `proximityToDayCluster`'s 0.20 weight is folded into `rating` and `costFit` (0.30 each instead of 0.20); `closedOnDate`'s penalty term is dropped entirely.
**Rejected:** Implementing both literally as design §8 specifies.
**Why:** Both need data this service doesn't own without a sync call the design's own §3.1 table never lists: `proximityToDayCluster` needs the coordinates of activities already in the current day's plan (Trip Service data), and `closedOnDate` needs opening-hours data Catalog's schema has never carried. Rather than add an undocumented sync dependency or silently ship a formula that doesn't match its own inline comment, both simplifications are named in the `RuleBasedRecommendationEngine` Javadoc and here. `alreadyInItinerary` (the other term in that formula) is implemented faithfully as a hard exclusion — the caller passes `excludeActivityIds` on the request, the same pattern ADR-011 established for Itinerary AI's trip parameters.

<!-- Add new decisions above this line as you build. -->
