# TODO — microservice-memes

Only open items. History = git log.

## Zrobione (walking skeleton)
- Multi-module Spring Boot (domain / image / application / infrastructure).
- Upload obrazka → optymalizacja do PNG (BMP→PNG, ImageIO) → zapis → serwowanie.
- Testy: image (BMP→PNG), application (publish), infrastructure (MockMvc upload+serve). Zielone na
  JDK 25 + Spring Boot 3.5.

## Otwarte — najbliższe (małe moduły, "à la security")
- **Miniatury/thumbnails** — mniejszy podgląd obok pełnego obrazka.
- **Dedup po hashu** — nie zapisywać dwa razy tego samego obrazka (SHA-256 bajtów).
- **Tagi + wyszukiwanie** — moduł `memes-tags`.
- **Komentarze** — moduł `memes-comments` (dodaj/listuj pod memem).
- **Głosowanie** — moduł `memes-voting` (up/down na memy i komentarze), ranking "hot" (Reddit-like).
- **EXIF** — re-enkodowanie do PNG już zrzuca EXIF; udokumentować/przetestować jawnie.
- **Rate-limit uploadu**, **flaga NSFW / moderacja**.

## Otwarte — infra
- **Realna persystencja** — bajty do object storage (S3/MinIO), metadane do bazy (JPA + Postgres).
- **WebP** zamiast PNG dla mniejszych plików (wymaga enkodera spoza JDK, np. imageio-webp / libwebp).
- **Autoryzacja** — kto może wrzucać/moderować.
- **Dokumentacja jak w security** — glosariusz już skanuje domain/application/infrastructure warstwy;
  ewentualnie cucumber-kontrakt.
