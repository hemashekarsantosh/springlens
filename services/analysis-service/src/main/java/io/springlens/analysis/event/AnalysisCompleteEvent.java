package io.springlens.analysis.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event published to analysis.complete topic after analysis finishes.
 * Consumed by recommendation-service.
 */
public record AnalysisCompleteEvent(
        UUID snapshotId,
        UUID workspaceId,
        UUID projectId,
        String environmentName,
        String gitCommitSha,
        int totalStartupMs,
        int bottleneckCount,
        List<BeanAnalysis> bottlenecks,
        List<PhaseBreakdown> phases,
        List<AutoconfigAnalysis> autoconfigurations,
        Instant analyzedAt) implements Serializable {

    public record BeanAnalysis(
            String beanName,
            String className,
            int durationMs,
            boolean isBottleneck,
            List<String> dependencies) {
    }

    public record PhaseBreakdown(
            String phaseName,
            int durationMs,
            int startMs,
            double percentageOfTotal) {
    }

    public record AutoconfigAnalysis(
            String className,
            boolean matched,
            int durationMs) {
    }
}
