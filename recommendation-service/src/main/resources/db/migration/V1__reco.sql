-- Recommendation Service schema (design §4.2). A read-model projection built
-- entirely from consumed events (design §4.3) — no sync call to User Service
-- is ever on the request path, so recommendations stay fast and survive User
-- being down.
create table user_interest_profiles (
    user_id                  uuid primary key,
    interests                jsonb not null default '[]',
    avoid_tags               jsonb not null default '[]',
    avg_budget_tier          numeric(10,2),
    visited_destination_ids  jsonb not null default '[]',
    updated_at               timestamptz not null default now()
);

-- Tunable without redeployment (design §8).
create table scoring_weights (
    key        varchar(64) primary key,
    value      numeric(6,4) not null,
    updated_at timestamptz not null default now()
);

create table processed_events (
    event_id     varchar(64) primary key,
    consumer     varchar(64) not null,
    processed_at timestamptz not null default now()
);
