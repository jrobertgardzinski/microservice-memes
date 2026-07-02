package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;

/**
 * The meme service's part of an account deletion (GDPR): the user's memes disappear together with
 * their whole comment threads and votes; the user's comments under other memes stay but are
 * anonymised ("deleted account"); and every vote the user ever cast is retracted. Idempotent —
 * the deletion saga may deliver the command more than once.
 */
public class PurgeUserContent {

    private final MemeRepository memeRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;
    private final CommentVoteRepository commentVoteRepository;
    private final MemeContentIndex contentIndex;

    public PurgeUserContent(MemeRepository memeRepository, CommentRepository commentRepository,
                            VoteRepository voteRepository, CommentVoteRepository commentVoteRepository,
                            MemeContentIndex contentIndex) {
        this.memeRepository = memeRepository;
        this.commentRepository = commentRepository;
        this.voteRepository = voteRepository;
        this.commentVoteRepository = commentVoteRepository;
        this.contentIndex = contentIndex;
    }

    public void execute(String author) {
        for (String memeId : memeRepository.findIdsByAuthor(author)) {
            for (Comment comment : commentRepository.findByMeme(memeId)) {
                commentVoteRepository.purgeComment(comment.id());
            }
            commentRepository.deleteByMeme(memeId);
            voteRepository.purgeMeme(memeId);
            contentIndex.remove(memeId);
            memeRepository.deleteById(memeId);
        }
        commentRepository.anonymizeAuthor(author, Comment.DELETED_ACCOUNT_AUTHOR);
        voteRepository.purgeVoter(author);
        commentVoteRepository.purgeVoter(author);
    }
}
