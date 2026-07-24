# platform-commons

Shared library — **cross-cutting, stable code only**:

- `ProblemDetail` error model (RFC 9457) + shared `@RestControllerAdvice`
- Correlation-ID filter (HTTP + Kafka header propagation)
- JWT claim extraction utilities
- Common envelope / pagination DTOs

> **Contains NO domain entities.** Shared domain models are the fastest way to turn
> microservices back into a distributed monolith. This is enforced in review.

Built in Phase 0.
