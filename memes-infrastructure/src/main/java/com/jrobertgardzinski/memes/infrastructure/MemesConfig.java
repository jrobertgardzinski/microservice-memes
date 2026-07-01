package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.AddComment;
import com.jrobertgardzinski.memes.application.CastVote;
import com.jrobertgardzinski.memes.application.CommentRepository;
import com.jrobertgardzinski.memes.application.ListComments;
import com.jrobertgardzinski.memes.application.MemeContentIndex;
import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.PublishMeme;
import com.jrobertgardzinski.memes.application.RankMemes;
import com.jrobertgardzinski.memes.application.ViewMeme;
import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.config.ImageLimits;
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
    PublishMeme publishMeme(WebImageOptimizer optimizer, MemeRepository repository, MemeContentIndex contentIndex) {
        return new PublishMeme(optimizer, repository, contentIndex);
    }

    @Bean
    ViewMeme viewMeme(MemeRepository repository) {
        return new ViewMeme(repository);
    }

    @Bean
    AddComment addComment(MemeRepository memeRepository, CommentRepository commentRepository) {
        return new AddComment(memeRepository, commentRepository);
    }

    @Bean
    ListComments listComments(CommentRepository commentRepository) {
        return new ListComments(commentRepository);
    }

    @Bean
    CastVote castVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        return new CastVote(memeRepository, voteRepository);
    }

    @Bean
    RankMemes rankMemes(VoteRepository voteRepository) {
        return new RankMemes(voteRepository);
    }
}

