-- THROWAWAY Phase 0 spike migration. Proves Flyway creates a table on boot.
-- Delete this file (and the com.wayfare.auth.spike package) before Phase 1,
-- then reset the volume:  docker compose down -v && docker compose up -d postgres
create table spike_ping (
    id         uuid primary key,
    note       varchar(200) not null,
    created_at timestamptz  not null default now()
);
