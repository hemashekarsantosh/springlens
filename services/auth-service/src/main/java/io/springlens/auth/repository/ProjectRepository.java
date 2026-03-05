package io.springlens.auth.repository;

import io.springlens.auth.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p WHERE p.workspaceId = :workspaceId AND p.deletedAt IS NULL")
    List<Project> findActiveByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    Optional<Project> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
