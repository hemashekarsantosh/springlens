package io.springlens.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Environment() {
    }

    public static Environment create(UUID projectId, UUID workspaceId, String name) {
        var e = new Environment();
        e.id = UUID.randomUUID();
        e.projectId = projectId;
        e.workspaceId = workspaceId;
        e.name = name;
        e.createdAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
