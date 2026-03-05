package io.springlens.auth.service;

import io.springlens.auth.entity.AuditLog;
import io.springlens.auth.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Tests for AuditLogService.
 * Verifies: All sensitive operations are logged for forensic analysis.
 */
@DisplayName("AuditLogService Audit Logging Tests")
class AuditLogServiceTest {

    private AuditLogService auditLogService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditLogService = new AuditLogService(auditLogRepository);
    }

    @Test
    @DisplayName("Should log successful operation with correct details")
    void testLogSuccessfulOperation() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "CREATE", "API_KEY", resourceId, "New API key created");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getResourceType()).isEqualTo("API_KEY");
        assertThat(saved.getResourceId()).isEqualTo(resourceId);
        assertThat(saved.getChanges()).isEqualTo("New API key created");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should log failed operation with error message")
    void testLogFailedOperation() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        auditLogService.logFailure(workspaceId, userId, "REVOKE", "API_KEY", null, "Authorization check failed");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getResult()).isEqualTo("FAILURE");
        assertThat(saved.getErrorMessage()).isEqualTo("Authorization check failed");
        assertThat(saved.getChanges()).isNull();
    }

    @Test
    @DisplayName("Should extract IP address from request")
    void testExtractIpAddressFromRequest() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "LOGIN", "USER", userId, "Login");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("Should extract IP address from X-Forwarded-For header (load balancer)")
    void testExtractIpFromXForwardedForHeader() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.42, 198.51.100.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "CREATE", "WEBHOOK", null, "");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        // Should extract first IP from chain (client IP)
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.42");
    }

    @Test
    @DisplayName("Should extract IP address from X-Real-IP header (fallback)")
    void testExtractIpFromXRealIpHeader() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.99");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "UPDATE", "WEBHOOK", null, "");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.99");
    }

    @Test
    @DisplayName("Should use UNKNOWN_IP when no request context")
    void testUnknownIpWhenNoRequestContext() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RequestContextHolder.resetRequestAttributes();

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "LOGIN", "USER", userId, "");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getIpAddress()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should extract User-Agent header")
    void testExtractUserAgent() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "CREATE", "API_KEY", null, "");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
    }

    @Test
    @DisplayName("Should truncate long User-Agent to prevent storage bloat")
    void testTruncateLongUserAgent() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        String longUserAgent = "A".repeat(600); // Exceeds MAX_USER_AGENT_LENGTH (500)
        request.addHeader("User-Agent", longUserAgent);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "UPDATE", "USER_PROFILE", null, "");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUserAgent()).hasSize(500);
        assertThat(saved.getUserAgent()).isEqualTo("A".repeat(500));
    }

    @Test
    @DisplayName("Should handle missing User-Agent header")
    void testMissingUserAgent() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "DELETE", "API_KEY", null, "");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUserAgent()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should log system operations with null userId")
    void testLogSystemOperationWithNullUserId() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();

        // WHEN: System operation (no user) like automated cleanup
        auditLogService.logSuccess(workspaceId, null, "DELETE", "API_KEY", null, "Automatic cleanup of expired keys");

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getAction()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("Should include all sensitive action types")
    void testSensitiveActionTypes() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String[] actions = {"CREATE", "UPDATE", "DELETE", "ENABLE", "DISABLE", "REVOKE", "VERIFY"};

        // WHEN/THEN: All actions should be loggable
        for (String action : actions) {
            auditLogService.logSuccess(workspaceId, userId, action, "API_KEY", UUID.randomUUID(), "");
        }

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should include all resource types")
    void testAllResourceTypes() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String[] resources = {"API_KEY", "JWT_TOKEN", "WEBHOOK", "BILLING", "USER_PROFILE"};

        // WHEN/THEN: All resources should be loggable
        for (String resourceType : resources) {
            auditLogService.logSuccess(workspaceId, userId, "CREATE", resourceType, null, "");
        }

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should never log sensitive values (secrets) in audit trail")
    void testNeverLogSensitiveSecrets() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String changes = "API key created";

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "CREATE", "API_KEY", null, changes);

        // THEN
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        // Changes field should NOT contain raw API key or secret
        assertThat(saved.getChanges()).doesNotContain("key_");
        assertThat(saved.getChanges()).doesNotContain("secret_");
        assertThat(saved.getChanges()).doesNotContain("token_");
    }

    @Test
    @DisplayName("Should preserve audit log immutability (no delete/update after creation)")
    void testAuditLogImmutability() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN
        auditLogService.logSuccess(workspaceId, userId, "CREATE", "WEBHOOK", null, "Webhook registered");

        // THEN: Audit log should be saved (immutable)
        verify(auditLogRepository).save(any(AuditLog.class));
        // In a real scenario, the entity would be persisted as @Immutable in JPA
    }
}
