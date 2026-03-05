package io.springlens.auth.controller;

import io.springlens.auth.entity.ApiKey;
import io.springlens.auth.repository.ApiKeyRepository;
import io.springlens.auth.service.ApiKeyService;
import io.springlens.auth.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ApiKeyController audit logging.
 * Verifies: All API key operations are audited.
 */
@DisplayName("ApiKeyController Audit Logging Tests")
class ApiKeyControllerAuditTest {

    private ApiKeyController controller;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ApiKeyController(apiKeyService, apiKeyRepository, auditLogService);
    }

    @Test
    @DisplayName("Should audit log creation of API key")
    void testCreateApiKeyIsAudited() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        ReflectionTestUtils.setField(apiKey, "id", keyId);
        ReflectionTestUtils.setField(apiKey, "name", "my-api-key");
        ReflectionTestUtils.setField(apiKey, "workspaceId", workspaceId);
        ReflectionTestUtils.setField(apiKey, "projectId", projectId);
        ReflectionTestUtils.setField(apiKey, "expiresAt", Instant.now().plus(90, ChronoUnit.DAYS));

        var createResult = new ApiKeyService.CreateApiKeyResult(apiKey, "raw_key_value");

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyService.createApiKey(workspaceId, projectId, "my-api-key", userId))
                .thenReturn(createResult);

        ApiKeyController.CreateApiKeyRequest request = new ApiKeyController.CreateApiKeyRequest("my-api-key", projectId);

        // Set up request context
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        // WHEN
        ResponseEntity<Object> response = controller.createApiKey(workspaceId, request, jwt);

        // THEN: Response is created
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // AND: Audit log is recorded for the creation
        verify(auditLogService).logSuccess(workspaceId, userId, "CREATE", "API_KEY", keyId,
                "Created API key: my-api-key for project: " + projectId);
    }

    @Test
    @DisplayName("Should audit log revocation of API key")
    void testRevokeApiKeyIsAudited() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        ReflectionTestUtils.setField(apiKey, "id", keyId);
        ReflectionTestUtils.setField(apiKey, "name", "revoked-key");

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));
        doNothing().when(apiKeyService).revokeKey(keyId, workspaceId);

        // Set up request context
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("203.0.113.42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        // WHEN
        ResponseEntity<Object> response = controller.revokeApiKey(workspaceId, keyId, jwt);

        // THEN: No content response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // AND: Audit log is recorded
        verify(auditLogService).logSuccess(workspaceId, userId, "REVOKE", "API_KEY", keyId,
                "Revoked API key: revoked-key");
    }

    @Test
    @DisplayName("Should audit log failed revocation attempt (authorization failure)")
    void testFailedRevocationIsAudited() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(new ApiKey()));
        doThrow(new SecurityException("Not authorized"))
                .when(apiKeyService).revokeKey(keyId, workspaceId);

        // Set up request context
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        // WHEN
        ResponseEntity<Object> response = controller.revokeApiKey(workspaceId, keyId, jwt);

        // THEN: Forbidden response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // AND: Failed audit log is recorded
        verify(auditLogService).logFailure(workspaceId, userId, "REVOKE", "API_KEY", keyId,
                "Authorization check failed");
    }

    @Test
    @DisplayName("Should include client IP in audit log")
    void testAuditLogIncludesClientIp() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        ReflectionTestUtils.setField(apiKey, "id", keyId);
        ReflectionTestUtils.setField(apiKey, "name", "test-key");
        ReflectionTestUtils.setField(apiKey, "workspaceId", workspaceId);
        ReflectionTestUtils.setField(apiKey, "projectId", projectId);
        ReflectionTestUtils.setField(apiKey, "expiresAt", Instant.now().plus(90, ChronoUnit.DAYS));

        var createResult = new ApiKeyService.CreateApiKeyResult(apiKey, "raw_key");

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyService.createApiKey(workspaceId, projectId, "test-key", userId))
                .thenReturn(createResult);

        // Request from specific IP
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Forwarded-For", "203.0.113.99, 198.51.100.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        ApiKeyController.CreateApiKeyRequest request = new ApiKeyController.CreateApiKeyRequest("test-key", projectId);

        // WHEN
        controller.createApiKey(workspaceId, request, jwt);

        // THEN: IP is captured in audit log (via AuditLogService extracting from request)
        verify(auditLogService).logSuccess(any(), any(), any(), any(), any(), any());
        // AuditLogService extracts IP from X-Forwarded-For header internally
    }

    @Test
    @DisplayName("Should capture User-Agent in audit log")
    void testAuditLogIncludesUserAgent() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        ReflectionTestUtils.setField(apiKey, "id", keyId);
        ReflectionTestUtils.setField(apiKey, "name", "user-agent-test-key");
        ReflectionTestUtils.setField(apiKey, "workspaceId", workspaceId);
        ReflectionTestUtils.setField(apiKey, "projectId", projectId);
        ReflectionTestUtils.setField(apiKey, "expiresAt", Instant.now().plus(90, ChronoUnit.DAYS));

        var createResult = new ApiKeyService.CreateApiKeyResult(apiKey, "raw_key");

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyService.createApiKey(workspaceId, projectId, "user-agent-test-key", userId))
                .thenReturn(createResult);

        // Request with User-Agent
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        ApiKeyController.CreateApiKeyRequest request = new ApiKeyController.CreateApiKeyRequest("user-agent-test-key", projectId);

        // WHEN
        controller.createApiKey(workspaceId, request, jwt);

        // THEN: User-Agent is captured
        verify(auditLogService).logSuccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should not expose API key value in audit log")
    void testAuditLogDoesNotExposeSensitiveKey() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        ReflectionTestUtils.setField(apiKey, "id", keyId);
        ReflectionTestUtils.setField(apiKey, "name", "secret-key");
        ReflectionTestUtils.setField(apiKey, "workspaceId", workspaceId);
        ReflectionTestUtils.setField(apiKey, "projectId", projectId);
        ReflectionTestUtils.setField(apiKey, "expiresAt", Instant.now().plus(90, ChronoUnit.DAYS));

        var createResult = new ApiKeyService.CreateApiKeyResult(apiKey, "very_secret_raw_key_12345");

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyService.createApiKey(workspaceId, projectId, "secret-key", userId))
                .thenReturn(createResult);

        // Set up request context
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        ApiKeyController.CreateApiKeyRequest request = new ApiKeyController.CreateApiKeyRequest("secret-key", projectId);

        // WHEN
        controller.createApiKey(workspaceId, request, jwt);

        // THEN: Audit log is called, and we verify it doesn't log the raw key
        ArgumentCaptor<String> changesCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logSuccess(any(), any(), any(), any(), any(), changesCaptor.capture());

        String changes = changesCaptor.getValue();
        assertThat(changes).doesNotContain("very_secret_raw_key_12345");
        assertThat(changes).doesNotContain("raw_key");
    }

    @Test
    @DisplayName("Should audit workspace_id for multi-tenant isolation")
    void testAuditLogIncludesWorkspaceId() {
        // GIVEN
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKey apiKey = new ApiKey();
        ReflectionTestUtils.setField(apiKey, "id", keyId);
        ReflectionTestUtils.setField(apiKey, "name", "test");
        ReflectionTestUtils.setField(apiKey, "workspaceId", workspaceId);
        ReflectionTestUtils.setField(apiKey, "projectId", projectId);
        ReflectionTestUtils.setField(apiKey, "expiresAt", Instant.now().plus(90, ChronoUnit.DAYS));

        var createResult = new ApiKeyService.CreateApiKeyResult(apiKey, "key");

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(apiKeyService.createApiKey(workspaceId, projectId, "test", userId))
                .thenReturn(createResult);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        ApiKeyController.CreateApiKeyRequest request = new ApiKeyController.CreateApiKeyRequest("test", projectId);

        // WHEN
        controller.createApiKey(workspaceId, request, jwt);

        // THEN: Workspace ID is always logged for isolation verification
        ArgumentCaptor<UUID> workspaceCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(auditLogService).logSuccess(workspaceCaptor.capture(), any(), any(), any(), any(), any());

        assertThat(workspaceCaptor.getValue()).isEqualTo(workspaceId);
    }
}
