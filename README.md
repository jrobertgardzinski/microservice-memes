# microservice-memes

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
