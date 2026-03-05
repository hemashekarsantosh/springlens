package io.springlens.ingestion.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for StartupSnapshot (required by TimescaleDB hypertable).
 */
public class StartupSnapshotId implements Serializable {

    private UUID id;
    private Instant capturedAt;

    public StartupSnapshotId() {
    }

    public StartupSnapshotId(UUID id, Instant capturedAt) {
        this.id = id;
        this.capturedAt = capturedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StartupSnapshotId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(capturedAt, that.capturedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, capturedAt);
    }
}
