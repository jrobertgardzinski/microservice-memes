package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.voting.VoteDirection;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two promises the schema makes to the code above it: that the columns queries filter BY can be
 * reached without reading the table, and that an identity this portal mints fits in the column that
 * stores it.
 */
@Epic("Infrastructure")
@Feature("Schema")
class SchemaReachesItsRowsTest {

    /** An address this portal really can mint: security stores 255, the e-mail domain caps nothing. */
    private static final String LONG_ADDRESS = "a".repeat(200) + "@example.com";

    @SpringBootTest(classes = MemesApplication.class)
    static class Indexes {

        @Autowired
        JdbcClient jdbc;

        @Test
        @DisplayName("every secondary lookup has an index leading with the column it filters by")
        void the_secondary_paths_are_indexed() {
            assertIndexLeadsWith("meme_tags", "tag",
                    "JdbcTagRepository.memesTagged — every gallery page narrowed by a tag");
            assertIndexLeadsWith("meme_votes", "voter",
                    "JdbcVoteRepository.purgeVoter — the first statement of the erasure transaction");
            assertIndexLeadsWith("content_index", "meme_id",
                    "JdbcMemeContentIndex.remove — once per meme inside the purge's one transaction");
        }

        /**
         * The leading column is what matters: a composite key whose second column is the one being
         * filtered on (meme_tags is keyed (meme_id, tag)) is exactly the case that reads as indexed
         * and scans.
         */
        private void assertIndexLeadsWith(String table, String column, String why) {
            List<String> indexes = jdbc.sql("SELECT index_name FROM information_schema.index_columns "
                            + "WHERE lower(table_name) = ? AND lower(column_name) = ? AND ordinal_position = 1")
                    .params(table, column)
                    .query((rs, n) -> rs.getString("index_name")).list();
            assertTrue(!indexes.isEmpty(),
                    "nothing indexes " + table + "(" + column + "), so it is a full scan: " + why);
        }
    }

    @SpringBootTest(classes = MemesApplication.class)
    static class Identities {

        @Autowired
        MemeRepository memes;

        @Autowired
        VoteRepository votes;

        @Test
        @DisplayName("an address as long as security allows can publish and vote")
        void a_long_address_is_not_a_500() {
            String id = UUID.randomUUID().toString();

            memes.save(new Meme(id, LONG_ADDRESS, "png", new byte[]{1, 2, 3}));
            votes.cast(id, LONG_ADDRESS, VoteDirection.UP);

            assertEquals(LONG_ADDRESS, memes.findMetadata(id).orElseThrow().author(),
                    "the gallery stores the whole address it was handed, not a prefix");
            assertEquals(Optional.of(VoteDirection.UP), votes.voteOf(id, LONG_ADDRESS),
                    "and the ballot is cast under the same address");
        }
    }
}
