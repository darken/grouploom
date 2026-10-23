-- A missing row means defaults: not paused, no opt-outs.
CREATE TABLE member_preferences (
    member_id             uuid        PRIMARY KEY REFERENCES members (id) ON DELETE CASCADE,
    invitations_paused_at timestamptz,
    updated_at            timestamptz NOT NULL DEFAULT now()
);

-- Only opt-outs are stored, so new event types include every member by default.
CREATE TABLE member_event_type_opt_outs (
    member_id     uuid        NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    event_type_id uuid        NOT NULL REFERENCES event_types (id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, event_type_id)
);

-- Recipient queries by event type, and FK cascade lookups.
CREATE INDEX ix_opt_outs_event_type ON member_event_type_opt_outs (event_type_id);
