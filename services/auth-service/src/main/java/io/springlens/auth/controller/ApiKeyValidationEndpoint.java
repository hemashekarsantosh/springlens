package io.springlens.auth.controller;

import io.springlens.auth.service.ApiKeyService;
import io.springlens.shared.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal endpoint used by ingestion-service and other services to validate API keys.
 * Secured to cluster IPs only via Spring Security.
 */
@RestController
@RequestMapping("/internal")
public class ApiKeyValidationEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyValidationEndpoint.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyValidationEndpoint(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping("/validate-key")
    public ResponseEntity<Object> validateKey(@RequestParam("key") String key) {
        log.debug("Validating API key prefix={}", key.substring(0, Math.min(12, key.length())));

        return apiKeyService.validateKey(key)
                .map(apiKey -> ResponseEntity.ok((Object) Map.of(
                        "workspace_id", apiKey.getWorkspaceId().toString(),
                        "project_id", apiKey.getProjectId().toString(),
                        "key_id", apiKey.getId().toString())))
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(ErrorResponse.of("INVALID_KEY", "API key not found or revoked", null)));
    }
}
