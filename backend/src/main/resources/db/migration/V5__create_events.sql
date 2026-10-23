CREATE TABLE events (
    id            uuid          PRIMARY KEY,
    group_id      uuid          NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    -- Default NO ACTION (checked at the end of the statement), not RESTRICT: deleting a group
    -- cascades to both event_types and events, and RESTRICT could fail depending on the order.
    event_type_id uuid          NOT NULL REFERENCES event_types (id),
    title         varchar(120)  NOT NULL,
    description   varchar(2000),
    location      varchar(200),
    starts_at     timestamptz   NOT NULL,
    ends_at       timestamptz   NOT NULL,
    time_zone     varchar(64)   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at)
);

-- Upcoming/past listings are scoped by group and ordered by start.
CREATE INDEX ix_events_group_starts ON events (group_id, starts_at);
-- The upcoming/past split filters on the end time.
CREATE INDEX ix_events_group_ends ON events (group_id, ends_at);
-- The "type in use" check and foreign key lookups.
CREATE INDEX ix_events_event_type ON events (event_type_id);
