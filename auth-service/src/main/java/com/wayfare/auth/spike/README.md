# Phase 0 Spike — THROWAWAY

Proves the whole stack talks end-to-end before real code depends on it
(GETTING-STARTED Day 5). **Delete this entire package + `V1__spike_ping.sql`
once verified.**

## Run

Prereqs: `docker compose up -d` is green, Maven installed.

```bash
# 1. start the infra stack
docker compose up -d postgres redpanda redis jaeger prometheus redpanda-console

# 2. run auth-service (from repo root)
mvn -pl auth-service -am spring-boot:run

# 3. hit the spike endpoint
curl -X POST "http://localhost:8081/spike?note=hello"
# -> {"id":"...","note":"hello","topic":"spike.ping","message":"..."}
```

## Verify all three moving parts

| Part | Check |
|---|---|
| **Postgres** | `docker exec -it wayfare-postgres psql -U wayfare -d auth_db -c "select * from spike_ping;"` |
| **Kafka** | Redpanda Console → http://localhost:8090 → topic `spike.ping` has your message |
| **Tracing** | Jaeger → http://localhost:16686 → service `auth-service` → trace spans HTTP POST **and** the Kafka send |

If the trace shows the HTTP request flowing into a Kafka producer span, the
observability backbone is wired correctly — that's the whole point of the spike.

## Delete (before Phase 1)

```bash
rm -r auth-service/src/main/java/com/wayfare/auth/spike
rm auth-service/src/main/resources/db/migration/V1__spike_ping.sql
docker compose down -v && docker compose up -d postgres   # reset the Flyway history
```

Then Phase 1 owns `V1__` for the real auth schema.
