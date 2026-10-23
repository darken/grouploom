-- Transactional outbox. Rows hold no content and no token: the dispatcher issues
-- a fresh member token and renders the email at send time.
CREATE TABLE email_outbox (
    id                   uuid         PRIMARY KEY,
    group_id             uuid         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    member_id            uuid         NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    requested_by_user_id uuid         REFERENCES users (id) ON DELETE SET NULL,
    kind                 varchar(40)  NOT NULL CHECK (kind IN ('PREFERENCES_LINK')),
    status               varchar(20)  NOT NULL DEFAULT 'PENDING'
                                      CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts             int          NOT NULL DEFAULT 0,
    next_attempt_at      timestamptz  NOT NULL,
    claimed_until        timestamptz,
    last_error           varchar(500),
    sent_at              timestamptz,
    created_at           timestamptz  NOT NULL DEFAULT now()
);

-- The dispatcher only ever looks at rows that still have work to do.
CREATE INDEX ix_email_outbox_due ON email_outbox (next_attempt_at) WHERE status IN ('PENDING', 'SENDING');

-- Cooldown check and latest-status lookup for a page of members.
CREATE INDEX ix_email_outbox_member_kind ON email_outbox (member_id, kind, created_at DESC);

-- FK cascade lookups.
CREATE INDEX ix_email_outbox_group ON email_outbox (group_id);
CREATE INDEX ix_email_outbox_requested_by ON email_outbox (requested_by_user_id);
