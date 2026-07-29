package com.jrobertgardzinski.memes.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two URLs Kubernetes actually calls — asserted over HTTP, which nothing did until now.
 *
 * <p>{@link ListenerHealthProbeGroupsTest} pins WHICH group the listener lamp belongs to and proves
 * the property names really build those groups. What neither it nor anything else touched is the
 * ADDRESS. {@code portal/k8s/base/memes.yaml} names {@code /actuator/health/readiness} and
 * {@code /actuator/health/liveness}, and a perfectly configured group can sit behind a path that
 * answers 404 — the path depends on {@code management.endpoints.web.exposure.include}, on the
 * actuator base path, and on the framework still serving groups as path segments at all.
 *
 * <p><strong>Why now.</strong> Spring Boot 4 moved the health API to a new package and split the
 * actuator into modules on 2026-07-29. The group configuration came through untouched and the older
 * test proved that; the URL was assumed on both sides of the migration. With k3s next, an assumed
 * probe URL is the sort of thing discovered by a pod that never goes ready.
 *
 * <p><strong>On the management port.</strong> Production serves these on 9083
 * ({@code management.server.port}), the main API on 8083 — this test drops that separation, because
 * MockMvc has no ports at all. So what is proven here is the PATH and the group's contents, not the
 * port split; {@code portal/wait-healthy.sh} and the compose healthcheck cover the port.
 *
 * <p>Asserts the group's own component rather than only the status code: a 200 carrying a
 * differently shaped body would still fail the probe's purpose.
 */
@SpringBootTest(classes = MemesApplication.class,
        properties = {
                "management.endpoint.health.probes.enabled=true",
                "management.endpoint.health.group.readiness.include=readinessState,kafkaListeners",
                "management.endpoint.health.group.liveness.include=livenessState",
                "management.endpoints.web.exposure.include=health,prometheus",
                "management.endpoint.health.show-details=always",
                // MockMvc drives the main context; the real deployment's separate management port is
                // covered by the compose healthcheck instead
                "management.server.port="})
@AutoConfigureMockMvc
class ProbeUrlsTest {

    @Autowired
    MockMvc http;

    @Test
    @DisplayName("/actuator/health/readiness answers, and the listener lamp is in what it answers")
    void the_readiness_url_from_the_manifest_serves_the_lamp() throws Exception {
        http.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.kafkaListeners").exists());
    }

    @Test
    @DisplayName("/actuator/health/liveness answers, and the lamp is deliberately NOT in it")
    void the_liveness_url_from_the_manifest_excludes_the_lamp() throws Exception {
        http.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.kafkaListeners").doesNotExist());
    }

    @Test
    @DisplayName("and the bare /actuator/health the compose stack probes still answers too")
    void the_url_the_compose_healthcheck_uses_answers() throws Exception {
        http.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
