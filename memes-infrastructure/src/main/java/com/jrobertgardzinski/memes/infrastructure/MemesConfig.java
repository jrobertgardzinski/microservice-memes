package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.observation.Observations;
import com.jrobertgardzinski.memes.system.votes.CastVote;
import com.jrobertgardzinski.memes.system.core.ListMemes;
import com.jrobertgardzinski.memes.system.core.MakeThumbnail;
import com.jrobertgardzinski.memes.domain.core.MemeContentIndex;
import com.jrobertgardzinski.memes.domain.core.MemeRepository;
import com.jrobertgardzinski.memes.domain.core.MemeEvents;
import com.jrobertgardzinski.memes.system.core.PublishMeme;
import com.jrobertgardzinski.memes.system.erasure.PurgeUserContent;
import com.jrobertgardzinski.memes.system.votes.RankMemes;
import com.jrobertgardzinski.memes.system.core.SearchMemesByTag;
import com.jrobertgardzinski.memes.system.tags.TagMeme;
import com.jrobertgardzinski.memes.domain.core.TagRepository;
import com.jrobertgardzinski.memes.system.votes.ShowMemeScores;
import com.jrobertgardzinski.memes.system.votes.ShowMemeVote;
import com.jrobertgardzinski.memes.system.core.ViewMeme;
import com.jrobertgardzinski.memes.domain.core.VoteRepository;
import com.jrobertgardzinski.memes.config.image.ImageLimits;
import com.jrobertgardzinski.purge.PurgeRule;
import com.jrobertgardzinski.memes.config.core.RateLimit;
import com.jrobertgardzinski.memes.config.tags.TagLimits;
import com.jrobertgardzinski.memes.config.core.ThumbnailSize;
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
    WebImageOptimizer webImageOptimizer(ImageLimits imageLimits,
                                        @Value("${memes.decode.concurrency:3}") int decodeConcurrency) {
        // the guard is infrastructure (a JVM-wide semaphore sized by an env dial), so it wraps
        // the pure optimizer here — the memes-image module stays free of concurrency policy
        return new ConcurrencyGuardedImageOptimizer(new WebImageOptimizer(imageLimits), imageLimits,
                decodeConcurrency, java.time.Duration.ofSeconds(5));
    }

    @Bean
    UploadAdmission uploadAdmission(
            @Value("${memes.upload.concurrency:8}") int uploadConcurrency) {
        // Sized WITH the container's memory, not guessed: 8 x the 10 MB multipart ceiling is 80 MB
        // of raw uploads at worst, which fits beside the decode budget inside the heap that
        // k8s/base/memes.yaml grants. It used to be Tomcat's default thread count — 200 — which at
        // the same 10 MB is 2000 MB against a ~1075 MiB heap.
        return new UploadAdmission(uploadConcurrency, java.time.Duration.ofSeconds(5));
    }

    @Bean
    ThumbnailSize thumbnailSize(@Value("${memes.image.thumbnail-max-dimension:256}") int maxDimension) {
        return new ThumbnailSize(maxDimension);
    }

    @Bean
    MakeThumbnail makeThumbnail(MemeRepository repository,
                                com.jrobertgardzinski.memes.domain.core.ObjectStore objectStore,
                                WebImageOptimizer optimizer, ThumbnailSize thumbnailSize) {
        return new MakeThumbnail(repository, objectStore, optimizer, thumbnailSize);
    }

    @Bean
    PublishMeme publishMeme(WebImageOptimizer optimizer, MemeRepository repository, MemeContentIndex contentIndex,
                            com.jrobertgardzinski.memes.domain.core.ObjectStore objectStore) {
        return new PublishMeme(optimizer, repository, contentIndex, objectStore);
    }

    @Bean
    com.jrobertgardzinski.authors.AuthorDirectory authorDirectory(
            @Value("${security.url}") String securityUrl,
            @Value("${memes.author-names.cache-seconds:60}") long cacheSeconds,
            java.time.Clock clock) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("memes.author-names");
        return com.jrobertgardzinski.authors.SecurityAuthorDirectory.overHttp(securityUrl,
                new com.fasterxml.jackson.databind.ObjectMapper(), java.time.Duration.ofSeconds(cacheSeconds), clock,
                failure -> log.warn("author names unavailable, showing content without them: {}", failure.toString()));
    }

    @Bean
    ViewMeme viewMeme(MemeRepository repository) {
        return new ViewMeme(repository);
    }

    @Bean
    com.jrobertgardzinski.memes.system.core.ServeMeme serveMeme(
            MemeRepository repository,
            com.jrobertgardzinski.memes.domain.core.ObjectStore objectStore,
            com.jrobertgardzinski.memes.domain.core.ImageEncoder imageEncoder) {
        return new com.jrobertgardzinski.memes.system.core.ServeMeme(repository, objectStore, imageEncoder);
    }

    @Bean
    RateLimit uploadRate(@Value("${memes.upload.rate-limit-per-minute:12}") int perMinute,
                         java.time.Clock clock) {
        return new RateLimit(perMinute, clock);   // the shared clock bean, same as the hot ranking
    }

    @Bean
    com.jrobertgardzinski.memes.system.core.FlagMeme flagMeme(
            MemeRepository memeRepository, com.jrobertgardzinski.memes.domain.core.ContentFlags contentFlags) {
        return new com.jrobertgardzinski.memes.system.core.FlagMeme(memeRepository, contentFlags);
    }

    @Bean
    com.jrobertgardzinski.memes.system.core.DeleteMeme deleteMeme(
            MemeRepository memeRepository, VoteRepository voteRepository, MemeContentIndex contentIndex,
            TagRepository tagRepository, com.jrobertgardzinski.memes.domain.core.MemeEvents memeEvents,
            org.springframework.transaction.support.TransactionTemplate tx) {
        // the transactional decorator, not the plain use case: the DB part of a teardown is atomic
        // (the use case itself stays framework-free — the seam lives here, in infrastructure)
        return new TransactionalDeleteMeme(
                memeRepository, voteRepository, contentIndex, tagRepository, memeEvents, tx);
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
    ShowMemeScores showMemeScores(MemeRepository memeRepository, VoteRepository voteRepository) {
        return new ShowMemeScores(memeRepository, voteRepository);
    }

    @Bean
    com.jrobertgardzinski.memes.config.erasure.ErasureTolerance erasureTolerance(
            @Value("${memes.erasure.stuck-after-seconds:1800}") long stuckAfterSeconds) {
        return new com.jrobertgardzinski.memes.config.erasure.ErasureTolerance(
                java.time.Duration.ofSeconds(stuckAfterSeconds));
    }

    @Bean
    com.jrobertgardzinski.memes.system.erasure.WatchErasureBacklog watchErasureBacklog(
            com.jrobertgardzinski.memes.domain.erasure.MemeErasure erasure,
            com.jrobertgardzinski.memes.config.erasure.ErasureTolerance tolerance,
            com.jrobertgardzinski.observation.Observations<com.jrobertgardzinski.memes.domain.erasure.Observation> observations,
            java.time.Clock clock) {
        return new com.jrobertgardzinski.memes.system.erasure.WatchErasureBacklog(
                erasure, tolerance, observations, clock);
    }

    @Bean
    PurgeRule defaultMemesPurgeRule(@Value("${memes.purge.memes:DELETE}") String rule) {
        return PurgeRule.parse(rule);
    }

    @Bean
    PurgeUserContent purgeUserContent(MemeRepository memeRepository,
                                      com.jrobertgardzinski.memes.domain.erasure.MemeErasure erasure,
                                      VoteRepository voteRepository,
                                      MemeContentIndex contentIndex, TagRepository tagRepository,
                                      MemeEvents memeEvents,
                                      com.jrobertgardzinski.memes.domain.erasure.PurgePolicyOverride override,
                                      PurgeRule defaultMemesPurgeRule,
                                      org.springframework.transaction.support.TransactionTemplate tx) {
        // transactional decorator — a purge that dies halfway must not strand half the leaver's memes
        return new TransactionalPurgeUserContent(memeRepository, erasure, voteRepository, contentIndex,
                tagRepository, memeEvents, override, defaultMemesPurgeRule, tx);
    }

    /**
     * The saga's reversible step and its inverse. Neither gets a transactional decorator of its own:
     * both are driven exclusively by {@code PurgeCommandsListener}, which already opens ONE
     * transaction per command so the work and the outbox row that reports it commit together —
     * a second template inside it would only nest a participation in the same transaction.
     */
    @Bean
    com.jrobertgardzinski.memes.system.erasure.MarkUserContentForErasure markUserContentForErasure(
            com.jrobertgardzinski.memes.domain.erasure.MemeErasure erasure, java.time.Clock clock) {
        return new com.jrobertgardzinski.memes.system.erasure.MarkUserContentForErasure(erasure, clock);
    }

    @Bean
    com.jrobertgardzinski.memes.system.erasure.RestoreUserContent restoreUserContent(
            com.jrobertgardzinski.memes.domain.erasure.MemeErasure erasure) {
        return new com.jrobertgardzinski.memes.system.erasure.RestoreUserContent(erasure);
    }


    @Bean
    RankMemes rankMemes(VoteRepository voteRepository, java.time.Clock clock) {
        return new RankMemes(voteRepository, clock);
    }

    @Bean
    java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }

    // ---- the application services: the bridge each controller calls, mapped onto beans ----

    @Bean
    com.jrobertgardzinski.memes.application.core.MemeService memeService(
            PublishMeme publishMeme, ListMemes listMemes, SearchMemesByTag searchMemesByTag,
            com.jrobertgardzinski.memes.system.core.ServeMeme serveMeme, MakeThumbnail makeThumbnail,
            ViewMeme viewMeme, com.jrobertgardzinski.memes.system.core.FlagMeme flagMeme,
            com.jrobertgardzinski.memes.system.core.DeleteMeme deleteMeme,
            com.jrobertgardzinski.memes.domain.core.ContentFlags contentFlags, RateLimit uploadRate,
            UploadAdmission uploadAdmission) {
        return new com.jrobertgardzinski.memes.application.core.MemeService(publishMeme, listMemes,
                searchMemesByTag, serveMeme, makeThumbnail, viewMeme, flagMeme, deleteMeme, contentFlags,
                uploadRate, uploadAdmission);
    }

    @Bean
    com.jrobertgardzinski.memes.application.votes.VoteService voteService(CastVote castVote,
            ShowMemeVote showMemeVote, RankMemes rankMemes, ShowMemeScores showMemeScores) {
        return new com.jrobertgardzinski.memes.application.votes.VoteService(castVote, showMemeVote, rankMemes,
                showMemeScores);
    }

    @Bean
    com.jrobertgardzinski.memes.application.tags.TagService tagService(TagMeme tagMeme, TagRepository tagRepository) {
        return new com.jrobertgardzinski.memes.application.tags.TagService(tagMeme, tagRepository);
    }

    @Bean
    com.jrobertgardzinski.memes.application.erasure.PurgePolicyService purgePolicyService(
            com.jrobertgardzinski.memes.domain.erasure.PurgePolicyOverride override, PurgeRule defaultMemesPurgeRule) {
        return new com.jrobertgardzinski.memes.application.erasure.PurgePolicyService(override, defaultMemesPurgeRule);
    }
}
