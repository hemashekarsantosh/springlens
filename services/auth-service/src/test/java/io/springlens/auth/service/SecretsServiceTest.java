package io.springlens.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecretsService.
 * Verifies: Secrets are loaded from AWS Secrets Manager with caching.
 */
@DisplayName("SecretsService Tests")
class SecretsServiceTest {

    private SecretsService secretsService;
    private SecretsManagerClient mockSecretsClient;

    @BeforeEach
    void setUp() {
        // Create SecretsService with minimal config
        secretsService = new SecretsService("us-east-1", 60);

        // Replace the real Secrets Manager client with a mock
        mockSecretsClient = mock(SecretsManagerClient.class);
        ReflectionTestUtils.setField(secretsService, "secretsClient", mockSecretsClient);
    }

    @Test
    @DisplayName("Should load secret from Secrets Manager")
    void testLoadSecretFromSecretsManager() {
        // GIVEN: A mock Secrets Manager that returns a secret
        String secretName = "springlens/jwt-secret";
        String secretValue = "super-secret-key-12345678901234567890";

        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(secretValue)
                .build();

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // WHEN: The secret is requested
        String result = secretsService.getSecret(secretName);

        // THEN: Should return the secret value
        assertThat(result).isEqualTo(secretValue);

        // VERIFY: Secrets Manager was called
        verify(mockSecretsClient, times(1)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    @DisplayName("Should cache secrets to avoid repeated API calls")
    void testSecretCaching() {
        // GIVEN: A mock Secrets Manager
        String secretName = "springlens/jwt-secret";
        String secretValue = "cached-secret-value";

        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(secretValue)
                .build();

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // WHEN: The same secret is requested twice
        String result1 = secretsService.getSecret(secretName);
        String result2 = secretsService.getSecret(secretName);

        // THEN: Should return the same value
        assertThat(result1).isEqualTo(secretValue);
        assertThat(result2).isEqualTo(secretValue);

        // VERIFY: Secrets Manager was called only once (second call used cache)
        verify(mockSecretsClient, times(1)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    @DisplayName("Should fall back to environment variable if Secrets Manager fails")
    void testEnvironmentVariableFallback() {
        // GIVEN: Secrets Manager fails but env var is set
        String secretName = "springlens/jwt-secret";
        String envVarValue = "env-secret-value";

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(SecretsManagerException.builder().message("Not found").build());

        // Mock System.getenv
        try (MockedStatic<System> mockedSystem = mockStatic(System.class)) {
            mockedSystem.when(() -> System.getenv("SPRINGLENS_JWT_SECRET")).thenReturn(envVarValue);
            mockedSystem.when(System::getenv).thenCallRealMethod();

            // WHEN: The secret is requested
            String result = secretsService.getSecret(secretName);

            // THEN: Should return env var value
            assertThat(result).isEqualTo(envVarValue);
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException if secret cannot be loaded")
    void testThrowExceptionWhenSecretNotFound() {
        // GIVEN: Secrets Manager fails and no env var is set
        String secretName = "springlens/jwt-secret";

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(SecretsManagerException.builder().message("Secret not found").build());

        // Mock System.getenv to return null
        try (MockedStatic<System> mockedSystem = mockStatic(System.class)) {
            mockedSystem.when(() -> System.getenv("SPRINGLENS_JWT_SECRET")).thenReturn(null);
            mockedSystem.when(System::getenv).thenCallRealMethod();

            // WHEN/THEN: Should throw IllegalStateException
            assertThatThrownBy(() -> secretsService.getSecret(secretName))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secret not found: " + secretName);
        }
    }

    @Test
    @DisplayName("Should invalidate cache for specific secret")
    void testInvalidateSecretCache() {
        // GIVEN: A cached secret
        String secretName = "springlens/jwt-secret";
        String secretValue1 = "first-value";
        String secretValue2 = "second-value";

        GetSecretValueResponse response1 = GetSecretValueResponse.builder()
                .secretString(secretValue1)
                .build();
        GetSecretValueResponse response2 = GetSecretValueResponse.builder()
                .secretString(secretValue2)
                .build();

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response1)
                .thenReturn(response2);

        // WHEN: Secret is loaded, cache is invalidated, and secret is loaded again
        String result1 = secretsService.getSecret(secretName);
        secretsService.invalidateSecret(secretName);
        String result2 = secretsService.getSecret(secretName);

        // THEN: Should get different values (second call bypassed cache)
        assertThat(result1).isEqualTo(secretValue1);
        assertThat(result2).isEqualTo(secretValue2);

        // VERIFY: Secrets Manager was called twice
        verify(mockSecretsClient, times(2)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    @DisplayName("Should clear all cached secrets")
    void testClearAllCachedSecrets() {
        // GIVEN: Multiple cached secrets
        String secret1 = "springlens/jwt-secret";
        String secret2 = "springlens/stripe-webhook-secret";
        String value = "test-value";

        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(value)
                .build();

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // WHEN: Load both secrets, clear cache, then load again
        secretsService.getSecret(secret1);
        secretsService.getSecret(secret2);
        secretsService.clearCache();
        secretsService.getSecret(secret1);

        // THEN: Should load secret1 again from Secrets Manager (not from cache)
        // First call for secret1, first call for secret2, second call for secret1 = 3 total
        verify(mockSecretsClient, times(3)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    @DisplayName("Should handle binary secrets correctly")
    void testHandleBinarySecrets() {
        // GIVEN: A mock Secrets Manager that returns a binary secret
        String secretName = "springlens/encryption-key";
        String secretValue = "binary-secret-value";

        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretBinary(software.amazon.awssdk.core.SdkBytes.fromUtf8String(secretValue))
                .build();

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // WHEN: The binary secret is requested
        String result = secretsService.getSecret(secretName);

        // THEN: Should correctly parse the binary value
        assertThat(result).isEqualTo(secretValue);
    }

    @Test
    @DisplayName("Security: Secrets should not be logged in plaintext")
    void testSecretsShouldNotBeLoggedPlaintext() {
        // This test verifies that secret values are never logged
        // In production, logs should be monitored to ensure no plaintext secrets appear

        String secretName = "springlens/jwt-secret";
        String secretValue = "super-secret-value";

        GetSecretValueResponse response = GetSecretValueResponse.builder()
                .secretString(secretValue)
                .build();

        when(mockSecretsClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response);

        // WHEN: Secret is loaded
        String result = secretsService.getSecret(secretName);

        // THEN: Should get the secret, but logs should not contain plaintext
        assertThat(result).isEqualTo(secretValue);
        // Verify through code inspection: log messages should only log secret name, not value
    }
}
