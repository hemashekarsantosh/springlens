package io.springlens.recommendation.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event consumed from analysis.complete topic.
 * Must mirror io.springlens.analysis.event.AnalysisCompleteEvent schema.
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
