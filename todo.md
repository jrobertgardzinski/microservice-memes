# TODO — microservice-memes

Only open items. History = git log.

## Zrobione (walking skeleton)
- Multi-module Spring Boot (domain / config / image / application / infrastructure).
- Upload obrazka → optymalizacja do PNG (BMP→PNG, ImageIO) → zapis → serwowanie.
- **Miniatury** — `GET /memes/{id}/thumbnail`, generowane na żądanie (`MakeThumbnail`).
- **Dedup po hashu** — SHA-256 bajtów po optymalizacji (`MemeContentIndex`); drugi upload tego
  samego obrazka zwraca istniejące id.
- **Komentarze** — `AddComment`/`ListComments` + REST + cucumber.
- **Głosowanie na memy** — `CastVote`/`RankMemes`, ranking `GET /memes/hot` + cucumber.
- Testy: unit (image/config/application), MockMvc, cucumber+Allure. Zielone na JDK 25 +
  Spring Boot 3.5.

## Otwarte — najbliższe (małe moduły, "à la security")
- **Tagi + wyszukiwanie** — moduł `memes-tags`.
- **Głosowanie na komentarze** — dziś głosuje się tylko na memy; ranking "hot" z czasem
  (Reddit-like decay) zamiast czystego score.
- **EXIF** — re-enkodowanie do PNG już zrzuca EXIF; udokumentować/przetestować jawnie.
- **Rate-limit uploadu**, **flaga NSFW / moderacja**.
- **Dedup pod współbieżnością** — `PublishMeme` ma check-then-act na indeksie treści; przy dwóch
  równoczesnych uploadach tego samego obrazka może powstać osierocony wpis w repo (nieszkodliwe,
  ale do domknięcia przy realnej persystencji).

## Otwarte — infra
- **Realna persystencja** — bajty do object storage (S3/MinIO), metadane do bazy (JPA + Postgres).
- **WebP** zamiast PNG dla mniejszych plików (wymaga enkodera spoza JDK, np. imageio-webp / libwebp).
- **Autoryzacja** — kto może wrzucać/moderować.
- **Dokumentacja jak w security** — glosariusz już skanuje domain/application/infrastructure warstwy;
  ewentualnie cucumber-kontrakt.
