package io.springlens.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for creating and verifying webhook signatures.
 *
 * ✅ SECURITY: Allows webhook receivers to verify that requests came from SpringLens.
 * - Uses HMAC-SHA256 for signing
 * - Signature included in X-Springlens-Signature header
 * - Client can verify by computing HMAC with their webhook secret
 * - Prevents webhook spoofing from attackers
 */
@Service
public class WebhookSignatureService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureService.class);
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Springlens-Signature";
    private static final String TIMESTAMP_HEADER = "X-Springlens-Timestamp";

    /**
     * Create a signature for a webhook payload using the webhook's secret key.
     *
     * ✅ SECURITY: Clients can verify this signature to ensure the webhook is authentic.
     * Signature format: "sha256=<base64_encoded_hmac>"
     *
     * @param payload The webhook payload (JSON string)
     * @param webhookSecret The webhook's secret key (stored in WebhookConfig)
     * @return The signature value to include in X-Springlens-Signature header
     */
    public String createSignature(String payload, String webhookSecret) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String dataToSign = payload + "." + timestamp;

            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    0,
                    webhookSecret.getBytes(StandardCharsets.UTF_8).length,
                    SIGNATURE_ALGORITHM);

            mac.init(secretKeySpec);
            byte[] signature = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));

            // Return signature in format: sha256=<base64_encoded_value>
            String encodedSignature = Base64.getEncoder().encodeToString(signature);
            return "sha256=" + encodedSignature;

        } catch (Exception ex) {
            log.error("Failed to create webhook signature", ex);
            throw new RuntimeException("Signature creation failed", ex);
        }
    }

    /**
     * Get the header name for webhook signatures.
     */
    public String getSignatureHeaderName() {
        return SIGNATURE_HEADER;
    }

    /**
     * Get the header name for timestamps.
     */
    public String getTimestampHeaderName() {
        return TIMESTAMP_HEADER;
    }

    /**
     * Documentation comment for webhook signature verification (for API docs).
     * Clients should include this in their webhook verification logic.
     */
    public static String getVerificationDocumentation() {
        return """
                # Verifying Webhook Signatures

                To verify that a webhook came from SpringLens, check the X-Springlens-Signature header:

                1. Extract the signature from the X-Springlens-Signature header: "sha256=<base64_value>"
                2. Extract the timestamp from the X-Springlens-Timestamp header
                3. Create a data string: payload + "." + timestamp
                4. Compute HMAC-SHA256 of the data string using your webhook secret
                5. Compare the computed signature with the header signature (constant-time comparison)

                Example (Python):
                ```python
                import hmac
                import hashlib
                import base64

                def verify_signature(payload, signature_header, timestamp_header, webhook_secret):
                    data_to_sign = payload + "." + timestamp_header
                    expected_sig = base64.b64encode(
                        hmac.new(
                            webhook_secret.encode(),
                            data_to_sign.encode(),
                            hashlib.sha256
                        ).digest()
                    ).decode()

                    # Extract signature from header (format: "sha256=<base64>")
                    received_sig = signature_header.split("=")[1]

                    # Constant-time comparison
                    return hmac.compare_digest(expected_sig, received_sig)
                ```
                """;
    }
}
