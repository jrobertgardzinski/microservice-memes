package com.jrobertgardzinski.memes.application.erasure;

import com.jrobertgardzinski.memes.domain.erasure.PurgePolicyOverride;
import com.jrobertgardzinski.purge.PurgeRule;

import java.util.Set;

/**
 * The administrators' dial over what becomes of a leaver's memes: the rule the deployment ships,
 * and the override written over it.
 */
public final class PurgePolicyService {

    private final PurgePolicyOverride override;
    private final PurgeRule envDefault;

    public PurgePolicyService(PurgePolicyOverride override, PurgeRule envDefault) {
        this.override = override;
        this.envDefault = envDefault;
    }

    public Reading current(Set<String> roles) {
        if (!isAdmin(roles)) {
            return new Reading.NotAnAdmin();
        }
        var overridden = override.current();
        return new Reading.InForce(overridden.orElse(envDefault), overridden.isPresent(), envDefault);
    }

    public Change set(String text, String caller, Set<String> roles) {
        if (!isAdmin(roles)) {
            return new Change.NotAnAdmin();
        }
        if (text == null || text.isBlank()) {
            return new Change.MissingRule();
        }
        PurgeRule rule;
        try {
            rule = PurgeRule.parse(text);
        } catch (IllegalArgumentException invalid) {
            return new Change.InvalidRule(invalid.getMessage());
        }
        override.set(rule, caller);
        return new Change.Overridden(rule);
    }

    public Change clear(String caller, Set<String> roles) {
        if (!isAdmin(roles)) {
            return new Change.NotAnAdmin();
        }
        override.clear(caller);
        return new Change.Restored(envDefault);
    }

    private static boolean isAdmin(Set<String> roles) {
        return roles != null && roles.contains("ADMIN");
    }

    public sealed interface Reading {
        /** {@code overridden}: the rule comes from the database, not the deployment. */
        record InForce(PurgeRule effective, boolean overridden, PurgeRule envDefault) implements Reading {}

        record NotAnAdmin() implements Reading {}
    }

    public sealed interface Change {
        record Overridden(PurgeRule rule) implements Change {}

        record Restored(PurgeRule envDefault) implements Change {}

        record MissingRule() implements Change {}

        record InvalidRule(String detail) implements Change {}

        record NotAnAdmin() implements Change {}
    }
}
