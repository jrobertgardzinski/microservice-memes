package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ContentPurgePolicy;
import com.jrobertgardzinski.memes.config.PurgeFate;
import com.jrobertgardzinski.memes.domain.Comment;
import com.jrobertgardzinski.memes.domain.DeletedAccount;

/**
 * The meme service's part of an account deletion (GDPR) — a configurable saga step, driven by
 * {@link ContentPurgePolicy}: the leaver's memes are deleted with their whole comment threads OR
 * kept with the author anonymised; the leaver's comments are deleted OR kept anonymised (the
 * default: memes go, comment texts stay as "deleted account"). Votes the leaver cast are always
 * retracted — they are keyed by identity. Idempotent: the saga may deliver the command twice.
 */
public class PurgeUserContent {

    private final MemeRepository memeRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final MemeContentIndex contentIndex;
    private final ContentPurgePolicy policy;

    public PurgeUserContent(MemeRepository memeRepository, CommentRepository commentRepository,
                            VoteRepository voteRepository, CommentVoteRepository commentVoteRepository,
                            MemeContentIndex contentIndex, ContentPurgePolicy policy) {
        this.memeRepository = memeRepository;
        this.commentRepository = commentRepository;
        this.voteRepository = voteRepository;
        this.commentVoteRepository = commentVoteRepository;
        this.contentIndex = contentIndex;
        this.policy = policy;
    }

    public void execute(String author) {
        if (policy.memes() == PurgeFate.DELETE) {
            deleteMemesWithTheirThreads(author);
        } else {
            memeRepository.anonymizeAuthor(author, DeletedAccount.AUTHOR);
        }
        if (policy.comments() == PurgeFate.DELETE) {
            deleteCommentsEverywhere(author);
        } else {
            commentRepository.anonymizeAuthor(author, DeletedAccount.AUTHOR);
        }
        voteRepository.purgeVoter(author);
        commentVoteRepository.purgeVoter(author);
    }

    private void deleteMemesWithTheirThreads(String author) {
        for (String memeId : memeRepository.findIdsByAuthor(author)) {
            for (Comment comment : commentRepository.findByMeme(memeId)) {
                commentVoteRepository.purgeComment(comment.id());
            }
            commentRepository.deleteByMeme(memeId);
            voteRepository.purgeMeme(memeId);
            contentIndex.remove(memeId);
            memeRepository.deleteById(memeId);
        }
    }

    private void deleteCommentsEverywhere(String author) {
        for (Comment comment : commentRepository.findByAuthor(author)) {
            commentVoteRepository.purgeComment(comment.id());
        }
        commentRepository.deleteByAuthor(author);
    }
}
