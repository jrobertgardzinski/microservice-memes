package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.ObjectStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Bytes on disk — the stepping stone from the database to object storage: the same port, keys as
 * file names under {@code memes.blob-dir}. An S3/MinIO adapter is the same shape over a bucket.
 * Active when {@code memes.blob-store=filesystem}; the DB store is the default.
 */
@Component
@ConditionalOnProperty(name = "memes.blob-store", havingValue = "filesystem")
class FilesystemObjectStore implements ObjectStore {

    private final Path root;

    FilesystemObjectStore(org.springframework.core.env.Environment env) {
        this.root = Path.of(env.getProperty("memes.blob-dir", "/var/lib/memes/blobs"));
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create blob dir " + root, e);
        }
    }

    @Override
    public void put(String key, byte[] data) {
        try {
            Files.write(resolve(key), data);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot store " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path path = resolve(key);
        try {
            return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete " + key, e);
        }
    }

    private Path resolve(String key) {
        // keys are UUIDs; refuse anything that could escape the root
        if (key.contains("/") || key.contains("..")) {
            throw new IllegalArgumentException("illegal object key: " + key);
        }
        return root.resolve(key);
    }
}
