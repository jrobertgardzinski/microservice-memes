package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.PurgePolicyOverride;
import com.jrobertgardzinski.memes.config.PurgeRule;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * The admin's purge-policy override, persisted in the generic {@code settings} table (Postgres;
 * H2 in dev/tests) under the {@code purge.memes} key. Only {@link PurgeRule#asText() canonical
 * rule text} is ever written, so a row that fails to parse (a hand-edited database) is treated
 * as no override rather than wedging every purge.
 */
@Repository
class JdbcPurgePolicyOverride implements PurgePolicyOverride {

    private static final String KEY = "purge.memes";

    private final JdbcClient jdbc;

    JdbcPurgePolicyOverride(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PurgeRule> current() {
        return jdbc.sql("SELECT setting_value FROM settings WHERE setting_key = ?")
                .params(KEY).query(String.class).optional()
                .flatMap(JdbcPurgePolicyOverride::parsedQuietly);
    }

    @Override
    public void set(PurgeRule rule, String updatedBy) {
        jdbc.sql("DELETE FROM settings WHERE setting_key = ?").params(KEY).update();
        jdbc.sql("INSERT INTO settings (setting_key, setting_value, updated_at, updated_by) VALUES (?, ?, ?, ?)")
                .params(KEY, rule.asText(), Timestamp.from(Instant.now()), updatedBy).update();
    }

    @Override
    public void clear() {
        jdbc.sql("DELETE FROM settings WHERE setting_key = ?").params(KEY).update();
    }

    private static Optional<PurgeRule> parsedQuietly(String text) {
        try {
            return Optional.of(PurgeRule.parse(text));
        } catch (IllegalArgumentException handEdited) {
            return Optional.empty();
        }
    }
}
