package io.springlens.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for loading secrets from AWS Secrets Manager.
 * Implements caching to avoid excessive API calls.
 *
 * ✅ SECURITY: Secrets are loaded from AWS Secrets Manager instead of config files.
 * - Secrets are cached in memory with configurable TTL
 * - Supports rotating secrets without redeployment
 * - Provides fallback to env vars for local development
 */
@Service
public class SecretsService {

    private static final Logger log = LoggerFactory.getLogger(SecretsService.class);

    private final SecretsManagerClient secretsClient;
    private final String awsRegion;
    private final ConcurrentHashMap<String, CachedSecret> secretCache = new ConcurrentHashMap<>();
    private final long cacheTtlMillis; // Default: 1 hour

    public SecretsService(
            @Value("${aws.region:us-east-1}") String awsRegion,
            @Value("${springlens.secrets.cache-ttl-minutes:60}") int cacheTtlMinutes) {
        this.awsRegion = awsRegion;
        this.cacheTtlMillis = TimeUnit.MINUTES.toMillis(cacheTtlMinutes);
        this.secretsClient = SecretsManagerClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(awsRegion))
                .build();
    }

    /**
     * Retrieves a secret from Secrets Manager with in-memory caching.
     * Loads from cache if available and not expired.
     * Falls back to environment variables if secret not found in Secrets Manager.
     *
     * @param secretName The name of the secret (e.g., "springlens/jwt-secret")
     * @return The secret value
     * @throws IllegalStateException if secret cannot be loaded
     */
    public String getSecret(String secretName) {
        // Check cache first
        CachedSecret cached = secretCache.get(secretName);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached secret: {}", secretName);
            return cached.value;
        }

        // Attempt to load from Secrets Manager
        try {
            String secretValue = fetchFromSecretsManager(secretName);
            // Cache the secret
            secretCache.put(secretName, new CachedSecret(secretValue, System.currentTimeMillis()));
            log.info("Loaded secret from Secrets Manager: {}", secretName);
            return secretValue;
        } catch (SecretsManagerException ex) {
            log.warn("Failed to load secret from Secrets Manager: {}. Trying environment variable fallback.", secretName);

            // Fallback to environment variable (useful for local dev with LocalStack)
            String envVarName = secretName.toUpperCase().replace("-", "_").replace("/", "_");
            String envValue = System.getenv(envVarName);
            if (envValue != null && !envValue.isBlank()) {
                secretCache.put(secretName, new CachedSecret(envValue, System.currentTimeMillis()));
                log.info("Loaded secret from environment variable: {}", envVarName);
                return envValue;
            }

            throw new IllegalStateException("Secret not found: " + secretName, ex);
        }
    }

    /**
     * Invalidates the cache for a specific secret (e.g., after rotation).
     */
    public void invalidateSecret(String secretName) {
        secretCache.remove(secretName);
        log.info("Invalidated cached secret: {}", secretName);
    }

    /**
     * Clears all cached secrets.
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Cleared all cached secrets");
    }

    private String fetchFromSecretsManager(String secretName) throws SecretsManagerException {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);

            // Handle both string secrets and binary secrets
            if (response.secretString() != null) {
                return response.secretString();
            } else if (response.secretBinary() != null) {
                return new String(response.secretBinary().asByteArray());
            } else {
                throw new IllegalStateException("Secret has neither string nor binary value: " + secretName);
            }
        } catch (SecretsManagerException ex) {
            log.error("Failed to fetch secret from Secrets Manager: {}", secretName);
            throw ex;
        }
    }

    /**
     * Closes the Secrets Manager client (called on application shutdown).
     */
    public void close() {
        if (secretsClient != null) {
            secretsClient.close();
            log.info("Closed Secrets Manager client");
        }
    }

    /**
     * Inner class to track cached secret with TTL.
     */
    private class CachedSecret {
        final String value;
        final long timestamp;

        CachedSecret(String value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > cacheTtlMillis;
        }
    }
}
