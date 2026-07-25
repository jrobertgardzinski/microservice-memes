-- The outbox payload was varchar(1024) (V5) — sized for today's one-line MEME_DELETED JSON.
-- The first richer event type to cross that line would not be truncated quietly, it would fail
-- the INSERT inside the delete/purge transaction and take the teardown down with it. TEXT costs
-- nothing in Postgres (same varlena storage as varchar) and removes the cliff entirely; the
-- append path still logs a canary when a payload crosses the old 1024-char expectation, so an
-- unexpectedly fat event gets noticed instead of becoming routine.
alter table meme_events_outbox alter column payload set data type text;
