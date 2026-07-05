-- Moderation flags over memes (today one axis: NSFW). A separate table on purpose: the flag is
-- the community's judgement, not the uploader's content — and the cascade means a deleted meme
-- (moderation or the account purge) takes its flags along without anyone remembering to.
CREATE TABLE meme_flags (
    meme_id VARCHAR(64) PRIMARY KEY REFERENCES memes(id) ON DELETE CASCADE,
    nsfw    BOOLEAN NOT NULL
);
