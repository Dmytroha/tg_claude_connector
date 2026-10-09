-- Trigram index support for case-insensitive substring search (ILIKE '%...%').
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE sources
(
    id                   UUID PRIMARY KEY,
    -- "@channel" for public channels, numeric chat id for bot chats (ChatReference#asString)
    reference            TEXT        NOT NULL UNIQUE,
    title                TEXT        NOT NULL,
    status               TEXT        NOT NULL,
    last_read_message_id BIGINT,
    registered_at        TIMESTAMPTZ NOT NULL,
    -- optimistic locking
    version              BIGINT      NOT NULL
);

CREATE TABLE messages
(
    source_id       UUID        NOT NULL REFERENCES sources (id),
    message_id      BIGINT      NOT NULL,
    text            TEXT        NOT NULL,
    -- [{"kind": "PHOTO", "reference": "..."}]
    attachments     JSONB       NOT NULL DEFAULT '[]',
    author_name     TEXT        NOT NULL,
    author_username TEXT,
    posted_at       TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source_id, message_id)
);

CREATE INDEX messages_posted_at_idx ON messages (posted_at DESC);
CREATE INDEX messages_text_trgm_idx ON messages USING GIN (text gin_trgm_ops);
