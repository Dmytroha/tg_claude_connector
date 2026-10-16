-- Hashtags (lower-case, without '#') extracted from the text when a message is stored: channels use them as
-- categories (#склад, #реклама). Existing rows are filled by the Java migration V4.
ALTER TABLE messages ADD COLUMN hashtags TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX messages_hashtags_idx ON messages USING GIN (hashtags);
