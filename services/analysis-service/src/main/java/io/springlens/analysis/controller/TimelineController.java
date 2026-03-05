package io.springlens.analysis.controller;

import io.springlens.analysis.entity.StartupTimeline;
import io.springlens.analysis.repository.StartupTimelineRepository;
import io.springlens.shared.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Timeline endpoint for analyzing Spring startup performance.
 * ✅ HARDENED: All repository queries now enforce project_id and workspace_id filtering.
 * This prevents cross-project data access even if projectId is tampered in the request.
 */
@RestController
@RequestMapping("/v1")
public class TimelineController {

    private static final Logger log = LoggerFactory.getLogger(TimelineController.class);

    private final StartupTimelineRepository timelineRepository;

    public TimelineController(StartupTimelineRepository timelineRepository) {
        this.timelineRepository = timelineRepository;
    }

    /**
     * GET /v1/projects/{projectId}/snapshots
     * ✅ HARDENED: Pagination limit is bounded [1, 100] to prevent DoS
     */
    @GetMapping("/projects/{projectId}/snapshots")
    public ResponseEntity<Map<String, Object>> listSnapshots(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);
        log.info("List snapshots project={} env={} workspace={}", projectId, environment, workspaceId);

        int offset = cursor != null ? decodeCursor(cursor) : 0;
        var page = timelineRepository.findByProjectFiltered(
                projectId, workspaceId, environment, from, to,
                PageRequest.of(offset / Math.max(limit, 1), Math.min(limit, 100)));

        List<Map<String, Object>> data = page.getContent().stream()
                .map(this::toSnapshotSummary)
                .toList();

        String nextCursor = page.hasNext() ? encodeCursor(offset + limit) : null;

