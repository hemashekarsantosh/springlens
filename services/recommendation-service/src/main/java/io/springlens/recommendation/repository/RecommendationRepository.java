package io.springlens.recommendation.repository;

import io.springlens.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Recommendation entity.
 * ✅ HARDENED: All queries enforce project_id and workspace_id filtering to prevent cross-project data access.
 */
@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    @Query("""
            SELECT r FROM Recommendation r
            WHERE r.projectId = :projectId
              AND r.workspaceId = :workspaceId
              AND (:environment IS NULL OR r.environmentName = :environment)
              AND (:category IS NULL OR r.category = :category)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.rank ASC
            """)
    List<Recommendation> findFiltered(
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId,
            @Param("environment") String environment,
            @Param("category") String category,
            @Param("status") String status);

    /**
     * ✅ HARDENED: Find recommendation by ID with mandatory project isolation.
     * Prevents cross-project access by enforcing projectId check at database level.
     */
    @Query("""
            SELECT r FROM Recommendation r
            WHERE r.id = :id
              AND r.projectId = :projectId
              AND r.workspaceId = :workspaceId
            """)
    Optional<Recommendation> findByIdAndProjectAndWorkspace(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId);

    /**
     * ✅ HARDENED: Find recommendations by snapshot with mandatory project isolation.
     * Prevents cross-project access by enforcing projectId and workspaceId at database level.
     */
    @Query("""
            SELECT r FROM Recommendation r
            WHERE r.snapshotId = :snapshotId
              AND r.projectId = :projectId
              AND r.workspaceId = :workspaceId
            ORDER BY r.rank ASC
            """)
    List<Recommendation> findBySnapshotIdAndProject(
            @Param("snapshotId") UUID snapshotId,
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT r.snapshotId FROM Recommendation r
            WHERE r.projectId = :projectId
              AND r.workspaceId = :workspaceId
              AND (:environment IS NULL OR r.environmentName = :environment)
            ORDER BY r.createdAt DESC
            LIMIT 1
            """)
    Optional<UUID> findLatestSnapshotId(
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId,
            @Param("environment") String environment);
}
