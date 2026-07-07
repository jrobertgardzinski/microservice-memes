package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.PurgeRule;

import java.util.Optional;

/**
 * An admin's runtime override of the deployment's default purge rule (the memes axis). The
 * resolution order for a purge is: the leaver's wizard choice (rides with the saga command),
 * then this override, then the env default — so an operator can retune the policy without a
 * redeploy, and a user's explicit wish still wins over everything.
 */
public interface PurgePolicyOverride {

    /** The override currently in force, if an admin has set one. */
    Optional<PurgeRule> current();

    /** Replaces the override; {@code updatedBy} is the admin's identity, kept for the audit. */
    void set(PurgeRule rule, String updatedBy);

    /** Removes the override — the deployment default applies again. */
    void clear();
}
