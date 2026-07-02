package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeContentIndex;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link MemeContentIndex}: maps a SHA-256 of the image bytes to the meme id. */
@Component
class InMemoryMemeContentIndex implements MemeContentIndex {

    private final Map<String, String> idByHash = new ConcurrentHashMap<>();

    @Override
    public Optional<String> findIdByContent(byte[] data) {
        return Optional.ofNullable(idByHash.get(sha256(data)));
    }

    @Override
    public void index(byte[] data, String memeId) {
        idByHash.put(sha256(data), memeId);
    }

    @Override
    public void remove(String memeId) {
        idByHash.values().removeIf(memeId::equals);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always available
        }
    }
}
