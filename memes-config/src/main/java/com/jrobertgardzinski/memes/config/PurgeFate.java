package com.jrobertgardzinski.memes.config;

/** What happens to a piece of content when its author's account is deleted. */
public enum PurgeFate {
    /** The content disappears entirely. */
    DELETE,
    /** The content stays, but its author reads "deleted account". */
    ANONYMIZE_AUTHOR
}
