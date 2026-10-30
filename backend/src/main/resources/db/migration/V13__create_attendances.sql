CREATE TABLE attendances (
    id             uuid         PRIMARY KEY,
    group_id       uuid         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    event_id       uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    member_id      uuid         NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    response       varchar(20)  NOT NULL CHECK (response IN ('GOING', 'NOT_GOING')),
    note           varchar(280),
    source         varchar(20)  NOT NULL CHECK (source IN ('MEMBER', 'ORGANIZER')),
    set_by_user_id uuid         REFERENCES users (id) ON DELETE SET NULL,
    responded_at   timestamptz  NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

-- One answer per member per event; also serves lookups by event.
CREATE UNIQUE INDEX ux_attendances_event_member ON attendances (event_id, member_id);
-- For the cascade from members and the reminder fan-out's per-member check.
CREATE INDEX ix_attendances_member ON attendances (member_id);
