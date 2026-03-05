package io.springlens.notification.repository;

import io.springlens.notification.entity.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {

    @Query("""
            SELECT wc FROM WebhookConfig wc
            WHERE wc.workspaceId = :workspaceId
              AND wc.enabled = true
              AND (wc.projectId IS NULL OR wc.projectId = :projectId)
            """)
    List<WebhookConfig> findEnabledForDelivery(
            @Param("workspaceId") UUID workspaceId,
            @Param("projectId") UUID projectId);

    List<WebhookConfig> findByWorkspaceId(UUID workspaceId);
}
