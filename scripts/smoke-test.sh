#!/usr/bin/env bash
# End-to-end smoke suite (design §9.3): register -> create trip -> generate ->
# edit, run against the REAL running stack through the gateway — no mocks,
# no shortcuts. Exits non-zero on the first failed step.
#
# Prereqs: infra up (`docker compose up -d postgres redis redpanda jaeger`)
# and all 7 service jars built and running (see README "Running locally").
#
# Usage: ./scripts/smoke-test.sh [gateway-base-url]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
GW="$BASE/api/v1"
PASS=0
FAIL=0

step() { printf '\n\033[1;34m▶ %s\033[0m\n' "$1"; }
ok()   { PASS=$((PASS+1)); printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31m✗ %s\033[0m\n' "$1"; }

json_field() { python3 -c "import sys,json; print(json.load(sys.stdin).get('$1',''))"; }

require_status() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then ok "$desc ($actual)"; else bad "$desc (expected $expected, got $actual)"; fi
}

step "0. Gateway reachable"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health")
require_status "GET /actuator/health" 200 "$CODE"

step "1. Register a new user"
EMAIL="smoke-$(date +%s)-$$@wayfare.dev"
REG=$(curl -s -w '\n%{http_code}' -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password123\"}")
REG_BODY=$(echo "$REG" | head -n1); REG_CODE=$(echo "$REG" | tail -n1)
require_status "POST /auth/register" 201 "$REG_CODE"
ACCESS=$(echo "$REG_BODY" | json_field accessToken)
USER_ID=$(echo "$REG_BODY" | json_field userId)
[ -n "$ACCESS" ] && ok "received access token" || bad "no access token in response"

step "2. Login with the same credentials"
LOGIN=$(curl -s -w '\n%{http_code}' -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"password123\"}")
LOGIN_CODE=$(echo "$LOGIN" | tail -n1)
require_status "POST /auth/login" 200 "$LOGIN_CODE"

AUTH_HEADER="Authorization: Bearer $ACCESS"

step "3. Search the seeded catalog"
DEST=$(curl -s "$GW/destinations?q=Kyoto" -H "$AUTH_HEADER")
DEST_ID=$(echo "$DEST" | python3 -c "import sys,json; c=json.load(sys.stdin)['content']; print(c[0]['id'] if c else '')")
if [ -n "$DEST_ID" ]; then ok "found Kyoto (id=$DEST_ID)"; else bad "Kyoto not found in catalog — is seed data loaded?"; fi

step "4. Create a trip"
START=$(date -v+30d +%Y-%m-%d 2>/dev/null || date -d '+30 days' +%Y-%m-%d)
END=$(date -v+33d +%Y-%m-%d 2>/dev/null || date -d '+33 days' +%Y-%m-%d)
TRIP=$(curl -s -w '\n%{http_code}' -X POST "$GW/trips" -H "$AUTH_HEADER" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke Test Trip\",\"destinationId\":\"$DEST_ID\",\"startDate\":\"$START\",\"endDate\":\"$END\",\"travelerCount\":2,\"budgetAmount\":800.00,\"budgetCurrency\":\"USD\"}")
TRIP_BODY=$(echo "$TRIP" | head -n1); TRIP_CODE=$(echo "$TRIP" | tail -n1)
require_status "POST /trips" 201 "$TRIP_CODE"
TRIP_ID=$(echo "$TRIP_BODY" | json_field id)
[ -n "$TRIP_ID" ] && ok "trip created (id=$TRIP_ID)" || bad "no trip id in response"

step "5. Generate an itinerary (DEMO_MODE)"
GEN=$(curl -s -w '\n%{http_code}' -X POST "$GW/trips/$TRIP_ID/itinerary:generate" -H "$AUTH_HEADER" -H 'Content-Type: application/json' \
  -d "{\"destinationId\":\"$DEST_ID\",\"startDate\":\"$START\",\"endDate\":\"$END\",\"travelerCount\":2,\"budgetAmount\":800.00,\"budgetCurrency\":\"USD\"}")
GEN_BODY=$(echo "$GEN" | head -n1); GEN_CODE=$(echo "$GEN" | tail -n1)
require_status "POST /trips/{id}/itinerary:generate" 202 "$GEN_CODE"
REQUEST_ID=$(echo "$GEN_BODY" | json_field id)

step "6. Poll generation status until SUCCEEDED"
GEN_STATUS=""
for i in $(seq 1 20); do
  GEN_STATUS=$(curl -s "$GW/generation-requests/$REQUEST_ID" -H "$AUTH_HEADER" | json_field status)
  [ "$GEN_STATUS" = "SUCCEEDED" ] && break
  [ "$GEN_STATUS" = "FAILED" ] && break
  sleep 1
done
if [ "$GEN_STATUS" = "SUCCEEDED" ]; then ok "generation SUCCEEDED"; else bad "generation ended as '$GEN_STATUS', expected SUCCEEDED"; fi

step "7. Confirm the AI itinerary landed in Trip Service (saga consumer)"
ITINS=""
for i in $(seq 1 15); do
  ITINS=$(curl -s "$GW/trips/$TRIP_ID/itineraries" -H "$AUTH_HEADER")
  COUNT=$(echo "$ITINS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo 0)
  [ "$COUNT" -ge 1 ] 2>/dev/null && break
  sleep 1
done
ACTIVE_ID=$(echo "$ITINS" | python3 -c "import sys,json; a=[i for i in json.load(sys.stdin) if i['active']]; print(a[0]['id'] if a else '')" 2>/dev/null || echo "")
if [ -n "$ACTIVE_ID" ]; then ok "active AI itinerary present (id=$ACTIVE_ID)"; else bad "no active itinerary found on the trip after generation"; fi

step "8. Edit the itinerary — update a day's theme"
if [ -n "$ACTIVE_ID" ]; then
  DETAIL=$(curl -s "$GW/itineraries/$ACTIVE_ID" -H "$AUTH_HEADER")
  DAY_ID=$(echo "$DETAIL" | python3 -c "import sys,json; print(json.load(sys.stdin)['days'][0]['id'])" 2>/dev/null || echo "")
  if [ -n "$DAY_ID" ]; then
    EDIT_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$GW/itineraries/$ACTIVE_ID/days/$DAY_ID" \
      -H "$AUTH_HEADER" -H 'Content-Type: application/json' -d '{"theme":"Edited by smoke test"}')
    require_status "PATCH day theme" 200 "$EDIT_CODE"
  else
    bad "could not find a day to edit"
  fi
else
  bad "skipped (no active itinerary)"
fi

step "9. Ownership check — a second user cannot read this trip"
EMAIL2="smoke2-$(date +%s)-$$@wayfare.dev"
REG2=$(curl -s -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL2\",\"password\":\"password123\"}")
ACCESS2=$(echo "$REG2" | json_field accessToken)
FORBIDDEN_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$GW/trips/$TRIP_ID" -H "Authorization: Bearer $ACCESS2")
require_status "GET another user's trip" 403 "$FORBIDDEN_CODE"

step "10. Recommendations endpoint responds"
REC_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$GW/recommendations/destinations" -H "$AUTH_HEADER")
require_status "GET /recommendations/destinations" 200 "$REC_CODE"

echo ""
echo "════════════════════════════════════════"
echo " Smoke test: $PASS passed, $FAIL failed"
echo "════════════════════════════════════════"
[ "$FAIL" -eq 0 ]
