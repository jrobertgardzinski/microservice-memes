package com.jrobertgardzinski.memes.application.tags;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.memes.domain.tags.TagRepository;
import com.jrobertgardzinski.memes.system.tags.TagMeme;
import com.jrobertgardzinski.memes.tags.Tag;

import java.util.List;

/** A meme's tags: its author curating them, and anybody reading them. */
public final class TagService {

    private final TagMeme tagMeme;
    private final TagRepository tags;

    public TagService(TagMeme tagMeme, TagRepository tags) {
        this.tagMeme = tagMeme;
        this.tags = tags;
    }

    /**
     * Replaces the meme's tags with what the caller typed. The text becomes tags only when the use
     * case asks — after it has found the meme and the caller as its author.
     */
    public Tagging tag(String memeId, UserId caller, List<String> raw) {
        if (raw == null) {
            return new Tagging.TagsRequired();
        }
        TagMeme.Result result;
        try {
            result = tagMeme.execute(memeId, caller, () -> raw.stream().map(Tag::of).toList());
        } catch (IllegalArgumentException illegalTag) {
            return new Tagging.InvalidTag(illegalTag.getMessage());
        }
        return switch (result.status()) {
            case TAGGED -> new Tagging.Tagged(result.tags().stream().map(Tag::value).sorted().toList());
            case NO_SUCH_MEME -> new Tagging.NoSuchMeme();
            case NOT_THE_AUTHOR -> new Tagging.NotTheAuthor();
            case TOO_MANY_TAGS -> new Tagging.TooManyTags();
        };
    }

    public List<String> tags(String memeId) {
        return tags.tagsOf(memeId).stream().map(Tag::value).sorted().toList();
    }

    public sealed interface Tagging {
        record Tagged(List<String> tags) implements Tagging {}

        record TagsRequired() implements Tagging {}

        record InvalidTag(String detail) implements Tagging {}

        record NoSuchMeme() implements Tagging {}

        record NotTheAuthor() implements Tagging {}

        record TooManyTags() implements Tagging {}
    }
}
