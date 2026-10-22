CREATE TABLE event_types (
    id                       uuid         PRIMARY KEY,
    group_id                 uuid         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    name                     varchar(80)  NOT NULL,
    description              varchar(500),
    color                    varchar(20)  NOT NULL
        CHECK (color IN ('blue', 'green', 'orange', 'red', 'purple', 'pink', 'teal', 'gray')),
    default_duration_minutes int
        CHECK (default_duration_minutes BETWEEN 5 AND 1440),
    archived_at              timestamptz,
    version                  bigint       NOT NULL DEFAULT 0,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now()
);

-- Also serves group_id lookups.
CREATE UNIQUE INDEX ux_event_types_group_name ON event_types (group_id, lower(name));
