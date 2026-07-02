package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ContentPurgePolicy;
import com.jrobertgardzinski.memes.domain.Comment;
import com.jrobertgardzinski.memes.domain.DeletedAccount;

import java.util.Optional;

/**
 * The meme service's part of an account deletion (GDPR) — a configurable saga step. The
 * {@link ContentPurgePolicy} (the deployment default, or the leaver's own choice carried with the
 * saga command) decides each item's fate: delete it, keep it anonymised, or keep it only when the
 * community rated it highly enough ({@code KEEP_POPULAR_ANONYMIZED}). A meme that goes takes its
 * whole comment thread, votes and dedup-index entry along; a kept one stays authored by
 * "deleted account". Votes the leaver cast are always retracted. Idempotent — the saga may
 * deliver the command twice.
 */
public class PurgeUserContent {

    private final MemeRepository memeRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final MemeContentIndex contentIndex;
    private final ContentPurgePolicy defaultPolicy;

    public PurgeUserContent(MemeRepository memeRepository, CommentRepository commentRepository,
                            VoteRepository voteRepository, CommentVoteRepository commentVoteRepository,
                            MemeContentIndex contentIndex, ContentPurgePolicy defaultPolicy) {
        this.memeRepository = memeRepository;
        this.commentRepository = commentRepository;
        this.voteRepository = voteRepository;
        this.commentVoteRepository = commentVoteRepository;
        this.contentIndex = contentIndex;
        this.defaultPolicy = defaultPolicy;
    }

    public void execute(String author, Optional<ContentPurgePolicy> requested) {
        ContentPurgePolicy policy = requested.orElse(defaultPolicy);
        for (String memeId : memeRepository.findIdsByAuthor(author)) {
            if (policy.memes().keeps(voteRepository.scoreOf(memeId))) {
                memeRepository.reassignAuthor(memeId, DeletedAccount.AUTHOR);
            } else {
                deleteMemeWithItsThread(memeId);
            }
        }
        for (Comment comment : commentRepository.findByAuthor(author)) {
            if (policy.comments().keeps(commentVoteRepository.scoreOf(comment.id()))) {
                commentRepository.reassignAuthor(comment.id(), DeletedAccount.AUTHOR);
            } else {
                commentVoteRepository.purgeComment(comment.id());
                commentRepository.delete(comment.id());
            }
        }
        voteRepository.purgeVoter(author);
        commentVoteRepository.purgeVoter(author);
    }

    private void deleteMemeWithItsThread(String memeId) {
        for (Comment comment : commentRepository.findByMeme(memeId)) {
            commentVoteRepository.purgeComment(comment.id());
        }
        commentRepository.deleteByMeme(memeId);
        voteRepository.purgeMeme(memeId);
        contentIndex.remove(memeId);
        memeRepository.deleteById(memeId);
    }
}
