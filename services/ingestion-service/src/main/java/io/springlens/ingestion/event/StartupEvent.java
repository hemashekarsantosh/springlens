package io.springlens.ingestion.event;

import io.springlens.ingestion.dto.AutoconfigurationEventDto;
import io.springlens.ingestion.dto.BeanEventDto;
import io.springlens.ingestion.dto.PhaseEventDto;
import io.springlens.ingestion.dto.StartupSnapshotRequest;
import io.springlens.ingestion.entity.StartupSnapshot;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event published to startup.events topic after a snapshot is persisted.
 * Serialized as JSON via Spring Kafka's JsonSerializer.
 */
public record StartupEvent(
        UUID snapshotId,
        UUID workspaceId,
        UUID projectId,
        String environmentName,
        String gitCommitSha,
        int totalStartupMs,
        String springBootVersion,
        String javaVersion,
        List<BeanEventDto> beans,
        List<PhaseEventDto> phases,
        List<AutoconfigurationEventDto> autoconfigurations,
        Instant capturedAt) implements Serializable {

    public static StartupEvent from(StartupSnapshot snapshot, StartupSnapshotRequest request) {
        return new StartupEvent(
                snapshot.getId(),
                snapshot.getWorkspaceId(),
                snapshot.getProjectId(),
                snapshot.getEnvironmentName(),
                snapshot.getGitCommitSha(),
                snapshot.getTotalStartupMs(),
                snapshot.getSpringBootVersion(),
                snapshot.getJavaVersion(),
                request.beans(),
                request.phases(),
                request.autoconfigurations() != null ? request.autoconfigurations() : List.of(),
                snapshot.getCapturedAt());
    }
}
