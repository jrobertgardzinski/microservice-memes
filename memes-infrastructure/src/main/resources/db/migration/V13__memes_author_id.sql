-- The author's stable identity beside the address. Nullable for now: rows written before this
-- migration carry only the address and are filled in by the backfill; new uploads write both.
-- The address column goes when every row and every reader has moved to the id.
ALTER TABLE memes ADD COLUMN author_id UUID;
CREATE INDEX idx_memes_author_id ON memes (author_id);

-- the gallery's view gains the column; same rows, same rule
DROP VIEW active_memes;
CREATE VIEW active_memes AS
    SELECT id, author, author_id, format, published_at
    FROM memes
    WHERE status = 'ACTIVE';
