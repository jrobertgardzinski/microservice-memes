package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.AddComment;
import com.jrobertgardzinski.memes.application.CastVote;
import com.jrobertgardzinski.memes.application.CommentRepository;
import com.jrobertgardzinski.memes.application.CommentVoteRepository;
import com.jrobertgardzinski.memes.application.ListComments;
import com.jrobertgardzinski.memes.application.ListMemes;
import com.jrobertgardzinski.memes.application.MakeThumbnail;
import com.jrobertgardzinski.memes.application.MemeContentIndex;
import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.PublishMeme;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.application.RankMemes;
import com.jrobertgardzinski.memes.application.ShowMemeVote;
import com.jrobertgardzinski.memes.application.ViewMeme;
import com.jrobertgardzinski.memes.application.VoteOnComment;
import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.config.ContentPurgePolicy;
import com.jrobertgardzinski.memes.config.ImageLimits;
import com.jrobertgardzinski.memes.config.PurgeFate;
import com.jrobertgardzinski.memes.config.ThumbnailSize;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free use cases and image optimiser as Spring beans; the repository is a
 * {@code @Component} discovered by scanning.
 */
@Configuration
class MemesConfig {

    @Bean
    ImageLimits imageLimits(@Value("${memes.image.max-dimension:1024}") int maxDimension) {
        return new ImageLimits(maxDimension);
    }

    @Bean
    WebImageOptimizer webImageOptimizer(ImageLimits imageLimits) {
        return new WebImageOptimizer(imageLimits);
    }

    @Bean
    ThumbnailSize thumbnailSize(@Value("${memes.image.thumbnail-max-dimension:256}") int maxDimension) {
        return new ThumbnailSize(maxDimension);
    }

    @Bean
    MakeThumbnail makeThumbnail(MemeRepository repository, WebImageOptimizer optimizer, ThumbnailSize thumbnailSize) {
        return new MakeThumbnail(repository, optimizer, thumbnailSize);
    }

    @Bean
    PublishMeme publishMeme(WebImageOptimizer optimizer, MemeRepository repository, MemeContentIndex contentIndex) {
        return new PublishMeme(optimizer, repository, contentIndex);
    }

    @Bean
    ViewMeme viewMeme(MemeRepository repository) {
        return new ViewMeme(repository);
    }

    @Bean
    ListMemes listMemes(MemeRepository repository) {
        return new ListMemes(repository);
    }

    @Bean
    AddComment addComment(MemeRepository memeRepository, CommentRepository commentRepository) {
        return new AddComment(memeRepository, commentRepository);
    }

    @Bean
    ListComments listComments(CommentRepository commentRepository, CommentVoteRepository commentVoteRepository) {
        return new ListComments(commentRepository, commentVoteRepository);
    }

    @Bean
    VoteOnComment voteOnComment(CommentRepository commentRepository, CommentVoteRepository commentVoteRepository) {
        return new VoteOnComment(commentRepository, commentVoteRepository);
    }

    @Bean
    CastVote castVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        return new CastVote(memeRepository, voteRepository);
    }

    @Bean
    ShowMemeVote showMemeVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        return new ShowMemeVote(memeRepository, voteRepository);
    }

    @Bean
    ContentPurgePolicy contentPurgePolicy(@Value("${memes.purge.memes:DELETE}") PurgeFate memes,
                                          @Value("${memes.purge.comments:ANONYMIZE_AUTHOR}") PurgeFate comments) {
        return new ContentPurgePolicy(memes, comments);
    }

    @Bean
    PurgeUserContent purgeUserContent(MemeRepository memeRepository, CommentRepository commentRepository,
                                      VoteRepository voteRepository, CommentVoteRepository commentVoteRepository,
                                      MemeContentIndex contentIndex, ContentPurgePolicy policy) {
        return new PurgeUserContent(memeRepository, commentRepository, voteRepository,
                commentVoteRepository, contentIndex, policy);
    }

    @Bean
    RankMemes rankMemes(VoteRepository voteRepository) {
        return new RankMemes(voteRepository);
    }
}

