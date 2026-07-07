# TODO — microservice-memes

Only open items. History = git log.

**Plan pracy z instrukcjami wykonawczymi: [docs/opus-playbook.md](docs/opus-playbook.md)**
(2026-07-07; M0–M2 ZROBIONE — zostaje M3 dokumentacja).

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

## Zrobione (2026-07-04..06 — moderacja, bramy, galeria×security; odnotowane 2026-07-07)
- **Moderacja + NSFW** — role z security (`Caller{email,roles}` przez bramę): MODERATOR/ADMIN
  kasuje cudze memy (`50557b7`, cucumber `f56cc81`, przyciski w galerii `8425e20`, autor swoje
  także z UI `5dcdf70`); **flaga NSFW moderatora rozmywa galerię** (`7ece5d3`: `FlagMeme`/
  `ContentFlags`/`JdbcContentFlags`, `moderate-meme.feature`, blur+odsłona w UI).
- **Offline JWT gate** (`3875410`): `JwtSecurityAuthenticationGate` weryfikuje access token
  po JWKS security (Ed25519) zamiast wołać `/me` — mniej ruchu; kompromis jak w security/todo
  (offline nie widzi logoutu do wygaśnięcia).
- **Galeria × security, pełny łańcuch** — logowanie z MFA (krok kodu `90baddb`), dokończenie
  OAuth wymagającego czynnika (`7c1b37e`), step-up przed delete (`404d9cb`), przyciski social
  z `GET /oauth/providers` (`a269c60`), hint recovery codes (`c18bb3d`), sign-in z Google
  (`ef60d6d`), cichy kontrakt rejestracji (`d9ad4b7`).

## Otwarte — najbliższe (małe moduły, "à la security")
- ~~Tagi + wyszukiwanie~~ — ZROBIONE (2026-07-04): moduł `memes-tags` (VO `Tag`: normalizacja
  lowercase/trim, 2..30 znaków [a-z0-9-], pojedyncze myślniki), use case'y `TagMeme` (autor
  kuratorem — REPLACE całego zestawu, limit `TagLimits` z env `memes.tags.max-per-meme:8`,
  403 NOT_THE_AUTHOR) i `SearchMemesByTag` (galeria zawężona tagiem, porządek galerii);
  REST: POST/GET `/memes/{id}/tags`, `GET /memes?tag=`; purge czyści indeks tagów;
  3 scenariusze w tag-meme.feature. UI ZROBIONE (2026-07-04): czipy tagów w dialogu (klik =
  filtr galerii), edytor "tags, comma-separated" dla zalogowanych (backend autorytetem —
  odmowa NOT_THE_AUTHOR/INVALID_TAG jako komunikat), pasek aktywnego filtra z krzyżykiem.
- ~~Ranking hot z czasem~~ — ZROBIONE (2026-07-04): hotness = score/(ageHours+2)^1.5
  (Reddit-like), port `PublicationLog` (store zna czas publikacji; nieznany mem = świeży,
  fail-safe), zwracany score bez zmian — decay tylko porządkuje; GET /memes/hot bez zmiany
  kontraktu. Zegar przez java.time.Clock (bean).
- ~~EXIF~~ — ZROBIONE (2026-07-04): jawny pin — spreparowany JPEG z segmentem APP1 Exif
  ("SecretGPSLocation…") wchodzi, wychodzi PNG bez śladu metadanych.
- ~~Rate-limit uploadu~~ — ZROBIONE (2026-07-04): `RateLimit` w memes-config (per-uploader, env MEMES_UPLOAD_RATE_LIMIT, default 12/min, 0 wyłącza), 429+Retry-After w POST /memes; unit pin + MockMvc z limitem 1/min.
- ~~Flaga NSFW / moderacja~~ — ZROBIONE (2026-07-05, patrz sekcja wyżej): RBAC w security
  odblokował temat, moderator flaguje, galeria rozmywa.
- ~~Dedup pod współbieżnością~~ — ZROBIONE (2026-07-04): port `MemeContentIndex` to teraz
  atomowy `claim(data, candidateId)` (putIfAbsent) — przy dwóch równoczesnych uploadach wygrywa
  dokładnie jeden id i nic osieroconego nie jest zapisywane (save dopiero PO wygranym claimie);
  pin: test z dwoma wątkami na jednej bramce.

