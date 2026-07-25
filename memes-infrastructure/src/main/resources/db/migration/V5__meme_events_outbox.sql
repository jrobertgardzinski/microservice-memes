-- The MEME_DELETED outbox (pattern borrowed from offboarding's outcome_announced): the
-- announcement is written HERE, in the SAME transaction as the delete/purge it announces.
-- A rollback takes the announcement with it; a crash between the commit and the Kafka send
-- no longer loses the event — the republisher re-sends whatever stayed unpublished.
-- The row carries the FULLY BUILT record (topic, key, payload, correlation id, all captured
-- at announce time, while the request's MDC is still alive), so a republish is identical to
-- the original attempt: same eventId inside the payload (it IS the row key), which makes a
-- redelivery a recognizable duplicate — and the consumer side (comments' DeleteThread) is
-- naturally idempotent anyway.
create table meme_events_outbox (
    id         varchar(36)   primary key,  -- also the payload's eventId: deterministic per row
    topic      varchar(64)   not null,
    event_type varchar(64)   not null,
    event_key  varchar(64)   not null,     -- Kafka partition key (the meme id)
    cid        varchar(64),                -- correlation header, stamped at announce time
    payload    varchar(1024) not null,
    created_at timestamp     not null,
    published  boolean       not null default false
);

-- the republisher polls "unpublished and old enough" every few seconds — keep it off a table scan
create index idx_meme_events_outbox_pending on meme_events_outbox (published, created_at);
