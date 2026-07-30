-- The obligation to delete image BYTES, made durable — the same idea as meme_events_outbox (V5),
-- for the same reason and with the same shape of guarantee.
--
-- WHAT WAS LOST. The database can delete a meme row transactionally; a filesystem or an S3 bucket
-- cannot join that transaction, so the adapters park the physical delete and run it after the commit
-- (TransactionAwareDeletes — deleting eagerly would leave a rolled-back meme row pointing at bytes
-- that are already gone). That parked Runnable lived in JVM MEMORY only. Two ways it evaporated:
--   * the delete threw — logged at WARN, and nothing ever retried it: there was no inventory of keys
--     to retry FROM;
--   * the pod died between the commit and the after-commit phase (a Recreate deploy, an OOM kill) —
--     and then nobody in the world knew the key any more, because the memes row that held it was
--     committed away a millisecond earlier.
-- Both end the same way with MEMES_BLOB_STORE=s3 and a DELETE purge policy: the saga's
-- USER_CONTENT_PURGED confirmation IS durable (it is an outbox row in that same transaction), so the
-- account is deleted and the leaver is told their content is gone, while their images — often
-- photographs of faces — stay in the bucket with no code path left that could ever find them. This
-- service already calls that exact outcome a GDPR breach, in OrphanedBlobMigration's javadoc.
--
-- HOW THIS FIXES IT. The key is written HERE, inside the transaction that removes the row, so the
-- obligation is exactly as durable and exactly as conditional as the deletion it belongs to: a
-- rollback takes it away (the object must stay — its row came back), a commit keeps it. After the
-- commit the adapter deletes the object and only then deletes this row. Whatever is left in this
-- table is, by construction, an obligation that was owed and not discharged — and a periodic sweep
-- (PendingBlobDeleteSweep) finishes it, at the next pass or at the next start.
--
-- Deliberately NOT a Flyway-owned copy of the outbox table: this needs no topic, payload, attempt
-- count or backoff. The object either goes or it does not, the retry is idempotent (both adapters'
-- deletes are no-ops on a missing key), and there is nothing to order by but age.
create table pending_blob_deletes (
    -- an object key: a meme id, or a variant like {id}.webp / {id}.thumb. PRIMARY KEY, because the
    -- same key owed twice is one obligation — and the sweep's own re-attempt must not add a row.
    object_key   varchar(64) primary key,
    -- when the obligation was taken on. The sweep only touches rows OLDER than its grace period:
    -- a fresh row may belong to a transaction that is still open (a purge of hundreds of memes runs
    -- for minutes), and deleting its bytes before it commits would resurrect the very bug the
    -- after-commit parking exists to prevent — a meme row restored by a rollback with no image left.
    requested_at timestamp   not null
);

-- the sweep asks one question only: "what has been owed for longer than the grace period?"
create index idx_pending_blob_deletes_age on pending_blob_deletes (requested_at);
