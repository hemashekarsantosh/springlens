package io.springlens.recommendation.controller;

import io.springlens.recommendation.entity.CiBudget;
import io.springlens.recommendation.entity.Recommendation;
import io.springlens.recommendation.repository.CiBudgetRepository;
import io.springlens.recommendation.repository.RecommendationRepository;
import io.springlens.shared.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Recommendation engine endpoints for suggesting Spring Boot optimizations.
 * ✅ HARDENED: All repository queries enforce project_id and workspace_id filtering.
 * This prevents cross-project data access even if projectId is tampered in the request.
 */
@RestController
@RequestMapping("/v1")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationRepository recommendationRepository;
    private final CiBudgetRepository ciBudgetRepository;

    public RecommendationController(RecommendationRepository recommendationRepository,
                                     CiBudgetRepository ciBudgetRepository) {
        this.recommendationRepository = recommendationRepository;
        this.ciBudgetRepository = ciBudgetRepository;
    }

    /**
     * GET /v1/projects/{projectId}/recommendations
     */
    @GetMapping("/projects/{projectId}/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);
        log.info("Get recommendations project={} env={} workspace={}", projectId, environment, workspaceId);

        List<Recommendation> recs = recommendationRepository.findFiltered(
                projectId, workspaceId, environment, category, status);

        // Determine if stale (based on data > 24h old)
        boolean isStale = recs.stream()
                .anyMatch(r -> r.getCreatedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS)));

        UUID latestSnapshotId = recs.isEmpty() ? null : recs.get(0).getSnapshotId();
        int totalSavings = recs.stream().mapToInt(Recommendation::getEstimatedSavingsMs).sum();

        return ResponseEntity.ok(Map.of(
                "snapshot_id", latestSnapshotId != null ? latestSnapshotId.toString() : "",
                "generated_at", recs.isEmpty() ? "" : recs.get(0).getCreatedAt().toString(),
                "is_stale", isStale,
                "total_potential_savings_ms", totalSavings,
                "recommendations", recs.stream().map(this::toDto).toList()));
    }

    /**
     * PATCH /v1/projects/{projectId}/recommendations/{recommendationId}/status
     * ✅ HARDENED: Query enforces project_id and workspace_id at database level
     */
    @PatchMapping("/projects/{projectId}/recommendations/{recommendationId}/status")
    public ResponseEntity<Object> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID recommendationId,
            @RequestBody @Valid StatusUpdateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);

        // ✅ FIXED: Use project-filtered query (prevents cross-project access)
        var rec = recommendationRepository.findByIdAndProjectAndWorkspace(
                recommendationId, projectId, workspaceId)
                .orElse(null);

        if (rec == null) {
            return ResponseEntity.notFound().build();
        }

        rec.setStatus(body.status());
        if (body.note() != null) rec.setAppliedNote(body.note());
        if ("applied".equals(body.status())) rec.setAppliedAt(Instant.now());
        rec.setUpdatedAt(Instant.now());
        recommendationRepository.save(rec);

        log.info("Updated recommendation status id={} status={}", recommendationId, body.status());
        return ResponseEntity.ok(toDto(rec));
    }

    /**
     * GET /v1/projects/{projectId}/ci-budgets
     */
    @GetMapping("/projects/{projectId}/ci-budgets")
    public ResponseEntity<Map<String, Object>> listCiBudgets(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);
        var budgets = ciBudgetRepository.findByProjectIdAndWorkspaceId(projectId, workspaceId);
        return ResponseEntity.ok(Map.of("budgets", budgets.stream().map(this::budgetToDto).toList()));
    }

    /**
     * PUT /v1/projects/{projectId}/ci-budgets
     */
    @PutMapping("/projects/{projectId}/ci-budgets")
    public ResponseEntity<Object> upsertCiBudget(
            @PathVariable UUID projectId,
            @RequestBody @Valid CiBudgetRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        UUID workspaceId = extractWorkspaceId(jwt);
        UUID userId = extractUserId(jwt);

        // BR-006: production budget requires Admin role
        if ("production".equals(body.environment())) {
            String role = jwt != null ? jwt.getClaimAsString("workspace_role") : null;
            if (!"admin".equals(role)) {
                return ResponseEntity.status(403)
                        .body(ErrorResponse.of("FORBIDDEN",
                                "Only Admin role can modify production budget thresholds", null));
            }
        }

        var existing = ciBudgetRepository.findByProjectIdAndWorkspaceIdAndEnvironment(
                projectId, workspaceId, body.environment());

        CiBudget budget;
        if (existing.isPresent()) {
            budget = existing.get();
            budget.setBudgetMs(body.budgetMs());
            budget.setAlertThresholdMs(body.alertThresholdMs());
            budget.setEnabled(body.enabled() != null ? body.enabled() : true);
            budget.setUpdatedAt(Instant.now());
        } else {
            budget = CiBudget.create(projectId, workspaceId, body.environment(),
                    body.budgetMs(), body.alertThresholdMs(),
                    body.enabled() != null ? body.enabled() : true,
                    userId);
        }

        ciBudgetRepository.save(budget);
        log.info("Upserted CI budget project={} env={} budget_ms={}", projectId, body.environment(), body.budgetMs());
        return ResponseEntity.ok(budgetToDto(budget));
    }

    @GetMapping("/healthz")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID extractWorkspaceId(Jwt jwt) {
        if (jwt == null) return UUID.randomUUID();
        String wsId = jwt.getClaimAsString("workspace_id");
        return wsId != null ? UUID.fromString(wsId) : UUID.randomUUID();
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null) return UUID.randomUUID();
        String sub = jwt.getSubject();
        try {
            return UUID.fromString(sub);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    private Map<String, Object> toDto(Recommendation r) {
        var map = new HashMap<String, Object>();
        map.put("recommendation_id", r.getId().toString());
        map.put("rank", r.getRank());
        map.put("category", r.getCategory());
        map.put("title", r.getTitle());
        map.put("description", r.getDescription());
        map.put("estimated_savings_ms", r.getEstimatedSavingsMs());
        map.put("estimated_savings_percent", r.getEstimatedSavingsPercent());
        map.put("effort", r.getEffort());
        map.put("status", r.getStatus());
        map.put("code_snippet", r.getCodeSnippet());
        map.put("config_snippet", r.getConfigSnippet());
        map.put("warnings", r.getWarnings() != null ? r.getWarnings() : List.of());
        map.put("affected_beans", r.getAffectedBeans() != null ? r.getAffectedBeans() : List.of());
        map.put("graalvm_feasibility", r.getGraalvmFeasibility());
        map.put("applied_at", r.getAppliedAt() != null ? r.getAppliedAt().toString() : null);
        return map;
    }

    private Map<String, Object> budgetToDto(CiBudget b) {
        var map = new HashMap<String, Object>();
        map.put("budget_id", b.getId().toString());
        map.put("environment", b.getEnvironment());
        map.put("budget_ms", b.getBudgetMs());
        map.put("alert_threshold_ms", b.getAlertThresholdMs());
        map.put("enabled", b.isEnabled());
        map.put("created_by", b.getCreatedBy() != null ? b.getCreatedBy().toString() : null);
        map.put("updated_at", b.getUpdatedAt().toString());
        return map;
    }

    // ─── Request DTOs ─────────────────────────────────────────────────────────

    public record StatusUpdateRequest(
            @NotBlank @Pattern(regexp = "applied|wont_fix|active") String status,
            @Size(max = 500) String note) {
    }

    public record CiBudgetRequest(
            @NotBlank @Pattern(regexp = "dev|staging|production|ci") String environment,
            @Min(100) @Max(300000) int budgetMs,
            Integer alertThresholdMs,
            Boolean enabled) {
    }
}
