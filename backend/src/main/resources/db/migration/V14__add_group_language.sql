ALTER TABLE groups
    ADD COLUMN language varchar(10) NOT NULL DEFAULT 'en',
    ADD CONSTRAINT groups_language_check CHECK (language IN ('en', 'ja'));
