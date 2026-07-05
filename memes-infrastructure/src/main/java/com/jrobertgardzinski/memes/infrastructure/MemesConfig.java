package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CastVote;
import com.jrobertgardzinski.memes.application.ListMemes;
import com.jrobertgardzinski.memes.application.MakeThumbnail;
import com.jrobertgardzinski.memes.application.MemeContentIndex;
import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.MemeEvents;
import com.jrobertgardzinski.memes.application.PublishMeme;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.application.PublicationLog;
import com.jrobertgardzinski.memes.application.RankMemes;
import com.jrobertgardzinski.memes.application.SearchMemesByTag;
import com.jrobertgardzinski.memes.application.TagMeme;
import com.jrobertgardzinski.memes.application.TagRepository;
import com.jrobertgardzinski.memes.application.ShowMemeVote;
import com.jrobertgardzinski.memes.application.ViewMeme;
import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.config.ImageLimits;
import com.jrobertgardzinski.memes.config.PurgeRule;
import com.jrobertgardzinski.memes.config.RateLimit;
import com.jrobertgardzinski.memes.config.TagLimits;
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
    com.jrobertgardzinski.memes.application.ServeMeme serveMeme(
            MemeRepository repository,
            com.jrobertgardzinski.memes.application.ObjectStore objectStore,
            com.jrobertgardzinski.memes.application.ImageEncoder imageEncoder) {
        return new com.jrobertgardzinski.memes.application.ServeMeme(repository, objectStore, imageEncoder);
    }

    @Bean
    RateLimit uploadRate(@Value("${memes.upload.rate-limit-per-minute:12}") int perMinute) {
        return new RateLimit(perMinute);
    }

    @Bean
    com.jrobertgardzinski.memes.application.FlagMeme flagMeme(
            MemeRepository memeRepository, com.jrobertgardzinski.memes.application.ContentFlags contentFlags) {
        return new com.jrobertgardzinski.memes.application.FlagMeme(memeRepository, contentFlags);
    }

    @Bean
    com.jrobertgardzinski.memes.application.DeleteMeme deleteMeme(
            MemeRepository memeRepository, VoteRepository voteRepository, MemeContentIndex contentIndex,
            TagRepository tagRepository, com.jrobertgardzinski.memes.application.MemeEvents memeEvents) {
        return new com.jrobertgardzinski.memes.application.DeleteMeme(
                memeRepository, voteRepository, contentIndex, tagRepository, memeEvents);
    }

    @Bean
    TagLimits tagLimits(@Value("${memes.tags.max-per-meme:8}") int maxPerMeme) {
        return new TagLimits(maxPerMeme);
    }

    @Bean
    TagMeme tagMeme(MemeRepository repository, TagRepository tagRepository, TagLimits tagLimits) {
        return new TagMeme(repository, tagRepository, tagLimits);
    }

    @Bean
    SearchMemesByTag searchMemesByTag(MemeRepository repository, TagRepository tagRepository) {
        return new SearchMemesByTag(repository, tagRepository);
    }

    @Bean
    ListMemes listMemes(MemeRepository repository) {
        return new ListMemes(repository);
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
    PurgeRule defaultMemesPurgeRule(@Value("${memes.purge.memes:DELETE}") String rule) {
        return PurgeRule.parse(rule);
    }

    @Bean
    PurgeUserContent purgeUserContent(MemeRepository memeRepository, VoteRepository voteRepository,
                                      MemeContentIndex contentIndex, TagRepository tagRepository,
                                      MemeEvents memeEvents, PurgeRule defaultMemesPurgeRule) {
        return new PurgeUserContent(memeRepository, voteRepository, contentIndex, tagRepository,
                memeEvents, defaultMemesPurgeRule);
    }

    @Bean
    RankMemes rankMemes(VoteRepository voteRepository, PublicationLog publicationLog, java.time.Clock clock) {
        return new RankMemes(voteRepository, publicationLog, clock);
    }

    @Bean
    java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }
}

