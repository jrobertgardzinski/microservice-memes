package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.PurgeRule;
import com.jrobertgardzinski.memes.domain.DeletedAccount;

import java.util.Optional;

/**
 * The meme service's part of an account deletion (GDPR) — this service owns only the memes axis:
 * the {@link PurgeRule} (the deployment default, or the leaver's wizard choice carried with the
 * saga command) decides each meme's fate by its score — delete it, or keep it anonymised. A meme
 * that goes takes its votes and dedup-index entry along, and a {@code MEME_DELETED} event tells
 * microservice-comments to drop the thread. Votes the leaver cast are always retracted.
 * Idempotent — the saga may deliver the command twice.
 */
public class PurgeUserContent {

    private final MemeRepository memeRepository;
    private final VoteRepository voteRepository;
    private final MemeContentIndex contentIndex;
    private final TagRepository tagRepository;
    private final MemeEvents memeEvents;
    private final PurgeRule defaultRule;

    public PurgeUserContent(MemeRepository memeRepository, VoteRepository voteRepository,
                            MemeContentIndex contentIndex, TagRepository tagRepository,
                            MemeEvents memeEvents, PurgeRule defaultRule) {
        this.memeRepository = memeRepository;
        this.voteRepository = voteRepository;
        this.contentIndex = contentIndex;
        this.tagRepository = tagRepository;
        this.memeEvents = memeEvents;
        this.defaultRule = defaultRule;
    }

    public void execute(String author, Optional<PurgeRule> requested) {
        PurgeRule rule = requested.orElse(defaultRule);
        for (String memeId : memeRepository.findIdsByAuthor(author)) {
            if (rule.keeps(voteRepository.scoreOf(memeId))) {
                memeRepository.reassignAuthor(memeId, DeletedAccount.AUTHOR);
            } else {
                voteRepository.purgeMeme(memeId);
                contentIndex.remove(memeId);
                tagRepository.removeMeme(memeId);
                memeRepository.deleteById(memeId);
                memeEvents.memeDeleted(memeId);
            }
        }
        voteRepository.purgeVoter(author);
    }
}
