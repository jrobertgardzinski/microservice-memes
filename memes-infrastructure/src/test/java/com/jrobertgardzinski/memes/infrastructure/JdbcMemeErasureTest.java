package com.jrobertgardzinski.memes.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.memes.application.MemeErasure;
import com.jrobertgardzinski.memes.application.MemeErasureContractTest;
import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.domain.Meme;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The REFERENCE. Everything the contract asks is answered here by the adapter the service
 * actually runs on, against a real database — so "the stand-in behaves like the adapter" means
 * something, instead of meaning "three stand-ins agree with each other".
 */
@Epic("Architecture")
@Feature("A stand-in behaves like the adapter it stands in for")
@SpringBootTest(classes = MemesApplication.class)
class JdbcMemeErasureTest extends MemeErasureContractTest {

    @Autowired
    MemeErasure erasure;

    @Autowired
    MemeRepository memes;

    @Override
    protected MemeErasure erasure() {
        return erasure;
    }

    @Override
    protected void givenActiveMeme(String id, String author, Optional<UserId> authorId) {
        memes.save(new Meme(id, author, authorId, "png", new byte[]{1}));
    }

    @Test
    void anonymising_drops_the_author_id_with_the_address() {
        String id = java.util.UUID.randomUUID().toString();
        memes.save(new Meme(id, "leaver@example.com", Optional.of(UserId.random()), "png", new byte[]{1}));

        memes.reassignAuthor(id, com.jrobertgardzinski.memes.domain.DeletedAccount.AUTHOR);

        assertEquals(Optional.empty(), memes.findMetadata(id).orElseThrow().authorId(),
                "kept content of a closed account must not be groupable by its old id");
    }
}
