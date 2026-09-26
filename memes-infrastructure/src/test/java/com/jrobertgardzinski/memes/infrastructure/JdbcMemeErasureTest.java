package com.jrobertgardzinski.memes.infrastructure;

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
}
