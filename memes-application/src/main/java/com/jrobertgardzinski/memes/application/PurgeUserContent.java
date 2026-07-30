package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.PurgeRule;
import com.jrobertgardzinski.memes.domain.DeletedAccount;

import java.util.Optional;

/**
 * The meme service's part of an account deletion (GDPR) — this service owns only the memes axis:
 * the {@link PurgeRule} decides each meme's fate by its score — delete it, or keep it anonymised.
 * Resolution order: the rule carried with the saga command, then the admin's runtime
 * {@link PurgePolicyOverride}, then the deployment default. A meme
 * that goes takes its votes and dedup-index entry along, and a {@code MEME_DELETED} event tells
 * microservice-comments to drop the thread. Votes the leaver cast are always retracted.
 * Idempotent — the saga may deliver the command twice.
 *
 * <p><strong>An absent rule is silence, not a choice.</strong> {@code requested} empty means the
 * command carried no policy at all, and this use case reads that as "decide for me" — which is why
 * the override gets its say next. It does NOT mean the leaver asked for the deployment default: a
 * wizard that wants its own label honoured has to say so, and the memes-ui wizard did not for its
 * preselected option (it sent an empty choice map, so the orchestrator omitted the policy field and
 * an admin's override silently replaced a stated "delete my memes" — P18 poz. 18). The order above
 * therefore honours the leaver over the operator only for a rule that actually arrived here.
 */
public class PurgeUserContent {

    private final MemeRepository memeRepository;
    private final VoteRepository voteRepository;
    private final MemeContentIndex contentIndex;
    private final TagRepository tagRepository;
    private final MemeEvents memeEvents;
    private final PurgePolicyOverride override;
    private final PurgeRule defaultRule;

    public PurgeUserContent(MemeRepository memeRepository, VoteRepository voteRepository,
                            MemeContentIndex contentIndex, TagRepository tagRepository,
                            MemeEvents memeEvents, PurgePolicyOverride override, PurgeRule defaultRule) {
        this.memeRepository = memeRepository;
        this.voteRepository = voteRepository;
        this.contentIndex = contentIndex;
        this.tagRepository = tagRepository;
        this.memeEvents = memeEvents;
        this.override = override;
        this.defaultRule = defaultRule;
    }

    public void execute(String author, Optional<PurgeRule> requested) {
        PurgeRule rule = requested.or(override::current).orElse(defaultRule);
        // FIRST, before any score is read: the leaver's own votes are leaving with him anyway, and a
        // rule like "keep what the community liked" must be answered by the COMMUNITY. Retracting
        // them afterwards meant the threshold was measured against a score that no longer existed a
        // moment later — a leaver who had upvoted his own memes bought their survival with a vote
        // this method was about to delete (P18 poz. 39).
        voteRepository.purgeVoter(author);
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
    }
}
