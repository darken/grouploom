CREATE TABLE member_tokens (
    id           uuid        PRIMARY KEY,
    member_id    uuid        NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    purpose      varchar(30) NOT NULL CHECK (purpose IN ('PREFERENCES')),
    token_hash   char(64)    NOT NULL UNIQUE,
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    last_used_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_member_tokens_member ON member_tokens (member_id);
