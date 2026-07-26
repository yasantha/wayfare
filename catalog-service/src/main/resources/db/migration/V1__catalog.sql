-- Catalog Service schema (design §4.2). Read-heavy, near-static reference data.
create table destinations (
    id                  uuid primary key,
    name                varchar(120) not null,
    country_code        varchar(2)   not null,
    region              varchar(120),
    latitude            double precision not null,
    longitude           double precision not null,
    timezone            varchar(64)  not null,
    description         text,
    best_months         jsonb        not null default '[]',
    avg_daily_cost_usd  numeric(10,2) not null,
    popularity_score    numeric(5,2) not null default 0,
    tags                jsonb        not null default '[]',
    created_at          timestamptz  not null default now()
);
create index idx_destinations_country on destinations (country_code);

create table activities (
    id                          uuid primary key,
    destination_id              uuid not null references destinations (id) on delete cascade,
    name                        varchar(160) not null,
    category                    varchar(64)  not null,
    description                 text,
    latitude                    double precision,
    longitude                   double precision,
    estimated_cost_usd          numeric(10,2) not null default 0,
    estimated_duration_minutes  int not null default 60,
    tags                        jsonb not null default '[]',
    rating                      numeric(3,2) not null default 0,
    indoor                      boolean not null default false,
    booking_url                 varchar(512),
    active                      boolean not null default true
);
create index idx_activities_destination on activities (destination_id);
create index idx_activities_category on activities (category);
