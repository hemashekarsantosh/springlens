package io.springlens.analysis.repository;

import io.springlens.analysis.entity.StartupTimeline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StartupTimelineRepository extends JpaRepository<StartupTimeline, UUID> {

    /**
     * ✅ HARDENED: Find timeline by snapshot with mandatory project isolation.
     * Prevents cross-project data access by enforcing both projectId and workspaceId.
     */
    @Query("""
            SELECT t FROM StartupTimeline t
            WHERE t.snapshotId = :snapshotId
              AND t.projectId = :projectId
              AND t.workspaceId = :workspaceId
            """)
    Optional<StartupTimeline> findBySnapshotIdAndProject(
            @Param("snapshotId") UUID snapshotId,
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT t FROM StartupTimeline t
            WHERE t.projectId = :projectId
              AND t.workspaceId = :workspaceId
              AND (:environment IS NULL OR t.environmentName = :environment)
              AND (:from IS NULL OR t.analyzedAt >= :from)
              AND (:to IS NULL OR t.analyzedAt <= :to)
            ORDER BY t.analyzedAt DESC
            """)
    Page<StartupTimeline> findByProjectFiltered(
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId,
            @Param("environment") String environment,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT t FROM StartupTimeline t
            WHERE t.workspaceId = :workspaceId
              AND t.projectId IN :projectIds
            ORDER BY t.projectId, t.analyzedAt DESC
            """)
    List<StartupTimeline> findLatestByWorkspace(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectIds") List<UUID> projectIds);

    @Query("""
            SELECT t FROM StartupTimeline t
            WHERE t.projectId = :projectId
              AND t.workspaceId = :workspaceId
            ORDER BY t.analyzedAt DESC
            LIMIT 7
            """)
    List<StartupTimeline> findLast7ForProject(
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId);

    /**
     * ✅ HARDENED: Find latest timeline for each project in a workspace.
     * Used for workspace overview - safe because workspaceId is filtered.
     */
    @Query("""
            SELECT t FROM StartupTimeline t
            WHERE t.workspaceId = :workspaceId
            ORDER BY t.projectId DESC, t.analyzedAt DESC
            """)
    List<StartupTimeline> findLatestByWorkspaceAllProjects(
            @Param("workspaceId") UUID workspaceId);
}
