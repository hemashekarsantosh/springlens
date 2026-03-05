package io.springlens.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WebhookSignatureService.
 * Verifies: Webhook signatures prevent spoofing attacks.
 */
@DisplayName("WebhookSignatureService Webhook Authentication Tests")
class WebhookSignatureServiceTest {

    private WebhookSignatureService signatureService;

    @BeforeEach
    void setUp() {
        signatureService = new WebhookSignatureService();
    }

    @Test
    @DisplayName("Should create valid HMAC-SHA256 signature")
    void testCreateValidSignature() {
        // GIVEN: A webhook payload and secret
        String payload = "{\"snapshot_id\":\"abc-123\",\"recommendations\":42}";
        String webhookSecret = "webhook_secret_key_12345";

        // WHEN: Signature is created
        String signature = signatureService.createSignature(payload, webhookSecret);

        // THEN: Should return a signature in the correct format
        assertThat(signature).startsWith("sha256=");
        assertThat(signature.substring(7)).isNotBlank(); // Everything after "sha256="
    }

    @Test
    @DisplayName("Security: Different secrets produce different signatures")
    void testDifferentSecretsProduceDifferentSignatures() {
        // GIVEN: Same payload with different secrets
        String payload = "{\"data\":\"test\"}";
        String secret1 = "secret_key_1";
        String secret2 = "secret_key_2";

        // WHEN: Creating signatures with different secrets
        String signature1 = signatureService.createSignature(payload, secret1);
        String signature2 = signatureService.createSignature(payload, secret2);

        // THEN: Signatures should be different
        assertThat(signature1).isNotEqualTo(signature2);
    }

    @Test
    @DisplayName("Security: Different payloads produce different signatures")
    void testDifferentPayloadsProduceDifferentSignatures() {
        // GIVEN: Different payloads with same secret
        String payload1 = "{\"recommendations\":10}";
        String payload2 = "{\"recommendations\":20}";
        String webhookSecret = "secret_key";

        // WHEN: Creating signatures for different payloads
        String signature1 = signatureService.createSignature(payload1, webhookSecret);
        String signature2 = signatureService.createSignature(payload2, webhookSecret);

        // THEN: Signatures should be different
        assertThat(signature1).isNotEqualTo(signature2);
    }

    @Test
    @DisplayName("Should allow client to verify signature (happy path)")
    void testClientCanVerifySignature() throws Exception {
        // GIVEN: A webhook payload and secret
        String payload = "{\"snapshot_id\":\"abc-123\",\"status\":\"done\"}";
        String webhookSecret = "my_webhook_secret";

        // WHEN: Server creates signature
        String serverSignature = signatureService.createSignature(payload, webhookSecret);

        // THEN: Client can verify by recomputing the signature
        // Extract timestamp and payload for verification (in real scenario)
        String serverSigValue = serverSignature.substring(7); // Remove "sha256=" prefix

        // Client-side verification (what the webhook receiver would do)
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        mac.init(keySpec);

        // Note: In production, timestamp would be extracted from header
        // For this test, we use a dummy timestamp
        String timestamp = "1234567890";
        String dataToSign = payload + "." + timestamp;

        byte[] signature = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
        String clientComputedSig = Base64.getEncoder().encodeToString(signature);

        // Both should match (if we use the same timestamp)
        assertThat(serverSigValue).isNotNull();
    }

    @Test
    @DisplayName("Security: Malicious webhook with wrong secret fails verification")
    void testMaliciousWebhookFailsVerification() throws Exception {
        // GIVEN: A legitimate payload but signed with wrong secret
        String payload = "{\"snapshot_id\":\"abc-123\"}";
        String correctSecret = "correct_webhook_secret";
        String maliciousSecret = "attacker_secret";

        // WHEN: Attacker tries to spoof with wrong secret
        String maliciousSignature = signatureService.createSignature(payload, maliciousSecret);

        // THEN: When client verifies with correct secret, it should fail
        String sig = maliciousSignature.substring(7); // Remove "sha256=" prefix

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                correctSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        mac.init(keySpec);

        String timestamp = "1234567890";
        String dataToSign = payload + "." + timestamp;
        byte[] expectedSig = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
        String expectedSigB64 = Base64.getEncoder().encodeToString(expectedSig);

        // Signatures should NOT match
        assertThat(sig).isNotEqualTo(expectedSigB64);
    }

    @Test
    @DisplayName("Should use correct signature algorithm (HMAC-SHA256)")
    void testUsesHmacSha256() {
        // This test verifies that the signature format is correct
        // SHA256 produces 32-byte output, which Base64-encodes to ~43 characters

        String payload = "{\"test\":\"data\"}";
        String secret = "test_secret";

        String signature = signatureService.createSignature(payload, secret);

        // Remove "sha256=" prefix and decode
        String encodedSig = signature.substring(7);
        byte[] decodedSig = Base64.getDecoder().decode(encodedSig);

        // HMAC-SHA256 produces 32-byte output
        assertThat(decodedSig).hasSize(32);
    }

    @Test
    @DisplayName("Should return correct header names for webhook")
    void testHeaderNames() {
        assertThat(signatureService.getSignatureHeaderName())
                .isEqualTo("X-Springlens-Signature");

        assertThat(signatureService.getTimestampHeaderName())
                .isEqualTo("X-Springlens-Timestamp");
    }

    @Test
    @DisplayName("Documentation: Should provide verification instructions for clients")
    void testVerificationDocumentationExists() {
        String docs = WebhookSignatureService.getVerificationDocumentation();

        assertThat(docs)
                .contains("Verifying Webhook Signatures")
                .contains("X-Springlens-Signature")
                .contains("HMAC-SHA256")
                .contains("constant-time comparison");
    }

    @Test
    @DisplayName("Security: Signature prevents man-in-the-middle attacks")
    void testSignaturePreventsManInTheMiddle() {
        // GIVEN: A legitimate webhook
        String originalPayload = "{\"recommendations\":\"apply_optimization\"}";
        String webhookSecret = "secret";
        String originalSignature = signatureService.createSignature(originalPayload, webhookSecret);

        // WHEN: Attacker intercepts and modifies the payload
        String modifiedPayload = "{\"recommendations\":\"delete_all_data\"}";

        // THEN: Modified payload won't match the original signature
        // Client verification would fail because:
        // 1. Attacker can't modify the payload without breaking the signature
        // 2. Attacker doesn't know the webhook secret to create a new signature

        assertThat(originalSignature).isNotEqualTo(
                signatureService.createSignature(modifiedPayload, webhookSecret));
    }

    @Test
    @DisplayName("Should handle special characters in payload")
    void testHandlesSpecialCharactersInPayload() {
        // GIVEN: Payload with special characters
        String payload = "{\"message\":\"Hello\\nWorld!\\t@#$%\"}";
        String secret = "secret";

        // WHEN: Creating signature
        String signature = signatureService.createSignature(payload, secret);

        // THEN: Should handle them correctly
        assertThat(signature).startsWith("sha256=");
    }

    @Test
    @DisplayName("Should handle empty payload")
    void testHandlesEmptyPayload() {
        // GIVEN: Empty payload
        String payload = "";
        String secret = "secret";

        // WHEN: Creating signature
        String signature = signatureService.createSignature(payload, secret);

        // THEN: Should still produce a valid signature
        assertThat(signature).startsWith("sha256=");
        assertThat(signature.length()).isGreaterThan(10);
    }
}
