-- An attendance link answers for exactly one event; every other purpose has no event.
ALTER TABLE member_tokens ADD COLUMN event_id uuid REFERENCES events (id) ON DELETE CASCADE;

ALTER TABLE member_tokens DROP CONSTRAINT member_tokens_purpose_check;
ALTER TABLE member_tokens ADD CONSTRAINT member_tokens_purpose_check
    CHECK (purpose IN ('PREFERENCES', 'UNSUBSCRIBE', 'ATTENDANCE'));
ALTER TABLE member_tokens ADD CONSTRAINT member_tokens_event_purpose_check
    CHECK ((purpose = 'ATTENDANCE') = (event_id IS NOT NULL));

CREATE INDEX ix_member_tokens_event ON member_tokens (event_id) WHERE event_id IS NOT NULL;
