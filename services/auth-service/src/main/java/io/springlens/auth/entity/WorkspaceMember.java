package io.springlens.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_members")
public class WorkspaceMember {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false)
    private String role = "developer";

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected WorkspaceMember() {
    }

    public static WorkspaceMember create(UUID workspaceId, UUID userId, String role) {
        var m = new WorkspaceMember();
        m.id = UUID.randomUUID();
        m.workspaceId = workspaceId;
        m.userId = userId;
        m.role = role;
        m.joinedAt = Instant.now();
        return m;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getUserId() { return userId; }
    public String getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setRole(String role) { this.role = role; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