        return ResponseEntity.ok(Map.of(
                "data", data,
                "cursor", nextCursor != null ? nextCursor : "",
                "total", page.getTotalElements()));
    }

    /**
     * GET /v1/projects/{projectId}/snapshots/{snapshotId}/timeline
     * ✅ HARDENED: Query enforces project_id and workspace_id at database level
     */
    @GetMapping("/projects/{projectId}/snapshots/{snapshotId}/timeline")
    public ResponseEntity<Object> getTimeline(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId,
            @RequestParam(defaultValue = "0") int minDurationMs,
            @RequestParam(required = false) String packagePrefix,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);

        // ✅ FIXED: Use project-filtered query (prevents cross-project access)
        return timelineRepository.findBySnapshotIdAndProject(snapshotId, projectId, workspaceId)
                .map(timeline -> {
                    var data = applyFilters(timeline.getTimelineData(), minDurationMs, packagePrefix);
                    return ResponseEntity.ok((Object) data);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /v1/projects/{projectId}/snapshots/{snapshotId}/bean-graph
     * ✅ HARDENED: Query enforces project_id and workspace_id at database level
     */
    @GetMapping("/projects/{projectId}/snapshots/{snapshotId}/bean-graph")
    public ResponseEntity<Object> getBeanGraph(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);

        // ✅ FIXED: Use project-filtered query (prevents cross-project access)
        return timelineRepository.findBySnapshotIdAndProject(snapshotId, projectId, workspaceId)
                .map(timeline -> ResponseEntity.ok((Object) timeline.getBeanGraphData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * GET /v1/projects/{projectId}/compare?baseline={id}&target={id}
     * ✅ HARDENED: Queries enforce project_id and workspace_id at database level
     */
    @GetMapping("/projects/{projectId}/compare")
    public ResponseEntity<Object> compareSnapshots(
            @PathVariable UUID projectId,
            @RequestParam UUID baseline,
            @RequestParam UUID target,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);

        // ✅ FIXED: Use project-filtered queries (prevents cross-project access)
        var baselineTimeline = timelineRepository.findBySnapshotIdAndProject(baseline, projectId, workspaceId)
                .orElse(null);
        var targetTimeline = timelineRepository.findBySnapshotIdAndProject(target, projectId, workspaceId)
                .orElse(null);

        if (baselineTimeline == null || targetTimeline == null) {
            return ResponseEntity.notFound().build();
        }

        var comparison = buildComparison(baselineTimeline, targetTimeline);
        return ResponseEntity.ok(comparison);
    }

    /**
     * GET /v1/workspaces/{workspaceId}/overview
     * ✅ HARDENED: Uses workspace-filtered query (safe because workspaceId is validated)
     */
    @GetMapping("/workspaces/{workspaceId}/overview")
    public ResponseEntity<Object> getWorkspaceOverview(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID jwtWorkspaceId = extractWorkspaceId(jwt);
        if (!workspaceId.equals(jwtWorkspaceId)) {
            return ResponseEntity.status(403).body(
                    ErrorResponse.of("FORBIDDEN", "Access denied to workspace", null));
        }

        // ✅ FIXED: Get all projects' latest timelines (safe - workspaceId already validated)
        var timelines = timelineRepository.findLatestByWorkspaceAllProjects(workspaceId);
        // Grouping timelines by project
        Map<UUID, List<StartupTimeline>> byProject = timelines.stream()
                .collect(Collectors.groupingBy(StartupTimeline::getProjectId));

        List<Map<String, Object>> projects = byProject.entrySet().stream()
                .map(e -> {
                    var latest = e.getValue().stream()
                            .max(Comparator.comparing(StartupTimeline::getAnalyzedAt))
                            .orElseThrow();
                    var history = e.getValue();
                    String trend = computeTrend(history);
                    return Map.<String, Object>of(
                            "project_id", e.getKey().toString(),
                            "project_name", e.getKey().toString(),
                            "latest_startup_ms", latest.getTotalStartupMs(),
                            "trend_7d", trend,
                            "last_seen", latest.getAnalyzedAt().toString());
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "workspace_id", workspaceId.toString(),
                "project_count", byProject.size(),
                "projects", projects));
    }

    @GetMapping("/healthz")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID extractWorkspaceId(Jwt jwt) {
        if (jwt == null) return UUID.randomUUID(); // should not happen in production
        String wsId = jwt.getClaimAsString("workspace_id");
        return wsId != null ? UUID.fromString(wsId) : UUID.randomUUID();
    }

    private Map<String, Object> toSnapshotSummary(StartupTimeline t) {
        return Map.of(
                "snapshot_id", t.getSnapshotId().toString(),
                "environment", t.getEnvironmentName(),
                "total_startup_ms", t.getTotalStartupMs(),
                "bean_count", t.getBeanCount(),
                "bottleneck_count", t.getBottleneckCount(),
                "git_commit_sha", t.getGitCommitSha(),
                "captured_at", t.getAnalyzedAt().toString(),
                "trend", "stable");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyFilters(Map<String, Object> timelineData,
                                              int minDurationMs,
                                              String packagePrefix) {
        if (minDurationMs == 0 && packagePrefix == null) return timelineData;

        var result = new HashMap<>(timelineData);
        if (timelineData.get("beans") instanceof List<?> beans) {
            var filtered = beans.stream()
                    .filter(b -> {
                        if (!(b instanceof Map<?, ?> beanMap)) return true;
                        int duration = (int) beanMap.getOrDefault("duration_ms", 0);
                        String className = (String) beanMap.getOrDefault("class_name", "");
                        boolean durationOk = duration >= minDurationMs;
                        boolean packageOk = packagePrefix == null || className.startsWith(packagePrefix);
                        return durationOk && packageOk;
                    })
                    .toList();
            result.put("beans", filtered);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildComparison(StartupTimeline baseline, StartupTimeline target) {
        int totalDelta = target.getTotalStartupMs() - baseline.getTotalStartupMs();

        // Extract beans from timeline data
        Map<String, Integer> baselineBeans = extractBeanDurations(baseline.getTimelineData());
        Map<String, Integer> targetBeans = extractBeanDurations(target.getTimelineData());

        List<Map<String, Object>> addedBeans = targetBeans.entrySet().stream()
                .filter(e -> !baselineBeans.containsKey(e.getKey()))
                .map(e -> Map.<String, Object>of("bean_name", e.getKey(), "duration_ms", e.getValue()))
                .toList();

        List<Map<String, Object>> removedBeans = baselineBeans.entrySet().stream()
                .filter(e -> !targetBeans.containsKey(e.getKey()))
                .map(e -> Map.<String, Object>of("bean_name", e.getKey(), "duration_ms", e.getValue()))
                .toList();

        List<Map<String, Object>> changedBeans = baselineBeans.entrySet().stream()
                .filter(e -> targetBeans.containsKey(e.getKey())
                        && !e.getValue().equals(targetBeans.get(e.getKey())))
                .map(e -> {
                    int baseMs = e.getValue();
                    int targMs = targetBeans.get(e.getKey());
                    int delta = targMs - baseMs;
                    double deltaPercent = baseMs > 0 ? (delta * 100.0) / baseMs : 0.0;
                    return Map.<String, Object>of(
                            "bean_name", e.getKey(),
                            "baseline_ms", baseMs,
                            "target_ms", targMs,
                            "delta_ms", delta,
                            "delta_percent", Math.round(deltaPercent * 100.0) / 100.0);
                })
                .toList();

        return Map.of(
                "baseline_snapshot_id", baseline.getSnapshotId().toString(),
                "target_snapshot_id", target.getSnapshotId().toString(),
                "total_delta_ms", totalDelta,
                "added_beans", addedBeans,
                "removed_beans", removedBeans,
                "changed_beans", changedBeans);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> extractBeanDurations(Map<String, Object> timelineData) {
        if (!(timelineData.get("beans") instanceof List<?> beans)) return Map.of();
        Map<String, Integer> result = new HashMap<>();
        for (var b : beans) {
            if (b instanceof Map<?, ?> beanMap) {
                String name = (String) beanMap.get("bean_name");
                int duration = (int) beanMap.getOrDefault("duration_ms", 0);
                if (name != null) result.put(name, duration);
            }
        }
        return result;
    }

    private String computeTrend(List<StartupTimeline> history) {
        if (history.size() < 2) return "no_data";
        var sorted = history.stream()
                .sorted(Comparator.comparing(StartupTimeline::getAnalyzedAt))
                .toList();
        int first = sorted.get(0).getTotalStartupMs();
        int last = sorted.get(sorted.size() - 1).getTotalStartupMs();
        double delta = (last - first) * 100.0 / Math.max(first, 1);
        if (delta > 5) return "degrading";
        if (delta < -5) return "improving";
        return "stable";
    }

    private String encodeCursor(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes());
    }

    private int decodeCursor(String cursor) {
        try {
            return Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
        } catch (Exception e) {
            return 0;
        }
    }
}
