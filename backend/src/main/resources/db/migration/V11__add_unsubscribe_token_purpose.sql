-- Declared inline in V6, so Postgres named it <table>_<column>_check.
ALTER TABLE member_tokens DROP CONSTRAINT member_tokens_purpose_check;
ALTER TABLE member_tokens ADD CONSTRAINT member_tokens_purpose_check CHECK (purpose IN ('PREFERENCES', 'UNSUBSCRIBE'));
