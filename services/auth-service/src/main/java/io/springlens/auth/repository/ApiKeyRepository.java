package io.springlens.auth.repository;

import io.springlens.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("""
            SELECT k FROM ApiKey k
            WHERE k.workspaceId = :workspaceId
              AND k.revokedAt IS NULL
              AND (k.expiresAt IS NULL OR k.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<ApiKey> findActiveByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT k FROM ApiKey k
            WHERE k.projectId = :projectId
              AND k.workspaceId = :workspaceId
              AND k.revokedAt IS NULL
              AND (k.expiresAt IS NULL OR k.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<ApiKey> findActiveByProjectId(@Param("projectId") UUID projectId,
                                        @Param("workspaceId") UUID workspaceId);
}
