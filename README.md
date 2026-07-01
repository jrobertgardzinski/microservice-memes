# microservice-memes

A meme service: upload images, serve them optimised for the browser, (later) comment and vote. More
monolithic than the split libraries of `microservice-security`, but still layered into small
modules. **Framework: Spring Boot** — the third flavour in the portfolio after Micronaut
(`microservice-security`, hexagonal) and Quarkus (`microservice-email`, BCE).

## Modules

- **memes-domain** — the `Meme` entity. Pure Java.
- **memes-image** — `WebImageOptimizer`: re-encodes any ImageIO-readable image (BMP, JPEG, …) to a
  browser-friendly PNG (which also drops EXIF). Pure JDK (`java.desktop`).
- **memes-application** — use cases (`PublishMeme`, `ViewMeme`) + the `MemeRepository` port. No
  framework.
- **memes-infrastructure** — the Spring Boot app: `MemeController` (web boundary) + an in-memory
  repository, wiring the framework-free use cases as beans.

## Contract

```
POST /memes            multipart/form-data, field "file"  -> 201 { "id": "..." }, Location: /memes/{id}
GET  /memes/{id}                                          -> 200 image/png (optimised bytes) | 404
```

## Run & test

```bash
../mvnw -f pom.xml test                              # all module tests (JDK 25 + Spring Boot 3.5)
../mvnw -f pom.xml -pl memes-infrastructure spring-boot:run   # run the service (port 8083)
```
