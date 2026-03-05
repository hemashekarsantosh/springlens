package io.springlens.auth.repository;

import io.springlens.auth.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    @Query("SELECT m FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId AND m.deletedAt IS NULL")
    List<WorkspaceMember> findActiveByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndDeletedAtIsNull(UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);
}
