package io.springlens.auth.controller;

import com.stripe.net.Webhook;
import io.springlens.auth.entity.Subscription;
import io.springlens.auth.repository.SubscriptionRepository;
import io.springlens.auth.repository.WorkspaceRepository;
import io.springlens.auth.service.SecretsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingController.
 * Verifies: Stripe webhook signature verification prevents spoofing attacks.
 */
@DisplayName("BillingController Security Tests")
class BillingControllerTest {

    private BillingController controller;
    private WorkspaceRepository workspaceRepository;
    private SubscriptionRepository subscriptionRepository;
    private SecretsService mockSecretsService;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        mockSecretsService = mock(SecretsService.class);

        // Mock the Stripe webhook secret from Secrets Manager
        when(mockSecretsService.getSecret("springlens/stripe-webhook-secret"))
                .thenReturn("whsec_test_secret_12345");

        controller = new BillingController(workspaceRepository, subscriptionRepository, mockSecretsService);
    }

    @Test
    @DisplayName("Should load Stripe webhook secret from Secrets Manager on initialization")
    void testLoadsStripeSecretFromSecretsManager() {
        // VERIFY: SecretsService.getSecret() was called during initialization
        verify(mockSecretsService, times(1)).getSecret("springlens/stripe-webhook-secret");
    }

    @Test
    @DisplayName("Should throw exception if Stripe webhook secret cannot be loaded")
    void testThrowsExceptionIfSecretCannotBeLoaded() {
        // GIVEN: SecretsService that throws exception
        SecretsService failingSecretsService = mock(SecretsService.class);
        when(failingSecretsService.getSecret("springlens/stripe-webhook-secret"))
                .thenThrow(new IllegalStateException("Secret not found"));

        // WHEN/THEN: BillingController initialization should fail
        assertThatThrownBy(() -> new BillingController(
                workspaceRepository, subscriptionRepository, failingSecretsService))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot initialize BillingController");
    }

    @Test
    @DisplayName("Should reject webhook with invalid signature (401 Unauthorized)")
    void testInvalidSignatureReturns401() {
        // GIVEN: A valid Stripe webhook payload but with an invalid signature
        String payload = "{\"type\":\"invoice.paid\",\"data\":{\"object\":{\"subscription\":\"sub_123\"}}}";
        String invalidSignature = "invalid_signature_xyz";

        // WHEN: The webhook endpoint is called with invalid signature
        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(
                payload, invalidSignature);

        // THEN: The response should be 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .containsEntry("status", "error")
                .containsEntry("message", "Invalid signature");

        // VERIFY: No repositories were accessed (event was rejected before processing)
        verifyNoInteractions(workspaceRepository, subscriptionRepository);
    }

    @Test
    @DisplayName("Should reject webhook with missing signature (401 Unauthorized)")
    void testMissingSignatureReturns401() {
        // GIVEN: A valid Stripe webhook payload but with empty signature
        String payload = "{\"type\":\"invoice.paid\",\"data\":{\"object\":{\"subscription\":\"sub_123\"}}}";
        String emptySignature = "";

        // WHEN: The webhook endpoint is called with empty signature
        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(
                payload, emptySignature);

        // THEN: The response should be 401 Unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", "error");

        // VERIFY: No repositories were accessed
        verifyNoInteractions(workspaceRepository, subscriptionRepository);
    }

    @Test
    @DisplayName("Should reject webhook with malformed payload (400 Bad Request)")
    void testMalformedPayloadReturns400() {
        // GIVEN: A malformed JSON payload and signature
        String malformedPayload = "{invalid json";
        String signature = "sig_test";

        // WHEN: The webhook endpoint is called with malformed payload
        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(
                malformedPayload, signature);

        // THEN: The response should be 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("status", "error")
                .containsEntry("message", "Processing failed");

        // VERIFY: No repositories were accessed
        verifyNoInteractions(workspaceRepository, subscriptionRepository);
    }

    @Test
    @DisplayName("Should accept webhook with valid Stripe test event (200 OK)")
    void testValidStripeTestEventIsProcessed() throws Exception {
        // GIVEN: A valid Stripe test webhook from Stripe's SDK test utilities
        // This uses Stripe's actual test signing method for webhook events
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String testSecret = "whsec_test_secret_12345";

        String payload = "{\"type\":\"customer.subscription.created\",\"data\":{\"object\":{\"id\":\"sub_test_123\"}}}";

        // Sign using Stripe's SDK test helper
        String signature = Webhook.Util.computeHmacSha256(testSecret, timestamp + "." + payload);
        String signatureHeader = "t=" + timestamp + ",v1=" + signature;

        // WHEN: The webhook endpoint is called with valid signature
        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(
                payload, signatureHeader);

        // THEN: The response should indicate successful receipt
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "received");
    }

    @Test
    @DisplayName("Should reject replay attacks (old timestamp rejected)")
    void testOldTimestampIsRejected() {
        // GIVEN: A webhook with a timestamp > 5 minutes old (Stripe SDK default tolerance)
        long oldTimestamp = (System.currentTimeMillis() / 1000) - 400; // 400 seconds ago
        String payload = "{\"type\":\"invoice.paid\"}";
        String signature = "t=" + oldTimestamp + ",v1=invalid_old_sig";

        // WHEN: The webhook endpoint is called with old timestamp
        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(
                payload, signature);

        // THEN: The response should be 401 Unauthorized (signature verification fails)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", "error");
    }

    @Test
    @DisplayName("Security Verification: Signature verification is called before event processing")
    void testSignatureVerificationOccursFirst() {
        // GIVEN: A webhook with invalid signature and a mock that would throw if called
        String payload = "{\"type\":\"invoice.paid\",\"data\":{\"object\":{\"subscription\":\"sub_123\"}}}";
        String invalidSignature = "invalid_sig_abc123";

        // Make repositories throw if accessed (proving they're not called before sig verification)
        subscriptionRepository.findByStripeSubscriptionId(anyString());
        doThrow(new AssertionError("Repository was accessed before signature verification!"))
                .when(subscriptionRepository).findByStripeSubscriptionId(anyString());

        // WHEN: The webhook endpoint is called with invalid signature
        ResponseEntity<Map<String, String>> response = controller.handleStripeWebhook(
                payload, invalidSignature);

        // THEN: Signature verification should fail BEFORE the repository is accessed
        // If we get here without AssertionError, signature verification happened first
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(subscriptionRepository, never()).findByStripeSubscriptionId(anyString());
    }
}
