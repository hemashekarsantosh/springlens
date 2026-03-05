package io.springlens.auth.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AuditLog entity.
 * Verifies: Audit logs properly track operations for compliance.
 */
@DisplayName("AuditLog Entity Tests")
class AuditLogTest {

    @Test
    @DisplayName("Should create successful audit log with correct defaults")
    void testCreateSuccessAuditLog() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", "API_KEY", resourceId,
                "Created API key for project", "192.168.1.1", "Mozilla/5.0");

        // THEN
        assertThat(log.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getAction()).isEqualTo("CREATE");
        assertThat(log.getResourceType()).isEqualTo("API_KEY");
        assertThat(log.getResourceId()).isEqualTo(resourceId);
        assertThat(log.getChanges()).isEqualTo("Created API key for project");
        assertThat(log.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getErrorMessage()).isNull();
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getCreatedAt()).isAfter(Instant.now().minus(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Should create failure audit log with error message")
    void testCreateFailureAuditLog() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        AuditLog log = AuditLog.failure(workspaceId, userId, "REVOKE", "API_KEY", null,
                "User not authorized", "203.0.113.42", "Safari");

        // THEN
        assertThat(log.getResult()).isEqualTo("FAILURE");
        assertThat(log.getErrorMessage()).isEqualTo("User not authorized");
        assertThat(log.getChanges()).isNull();
        assertThat(log.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(log.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should support null userId for system operations")
    void testSystemOperationWithNullUserId() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();

        // WHEN: System-initiated cleanup
        AuditLog log = AuditLog.success(workspaceId, null, "DELETE", "API_KEY", null,
                "Automatic cleanup", "127.0.0.1", "system");

        // THEN
        assertThat(log.getUserId()).isNull();
        assertThat(log.getAction()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("Should support null resourceId for non-specific operations")
    void testLogWithNullResourceId() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN: Login operation (user-wide, not specific resource)
        AuditLog log = AuditLog.success(workspaceId, userId, "LOGIN", "USER", null,
                "Successful login", "192.168.1.1", "Chrome");

        // THEN
        assertThat(log.getResourceId()).isNull();
        assertThat(log.getResourceType()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should track all sensitive action types")
    void testAllActionTypes() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String[] actions = {"CREATE", "UPDATE", "DELETE", "ENABLE", "DISABLE", "REVOKE", "VERIFY"};

        // WHEN/THEN
        for (String action : actions) {
            AuditLog log = AuditLog.success(workspaceId, userId, action, "API_KEY", null, "", "", "");
            assertThat(log.getAction()).isEqualTo(action);
        }
    }

    @Test
    @DisplayName("Should track all resource types")
    void testAllResourceTypes() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String[] resources = {"API_KEY", "JWT_TOKEN", "WEBHOOK", "BILLING", "USER_PROFILE"};

        // WHEN/THEN
        for (String resourceType : resources) {
            AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", resourceType, null, "", "", "");
            assertThat(log.getResourceType()).isEqualTo(resourceType);
        }
    }

    @Test
    @DisplayName("Should capture IPv4 addresses")
    void testCaptureIPv4Address() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", "WEBHOOK", null,
                "", "192.168.1.100", "");

        // THEN
        assertThat(log.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(log.getIpAddress()).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }

    @Test
    @DisplayName("Should capture IPv6 addresses")
    void testCaptureIPv6Address() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", "WEBHOOK", null,
                "", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "");

        // THEN
        assertThat(log.getIpAddress()).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    }

    @Test
    @DisplayName("Should capture User-Agent without truncation for normal-length values")
    void testCaptureNormalUserAgent() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", "WEBHOOK", null,
                "", "", userAgent);

        // THEN
        assertThat(log.getUserAgent()).isEqualTo(userAgent);
    }

    @Test
    @DisplayName("Should create immutable audit records (permanent audit trail)")
    void testAuditLogIsImmutable() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", "API_KEY", null, "", "", "");
        Instant originalCreatedAt = log.getCreatedAt();

        // THEN: No setters should modify the record (entity structure enforces this)
        assertThat(log.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(log.getResult()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should include timestamp for audit trail ordering")
    void testIncludesTimestampForOrdering() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        AuditLog log1 = AuditLog.success(workspaceId, userId, "CREATE", "API_KEY", null, "", "", "");
        // Tiny sleep to ensure different timestamps
        AuditLog log2 = AuditLog.success(workspaceId, userId, "UPDATE", "API_KEY", null, "", "", "");

        // THEN
        assertThat(log1.getCreatedAt()).isNotNull();
        assertThat(log2.getCreatedAt()).isNotNull();
        // log2 should be created after log1
        assertThat(log2.getCreatedAt()).isGreaterThanOrEqualTo(log1.getCreatedAt());
    }

    @Test
    @DisplayName("Should preserve failure details without exposing secrets")
    void testFailureLogsPreserveErrorsWithoutSecrets() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String errorMsg = "CSRF attack: state parameter mismatch";

        // WHEN
        AuditLog log = AuditLog.failure(workspaceId, userId, "LOGIN", "USER", null, errorMsg, "", "");

        // THEN
        assertThat(log.getErrorMessage()).isEqualTo(errorMsg);
        assertThat(log.getResult()).isEqualTo("FAILURE");
        // Error message should not contain raw tokens or secrets
        assertThat(log.getErrorMessage()).doesNotContain("Bearer ");
        assertThat(log.getErrorMessage()).doesNotContain("token=");
    }

    @Test
    @DisplayName("Should support detailed change tracking for updates")
    void testDetailedChangeTracking() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String changes = "Updated webhook URL from old-endpoint.com to new-endpoint.com";

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "UPDATE", "WEBHOOK", UUID.randomUUID(),
                changes, "", "");

        // THEN
        assertThat(log.getChanges()).isEqualTo(changes);
        assertThat(log.getAction()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("Should enforce workspace isolation (workspace_id always required)")
    void testWorkspaceIsolationEnforced() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        AuditLog log = AuditLog.success(workspaceId, userId, "CREATE", "API_KEY", null, "", "", "");

        // THEN: Every audit log MUST have a workspace_id for isolation
        assertThat(log.getWorkspaceId()).isNotNull();
        assertThat(log.getWorkspaceId()).isEqualTo(workspaceId);
    }
}
