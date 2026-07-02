package com.jrobertgardzinski.memes.config;

/**
 * What an account deletion does to the leaver's content — one {@link PurgeRule} per axis (their
 * memes, their comments). The deployment configures the DEFAULT; the deletion wizard may override
 * it per request (the choice travels with the saga command). Votes the leaver cast are always
 * retracted — they are keyed by identity, so no policy can keep them.
 */
public record ContentPurgePolicy(PurgeRule memes, PurgeRule comments) {

    public static ContentPurgePolicy defaults() {
        return new ContentPurgePolicy(new PurgeRule.Delete(), new PurgeRule.AnonymizeAuthor());
    }
}
