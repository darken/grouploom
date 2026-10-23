ALTER TABLE events
    ADD COLUMN cancelled_at          timestamptz,
    ADD COLUMN cancellation_message  varchar(1000);
