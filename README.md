# microservice-memes

A meme service: upload images, serve them optimised for the browser (with on-demand thumbnails and
content-hash deduplication), comment and vote ("hot" ranking). More monolithic than the split
libraries of `microservice-security`, but still layered into small modules. **Framework: Spring
Boot** — the third flavour in the portfolio after Micronaut (`microservice-security`, hexagonal)
and Quarkus (`microservice-email`, BCE).

## Modules

- **memes-domain** — the entities: `Meme`, `Comment`, `RankedMeme`, `VoteDirection`. Pure Java.
- **memes-config** — typed, validated configuration values (`ImageLimits`, `ThumbnailSize`). Pure
  Java.
- **memes-image** — `WebImageOptimizer`: re-encodes any ImageIO-readable image (BMP, JPEG, …) to a
  browser-friendly PNG (which also drops EXIF), bounded by the configured limits. Pure JDK
  (`java.desktop`).
- **memes-application** — use cases (`PublishMeme`, `ViewMeme`, `MakeThumbnail`, `AddComment`,
  `ListComments`, `CastVote`, `RankMemes`) + the ports (`MemeRepository`, `CommentRepository`,
  `VoteRepository`, `MemeContentIndex`). No framework.
- **memes-infrastructure** — the Spring Boot app: web boundaries (`MemeController`,
  `CommentController`, `VoteController`) + in-memory adapters, wiring the framework-free use cases
  as beans. Cucumber features (`src/test/resources/features/`) document the flows; results feed
  Allure.

## Contract

```
POST /memes                     multipart/form-data, field "file"
                                -> 201 { "id": "..." }, Location: /memes/{id}
                                   (uploading the same image twice returns the existing id)
GET  /memes/{id}                -> 200 image/png (optimised bytes) | 404
GET  /memes/{id}/thumbnail      -> 200 image/png (small preview) | 404
POST /memes/{id}/comments       { "author": "...", "text": "..." }  -> 201 { "id": "..." } | 400 | 404
GET  /memes/{id}/comments       -> 200 [ { "id", "author", "text" }, ... ]
POST /memes/{id}/votes          { "direction": "UP" | "DOWN" }      -> 200 { "score": n } | 400 | 404
GET  /memes/hot                 -> 200 [ { "memeId", "score" }, ... ]  (highest score first)
```

## Run & test

```bash
../mvnw -f pom.xml test                              # all module tests (JDK 25 + Spring Boot 3.5)
../mvnw -f pom.xml -pl memes-infrastructure spring-boot:run   # run the service (port 8083)
```
