package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.erasure.PurgePolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * The operator's dial: reads and writes the runtime purge-policy override. Every route requires
 * the ADMIN role (as microservice-security reports it); {@link RequireSignInFilter} already
 * refuses anonymous callers on {@code /admin/**}, this controller narrows to admins.
 *
 * <p>What is dialled here loses to a purge command that CARRIES a rule, and decides every purge
 * that arrives without one — see {@code PurgeUserContent}. So this dial is not only a default for
 * clients that express no preference: it is also the fate of every leaver whose client forgot to
 * state theirs (the memes-ui wizard's preselected option used to, P18 poz. 18). Setting it is a
 * decision about other people's content, which is why it is audited.
 */
@RestController
@RequestMapping("/admin/purge-policy")
class AdminController {

    private final PurgePolicyService policy;

    AdminController(PurgePolicyService policy) {
        this.policy = policy;
    }

    @GetMapping
    ResponseEntity<?> current(@RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
            required = false) Set<String> roles) {
        return switch (policy.current(roles)) {
            case PurgePolicyService.Reading.NotAnAdmin notAnAdmin -> refused();
            case PurgePolicyService.Reading.InForce inForce -> ResponseEntity.ok(Map.of(
                    "axis", "memes",
                    "effective", inForce.effective().asText(),
                    "source", inForce.overridden() ? "DB" : "ENV",
                    "envDefault", inForce.envDefault().asText()));
        };
    }

    @PutMapping
    ResponseEntity<?> set(@RequestBody Map<String, String> body,
                          @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER) String caller,
                          @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
                                  required = false) Set<String> roles) {
        return answer(policy.set(body.get("memes"), caller, roles));
    }

    @DeleteMapping
    ResponseEntity<?> clear(@RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER) String caller,
                            @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
                                    required = false) Set<String> roles) {
        return answer(policy.clear(caller, roles));
    }

    private static ResponseEntity<?> answer(PurgePolicyService.Change change) {
        return switch (change) {
            case PurgePolicyService.Change.NotAnAdmin notAnAdmin -> refused();
            case PurgePolicyService.Change.MissingRule missing -> ResponseEntity.badRequest().body(Map.of("status", "MISSING_RULE",
                    "detail", "expected {\"memes\": \"DELETE|ANONYMIZE_AUTHOR|KEEP_POPULAR_ANONYMIZED:n\"}"));
            case PurgePolicyService.Change.InvalidRule invalid -> ResponseEntity.badRequest().body(Map.of("status", "INVALID_RULE",
                    "detail", invalid.detail()));
            case PurgePolicyService.Change.Overridden overridden ->
                    ResponseEntity.ok(Map.of("status", "OVERRIDDEN", "memes", overridden.rule().asText()));
            case PurgePolicyService.Change.Restored restored ->
                    ResponseEntity.ok(Map.of("status", "ENV_DEFAULT_RESTORED", "memes", restored.envDefault().asText()));
        };
    }

    private static ResponseEntity<?> refused() {
        return ResponseEntity.status(403).body(Map.of("status", "NOT_AN_ADMIN",
                "detail", "this dial belongs to administrators"));
    }
}
