#!/bin/bash
# The gallery's browser entry point, end to end: real security (test environment: in-memory
# stores, captured mailbox), real memes and comments (in-memory H2, no Kafka), the Vite dev
# server, then cucumber-js + Playwright. Ports dodge the docker stack (8080/8083/8085).
set -euo pipefail
cd "$(dirname "$0")"

# security lives in the SHARED workspace (../../../shared), the portal neighbours in this one
SEC_JAR=../../../shared/microservice-security/security-infrastructure/target/security-infrastructure-1.0.0-SNAPSHOT.jar
MEMES_JAR=../memes-infrastructure/target/memes-infrastructure-1.0.0-SNAPSHOT.jar
COMMENTS_JAR=../../microservice-comments/target/microservice-comments-1.0.0-SNAPSHOT.jar
COLLECTIONS_JAR=../../microservice-user-collections/target/microservice-user-collections.jar
if [ ! -f "$SEC_JAR" ] || [ ! -f "$MEMES_JAR" ] || [ ! -f "$COMMENTS_JAR" ] || [ ! -f "$COLLECTIONS_JAR" ]; then
    echo "building the service jars first (shared kernel, then the portal reactor closure)..."
    (cd ../../../shared && ./mvnw -q -pl microservice-security/security-infrastructure,voting -am install -DskipTests)
    (cd ../../ && ./mvnw -q -pl microservice-memes/memes-infrastructure,microservice-comments,microservice-user-collections \
        -am package -DskipTests)
fi

cleanup() { kill "${SEC_PID:-}" "${MEMES_PID:-}" "${COMMENTS_PID:-}" "${COLLECTIONS_PID:-}" "${UI_PID:-}" 2>/dev/null || true; }
trap cleanup EXIT

echo "== starting security (test environment, :8180)"
# ...in its IDENTITY-ONLY mode: there is no Kafka here, so the account-deletion saga would sit
# waiting for a portal confirmation that can never arrive. The deletion scenarios pin the
# identity half (wizard, step-up, the account really gone); the content half is proven by the
# purge use cases' own tests and by the live stack's infra-smoke.
MICRONAUT_ENVIRONMENTS=test MICRONAUT_SERVER_PORT=8180 \
    KAFKA_ENABLED=false SECURITY_REGISTRATION_MAX_PER_WINDOW=0 SECURITY_COOKIE_SECURE=false \
    ACCOUNT_DELETION_AWAIT_PORTAL_PURGE=false \
    MICRONAUT_SERVER_CORS_CONFIGURATIONS_UI_ALLOWED_ORIGINS=http://localhost:4300 \
    java -cp "$SEC_JAR:../../../shared/microservice-security/security-infrastructure/target/lib/*" com.jrobertgardzinski.App \
    >/tmp/memes-ui-e2e-security.log 2>&1 &
SEC_PID=$!

echo "== starting memes (in-memory H2, :8183)"
SERVER_PORT=8183 SECURITY_URL=http://localhost:8180 \
    java -jar "$MEMES_JAR" >/tmp/memes-ui-e2e-memes.log 2>&1 &
MEMES_PID=$!

echo "== starting comments (in-memory H2, :8185)"
SERVER_PORT=8185 SECURITY_URL=http://localhost:8180 MEMES_URL=http://localhost:8183 \
    DB_URL='jdbc:h2:mem:comments;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1' \
    DB_USER=sa DB_PASSWORD= \
    COMMENTS_UI_ORIGIN=http://localhost:4300 \
    java -jar "$COMMENTS_JAR" >/tmp/memes-ui-e2e-comments.log 2>&1 &
COMMENTS_PID=$!

echo "== starting user-collections (in-memory H2, :8192) — the favourites star's backend"
COLLECTIONS_PORT=8192 SECURITY_URL=http://localhost:8180 \
    COLLECTIONS_ALLOWED_ORIGINS=http://localhost:4300 \
    java -jar "$COLLECTIONS_JAR" >/tmp/memes-ui-e2e-collections.log 2>&1 &
COLLECTIONS_PID=$!

echo "== starting the Vite dev server (:4300, proxying /memes to :8183)"
MEMES_URL=http://localhost:8183 \
    VITE_SECURITY_URL=http://localhost:8180 VITE_COMMENTS_URL=http://localhost:8185 \
    VITE_COLLECTIONS_URL=http://localhost:8192 \
    npx vite --port 4300 >/tmp/memes-ui-e2e-ui.log 2>&1 &
UI_PID=$!

for url in http://localhost:8180/health http://localhost:8183/memes/hot \
           http://localhost:8185/memes/warmup/comments http://localhost:8192/health http://localhost:4300; do
    for i in $(seq 1 60); do
        curl -sf "$url" >/dev/null && break
        [ "$i" = 60 ] && { echo "FAIL: $url did not come up"; exit 1; }
        sleep 2
    done
done

echo "== driving the gallery through the browser"
SECURITY_URL=http://localhost:8180 MEMES_URL=http://localhost:8183 UI_URL=http://localhost:4300 \
    npx cucumber-js --config e2e/cucumber.mjs
