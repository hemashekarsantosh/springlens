package io.springlens.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for webhook URLs to prevent Server-Side Request Forgery (SSRF) attacks.
 *
 * ✅ SECURITY: Validates that webhook URLs:
 * - Use HTTPS only (no HTTP)
 * - Do not point to localhost or private IPs
 * - Do not target AWS/GCP/Azure metadata endpoints
 * - Resolve to publicly accessible hosts
 *
 * This prevents attackers from configuring webhooks to internal services
 * (auth-service, metadata endpoints, etc.)
 */
@Service
public class WebhookUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookUrlValidator.class);

    private static final Pattern HTTPS_URL_PATTERN =
            Pattern.compile("^https://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$", Pattern.CASE_INSENSITIVE);

    private static final List<String> BLOCKED_PATTERNS = Arrays.asList(
            "localhost",           // localhost
            "127\\.0\\.0\\..*",    // 127.x.x.x loopback
            "0\\.0\\.0\\.0",       // 0.0.0.0 wildcard
            "169\\.254\\.169\\.254",  // AWS metadata endpoint
            "metadata\\.google\\.internal",  // GCP metadata endpoint
            "169\\.254\\..*",      // Azure metadata
            "10\\..*",             // Private: 10.0.0.0/8
            "172\\.1[6-9]\\..*",   // Private: 172.16.0.0/12
            "172\\.2[0-9]\\..*",   // Private: 172.20.0.0/12
            "172\\.3[01]\\..*",    // Private: 172.30.0.0/12
            "192\\.168\\..*"       // Private: 192.168.0.0/16
    );

    /**
     * Validates a webhook URL to prevent SSRF attacks.
     *
     * @param url The URL to validate
     * @throws IllegalArgumentException if URL is invalid or blocked
     */
    public void validateWebhookUrl(String url) throws IllegalArgumentException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is required and cannot be empty");
        }

        // ✅ RULE 1: Must use HTTPS (no HTTP)
        if (!url.toLowerCase().startsWith("https://")) {
            log.warn("Webhook URL does not use HTTPS: {}", url);
            throw new IllegalArgumentException("Webhook URL must use HTTPS protocol (not HTTP)");
        }

        // ✅ RULE 2: Validate URL format
        if (!HTTPS_URL_PATTERN.matcher(url).matches()) {
            log.warn("Webhook URL format is invalid: {}", url);
            throw new IllegalArgumentException("Webhook URL format is invalid");
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Webhook URL must contain a valid hostname");
            }

            // ✅ RULE 3: Block private IPs and metadata endpoints
            for (String blockedPattern : BLOCKED_PATTERNS) {
                if (host.matches(blockedPattern)) {
                    log.warn("Webhook URL points to blocked host: {} (pattern: {})", host, blockedPattern);
                    throw new IllegalArgumentException(
                            "Webhook URL cannot point to private or internal hosts: " + host);
                }
            }

            // ✅ RULE 4: Verify host is publicly resolvable (DNS lookup)
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);

                boolean hasPublicAddress = Arrays.stream(addresses)
                        .anyMatch(addr -> !addr.isSiteLocalAddress() &&
                                !addr.isLoopbackAddress() &&
                                !addr.isLinkLocalAddress() &&
                                !addr.isMulticastAddress());

                if (!hasPublicAddress) {
                    log.warn("Webhook URL hostname resolves only to private/local addresses: {}", host);
                    throw new IllegalArgumentException(
                            "Webhook URL hostname must resolve to a publicly accessible address");
                }

                log.debug("Webhook URL validated successfully: {}", url);

            } catch (UnknownHostException ex) {
                log.warn("Webhook URL hostname could not be resolved: {}", host);
                throw new IllegalArgumentException(
                        "Webhook URL hostname could not be resolved: " + host);
            }

        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            log.error("Error validating webhook URL: {}", url, ex);
            throw new IllegalArgumentException("Invalid webhook URL: " + ex.getMessage());
        }
    }
}
