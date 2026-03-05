package io.springlens.ingestion.controller;

import io.springlens.ingestion.dto.IngestResponse;
import io.springlens.ingestion.dto.StartupSnapshotRequest;
import io.springlens.ingestion.service.IngestionService;
import io.springlens.shared.ErrorResponseBuilder;
import io.springlens.shared.RateLimitingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Startup snapshot ingestion endpoint.
 * ✅ HARDENED: Rate limiting prevents DoS attacks on this high-volume endpoint.
 */
@RestController
@RequestMapping("/v1")
public class IngestController {

    private final IngestionService ingestionService;
    private final RateLimitingService rateLimitingService;

    public IngestController(IngestionService ingestionService,
                           RateLimitingService rateLimitingService) {
        this.ingestionService = ingestionService;
        this.rateLimitingService = rateLimitingService;
    }

    /**
     * POST /v1/ingest
     * ✅ SECURITY: Rate limited to 100 requests per minute per endpoint
     */
    @PostMapping("/ingest")
    public ResponseEntity<Object> ingest(
            @Valid @RequestBody StartupSnapshotRequest request,
            @RequestAttribute("projectId") UUID projectId,
            @RequestAttribute("workspaceId") UUID workspaceId) {

        // ✅ FIXED: Check rate limit before processing
        if (!rateLimitingService.canIngest()) {
            return ResponseEntity.status(429)
                    .body(ErrorResponseBuilder.rateLimited());
        }

        var result = ingestionService.ingest(request, projectId, workspaceId);

        var status = result.isDeduplicated() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/snapshots/{snapshotId}/status")
    public ResponseEntity<Object> getStatus(
            @PathVariable UUID snapshotId,
            @RequestAttribute("workspaceId") UUID workspaceId) {

        return ResponseEntity.ok(ingestionService.getStatus(snapshotId, workspaceId));
    }

    @GetMapping("/snapshots/{snapshotId}/budget-check")
    public ResponseEntity<Object> checkBudget(
            @PathVariable UUID snapshotId,
            @RequestParam("budget_ms") @Min(100) @Max(300000) int budgetMs,
            @RequestAttribute("workspaceId") UUID workspaceId) {

        return ingestionService.checkBudget(snapshotId, budgetMs, workspaceId);
    }

    @GetMapping("/healthz")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
