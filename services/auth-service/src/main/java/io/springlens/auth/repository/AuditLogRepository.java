package io.springlens.auth.repository;

import io.springlens.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for audit log persistence.
 * Provides audit trail queries for compliance and forensic analysis.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs for a specific workspace with pagination.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.workspaceId = :workspaceId ORDER BY a.createdAt DESC")
    Page<AuditLog> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    /**
     * Find audit logs for a specific user.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.workspaceId = :workspaceId ORDER BY a.createdAt DESC")
    List<AuditLog> findByUserIdAndWorkspaceId(@Param("userId") UUID userId, @Param("workspaceId") UUID workspaceId);

    /**
     * Find audit logs for a specific resource (object-level audit trail).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.workspaceId = :workspaceId
              AND a.resourceType = :resourceType
              AND a.resourceId = :resourceId
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByResourceId(@Param("workspaceId") UUID workspaceId,
                                    @Param("resourceType") String resourceType,
                                    @Param("resourceId") UUID resourceId);

    /**
     * Find audit logs by action type and workspace.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.workspaceId = :workspaceId
              AND a.action = :action
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByActionAndWorkspaceId(@Param("action") String action, @Param("workspaceId") UUID workspaceId);

    /**
     * Find failed audit logs (security incidents).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.workspaceId = :workspaceId
              AND a.result = 'FAILURE'
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findFailedLogsForWorkspace(@Param("workspaceId") UUID workspaceId);

    /**
     * Find audit logs within a date range (for periodic compliance reviews).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.workspaceId = :workspaceId
              AND a.createdAt BETWEEN :startTime AND :endTime
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByWorkspaceAndDateRange(
            @Param("workspaceId") UUID workspaceId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
}
