package io.springlens.ingestion.repository;

import io.springlens.ingestion.entity.StartupSnapshot;
import io.springlens.ingestion.entity.StartupSnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StartupSnapshotRepository extends JpaRepository<StartupSnapshot, StartupSnapshotId> {

    @Query("SELECT s FROM StartupSnapshot s WHERE s.id = :id AND s.workspaceId = :workspaceId AND s.deletedAt IS NULL")
    Optional<StartupSnapshot> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT s FROM StartupSnapshot s
            WHERE s.projectId = :projectId
              AND s.workspaceId = :workspaceId
              AND s.environmentName = :environment
              AND s.gitCommitSha = :commitSha
              AND s.deletedAt IS NULL
            ORDER BY s.capturedAt DESC
            LIMIT 1
            """)
    Optional<StartupSnapshot> findLatestByCommit(
            @Param("projectId") UUID projectId,
            @Param("workspaceId") UUID workspaceId,
            @Param("environment") String environment,
            @Param("commitSha") String commitSha);
}
