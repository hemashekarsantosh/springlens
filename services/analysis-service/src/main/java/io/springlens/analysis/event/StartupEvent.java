package io.springlens.analysis.event;

import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event consumed from startup.events topic.
 * Must mirror io.springlens.ingestion.event.StartupEvent schema.
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
        @Nullable List<BeanEventData> beans,
        @Nullable List<PhaseEventData> phases,
        @Nullable List<AutoconfigEventData> autoconfigurations,
        Instant capturedAt) implements Serializable {

    public record BeanEventData(
            String beanName,
            String className,
            int durationMs,
            int startMs,
            @Nullable List<String> dependencies,
            @Nullable String contextId) {
    }

    public record PhaseEventData(
            String phaseName,
            int durationMs,
            int startMs) {
    }

    public record AutoconfigEventData(
            String className,
            boolean matched,
            int durationMs,
            int conditionEvaluationMs) {
    }
}
