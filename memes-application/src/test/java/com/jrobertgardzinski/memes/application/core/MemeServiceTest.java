package com.jrobertgardzinski.memes.application.core;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.memes.application.UploadGate;
import com.jrobertgardzinski.memes.config.core.RateLimit;
import com.jrobertgardzinski.memes.domain.core.ContentFlags;
import com.jrobertgardzinski.memes.domain.erasure.FakeMemeRepository;
import com.jrobertgardzinski.memes.system.core.FlagMeme;
import com.jrobertgardzinski.memes.system.core.ViewMeme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** The decisions the bridge took over from the controllers, on the use cases they reach. */
class MemeServiceTest {

    private static final UserId SOMEBODY = UserId.random();

    private final FakeMemeRepository memes = new FakeMemeRepository();
    private final ContentFlags flags = new ContentFlags() {
        @Override public void setNsfw(String memeId, boolean nsfw) { }
        @Override public boolean isNsfw(String memeId) { return false; }
        @Override public Set<String> nsfwIds() { return Set.of(); }
    };

    private MemeService service(RateLimit rate, UploadGate gate) {
        return new MemeService(null, null, null, null, null, new ViewMeme(memes), new FlagMeme(memes, flags),
                null, flags, rate, gate);
    }

    private static final UploadGate OPEN = new UploadGate() {
        @Override
        public <T> T admit(Supplier<T> upload) {
            return upload.get();
        }
    };

    @Test
    @DisplayName("an uploader over the rate is refused before the gate, and the bytes are never read")
    void the_rate_is_asked_before_the_gate() {
        boolean[] gateAsked = {false};
        boolean[] bytesRead = {false};
        UploadGate watching = new UploadGate() {
            @Override
            public <T> T admit(Supplier<T> upload) {
                gateAsked[0] = true;
                return upload.get();
            }
        };

        RateLimit onePerMinute = new RateLimit(1);
        onePerMinute.tryAcquire(SOMEBODY.toString());   // the minute's one upload is spent

        assertInstanceOf(MemeService.Upload.RateLimited.class,
                service(onePerMinute, watching).publish(SOMEBODY, () -> {
                    bytesRead[0] = true;
                    return new byte[0];
                }));
        assertFalse(gateAsked[0], "a refused uploader must not hold an upload permit");
        assertFalse(bytesRead[0]);
    }

    @Test
    @DisplayName("a flag nobody stated is refused, not read as an unflag")
    void an_unstated_flag_is_not_false() {
        assertInstanceOf(MemeService.Flagging.MissingFlag.class,
                service(new RateLimit(60), OPEN).flag("m1", null, Set.of("MODERATOR")));
    }

    @Test
    @DisplayName("a tag the domain cannot read is an invalid tag, not a server fault")
    void an_unreadable_tag_is_answered() {
        assertInstanceOf(MemeService.Listing.InvalidTag.class,
                service(new RateLimit(60), OPEN).list("not a tag!", 0, 10));
    }

    @Test
    @DisplayName("an admin counts as a moderator")
    void an_admin_moderates() {
        assertEquals(new MemeService.Flagging.NoSuchMeme(),
                service(new RateLimit(60), OPEN).flag("ghost", true, Set.of("ADMIN")));
    }
}
