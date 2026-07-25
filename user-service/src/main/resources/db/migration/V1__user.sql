-- User Service schema (design §4.2). Preference string-lists are stored as jsonb
-- (no relational array queries are needed on them, and jsonb is validate-clean).
create table user_profiles (
    user_id       uuid primary key,
    display_name  varchar(120),
    home_country  varchar(2),
    home_city     varchar(120),
    date_of_birth date,
    currency_code varchar(3),
    locale        varchar(16),
    avatar_url    varchar(512),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create table user_preferences (
    id                      uuid primary key,
    user_id                 uuid not null unique references user_profiles (user_id) on delete cascade,
    travel_style            varchar(32),
    pace                    varchar(32),
    interests               jsonb not null default '[]',
    dietary_restrictions    jsonb not null default '[]',
    accessibility_needs     jsonb not null default '[]',
    avoid_tags              jsonb not null default '[]',
    preferred_accommodation varchar(64),
    version                 int  not null default 0,
    updated_at              timestamptz not null default now()
);

-- Idempotent consumption (design §3.2): each event id processed at most once.
create table processed_events (
    event_id     varchar(64) primary key,
    consumer     varchar(64) not null,
    processed_at timestamptz not null default now()
);

create table outbox (
    id             uuid primary key,
    aggregate_type varchar(64)  not null,
    aggregate_id   varchar(64)  not null,
    event_type     varchar(64)  not null,
    payload        jsonb        not null,
    correlation_id varchar(64),
    created_at     timestamptz  not null default now(),
    published_at   timestamptz,
    attempts       int          not null default 0
);
create index idx_user_outbox_unpublished on outbox (created_at) where published_at is null;
