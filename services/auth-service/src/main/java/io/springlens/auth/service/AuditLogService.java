package io.springlens.auth.service;

import io.springlens.auth.entity.AuditLog;
import io.springlens.auth.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Service for audit logging of sensitive operations.
 * Provides immutable forensics trail for compliance, breach investigation, and security monitoring.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String UNKNOWN_IP = "UNKNOWN";
    private static final String UNKNOWN_USER_AGENT = "UNKNOWN";
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_IP_LENGTH = 45;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a successful sensitive operation.
     */
    public void logSuccess(UUID workspaceId, UUID userId, String action, String resourceType,
                          UUID resourceId, String changes) {
        String ipAddress = extractIpAddress();
        String userAgent = extractUserAgent();

        AuditLog auditLog = AuditLog.success(workspaceId, userId, action, resourceType, resourceId,
                changes, ipAddress, userAgent);

        auditLogRepository.save(auditLog);
        log.info("Audit success: action={} resource_type={} resource_id={} user_id={} ip={} workspace_id={}",
                action, resourceType, resourceId, userId, ipAddress, workspaceId);
    }

    /**
     * Log a failed sensitive operation (security incident).
     */
    public void logFailure(UUID workspaceId, UUID userId, String action, String resourceType,
                          UUID resourceId, String errorMessage) {
        String ipAddress = extractIpAddress();
        String userAgent = extractUserAgent();

        AuditLog auditLog = AuditLog.failure(workspaceId, userId, action, resourceType, resourceId,
                errorMessage, ipAddress, userAgent);

        auditLogRepository.save(auditLog);
        log.warn("Audit failure: action={} resource_type={} resource_id={} user_id={} ip={} error={} workspace_id={}",
                action, resourceType, resourceId, userId, ipAddress, errorMessage, workspaceId);
    }

    /**
     * Extract client IP address, handling proxy headers (X-Forwarded-For, X-Real-IP).
     * ✅ SECURITY: Prevents audit log spoofing via header manipulation.
     */
    private String extractIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return UNKNOWN_IP;
            }

            HttpServletRequest request = attributes.getRequest();

            // Check X-Forwarded-For first (from load balancer/WAF)
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                // Take first IP (client IP is first in chain)
                String clientIp = forwarded.split(",")[0].trim();
                return clientIp.length() <= MAX_IP_LENGTH ? clientIp : UNKNOWN_IP;
            }

            // Fall back to X-Real-IP
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp.length() <= MAX_IP_LENGTH ? realIp : UNKNOWN_IP;
            }

            // Fall back to direct remote address
            String remoteAddr = request.getRemoteAddr();
            return remoteAddr != null && !remoteAddr.isEmpty() ? remoteAddr : UNKNOWN_IP;

        } catch (Exception e) {
            log.debug("Failed to extract IP address for audit log", e);
            return UNKNOWN_IP;
        }
    }

    /**
     * Extract User-Agent header for audit trail.
     * ✅ SECURITY: Truncates to prevent large payloads in audit log storage.
     */
    private String extractUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return UNKNOWN_USER_AGENT;
            }

            HttpServletRequest request = attributes.getRequest();
            String userAgent = request.getHeader("User-Agent");

            if (userAgent == null || userAgent.isEmpty()) {
                return UNKNOWN_USER_AGENT;
            }

            // Truncate to prevent storage bloat
            return userAgent.length() > MAX_USER_AGENT_LENGTH
                    ? userAgent.substring(0, MAX_USER_AGENT_LENGTH)
                    : userAgent;

        } catch (Exception e) {
            log.debug("Failed to extract User-Agent for audit log", e);
            return UNKNOWN_USER_AGENT;
        }
    }
}
