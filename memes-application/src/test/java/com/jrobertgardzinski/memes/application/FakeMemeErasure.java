package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.domain.MemeMetadata;
import com.jrobertgardzinski.memes.domain.MemeStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory {@link MemeErasure} over the same meme map the repository fakes use, so a test can
 * run the saga's two phases end to end: mark, then erase — or mark, then restore.
 *
 * <p>The marks live in their own map rather than on the {@link Meme} records, for the same reason
 * the real schema keeps them on the row: the meme's bytes have nothing to do with its erasure
 * state. Whatever is not marked is ACTIVE, which is exactly what the {@code active_memes} view says.
 */
class FakeMemeErasure implements MemeErasure {

    private final Map<String, Meme> memes;
    private final Map<String, Instant> marks = new HashMap<>();

    FakeMemeErasure(Map<String, Meme> memes) {
        this.memes = memes;
    }

    @Override
    public List<MemeMetadata> activeOf(String author) {
        return byAuthor(author, false);
    }

    @Override
    public List<MemeMetadata> pendingOf(String author) {
        return byAuthor(author, true);
    }

    private List<MemeMetadata> byAuthor(String author, boolean marked) {
        List<MemeMetadata> found = new ArrayList<>();
        for (Meme meme : memes.values()) {
            if (meme.author().equals(author) && marks.containsKey(meme.id()) == marked) {
                found.add(metadataOf(meme));
            }
        }
        return found;
    }

    @Override
    public void store(MemeMetadata state) {
        if (state.isPendingErasure()) {
            marks.put(state.id(), state.markedForErasureAt());
        } else {
            marks.remove(state.id());
        }
    }

    @Override
    public List<MemeMetadata> pendingSince(Instant cutoff) {
        return memes.values().stream()
                .filter(meme -> marks.containsKey(meme.id()))
                .filter(meme -> marks.get(meme.id()).isBefore(cutoff))
                .map(this::metadataOf)
                .toList();
    }

    /** Whether this meme is hidden from the gallery right now — what a read-side assertion asks. */
    boolean isMarked(String memeId) {
        return marks.containsKey(memeId);
    }

    /** The reservations, for a test that fingerprints the whole world (idempotence). */
    Map<String, Instant> marks() {
        return Map.copyOf(marks);
    }

    private MemeMetadata metadataOf(Meme meme) {
        Instant marked = marks.get(meme.id());
        return new MemeMetadata(meme.id(), meme.author(), meme.format(),
                marked == null ? MemeStatus.ACTIVE : MemeStatus.PENDING_ERASURE, marked);
    }
}
