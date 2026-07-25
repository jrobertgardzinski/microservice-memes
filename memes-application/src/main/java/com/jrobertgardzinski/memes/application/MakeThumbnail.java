package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ThumbnailSize;
import com.jrobertgardzinski.memes.image.InvalidImageException;
import com.jrobertgardzinski.memes.image.OptimizedImage;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;

import java.util.Optional;

/**
 * Makes a thumbnail (small PNG) of a stored meme on demand. Empty if there is no such meme.
 *
 * <p>Cached in the {@link ObjectStore} under {@code {id}.thumb}, the same pattern as ServeMeme's
 * WebP variant: the FIRST request decodes the full image (under the infrastructure's decode
 * semaphore — the expensive, permit-holding step) and caches the result; every later request is
 * served straight from the store — no decode, no permit, just one indexed existence check on the
 * meme row. Before this, EVERY thumbnail request decoded the full image, so a burst of gallery
 * scrolls could saturate the semaphore and feed readers 429s. A thumbnail is immutable per id
 * (ids never return to circulation), so the cache needs no invalidation beyond the delete sweep
 * in the repository adapter — but it does need that existence check, because a crash can strand
 * a variant no sweep will ever reach (see {@link #execute}).
 */
public class MakeThumbnail {

    // JDK System.Logger, not SLF4J — this module is deliberately framework-free
    private static final System.Logger LOG = System.getLogger(MakeThumbnail.class.getName());

    private final MemeRepository memeRepository;
    private final ObjectStore objects;
    private final WebImageOptimizer optimizer;
    private final ThumbnailSize thumbnailSize;

    public MakeThumbnail(MemeRepository memeRepository, ObjectStore objects,
                         WebImageOptimizer optimizer, ThumbnailSize thumbnailSize) {
        this.memeRepository = memeRepository;
        this.objects = objects;
        this.optimizer = optimizer;
        this.thumbnailSize = thumbnailSize;
    }

    public Optional<byte[]> execute(String memeId) {
        String thumbKey = memeId + ".thumb";
        // A cache hit is served only for a meme that still EXISTS. The sweeps (in-transaction +
        // after-commit in JdbcMemeRepository) and the post-put re-check below cover every
        // interleaving in which both parties survive — but not a CRASH, and the window they leave
        // open is the ugly kind: a JVM that dies between the put and the re-check leaves a
        // .thumb of a meme that is gone, no later delete will ever come for it, and a cache-only
        // hit would keep serving a deleted person's picture forever. (ServeMeme's .webp variant
        // has no such window: it reaches the store only through find(), which gates on the row.)
        //
        // The check is exists(), not find(): one indexed "SELECT 1 FROM memes WHERE id = ?" that
        // loads no blob. Measured on the DB store (H2, warm): ~21us for the exists, ~12us for the
        // blob read it joins, ~609us for the whole served cache hit — the gate costs about 3.5%
        // of a hit it makes safe. At that price tightness beats statistics: a sampled check
        // (every Nth hit) would keep serving the orphan in between, and a periodic sweep would
        // have to enumerate the store to find one. Ids are never reissued, so a hit that passes
        // this gate can never be a DIFFERENT meme's thumbnail.
        Optional<byte[]> cached = objects.get(thumbKey);
        if (cached.isPresent()) {
            if (memeRepository.exists(memeId)) {
                return cached;
            }
            // self-healing: the orphan the crash left behind goes now, so the next request pays
            // a plain miss (and the store stops carrying a deleted meme's derived image)
            objects.delete(thumbKey);
            LOG.log(System.Logger.Level.INFO, "dropped an orphaned " + thumbKey
                    + " — the meme is gone, so the cached thumbnail outlived it (crash between the"
                    + " cache write and its re-check); it was NOT served");
            return Optional.empty();
        }
        return memeRepository.find(memeId)
                .map(meme -> decodeStored(memeId, meme.data()))
                .map(OptimizedImage::data)
                .map(thumb -> {
                    cacheBestEffort(memeId, thumbKey, thumb);   // decode once, then serve from cache
                    return thumb;
                });
    }

    /**
     * These are STORED bytes, already validated by the optimizer at upload — if they no longer
     * decode, the server's data went bad, not the caller's request. InvalidImageException would
     * ride the web boundary's "bad upload" mapping down to a 400 blaming the caller; re-signal it
     * as the server fault it is (WebErrorHandler's floor answers a 500 with a generic body).
     */
    private OptimizedImage decodeStored(String memeId, byte[] stored) {
        try {
            return optimizer.toPngWithin(stored, thumbnailSize.maxDimension());
        } catch (InvalidImageException corruptedInStore) {
            throw new IllegalStateException(
                    "stored image no longer decodes — data corruption for meme " + memeId,
                    corruptedInStore);
        }
    }

    /**
     * The cache write is an optimisation, never a condition — a store hiccup must cost the NEXT
     * request a re-decode, not this request its response. The post-put {@code exists} re-check
     * mirrors ServeMeme's WebP guard against the delete race: a variant written after the
     * delete's sweeps would otherwise be orphaned forever (no later delete will come for it);
     * together with the deleter's in-transaction and after-commit sweeps every interleaving is
     * covered.
     */
    private void cacheBestEffort(String memeId, String key, byte[] thumb) {
        try {
            objects.put(key, thumb);
            if (!memeRepository.exists(memeId)) {
                objects.delete(key);
                LOG.log(System.Logger.Level.INFO,
                        "meme " + memeId + " was deleted while its thumbnail was being made — removed the freshly cached " + key);
            }
        } catch (RuntimeException cacheMiss) {
            LOG.log(System.Logger.Level.WARNING,
                    "could not cache " + key + " — serving the decoded thumbnail anyway", cacheMiss);
        }
    }
}
