-- The three secondary access paths this schema never indexed. V1 gave every table a primary key and
-- stopped there; V10 added the two indexes the erasure needed and said why ("it was a sequential scan
-- before, over a table whose whole point is to grow"). These are the same argument, for the reads and
-- writes that were left scanning. microservice-comments has had all three shapes since its own V1.

-- meme_tags is keyed (meme_id, tag), so the TAG is the trailing column and no index leads with it:
-- every page of the gallery narrowed by a tag (JdbcTagRepository.memesTagged) scanned the whole table.
create index idx_meme_tags_tag on meme_tags (tag);

-- meme_votes is keyed (meme_id, voter). "Retract everything this person ever voted on" is the FIRST
-- statement of the account-erasure transaction (JdbcVoteRepository.purgeVoter, called by
-- PurgeUserContent before anything else), and it ran as a scan — on the Kafka listener thread, inside
-- a transaction bounded by max.poll.interval.ms. comments indexes the identical statement.
create index idx_meme_votes_voter on meme_votes (voter);

-- content_index is keyed by the hash, and the erasure looks the row up by MEME. That one is the worst
-- of the three because it runs once PER MEME inside the single purge transaction: a leaver with n
-- memes cost n scans of the dedup index, and an overrun does not degrade the purge, it retries it.
create index idx_content_index_meme_id on content_index (meme_id);
