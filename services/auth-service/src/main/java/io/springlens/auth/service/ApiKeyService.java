package io.springlens.auth.service;

import io.springlens.auth.entity.ApiKey;
import io.springlens.auth.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates, validates, and revokes API keys.
 * Key format: sl_proj_<base64url-32bytes>
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX = "sl_proj_";

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder bcrypt;
    private final SecureRandom secureRandom;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.bcrypt = new BCryptPasswordEncoder(10);
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public ApiKeyCreationResult createApiKey(UUID workspaceId, UUID projectId,
                                              String name, UUID createdBy) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String keyHash = bcrypt.encode(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        var apiKey = ApiKey.create(workspaceId, projectId, name, keyHash, keyPrefix, createdBy);
        apiKeyRepository.save(apiKey);

        log.info("Created API key id={} project={} workspace={}", apiKey.getId(), projectId, workspaceId);
        return new ApiKeyCreationResult(apiKey, rawKey);
    }

    /**
     * Validates a raw API key. Returns matching ApiKey if valid, empty otherwise.
     * Updates last_used_at on success.
     */
    @Transactional
    public java.util.Optional<ApiKey> validateKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return java.util.Optional.empty();
        }

        String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        // Find candidate keys by prefix, then bcrypt match
        var candidates = apiKeyRepository.findAll().stream()
                .filter(k -> k.isActive() && k.getKeyPrefix().equals(prefix))
                .filter(k -> bcrypt.matches(rawKey, k.getKeyHash()))
                .findFirst();

        candidates.ifPresent(k -> {
            k.setLastUsedAt(java.time.Instant.now());
            apiKeyRepository.save(k);
        });

        return candidates;
    }

    @Transactional
    public void revokeKey(UUID keyId, UUID workspaceId) {
        apiKeyRepository.findById(keyId).ifPresent(k -> {
            if (!k.getWorkspaceId().equals(workspaceId)) {
                throw new SecurityException("Key does not belong to workspace");
            }
            k.setRevokedAt(java.time.Instant.now());
            apiKeyRepository.save(k);
            log.info("Revoked API key id={} workspace={}", keyId, workspaceId);
        });
    }

    public record ApiKeyCreationResult(ApiKey apiKey, String rawKey) {
    }
}
