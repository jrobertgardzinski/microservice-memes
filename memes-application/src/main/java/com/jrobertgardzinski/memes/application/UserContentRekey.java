package com.jrobertgardzinski.memes.application;

/**
 * Moving everything this service holds of one person from the address they had to the address they
 * have. It is a port of its own, and not a method on {@link MemeRepository} or
 * {@link VoteRepository}, for the reason that decides most of this service's port boundaries: the
 * question is not about a meme or about a ballot, it is about a PERSON, and it has to be answered
 * for every row keyed by their address in one step. Split across the existing ports it would be
 * three calls that a later reader has to remember to keep together, which is exactly how the
 * authorship and the ballots would drift apart.
 *
 * <p>The count is the row count, and it is for the log line and for the caller's sense of scale —
 * nothing decides anything on it. Zero is an ordinary answer: the person may have uploaded nothing,
 * and a redelivered rename finds the rows already moved.
 */
public interface UserContentRekey {

    /** Every row keyed by {@code oldEmail}, re-keyed to {@code newEmail}; returns how many moved. */
    int moveTo(String oldEmail, String newEmail);
}
