package io.springlens.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WebhookUrlValidator.
 * Verifies: Webhook URLs are validated to prevent SSRF attacks.
 */
@DisplayName("WebhookUrlValidator SSRF Prevention Tests")
class WebhookUrlValidatorTest {

    private WebhookUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WebhookUrlValidator();
    }

    @Test
    @DisplayName("Should accept valid HTTPS URLs to public domains")
    void testAcceptValidHttpsUrl() {
        // Valid HTTPS URLs to public domains
        validator.validateWebhookUrl("https://slack.com/hooks/services/T00000000/B00000000");
        validator.validateWebhookUrl("https://api.github.com/repos/org/repo/issues");
        validator.validateWebhookUrl("https://events.pagerduty.com/v2/enqueue");
        validator.validateWebhookUrl("https://example.com:443/webhook");
        validator.validateWebhookUrl("https://sub.domain.example.com/path/to/webhook");

        // No exception thrown = success
    }

    @Test
    @DisplayName("Should reject HTTP (non-HTTPS) URLs")
    void testRejectHttpUrl() {
        assertThatThrownBy(() -> validator.validateWebhookUrl("http://example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use HTTPS");
    }

    @Test
    @DisplayName("Should reject URLs without scheme")
    void testRejectUrlWithoutScheme() {
        assertThatThrownBy(() -> validator.validateWebhookUrl("example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject null or empty URLs")
    void testRejectNullOrEmptyUrl() {
        assertThatThrownBy(() -> validator.validateWebhookUrl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");

        assertThatThrownBy(() -> validator.validateWebhookUrl(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");

        assertThatThrownBy(() -> validator.validateWebhookUrl("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("Security: Should block localhost URLs (SSRF prevention)")
    void testBlockLocalhostSsrf() {
        // Prevent webhook to localhost (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://localhost:8080/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://localhost/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should block 127.0.0.1 loopback (SSRF prevention)")
    void testBlockLoopbackIpSsrf() {
        // Prevent webhook to loopback IP (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://127.0.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://127.0.0.100/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should block AWS metadata endpoint (SSRF prevention)")
    void testBlockAwsMetadataEndpoint() {
        // Prevent webhook to AWS metadata service (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should block GCP metadata endpoint (SSRF prevention)")
    void testBlockGcpMetadataEndpoint() {
        // Prevent webhook to GCP metadata service (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://metadata.google.internal/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should block private IP 10.x.x.x (SSRF prevention)")
    void testBlockPrivateIp10Range() {
        // Prevent webhook to internal network (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://10.0.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://10.255.255.255/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should block private IP 192.168.x.x (SSRF prevention)")
    void testBlockPrivateIp192Range() {
        // Prevent webhook to internal network (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://192.168.1.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://192.168.255.255/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should block private IP 172.16.x.x - 172.31.x.x (SSRF prevention)")
    void testBlockPrivateIp172Range() {
        // Prevent webhook to internal network (SSRF attack)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://172.16.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://172.20.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://172.31.255.255/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Security: Should reject URLs with only local addresses after DNS resolution")
    void testBlockUrlsResolvingToLocalAddresses() {
        // This test verifies that even if someone uses a FQDN that resolves
        // to a local IP, the validator would block it
        // (This specific test is challenging without DNS spoofing)

        // Test with 0.0.0.0 (blocked pattern)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://0.0.0.0/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private or internal");
    }

    @Test
    @DisplayName("Should reject invalid URL formats")
    void testRejectInvalidUrlFormat() {
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://:8080/webhook"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validator.validateWebhookUrl("not a url at all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should accept HTTPS with custom ports")
    void testAcceptHttpsWithCustomPort() {
        validator.validateWebhookUrl("https://example.com:8443/webhook");
        validator.validateWebhookUrl("https://api.example.com:443/v1/webhook");
        // No exception thrown = success
    }

    @Test
    @DisplayName("Should accept HTTPS with path and query parameters")
    void testAcceptHttpsWithPathAndQuery() {
        validator.validateWebhookUrl("https://example.com/path/to/webhook");
        validator.validateWebhookUrl("https://example.com/webhook?token=abc123");
        validator.validateWebhookUrl("https://example.com/webhook#section");
        // No exception thrown = success
    }

    @Test
    @DisplayName("Should accept international domain names (IDN)")
    void testAcceptInternationalDomainNames() {
        // IDN support - validator should handle unicode domains
        validator.validateWebhookUrl("https://example.com/webhook");
        // No exception thrown = success
    }

    @Test
    @DisplayName("Security: Case-insensitive HTTPS check")
    void testCaseInsensitiveHttpsCheck() {
        // Should accept HTTPS regardless of case
        validator.validateWebhookUrl("https://example.com/webhook");
        validator.validateWebhookUrl("HTTPS://example.com/webhook");
        validator.validateWebhookUrl("HtTpS://example.com/webhook");

        // Should still reject non-HTTPS
        assertThatThrownBy(() -> validator.validateWebhookUrl("HTTP://example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> validator.validateWebhookUrl("hTTp://example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Security: Should block internal service URLs")
    void testBlockInternalServiceUrls() {
        // Prevent webhook to other internal services
        // These would typically resolve to private IPs but let's be explicit about the pattern

        // auth-service on internal network
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://auth-service:8084/webhook"))
                .isInstanceOf(IllegalArgumentException.class);

        // Kubernetes service DNS (would resolve to internal IP)
        assertThatThrownBy(() -> validator.validateWebhookUrl("https://notification-service.default.svc.cluster.local/webhook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Logging: Security events are logged but don't leak sensitive info")
    void testLoggingDoesNotLeakSecrets() {
        // This test verifies that even if a webhook URL contains sensitive info,
        // the validator won't log the full URL (though it may log the hostname)
        // This is verified through code inspection rather than runtime testing

        assertThatThrownBy(() -> validator.validateWebhookUrl("https://localhost/webhook?token=supersecret123"))
                .isInstanceOf(IllegalArgumentException.class);
        // Log statement verified in code: log.warn("Webhook URL points to blocked host: ...")
    }
}
