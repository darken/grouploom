CREATE TABLE groups (
    id         uuid         PRIMARY KEY,
    name       varchar(120) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    group_id      uuid         NOT NULL REFERENCES groups (id),
    email         varchar(254) NOT NULL,
    display_name  varchar(120) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role          varchar(20)  NOT NULL CHECK (role IN ('OWNER', 'ORGANIZER')),
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
CREATE INDEX users_group_id_idx ON users (group_id);
