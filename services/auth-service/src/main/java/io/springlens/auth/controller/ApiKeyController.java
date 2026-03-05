package io.springlens.auth.controller;

import io.springlens.auth.entity.ApiKey;
import io.springlens.auth.repository.ApiKeyRepository;
import io.springlens.auth.service.ApiKeyService;
import io.springlens.auth.service.AuditLogService;
import io.springlens.shared.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * API Key management endpoints.
 * ✅ HARDENED: All API keys expire after 90 days to reduce impact of compromise.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/api-keys")
public class ApiKeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyController.class);

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogService auditLogService;

    public ApiKeyController(ApiKeyService apiKeyService, ApiKeyRepository apiKeyRepository,
                            AuditLogService auditLogService) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * List all active API keys (non-expired, non-revoked).
     * ✅ SECURITY: Only returns keys that are currently valid (isActive() = true)
     */
    @GetMapping
    public ResponseEntity<Object> listApiKeys(@PathVariable UUID workspaceId,
                                               @AuthenticationPrincipal Jwt jwt) {
        var keys = apiKeyRepository.findActiveByWorkspaceId(workspaceId);
        return ResponseEntity.ok(Map.of("api_keys", keys.stream().map(k -> Map.of(
                "id", k.getId().toString(),
                "name", k.getName(),
                "key_prefix", k.getKeyPrefix(),
                "project_id", k.getProjectId().toString(),
                "last_used_at", k.getLastUsedAt() != null ? k.getLastUsedAt().toString() : "",
                "expires_at", k.getExpiresAt() != null ? k.getExpiresAt().toString() : "",
                "created_at", k.getCreatedAt().toString())).toList()));
    }

    /**
     * Create a new API key with 90-day default expiration.
     * ✅ SECURITY: Keys are automatically set to expire after 90 days.
     */
    @PostMapping
    public ResponseEntity<Object> createApiKey(@PathVariable UUID workspaceId,
                                                @RequestBody @Valid CreateApiKeyRequest body,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        var result = apiKeyService.createApiKey(workspaceId, body.projectId(), body.name(), userId);

        // ✅ SECURITY: Audit log creation of new API key
        auditLogService.logSuccess(workspaceId, userId, "CREATE", "API_KEY", result.apiKey().getId(),
                String.format("Created API key: %s for project: %s", body.name(), body.projectId()));

        log.info("API key created id={} workspace={} project={} expires={}",
                result.apiKey().getId(), workspaceId, body.projectId(), result.apiKey().getExpiresAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", result.apiKey().getId().toString(),
                "name", result.apiKey().getName(),
                "key_prefix", result.apiKey().getKeyPrefix(),
                "raw_key", result.rawKey(),
                "project_id", result.apiKey().getProjectId().toString(),
                "expires_at", result.apiKey().getExpiresAt().toString(),
                "created_at", result.apiKey().getCreatedAt().toString(),
                "message", "Store this key securely. It will not be shown again. Key expires in 90 days."));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Object> revokeApiKey(@PathVariable UUID workspaceId,
                                                @PathVariable UUID keyId,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        try {
            // Get key details before revocation for audit log
            var keyOpt = apiKeyRepository.findById(keyId);
            String keyName = keyOpt.isPresent() ? keyOpt.get().getName() : "unknown";

            apiKeyService.revokeKey(keyId, workspaceId);

            // ✅ SECURITY: Audit log revocation of API key
            auditLogService.logSuccess(workspaceId, userId, "REVOKE", "API_KEY", keyId,
                    String.format("Revoked API key: %s", keyName));

            return ResponseEntity.noContent().build();
        } catch (SecurityException ex) {
            // ✅ SECURITY: Audit log failed revocation attempt
            auditLogService.logFailure(workspaceId, userId, "REVOKE", "API_KEY", keyId,
                    "Authorization check failed");

            return ResponseEntity.status(403)
                    .body(ErrorResponse.of("FORBIDDEN", ex.getMessage(), null));
        }
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null) return UUID.randomUUID();
        try { return UUID.fromString(jwt.getSubject()); } catch (Exception e) { return UUID.randomUUID(); }
    }

    public record CreateApiKeyRequest(
            @NotBlank String name,
            UUID projectId) {
    }
}
