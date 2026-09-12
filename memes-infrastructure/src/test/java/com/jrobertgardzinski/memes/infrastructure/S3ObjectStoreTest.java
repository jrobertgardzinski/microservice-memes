package com.jrobertgardzinski.memes.infrastructure;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S3 adapter against a real MinIO (Testcontainers) — the port's contract is a narrow
 * round-trip: put/get/delete by key, absent key = empty. Constructing the store twice proves the
 * create-bucket-at-startup step is idempotent. Skipped where docker is absent; the compose stack
 * exercises the same adapter live.
 */
@Epic("Infrastructure")
@Feature("Object store")
@Testcontainers(disabledWithoutDocker = true)
class S3ObjectStoreTest {

    /**
     * From quay.io, not Docker Hub.
     *
     * <p>{@code docker.io/minio/minio} stopped answering anonymous pulls — "repository does not
     * exist or may require 'docker login'" — so every CI run failed on fetching the image rather
     * than on anything this test is about, and a laptop without a cached copy failed the same way.
     * quay.io serves the identical release; Testcontainers takes the registry from the name, and
     * the tag stays pinned because a floating one would make this suite's result depend on the day.
     */
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
                    // Testcontainers recognises MinIO by the Docker Hub name; saying the two are
                    // the same image is the whole of what this line does
                    .asCompatibleSubstituteFor("minio/minio"));

    static S3ObjectStore store;

    @BeforeAll
    static void connect() {
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .forcePathStyle(true)
                .build();
        store = new S3ObjectStore(s3, "memes", PendingBlobDeletes.none());
        new S3ObjectStore(s3, "memes", PendingBlobDeletes.none());   // second startup against an existing bucket must not throw
    }

    @Test
    @DisplayName("bytes round-trip by key; deletion leaves nothing behind")
    void round_trips() {
        byte[] bytes = "a very good meme".getBytes(StandardCharsets.UTF_8);

        store.put("meme-1", bytes);
        assertArrayEquals(bytes, store.get("meme-1").orElseThrow());

        store.delete("meme-1");
        assertTrue(store.get("meme-1").isEmpty());
    }

    @Test
    @DisplayName("an absent key is empty, not an error")
    void absent_is_empty() {
        assertTrue(store.get("never-uploaded").isEmpty());
    }
}
