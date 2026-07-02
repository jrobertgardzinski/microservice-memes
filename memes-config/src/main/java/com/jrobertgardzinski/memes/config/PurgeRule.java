package com.jrobertgardzinski.memes.config;

/**
 * What happens to a piece of a leaver's content. Three rules: delete it; keep it with the author
 * anonymised; or decide by popularity — content that earned at least {@code minScore} survives
 * anonymised ("the community's, not the author's"), the rest disappears. The textual form
 * ({@code DELETE}, {@code ANONYMIZE_AUTHOR}, {@code KEEP_POPULAR_ANONYMIZED:100}) is the
 * vocabulary shared with callers: deployment env vars and the deletion wizard both speak it.
 */
public sealed interface PurgeRule {

    record Delete() implements PurgeRule {}

    record AnonymizeAuthor() implements PurgeRule {}

    record KeepPopularAnonymized(int minScore) implements PurgeRule {
        public KeepPopularAnonymized {
            if (minScore < 1) {
                throw new IllegalArgumentException("minScore must be at least 1, was " + minScore);
            }
        }
    }

    /** True when content with this score survives (anonymised) under the rule. */
    default boolean keeps(int score) {
        return switch (this) {
            case Delete() -> false;
            case AnonymizeAuthor() -> true;
            case KeepPopularAnonymized(int minScore) -> score >= minScore;
        };
    }

    static PurgeRule parse(String text) {
        if ("DELETE".equals(text)) {
            return new Delete();
        }
        if ("ANONYMIZE_AUTHOR".equals(text)) {
            return new AnonymizeAuthor();
        }
        if (text != null && text.startsWith("KEEP_POPULAR_ANONYMIZED:")) {
            try {
                return new KeepPopularAnonymized(
                        Integer.parseInt(text.substring("KEEP_POPULAR_ANONYMIZED:".length())));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("not a purge rule: " + text);
            }
        }
        throw new IllegalArgumentException("not a purge rule: " + text);
    }
}
