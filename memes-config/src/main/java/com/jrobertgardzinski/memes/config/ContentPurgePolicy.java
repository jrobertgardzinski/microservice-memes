package com.jrobertgardzinski.memes.config;

/**
 * Configuration of the account-deletion purge — the saga is configurable per deployment: what
 * happens to a leaver's memes and what happens to their comments under other memes. The default
 * (memes deleted with their whole threads, comments kept but anonymised) is the product decision;
 * operators may flip either axis. Votes the leaver cast are always retracted — they are keyed by
 * identity, so no policy can keep them.
 */
public record ContentPurgePolicy(PurgeFate memes, PurgeFate comments) {

    public static ContentPurgePolicy defaults() {
        return new ContentPurgePolicy(PurgeFate.DELETE, PurgeFate.ANONYMIZE_AUTHOR);
    }
}
