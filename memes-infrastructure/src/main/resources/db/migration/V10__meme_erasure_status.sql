-- The offboarding saga becomes compensatable: a leaver's memes leave the GALLERY before they leave
-- the DISK, and the step that hides them can be taken back.
--
-- WHAT WAS WRONG. The purge command destroyed rows, blobs and votes on arrival. That is a saga step
-- with no inverse, and it sat in the middle of a distributed transaction whose LATER participants
-- can still fail: when comments never confirmed, the orchestrator "compensated" by announcing
-- PORTAL_PURGE_FAILED, security unlocked the account, and the leaver got back a profile whose memes
-- had already been shredded. The word compensation was in the code; the act was not.
--
-- HOW THIS FIXES IT. Two columns and one view. The purge command now MARKS (status =
-- PENDING_ERASURE, marked_for_erasure_at = now); the mark hides the row from every public read
-- because every public read goes through active_memes; the orchestrator's compensation flips it
-- back; and only the orchestrator's CLOSURE command turns the mark into a DELETE. The saga's
-- irreversible act therefore happens after every participant has already succeeded, instead of
-- before the first one can fail.
--
-- DELIBERATELY NOT a separate erasure table or a queue of ids to delete (and above all not a
-- generic "to_delete(entity_type, entity_id)"): the fact is a property OF THE MEME — of exactly one
-- row, with exactly one lifetime — and a second table would need the row's key duplicated, its own
-- delete cascade, its own idempotence story, and a join on every read that must not see the meme.
-- The status is the shorter true statement. ADR 0007 states the reasoning in full.

ALTER TABLE memes ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
-- when the running saga reserved this meme; NULL for everything in the gallery. Not "when to
-- delete it": there is no such moment, because nothing but the saga's closure may delete it. The
-- instant is what the erasure BACKLOG is measured by — a mark this service has been sitting on for
-- longer than any saga can legitimately last is an alarm, never an expiry.
ALTER TABLE memes ADD COLUMN marked_for_erasure_at TIMESTAMP;

-- The invariants the aggregate enforces, repeated where rows are also written by migrations and by
-- humans at a psql prompt. The second one is the one that matters: a mark without its instant would
-- be invisible to the backlog alarm, and an instant without its mark would be a meme the gallery
-- shows and the reaper counts.
ALTER TABLE memes ADD CONSTRAINT ck_memes_status CHECK (status IN ('ACTIVE', 'PENDING_ERASURE'));
ALTER TABLE memes ADD CONSTRAINT ck_memes_erasure_mark
    CHECK ((status = 'PENDING_ERASURE') = (marked_for_erasure_at IS NOT NULL));

-- THE one place the ACTIVE filter is written down. Every read in this service that could reach a
-- viewer — the gallery page, the metadata, the existence checks the votes and thumbnails gate on,
-- the dedup index's answer, the ranking's join — selects from HERE and never from memes. A new
-- query cannot forget the filter, because forgetting it means naming a table the read side does not
-- use (and MemeReadFilterTest fails the build over it).
--
-- Columns are listed, not SELECT *: a view built with a star pins the column list at creation time
-- on Postgres, so the next ALTER TABLE would leave the view silently behind.
CREATE VIEW active_memes AS
    SELECT id, author, format, published_at
    FROM memes
    WHERE status = 'ACTIVE';

-- The reaper's whole data structure: status and the instant, in one index. This is what "no
-- separate queue" costs — one index — and what it buys is that a mark cannot go missing from a
-- queue that was written in a different transaction from the row it is about.
CREATE INDEX idx_memes_pending_erasure ON memes (status, marked_for_erasure_at);
-- and the leaver's own memes, the lookup both the mark and the erasure run: it was a sequential
-- scan before, over a table whose whole point is to grow
CREATE INDEX idx_memes_author ON memes (author);
