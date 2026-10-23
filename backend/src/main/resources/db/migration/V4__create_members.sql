CREATE TABLE members (
    id         uuid         PRIMARY KEY,
    group_id   uuid         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    name       varchar(120) NOT NULL,
    email      varchar(254) NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- Also serves group_id lookups.
CREATE UNIQUE INDEX ux_members_group_email ON members (group_id, lower(email));

-- Name sorting and prefix search within a group.
CREATE INDEX ix_members_group_name ON members (group_id, lower(name));
