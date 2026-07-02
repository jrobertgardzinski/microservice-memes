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
- **Autoryzacja przez microservice-security** — odczyty publiczne, POST-y pod `/memes` wymagają
  Bearer tokena potwierdzanego przez `GET /me` security (`RequireSignInFilter` + brama HTTP;
  w testach stub). Autor komentarza = potwierdzona tożsamość, nie pole z requesta.
- **UI galerii** — moduł `memes-ui`: React + TypeScript + Material UI (Vite przez
  frontend-maven-plugin, dist w jarze jako `META-INF/resources`); logowanie/rejestracja/
  weryfikacja przez security (CORS), upload/komentarze/głosy po zalogowaniu.
- **Jeden głos na użytkownika** (mem i komentarz) — ponowny głos zastępuje poprzedni, nie
  kumuluje się; **głosowanie na komentarze** (`POST .../comments/{id}/votes`), listing komentarzy
  niesie score.

- **Saga usuwania konta** — `PurgeUserContent` na komendę `PURGE_USER_CONTENT` z Kafki
  (memy usera znikają z całymi wątkami komentarzy i głosami; jego komentarze gdzie indziej
  zostają jako „deleted account"; wszystkie jego głosy wycofane); potwierdzenie na
  `memes-events`. Memy mają od teraz autora (tożsamość z security przy uploadzie).

## Otwarte — najbliższe (małe moduły, "à la security")
- **Tagi + wyszukiwanie** — moduł `memes-tags`.
- Ranking "hot" z czasem (Reddit-like decay) zamiast czystego score.
- **EXIF** — re-enkodowanie do PNG już zrzuca EXIF; udokumentować/przetestować jawnie.
- **Rate-limit uploadu**, **flaga NSFW / moderacja** (moderator = rola po stronie security — czeka
  na RBAC tam).
- **Dedup pod współbieżnością** — `PublishMeme` ma check-then-act na indeksie treści; przy dwóch
  równoczesnych uploadach tego samego obrazka może powstać osierocony wpis w repo (nieszkodliwe,
  ale do domknięcia przy realnej persystencji).

## Otwarte — infra
- **Realna persystencja** — bajty do object storage (S3/MinIO), metadane do bazy (JPA + Postgres).
- **WebP** zamiast PNG dla mniejszych plików (wymaga enkodera spoza JDK, np. imageio-webp / libwebp).
- **Dokumentacja jak w security** — glosariusz już skanuje domain/application/infrastructure warstwy;
  ewentualnie cucumber-kontrakt.
