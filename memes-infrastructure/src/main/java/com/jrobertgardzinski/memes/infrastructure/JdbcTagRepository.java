package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.TagRepository;
import com.jrobertgardzinski.memes.tags.Tag;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/** Postgres-backed {@link TagRepository} (H2 in dev/tests): both directions from one table. */
@Repository
class JdbcTagRepository implements TagRepository {

    private final JdbcClient jdbc;

    JdbcTagRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replaceTags(String memeId, Set<Tag> tags) {
        jdbc.sql("DELETE FROM meme_tags WHERE meme_id = ?").params(memeId).update();
        for (Tag tag : tags) {
            jdbc.sql("INSERT INTO meme_tags (meme_id, tag) VALUES (?, ?)")
                    .params(memeId, tag.value()).update();
        }
    }

    @Override
    public Set<Tag> tagsOf(String memeId) {
        return jdbc.sql("SELECT tag FROM meme_tags WHERE meme_id = ?")
                .params(memeId)
                .query((rs, n) -> new Tag(rs.getString("tag")))
                .list().stream().collect(Collectors.toSet());
    }

    @Override
    public Set<String> memesTagged(Tag tag) {
        return jdbc.sql("SELECT meme_id FROM meme_tags WHERE tag = ?")
                .params(tag.value())
                .query((rs, n) -> rs.getString("meme_id"))
                .list().stream().collect(Collectors.toSet());
    }

    @Override
    public void removeMeme(String memeId) {
        jdbc.sql("DELETE FROM meme_tags WHERE meme_id = ?").params(memeId).update();
    }
}
