-- Trip Service schema (design §4.2). Itinerary versioning: a new itinerary is
-- always a NEW row, never an update to an existing one — that's what makes a
-- failed/partial generation harmless (design §4.4) and saga compensation trivial.
create table trips (
    id                     uuid primary key,
    user_id                uuid not null,
    title                  varchar(160) not null,
    destination_id         uuid,
    destination_snapshot   jsonb,
    start_date             date not null,
    end_date               date not null,
    traveler_count         int not null default 1,
    budget_amount          numeric(10,2),
    budget_currency        varchar(3),
    preferences_snapshot   jsonb,
    status                 varchar(32) not null default 'DRAFT',
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now()
);
create index idx_trips_user on trips (user_id);

create table itineraries (
    id                     uuid primary key,
    trip_id                uuid not null references trips (id) on delete cascade,
    version                int not null,
    source                 varchar(16) not null default 'MANUAL',
    summary                text,
    total_estimated_cost   numeric(10,2),
    currency               varchar(3),
    is_active              boolean not null default false,
    generation_request_id  uuid,
    created_at             timestamptz not null default now()
);
create index idx_itineraries_trip on itineraries (trip_id);
create unique index uq_itineraries_trip_version on itineraries (trip_id, version);

create table itinerary_days (
    id              uuid primary key,
    itinerary_id    uuid not null references itineraries (id) on delete cascade,
    day_number      int not null,
    date            date not null,
    theme           varchar(160),
    notes           text,
    estimated_cost  numeric(10,2)
);
create index idx_itinerary_days_itinerary on itinerary_days (itinerary_id);
create unique index uq_itinerary_days_number on itinerary_days (itinerary_id, day_number);

create table itinerary_items (
    id                  uuid primary key,
    itinerary_day_id    uuid not null references itinerary_days (id) on delete cascade,
    sort_order          int not null,
    catalog_activity_id uuid,
    activity_snapshot   jsonb,
    title               varchar(200) not null,
    description         text,
    item_type           varchar(32) not null,
    start_time          time,
    end_time            time,
    location_name       varchar(200),
    latitude            double precision,
    longitude           double precision,
    estimated_cost      numeric(10,2),
    booking_url         varchar(512),
    user_modified       boolean not null default false
);
create index idx_itinerary_items_day on itinerary_items (itinerary_day_id);

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
create index idx_trip_outbox_unpublished on outbox (created_at) where published_at is null;
