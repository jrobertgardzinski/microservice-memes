package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.DeleteMeme;
import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.TagRepository;
import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

/**
 * Pins the transactional seam ({@link TransactionalDeleteMeme}): a teardown consists of four DB
 * steps (votes, content-index claim, tags, the row) — when a late step dies, the earlier ones must
 * roll back, or the gallery serves a meme whose tags and votes have silently vanished.
 */
@SpringBootTest(classes = MemesApplication.class)
class DeleteMemeTransactionTest {

    @Autowired
    DeleteMeme deleteMeme;

    @Autowired
    TagRepository tags;

    @MockitoSpyBean
    MemeRepository memes;

    @Test
    @DisplayName("a teardown that dies on its last DB step leaves nothing half-deleted")
    void failed_teardown_rolls_back_the_earlier_steps() {
        memes.save(new Meme("half-dead", "author@example.com", "png", new byte[]{7}));
        tags.replaceTags("half-dead", Set.of(Tag.of("pinned")));
        // tags.removeMeme runs BEFORE deleteById in the use case — so if this rollback did not
        // happen, the meme would survive the failed delete stripped of its tags
        doThrow(new IllegalStateException("db died mid-teardown")).when(memes).deleteById("half-dead");

        assertThrows(IllegalStateException.class, () -> deleteMeme.execute("half-dead"));

        assertEquals(Set.of(Tag.of("pinned")), tags.tagsOf("half-dead"),
                "the earlier steps of the teardown must have rolled back");
    }
}
