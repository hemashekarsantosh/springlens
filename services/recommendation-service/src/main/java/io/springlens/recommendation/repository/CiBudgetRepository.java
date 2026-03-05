package io.springlens.recommendation.repository;

import io.springlens.recommendation.entity.CiBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CiBudgetRepository extends JpaRepository<CiBudget, UUID> {

    List<CiBudget> findByProjectIdAndWorkspaceId(UUID projectId, UUID workspaceId);

    Optional<CiBudget> findByProjectIdAndWorkspaceIdAndEnvironment(
            UUID projectId, UUID workspaceId, String environment);
}
