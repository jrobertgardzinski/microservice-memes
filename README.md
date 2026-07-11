# microservice-memes

> 👋 **Welcome, and thanks for looking!** This is a meme gallery that looks simple from the
> browser and is deliberately grown-up underneath. The five specs below are **executable** —
> Cucumber drives them over real HTTP in every build — so everything they describe is verified,
> not aspirational:
> [upload](./memes-infrastructure/src/test/resources/features/upload-meme.feature) ·
> [vote](./memes-infrastructure/src/test/resources/features/vote-meme.feature) ·
> [tag](./memes-infrastructure/src/test/resources/features/tag-meme.feature) ·
> [moderate](./memes-infrastructure/src/test/resources/features/moderate-meme.feature) ·
> [admin purge policy](./memes-infrastructure/src/test/resources/features/admin-purge-policy.feature)

## Highlights — what's worth a closer look

- **The framework is a detail**: Spring Boot lives in **one module of seven**; domain, use cases,
  image processing and tags compile and test without it.
- **Upload pipeline doubles as a security boundary**: every image is re-encoded (a crafted JPEG
  with GPS EXIF goes in, a clean PNG comes out — there's a test), bounded by size limits and a
  per-uploader rate limit (429 + `Retry-After`).
- **Content-hash dedup that survives a race**: two concurrent uploads of the same bytes yield
  exactly one meme — an atomic *claim* (unique constraint in Postgres), saving only after the
  claim is won; pinned by a two-thread test.
- **Image bytes behind a port**: one `ObjectStore` port, three adapters (DB blob / filesystem /
  S3-MinIO), switched by env — the hexagonal promise kept for real.
- **Graceful degradation**: WebP is encoded by a sidecar service and cached once per meme; if the
  encoder is down you get PNG — quality degrades, availability doesn't.
- **Identity done right**: reads are public, writes need a bearer token confirmed against
  `microservice-security`; the author is the **confirmed identity**, never a request field; a
  privileged role without completed MFA is demoted at the gate (fail-closed MFA floor).
- **A real distributed flow**: this service is a participant in the account-deletion saga —
  the leaver's content follows a configurable rule per axis (`DELETE` / `ANONYMIZE_AUTHOR` /
  `KEEP_POPULAR_ANONYMIZED:n`), the leaver's own choice wins, and their votes are always
  retracted; confirmations flow back over Kafka.
- **Contracts, not hope**: Pact pins both what this service reads from the saga command
  (message pact) and from `GET /me` (HTTP pact); event envelopes carry a version and evolve
  additively.
- **Proven at every level**: unit → Cucumber black-box over HTTP → a Playwright suite driving
  the gallery in a real Chromium → whole-stack smoke; metrics, correlation-id logs and
  distributed traces out of the box (one upload = one trace across memes and security).

