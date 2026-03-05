package io.springlens.shared;

import java.util.UUID;

/**
 * Thread-local holder for the current workspace (tenant) ID.
 * Must be cleared at the end of every request to prevent leakage across threads.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> WORKSPACE_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setWorkspaceId(UUID workspaceId) {
        WORKSPACE_ID.set(workspaceId);
    }

    public static UUID getWorkspaceId() {
        return WORKSPACE_ID.get();
    }

    public static void clear() {
        WORKSPACE_ID.remove();
    }
}
