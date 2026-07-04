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

- **Saga usuwania konta, KONFIGUROWALNA** — `PurgeUserContent` na komendę `PURGE_USER_CONTENT`
  z Kafki; los treści to polityka wdrożeniowa (`ContentPurgePolicy` w memes-config, env
  `PURGE_MEMES_POLICY`/`PURGE_COMMENTS_POLICY`, osie DELETE|ANONYMIZE_AUTHOR). Domyślnie: memy
  znikają z całymi wątkami i głosami, komentarze gdzie indziej zostają jako „deleted account";
  głosy usera wycofywane zawsze. Potwierdzenie na `memes-events`. Memy mają autora (tożsamość
  z security przy uploadzie). Reguły per oś: DELETE | ANONYMIZE_AUTHOR |
  KEEP_POPULAR_ANONYMIZED:n; wybór usera z wizarda w UI nadpisuje default per żądanie.

- **Komentarze wydzielone do `microservice-comments`** (2026-07-02 wieczór): ten serwis trzyma
  memy i głosy na memy (lib `voting`); skasowany mem ogłasza `MEME_DELETED`, a serwis komentarzy
  kasuje wątek.

## Otwarte — najbliższe (małe moduły, "à la security")
- ~~Tagi + wyszukiwanie~~ — ZROBIONE (2026-07-04): moduł `memes-tags` (VO `Tag`: normalizacja
  lowercase/trim, 2..30 znaków [a-z0-9-], pojedyncze myślniki), use case'y `TagMeme` (autor
  kuratorem — REPLACE całego zestawu, limit `TagLimits` z env `memes.tags.max-per-meme:8`,
  403 NOT_THE_AUTHOR) i `SearchMemesByTag` (galeria zawężona tagiem, porządek galerii);
  REST: POST/GET `/memes/{id}/tags`, `GET /memes?tag=`; purge czyści indeks tagów;
  3 scenariusze w tag-meme.feature. UI galerii (pole tagów + filtr) — do zrobienia.
- ~~Ranking hot z czasem~~ — ZROBIONE (2026-07-04): hotness = score/(ageHours+2)^1.5
  (Reddit-like), port `PublicationLog` (store zna czas publikacji; nieznany mem = świeży,
  fail-safe), zwracany score bez zmian — decay tylko porządkuje; GET /memes/hot bez zmiany
  kontraktu. Zegar przez java.time.Clock (bean).
- ~~EXIF~~ — ZROBIONE (2026-07-04): jawny pin — spreparowany JPEG z segmentem APP1 Exif
  ("SecretGPSLocation…") wchodzi, wychodzi PNG bez śladu metadanych.
- **Rate-limit uploadu**, **flaga NSFW / moderacja** (moderator = rola po stronie security — czeka
  na RBAC tam).
- ~~Dedup pod współbieżnością~~ — ZROBIONE (2026-07-04): port `MemeContentIndex` to teraz
  atomowy `claim(data, candidateId)` (putIfAbsent) — przy dwóch równoczesnych uploadach wygrywa
  dokładnie jeden id i nic osieroconego nie jest zapisywane (save dopiero PO wygranym claimie);
  pin: test z dwoma wątkami na jednej bramce.

## Otwarte — infra
- **Default polityki czystki z bazy** — dziś default z env; docelowo nadpisywalny w bazie
  (panel administracyjny), wybór per żądanie już działa.
- **Realna persystencja** — ZROBIONA W RDZENIU (2026-07-04): metadane I bajty w bazie —
  Postgres na stacku (DB_URL), bez DB_URL in-memory H2 w trybie PostgreSQL (dev/testy jeżdżą
  na TYCH SAMYCH adapterach JDBC co produkcja, zero drugiej implementacji); Flyway V1 (memes/
  content_index/meme_tags/meme_votes), claim dedupu = unikalność PK w bazie (wyścig rozstrzyga
  constraint), upserty delete+insert jak w comments. Zweryfikowane live na PG: Flyway, galeria,
  filtr tagiem i bajty obrazka przeżywają restart aplikacji. ZOSTAJE: bajty do object storage
  (S3/MinIO) zamiast bytea, gdy galeria urośnie.
- **WebP** zamiast PNG dla mniejszych plików (wymaga enkodera spoza JDK, np. imageio-webp / libwebp).
- **Dokumentacja jak w security** — glosariusz już skanuje domain/application/infrastructure warstwy;
  ewentualnie cucumber-kontrakt.
