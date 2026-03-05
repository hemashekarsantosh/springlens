package io.springlens.notification.controller;

import io.springlens.notification.entity.WebhookConfig;
import io.springlens.notification.repository.WebhookConfigRepository;
import io.springlens.notification.service.EncryptionService;
import io.springlens.notification.service.WebhookUrlValidator;
import io.springlens.shared.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook configuration endpoints.
 * ✅ HARDENED: All webhook URLs are validated to prevent SSRF attacks.
 * - Must use HTTPS
 * - Cannot point to private IPs or metadata endpoints
 * - Must be publicly resolvable
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/webhooks")
public class WebhookConfigController {

    private static final Logger log = LoggerFactory.getLogger(WebhookConfigController.class);

    private final WebhookConfigRepository webhookConfigRepository;
    private final EncryptionService encryptionService;
    private final WebhookUrlValidator webhookUrlValidator;

    public WebhookConfigController(WebhookConfigRepository webhookConfigRepository,
                                    EncryptionService encryptionService,
                                    WebhookUrlValidator webhookUrlValidator) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.encryptionService = encryptionService;
        this.webhookUrlValidator = webhookUrlValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listWebhooks(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal Jwt jwt) {

        var configs = webhookConfigRepository.findByWorkspaceId(workspaceId);
        return ResponseEntity.ok(Map.of("webhooks", configs.stream().map(this::toDto).toList()));
    }

    /**
     * Create a new webhook configuration.
     * ✅ SECURITY: URL is validated to prevent SSRF attacks before saving.
     */
    @PostMapping
    public ResponseEntity<Object> createWebhook(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid CreateWebhookRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            // ✅ FIXED: Validate webhook URL to prevent SSRF attacks
            webhookUrlValidator.validateWebhookUrl(body.url());
        } catch (IllegalArgumentException ex) {
            log.warn("Webhook URL validation failed: {}", ex.getMessage());
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("INVALID_URL", ex.getMessage(), null));
        }

        String encryptedUrl = encryptionService.encrypt(body.url());
        var config = WebhookConfig.create(workspaceId, body.projectId(), body.type(), encryptedUrl);
        webhookConfigRepository.save(config);

        log.info("Created webhook config id={} workspace={} type={}", config.getId(), workspaceId, body.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(config));
    }

    /**
     * Update an existing webhook configuration.
     * ✅ SECURITY: URL is validated to prevent SSRF attacks before saving.
     */
    @PutMapping("/{webhookId}")
    public ResponseEntity<Object> updateWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @RequestBody @Valid UpdateWebhookRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        var config = webhookConfigRepository.findById(webhookId)
                .filter(wc -> wc.getWorkspaceId().equals(workspaceId))
                .orElse(null);

        if (config == null) return ResponseEntity.notFound().build();

        if (body.url() != null) {
            try {
                // ✅ FIXED: Validate webhook URL to prevent SSRF attacks
                webhookUrlValidator.validateWebhookUrl(body.url());
            } catch (IllegalArgumentException ex) {
                log.warn("Webhook URL validation failed: {}", ex.getMessage());
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.of("INVALID_URL", ex.getMessage(), null));
            }
            config.setUrlEncrypted(encryptionService.encrypt(body.url()));
        }
        if (body.enabled() != null) {
            config.setEnabled(body.enabled());
        }
        config.setUpdatedAt(Instant.now());
        webhookConfigRepository.save(config);

        log.info("Updated webhook config id={} workspace={}", webhookId, workspaceId);
        return ResponseEntity.ok(toDto(config));
    }

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal Jwt jwt) {

        var config = webhookConfigRepository.findById(webhookId)
                .filter(wc -> wc.getWorkspaceId().equals(workspaceId))
                .orElse(null);

        if (config == null) return ResponseEntity.notFound().build();

        webhookConfigRepository.delete(config);
        log.info("Deleted webhook config id={} workspace={}", webhookId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/healthz")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    private Map<String, Object> toDto(WebhookConfig wc) {
        return Map.of(
                "id", wc.getId().toString(),
                "workspace_id", wc.getWorkspaceId().toString(),
                "project_id", wc.getProjectId() != null ? wc.getProjectId().toString() : "",
                "type", wc.getType(),
                "enabled", wc.isEnabled(),
                "created_at", wc.getCreatedAt().toString(),
                "updated_at", wc.getUpdatedAt().toString());
    }

    public record CreateWebhookRequest(
            @NotBlank @Pattern(regexp = "slack|github_pr|pagerduty") String type,
            @NotBlank String url,
            UUID projectId) {
    }

    public record UpdateWebhookRequest(String url, Boolean enabled) {
    }
}