Part of a [portfolio of microservices](https://github.com/jrobertgardzinski) exploring the same
architecture across six frameworks; this one plays the Spring Boot flavour.

---

A meme service: upload images, serve them optimised for the browser (with on-demand thumbnails and
content-hash deduplication) and vote ("hot" ranking); comment threads moved to
`microservice-comments`, voting semantics come from the shared `voting` library. More monolithic than the split
libraries of `microservice-security`, but still layered into small modules. **Framework: Spring
Boot** — the third flavour in the portfolio after Micronaut (`microservice-security`, hexagonal)
and Quarkus (`microservice-email`, BCE).

## Modules

- **memes-domain** — the entities: `Meme`, `Comment`, `RankedMeme`, `VoteDirection`. Pure Java.
- **memes-config** — typed, validated configuration values (`ImageLimits`, `ThumbnailSize`,
  `ContentPurgePolicy` — what an account deletion does to the leaver's content). Pure Java.
- **memes-image** — `WebImageOptimizer`: re-encodes any ImageIO-readable image (BMP, JPEG, …) to a
  browser-friendly PNG (which also drops EXIF), bounded by the configured limits. Pure JDK
  (`java.desktop`).
- **memes-application** — use cases (`PublishMeme`, `ViewMeme`, `MakeThumbnail`, `AddComment`,
  `ListComments`, `CastVote`, `RankMemes`) + the ports (`MemeRepository`, `CommentRepository`,
  `VoteRepository`, `MemeContentIndex`). No framework.
- **memes-ui** — the gallery UI: React + TypeScript + Material UI, built by Vite through
  frontend-maven-plugin (own pinned Node) and packed as `META-INF/resources`, so the service jar
  serves it at `/`. UI development: `cd memes-ui && npm run dev` (proxies `/memes` to :8083).
- **memes-infrastructure** — the Spring Boot app: web boundaries (`MemeController`,
  `CommentController`, `VoteController`), the sign-in gate (`RequireSignInFilter` +
  `HttpSecurityAuthenticationGate`, which confirms bearer tokens against
  `microservice-security`'s `GET /me`), in-memory adapters. Cucumber features (`src/test/resources/features/`) document
  the flows; results feed Allure.

## Security integration

Browsing is public; **contributing requires signing in**. Every `POST` under `/memes` must carry
`Authorization: Bearer <access token issued by microservice-security>`; the gate confirms it via
`GET /me` (config: `security.url` / `SECURITY_URL`) and the confirmed identity becomes e.g. the
comment's author — the request body cannot impersonate anyone. Anonymous writes get
`401 {"status": "SIGN_IN_REQUIRED"}`.

## Account deletion (the saga's memes side)

`PURGE_USER_CONTENT` commands arrive over Kafka. What happens to the leaver's content is a
**rule per axis** (their memes / their comments), decided in two places: the deployment default
(`PURGE_MEMES_POLICY` / `PURGE_COMMENTS_POLICY`) and — taking precedence — **the leaver's own
choice from the deletion wizard**, carried inside the saga command:

| rule | effect |
|------|--------|
| `DELETE` | the content disappears (a meme takes its whole comment thread and votes along) |
| `ANONYMIZE_AUTHOR` | the content stays, authored by "deleted account" |
| `KEEP_POPULAR_ANONYMIZED:<n>` | items with score ≥ n survive anonymised (the community earned them); the rest is deleted |

Defaults: memes `DELETE`, comments `ANONYMIZE_AUTHOR`. Votes the leaver cast are always
retracted — identity-keyed data has no policy escape hatch. Unparseable rules in a command fall
back to the defaults (logged), never wedging the saga.

## Contract

```
GET  /                          the gallery UI (single-file React, no build step)

# public reads
GET  /memes                     -> 200 [ { "id" }, ... ]  (newest first)
GET  /memes/{id}                -> 200 image/png (optimised bytes) | 404
GET  /memes/{id}/thumbnail      -> 200 image/png (small preview) | 404
GET  /memes/{id}/comments       -> 200 [ { "id", "author", "text" }, ... ]
GET  /memes/hot                 -> 200 [ { "memeId", "score" }, ... ]  (highest score first)

# writes: Authorization: Bearer <security access token>, else 401 SIGN_IN_REQUIRED
POST /memes                     multipart/form-data, field "file"
                                -> 201 { "id": "..." }, Location: /memes/{id}
                                   (uploading the same image twice returns the existing id)
POST /memes/{id}/comments       { "text": "..." }               -> 201 { "id": "..." } | 400 | 404
                                   (author = the signed-in identity)
POST /memes/{id}/votes          { "direction": "UP" | "DOWN" }  -> 200 { "score": n } | 400 | 404
POST /memes/{id}/comments/{cid}/votes   same body/answers — votes on a comment

One vote per user per meme/comment: voting again replaces your previous vote (never stacks);
comment listings include each comment's current score.
```

## Run & test

```bash
../mvnw -f pom.xml test                              # all module tests (JDK 25 + Spring Boot 3.5)
../mvnw -f pom.xml -pl memes-infrastructure spring-boot:run   # run the service (port 8083)
```

## Documentation — the living contract

The behaviour contract is the Cucumber features in
`memes-infrastructure/src/test/resources/features/` (upload, vote, tag, moderate,
admin-purge-policy) — black-box over HTTP, green in every build. The workspace-level tooling
(`../create-documentation.sh`) folds this repo into the shared surfaces: the
Ubiquitous-Language glossary (`../docs/glossary/glossary.md`, scanned from the domain,
application and infrastructure layers here) and the aggregated Allure summary
(`../allure-summary.md`). Run it from the workspace root after `mvn test` to regenerate both.

## Where the image bytes live (`memes.blob-store` / env `MEMES_BLOB_STORE`)

Metadata is always in the database; the bytes sit behind the `ObjectStore` port with three
interchangeable adapters — `db` (default: a blob table, durability matches the metadata),
`filesystem` (`memes.blob-dir`), and `s3` (`memes.s3.endpoint/bucket/access-key/secret-key`,
`path-style` on by default — serves MinIO in the compose stack and real S3 alike; the bucket
is created at startup). Exactly one adapter bean exists per mode, pinned by
`BlobStoreSelectionTest`.

> Testcontainers note: the S3 adapter's round-trip test runs against a real MinIO and skips
> without docker. On Docker Engine ≥ 29 the bundled docker-java client may refuse to negotiate
> (`client version 1.32 is too old`) — fix once per machine with
> `echo "api.version=1.44" > ~/.docker-java.properties`.
