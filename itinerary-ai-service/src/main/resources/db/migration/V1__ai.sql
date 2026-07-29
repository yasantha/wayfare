-- Itinerary AI Service schema (design §4.2).
create table generation_requests (
    id                 uuid primary key,
    trip_id            uuid not null,
    user_id            uuid not null,
    status             varchar(32) not null default 'PENDING',
    model              varchar(64),
    prompt_version     int,
    prompt_hash        varchar(64) not null,
    prompt_tokens      int,
    completion_tokens  int,
    cost_usd           numeric(10,4),
    latency_ms         int,
    attempt_count      int not null default 0,
    error_code         varchar(64),
    error_message       text,
    created_at         timestamptz not null default now(),
    completed_at        timestamptz
);
create index idx_generation_requests_trip on generation_requests (trip_id);
create index idx_generation_requests_user_created on generation_requests (user_id, created_at);
create index idx_generation_requests_hash on generation_requests (prompt_hash);

create table generation_payloads (
    request_id        uuid primary key references generation_requests (id) on delete cascade,
    raw_response      jsonb,
    validated_payload jsonb
);

create table prompt_templates (
    id             uuid primary key,
    name           varchar(64) not null,
    version        int not null,
    system_prompt  text not null,
    user_template  text not null,
    active         boolean not null default true
);
create unique index uq_prompt_templates_name_version on prompt_templates (name, version);

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
create index idx_ai_outbox_unpublished on outbox (created_at) where published_at is null;
