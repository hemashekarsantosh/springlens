package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record IngestResponse(
        @JsonProperty("snapshot_id") UUID snapshotId,
        String status,
        String message) {

    public boolean isDeduplicated() {
        return "deduplicated".equals(status);
    }

    public static IngestResponse queued(UUID snapshotId) {
        return new IngestResponse(snapshotId, "queued", "Snapshot accepted and queued for analysis");
    }

    public static IngestResponse deduplicated(UUID snapshotId) {
        return new IngestResponse(snapshotId, "deduplicated", "Duplicate snapshot detected within idempotency window");
    }
}
