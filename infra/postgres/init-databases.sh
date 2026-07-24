#!/bin/bash
# Creates one database per service on the shared PostgreSQL instance.
# Runs automatically on FIRST boot of an empty pgdata volume.
# The access pattern is already "database-per-service" so splitting onto
# separate instances later is pure infrastructure, zero code change.
set -euo pipefail

DATABASES=(
  auth_db
  user_db
  catalog_db
  trip_db
  itinerary_ai_db
  reco_db
)

for db in "${DATABASES[@]}"; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE $db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
done

echo "All service databases created."
