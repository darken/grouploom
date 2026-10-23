-- Notifications an organizer scheduled for an event (or the system created on cancellation).
-- Recipients are not stored here: they are chosen at send time and expanded into email_outbox rows.
CREATE TABLE event_notifications (
    id                 uuid         PRIMARY KEY,
    event_id           uuid         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    group_id           uuid         NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    kind               varchar(20)  NOT NULL CHECK (kind IN ('INVITATION', 'REMINDER', 'CANCELLATION')),
    status             varchar(20)  NOT NULL CHECK (status IN ('SCHEDULED', 'SENDING', 'SENT', 'CANCELLED')),
    send_at            timestamptz  NOT NULL,
    -- Set for "N minutes before the start" notifications, so send_at follows the event if it moves.
    offset_minutes     int          CHECK (offset_minutes BETWEEN 15 AND 43200),
    message            varchar(1000),
    recipient_count    int,
    created_by_user_id uuid         REFERENCES users (id) ON DELETE SET NULL,
    claimed_until      timestamptz,
    sent_at            timestamptz,
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now()
);

-- The fan-out only ever looks at notifications that still have work to do.
CREATE INDEX ix_event_notifications_due ON event_notifications (send_at) WHERE status IN ('SCHEDULED', 'SENDING');
CREATE INDEX ix_event_notifications_event ON event_notifications (event_id);
-- FK cascade lookups.
CREATE INDEX ix_event_notifications_group ON event_notifications (group_id);
CREATE INDEX ix_event_notifications_created_by ON event_notifications (created_by_user_id);

ALTER TABLE email_outbox
    ADD COLUMN event_id        uuid REFERENCES events (id) ON DELETE CASCADE,
    ADD COLUMN notification_id uuid REFERENCES event_notifications (id) ON DELETE CASCADE;

-- Declared inline in V8, so Postgres named it <table>_<column>_check.
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_kind_check;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_kind_check
    CHECK (kind IN ('PREFERENCES_LINK', 'EVENT_INVITATION', 'EVENT_REMINDER', 'EVENT_CANCELLATION'));

-- Makes the fan-out idempotent: a retry can never queue a second email for the same member.
CREATE UNIQUE INDEX ux_email_outbox_notification_member ON email_outbox (notification_id, member_id)
    WHERE notification_id IS NOT NULL;
-- Delivery counts per notification.
CREATE INDEX ix_email_outbox_notification_status ON email_outbox (notification_id, status);
-- Cancellation recipients (who got a SENT email for this event) and FK cascade lookups.
CREATE INDEX ix_email_outbox_event ON email_outbox (event_id, kind, status);
