package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.tags.Tag;

import java.util.List;
import java.util.Set;

/**
 * The gallery filtered by one tag, newest first — the same order the unfiltered gallery uses,
 * narrowed to the memes carrying the tag (and only memes that still exist).
 */
public class SearchMemesByTag {

    private final MemeRepository memes;
    private final TagRepository tags;

    public SearchMemesByTag(MemeRepository memes, TagRepository tags) {
        this.memes = memes;
        this.tags = tags;
    }

    public List<String> execute(Tag tag) {
        Set<String> tagged = tags.memesTagged(tag);
        return memes.allIds().stream().filter(tagged::contains).toList();
    }
}
