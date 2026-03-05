// MEDIUM-001 Remediation: Extract TimelineMapper service
// This reduces complexity in StartupEventConsumer from 8 to 5-6 branches
// Place in: services/analysis-service/src/main/java/io/springlens/analysis/mapper/TimelineMapper.java

package io.springlens.analysis.mapper;

import io.springlens.analysis.analyzer.BeanGraphAnalyzer;
import io.springlens.analysis.analyzer.PhaseAnalyzer;
import io.springlens.analysis.entity.StartupTimeline;
import io.springlens.analysis.event.StartupEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps startup events and analysis results to StartupTimeline entities.
 * Extracts JSONB building logic from StartupEventConsumer for better testability.
 */
@Component
public class TimelineMapper {

    /**
     * Maps analysis results to a StartupTimeline entity with JSONB structures.
     */
    public StartupTimeline mapToTimeline(
            StartupEvent event,
            BeanGraphAnalyzer.BeanGraphResult graphResult,
            List<PhaseAnalyzer.PhaseResult> phaseResults) {

        return StartupTimeline.create(
                event.snapshotId(),
                event.workspaceId(),
                event.projectId(),
                event.environmentName(),
                event.gitCommitSha(),
                event.totalStartupMs(),
                graphResult.bottleneckBeanNames().size(),
                Optional.ofNullable(event.beans()).orElse(List.of()).size(),
                buildTimelineData(event, graphResult, phaseResults),
                buildBeanGraphData(graphResult));
    }

    /**
     * Builds JSONB timeline data with beans, phases, and autoconfigurations.
     */
    private Map<String, Object> buildTimelineData(
            StartupEvent event,
            BeanGraphAnalyzer.BeanGraphResult graphResult,
            List<PhaseAnalyzer.PhaseResult> phaseResults) {

        var beanSet = graphResult.bottleneckBeanNames();
        var beansList = buildBeansList(event, beanSet);
        var phaseList = buildPhaseList(phaseResults);
        var autoconfigList = buildAutoconfigList(event);

        return Map.of(
                "snapshot_id", event.snapshotId().toString(),
                "total_startup_ms", event.totalStartupMs(),
                "phases", phaseList,
                "beans", beansList,
                "autoconfigurations", autoconfigList);
    }

    private List<Map<String, Object>> buildBeansList(
            StartupEvent event,
            List<String> bottleneckBeanNames) {

        List<Map<String, Object>> beansList = new ArrayList<>();
        Optional.ofNullable(event.beans()).orElse(List.of()).forEach(bean -> {
            Map<String, Object> b = new HashMap<>();
            b.put("bean_name", bean.beanName());
            b.put("class_name", bean.className());
            b.put("duration_ms", bean.durationMs());
            b.put("start_ms", bean.startMs());
            b.put("is_bottleneck", bottleneckBeanNames.contains(bean.beanName()));
            b.put("dependencies", Optional.ofNullable(bean.dependencies()).orElse(List.of()));
            beansList.add(b);
        });
        return beansList;
    }

    private List<Map<String, Object>> buildPhaseList(List<PhaseAnalyzer.PhaseResult> phaseResults) {
        List<Map<String, Object>> phaseList = new ArrayList<>();
        phaseResults.forEach(phase -> {
            Map<String, Object> p = new HashMap<>();
            p.put("phase_name", phase.phaseName());
            p.put("duration_ms", phase.durationMs());
            p.put("start_ms", phase.startMs());
            p.put("percentage_of_total", phase.percentageOfTotal());
            phaseList.add(p);
        });
        return phaseList;
    }

    private List<Map<String, Object>> buildAutoconfigList(StartupEvent event) {
        List<Map<String, Object>> autoconfigList = new ArrayList<>();
        Optional.ofNullable(event.autoconfigurations()).orElse(List.of()).forEach(ac -> {
            Map<String, Object> a = new HashMap<>();
            a.put("class_name", ac.className());
            a.put("matched", ac.matched());
            a.put("duration_ms", ac.durationMs());
            autoconfigList.add(a);
        });
        return autoconfigList;
    }

    /**
     * Builds JSONB bean graph data with nodes and edges.
     */
    private Map<String, Object> buildBeanGraphData(BeanGraphAnalyzer.BeanGraphResult graphResult) {
        List<Map<String, Object>> nodes = graphResult.nodes().stream()
                .map(n -> {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", n.id());
                    node.put("label", n.label());
                    node.put("class_name", n.className());
                    node.put("duration_ms", n.durationMs());
                    node.put("is_bottleneck", n.isBottleneck());
                    return node;
                })
                .toList();

        List<Map<String, Object>> edges = graphResult.edges().stream()
                .map(e -> Map.<String, Object>of("source", e.source(), "target", e.target()))
                .toList();

        return Map.of("nodes", nodes, "edges", edges);
    }
}