## Otwarte — infra
- ~~Default polityki czystki z bazy~~ — ZROBIONE (2026-07-07): port `PurgePolicyOverride`
  + generyczna tabela `settings` (V4, klucz `purge.memes`), rozstrzyganie wizard > baza > env
  w `PurgeUserContent`; REST `GET/PUT/DELETE /admin/purge-policy` (filtr wymaga zalogowania
  na całym `/admin/**`, kontroler roli ADMIN — 403 NOT_AN_ADMIN); `PurgeRule.asText()`
  (odwrotność parse, round-trip w teście); panel „Admin" w galerii (dial + reset do env).
  Testy: 2 nowe unit w PurgeUserContentTest + `admin-purge-policy.feature` (3 scenariusze).
- **Realna persystencja** — ZROBIONA W RDZENIU (2026-07-04): metadane I bajty w bazie —
  Postgres na stacku (DB_URL), bez DB_URL in-memory H2 w trybie PostgreSQL (dev/testy jeżdżą
  na TYCH SAMYCH adapterach JDBC co produkcja, zero drugiej implementacji); Flyway V1 (memes/
  content_index/meme_tags/meme_votes), claim dedupu = unikalność PK w bazie (wyścig rozstrzyga
  constraint), upserty delete+insert jak w comments. Zweryfikowane live na PG: Flyway, galeria,
  filtr tagiem i bajty obrazka przeżywają restart aplikacji. ZOSTAJE: bajty do object storage
  (S3/MinIO) zamiast bytea, gdy galeria urośnie. SEAM ZROBIONY (2026-07-04): bajty wyjęte
  z wiersza mema za port `ObjectStore` (put/get/delete po id); JdbcMemeRepository trzyma tylko
  metadane i deleguje bajty (zapis/odczyt/kasowanie razem, transakcyjnie). Migracja V2 przenosi
  bajty do `meme_blobs` i usuwa kolumnę `data`. Dwa adaptery: DB-blob (default, bez nowych
  zależności) i FILESYSTEM (`memes.blob-store=filesystem`, `memes.blob-dir`). Zweryfikowane
  na PG (schemat V2) + testy (MockMvc round-trip, FilesystemObjectStoreTest z ochroną przed
  path-traversal). TRZECI ADAPTER S3/MinIO — ZROBIONY (2026-07-07): `S3ObjectStore`
  (awssdk s3, `memes.blob-store=s3`, path-style dla MinIO, bucket tworzony idempotentnie
  na starcie), round-trip na ŻYWYM MinIO (Testcontainers, skip bez dockera; gotcha
  Docker≥29 → `~/.docker-java.properties` w README), MinIO w compose workspace'u + krok
  smoke (obiekt mema widoczny w bucket). PRZY OKAZJI NAPRAWIONY BUG doboru adaptera:
  DbObjectStore był bezwarunkowo `@Primary`, więc `memes.blob-store=filesystem` niczego
  nie przełączał — teraz dokładnie jeden bean per tryb, pin `BlobStoreSelectionTest`.
- ~~WebP~~ — ZROBIONE (2026-07-04): OSOBNY MIKROSERWIS `microservice-image` (Python + Pillow,
  bezframeworkowy jak race-sim: POST /encode?format=webp&quality=, /health). memes: port
  `ImageEncoder` + adapter HTTP (`HttpImageEncoder`, pusty URL/awaria = empty), use case
  `ServeMeme` negocjuje po `Accept: image/webp` — WebP kodowany RAZ i cache'owany w ObjectStore
  pod kluczem {id}.webp, inaczej PNG (enkoder padł = degradacja jakości, nie dostępności).
  Zweryfikowane live: PNG 1790B → WebP 900B, cache w PG. Testy: 4 sim + WebpNegotiationTest.
- **Dokumentacja jak w security** — glosariusz już skanuje domain/application/infrastructure warstwy;
  ewentualnie cucumber-kontrakt.
