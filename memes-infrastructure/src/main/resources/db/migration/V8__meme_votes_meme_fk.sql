-- meme_votes was the last child of `memes` without a foreign key. meme_flags (V3) has had one from
-- the start, with a comment explaining exactly why: "a deleted meme takes its flags along without
-- anyone remembering to". Ballots were left remembering to, in application code, in two places
-- (DeleteMeme and PurgeUserContent both call purgeMeme before deleteById).
--
-- WHY THAT IS NOT ENOUGH. CastVote checks memeRepository.exists(memeId) and then inserts the ballot
-- in a SEPARATE transaction. A vote cast in the window between that check and the insert — while a
-- delete or a GDPR purge commits — lands on a meme id that no longer exists. Nothing ever removes
-- such a row: purgeMeme has already run for that meme, and no later pass looks for ballots without
-- memes. The orphan is not inert either: the hot page reads score AND age with a LEFT JOIN, an
-- unknown age is deliberately treated as "brand new" rather than buried (RankMemes.hotness), and
-- "brand new" is the largest multiplier there is — so the ranking promotes a meme that cannot be
-- served to the top of the page, and keeps it there. Under a purge it is also a leftover of a
-- deleted account's activity, which is the axis GDPR cares about.
--
-- The constraint moves the obligation to where it cannot be forgotten: the insert of an orphan is
-- refused, and a meme deleted by any path takes its ballots with it. The existing purgeMeme calls
-- stay — they are still the ones that make the purge's DB part one atomic step, and the cascade is
-- now the floor under them rather than a duplicate of them.
--
-- Any orphan already in the table goes first: it is exactly the row the constraint forbids, and the
-- ALTER would fail on it. There is no owner left to ask, and the meme it votes on cannot come back.
delete from meme_votes where not exists (select 1 from memes where memes.id = meme_votes.meme_id);

-- The primary key is (meme_id, voter), so meme_id already leads an index: the cascade's lookup on
-- delete needs no index of its own.
alter table meme_votes
    add constraint fk_meme_votes_meme foreign key (meme_id) references memes (id) on delete cascade;
