package com.jrobertgardzinski.memes.system.tags;

import com.jrobertgardzinski.memes.system.core.SearchMemesByTag;

import com.jrobertgardzinski.memes.domain.core.MemeRepository;
import com.jrobertgardzinski.memes.domain.core.TagRepository;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.memes.config.tags.TagLimits;
import com.jrobertgardzinski.memes.domain.core.Meme;
import com.jrobertgardzinski.memes.tags.Tag;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Use case")
@Feature("Tagging and search")
class TagMemeTest {

    private final Map<String, Meme> store = new HashMap<>();
    private final Map<String, Set<Tag>> tagIndex = new HashMap<>();

    private final MemeRepository memes = new MemeRepository() {
        public void save(Meme meme) {
            store.put(meme.id(), meme);
        }

        public Optional<Meme> find(String id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<String> allIds() {
            return List.copyOf(store.keySet()).reversed();
        }

        public void deleteById(String memeId) {
            store.remove(memeId);
        }

        public void anonymise(String memeId) {
        }
    };

    private final TagRepository tags = new TagRepository() {
        public void replaceTags(String memeId, Set<Tag> newTags) {
            tagIndex.put(memeId, newTags);
        }

        public Set<Tag> tagsOf(String memeId) {
            return tagIndex.getOrDefault(memeId, Set.of());
        }

        public Set<String> memesTagged(Tag tag) {
            return tagIndex.entrySet().stream()
                    .filter(e -> e.getValue().contains(tag))
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
        }

        public void removeMeme(String memeId) {
            tagIndex.remove(memeId);
        }
    };

    private final TagMeme tagMeme = new TagMeme(memes, tags, new TagLimits(3));
    private final SearchMemesByTag search = new SearchMemesByTag(memes, tags);

    private static final UserId ALICE = UserId.of("11111111-1111-4111-8111-111111111111");
    private static final UserId MALLORY = UserId.of("22222222-2222-4222-8222-222222222222");

    private String meme(String id, UserId author) {
        memes.save(new Meme(id, author, "png", new byte[]{1}));
        return id;
    }

    @Test
    @DisplayName("the author curates the whole tag set in one move")
    void author_replaces_the_set() {
        meme("m1", ALICE);
        assertEquals(TagMeme.Status.TAGGED,
                tagMeme.execute("m1", ALICE, tags("Cats", "monday-mood")).status());
        assertEquals(Set.of(Tag.of("cats"), Tag.of("monday-mood")), tags.tagsOf("m1"));

        tagMeme.execute("m1", ALICE, tags("dogs"));
        assertEquals(Set.of(Tag.of("dogs")), tags.tagsOf("m1"), "a replace, not an append");
    }

    @Test
    @DisplayName("only the uploader tags their meme; ghosts and spam are refused")
    void refusals() {
        meme("m1", ALICE);
        assertEquals(TagMeme.Status.NOT_THE_AUTHOR,
                tagMeme.execute("m1", MALLORY, tags("cats")).status());
        assertEquals(TagMeme.Status.NO_SUCH_MEME,
                tagMeme.execute("ghost", ALICE, tags("cats")).status());
        assertEquals(TagMeme.Status.TOO_MANY_TAGS,
                tagMeme.execute("m1", ALICE, tags("a1", "a2", "a3", "a4")).status());
        assertThrows(IllegalArgumentException.class,
                () -> tagMeme.execute("m1", ALICE, tags("not a tag!")));
    }

    @Test
    @DisplayName("search narrows the gallery to the tag, in gallery order, existing memes only")
    void search_by_tag() {
        meme("m1", ALICE);
        meme("m2", ALICE);
        meme("m3", ALICE);
        tagMeme.execute("m1", ALICE, tags("cats"));
        tagMeme.execute("m3", ALICE, tags("cats", "dogs"));

        assertEquals(memes.allIds().stream().filter(Set.of("m1", "m3")::contains).toList(),
                search.execute(Tag.of("cats"), 0, 50), "the gallery's own order, narrowed");
        assertEquals(List.of("m3"), search.execute(Tag.of("dogs"), 0, 50));
        assertEquals(List.of(), search.execute(Tag.of("nobody"), 0, 50));
    }

    /** What the bridge hands the use case: the typed tags, built only when asked. */
    private static java.util.function.Supplier<List<Tag>> tags(String... raw) {
        return () -> java.util.Arrays.stream(raw).map(Tag::of).toList();
    }
}
