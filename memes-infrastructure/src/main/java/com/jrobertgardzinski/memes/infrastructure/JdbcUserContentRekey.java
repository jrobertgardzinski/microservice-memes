package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.UserContentRekey;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Every column in this schema that holds a person's e-mail address, in one place — because the
 * value of writing them down together is that the next one cannot be forgotten quietly.
 *
 * <p>The list, and why each is here:
 * <ul>
 *   <li>{@code memes.author} (V1) — the authorship itself, and the one the gallery authorises on;</li>
 *   <li>{@code meme_votes.voter} (V1) — the ballots. Left behind they would be counted for a person
 *       who can no longer see or change them, and a deletion's {@code purgeVoter} would not find
 *       them either;</li>
 *   <li>{@code settings.updated_by} (V4) — who last moved an operator dial. An audit line that
 *       names a freed address does not become vague, it becomes WRONG: it attributes the change to
 *       whoever registers that address next.</li>
 * </ul>
 * Nothing else keys on an address. {@code content_index}, {@code meme_tags}, {@code meme_flags},
 * {@code meme_blobs} and {@code pending_blob_deletes} are keyed by meme id or object key, and the
 * one remaining occurrence — a leaver's address inside a {@code meme_events_outbox} payload — is
 * deliberately NOT rewritten: that table is a record of messages already built, some already sent,
 * each with a derived id its consumers deduplicate on. Editing history there would change what a
 * redelivery says without changing its id.
 *
 * <p>{@code memes} and not {@code active_memes}: a meme a running saga has already reserved must
 * move with the rest, or the closure command — which looks for the leaver's PENDING memes by
 * address — would find nothing left to erase.
 *
 * <p>Three plain UPDATEs, no upsert and no conflict handling, and that is a statement about the
 * data rather than an omission. The only unique constraint an address takes part in is
 * {@code meme_votes}' primary key {@code (meme_id, voter)}, so a collision would need live rows
 * under the NEW address at the moment of the move — which cannot happen: security refuses a move
 * onto a registered address, and an unregistered one is either untouched or has had its owner's
 * rows taken by the deletion that freed it. If that ever stopped being true the insert would fail
 * loudly inside the listener's transaction and be retried, which is the right failure: nothing is
 * silently merged.
 */
@Repository
class JdbcUserContentRekey implements UserContentRekey {

    private final JdbcClient jdbc;

    JdbcUserContentRekey(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int moveTo(String oldEmail, String newEmail) {
        return jdbc.sql("UPDATE memes SET author = ? WHERE author = ?")
                       .params(newEmail, oldEmail).update()
                + jdbc.sql("UPDATE meme_votes SET voter = ? WHERE voter = ?")
                       .params(newEmail, oldEmail).update()
                + jdbc.sql("UPDATE settings SET updated_by = ? WHERE updated_by = ?")
                       .params(newEmail, oldEmail).update();
    }
}
