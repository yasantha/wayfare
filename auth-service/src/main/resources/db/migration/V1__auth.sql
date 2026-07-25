-- Auth Service schema (design §4.2). Flyway owns it; ddl-auto=validate.
-- Email is stored lowercase-normalized by the application (AuthService), so a
-- plain unique varchar gives case-insensitive behaviour without citext (which
-- Hibernate schema-validation reports as an incompatible column type).
create table users (
    id             uuid primary key,
    email          varchar(255) not null unique,
    password_hash  varchar(100) not null,
    role           varchar(32) not null default 'ROLE_USER',
    status         varchar(32) not null default 'ACTIVE',
    email_verified boolean     not null default false,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- Opaque refresh tokens, stored only as a SHA-256 hash, rotated on every use.
-- family_id groups a rotation chain so reuse of a rotated token can revoke the
-- whole family (theft detection).
create table refresh_tokens (
    id          uuid primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    token_hash  varchar(64) not null unique,
    family_id   uuid        not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    device_info varchar(256),
    created_at  timestamptz not null default now()
);
create index idx_refresh_tokens_user on refresh_tokens (user_id);
create index idx_refresh_tokens_family on refresh_tokens (family_id);

create table password_resets (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);

-- Transactional outbox (design §3.3). Written in the same tx as the state
-- change; a poller (Phase 3) publishes to Kafka and stamps published_at.
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
create index idx_outbox_unpublished on outbox (created_at) where published_at is null;
