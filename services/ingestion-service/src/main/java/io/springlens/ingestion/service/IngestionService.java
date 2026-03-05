package io.springlens.ingestion.service;

import io.springlens.ingestion.dto.BudgetCheckResponse;
import io.springlens.ingestion.dto.BudgetExceededResponse;
import io.springlens.ingestion.dto.IngestResponse;
import io.springlens.ingestion.dto.SnapshotStatusResponse;
import io.springlens.ingestion.dto.StartupSnapshotRequest;
import io.springlens.ingestion.entity.StartupSnapshot;
import io.springlens.ingestion.event.StartupEvent;
import io.springlens.ingestion.exception.ResourceNotFoundException;
import io.springlens.ingestion.repository.StartupSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String STARTUP_EVENTS_TOPIC = "startup.events";

    private final StartupSnapshotRepository snapshotRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, StartupEvent> kafkaTemplate;

    @Value("${springlens.ingestion.idempotency-window-seconds:60}")
    private int idempotencyWindowSeconds;

    public IngestionService(
            StartupSnapshotRepository snapshotRepository,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, StartupEvent> kafkaTemplate) {
        this.snapshotRepository = snapshotRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public IngestResponse ingest(StartupSnapshotRequest request, UUID projectId, UUID workspaceId) {
        String idempotencyKey = buildIdempotencyKey(projectId, request.environment(), request.gitCommitSha());

        // Check Redis for duplicate within idempotency window
        String existingSnapshotId = redisTemplate.opsForValue().get(IDEMPOTENCY_KEY_PREFIX + idempotencyKey);
        if (existingSnapshotId != null) {
            log.info("Deduplicated ingestion project={} commit={}", projectId, request.gitCommitSha());
            return IngestResponse.deduplicated(UUID.fromString(existingSnapshotId));
        }

        // Persist snapshot
        var snapshot = StartupSnapshot.from(request, projectId, workspaceId);
        snapshot = snapshotRepository.save(snapshot);

        // Store idempotency key in Redis
        redisTemplate.opsForValue().set(
                IDEMPOTENCY_KEY_PREFIX + idempotencyKey,
                snapshot.getId().toString(),
                Duration.ofSeconds(idempotencyWindowSeconds));

        // Publish to Kafka async
        final UUID snapshotId = snapshot.getId();
        var event = StartupEvent.from(snapshot, request);
        kafkaTemplate.send(STARTUP_EVENTS_TOPIC, snapshotId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish StartupEvent snapshot={}", snapshotId, ex);
                    } else {
                        log.debug("Published StartupEvent snapshot={} offset={}",
                                snapshotId, result.getRecordMetadata().offset());
                    }
                });

        log.info("Ingested snapshot={} project={} env={} startup_ms={}",
                snapshotId, projectId, request.environment(), request.totalStartupMs());

        return IngestResponse.queued(snapshotId);
    }

    public SnapshotStatusResponse getStatus(UUID snapshotId, UUID workspaceId) {
        return snapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId)
                .map(SnapshotStatusResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found: " + snapshotId));
    }

    public ResponseEntity<Object> checkBudget(UUID snapshotId, int budgetMs, UUID workspaceId) {
        var snapshot = snapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found: " + snapshotId));

        if (snapshot.getTotalStartupMs() <= budgetMs) {
            return ResponseEntity.ok(new BudgetCheckResponse(true, snapshot.getTotalStartupMs(), budgetMs));
        }

        return ResponseEntity.unprocessableEntity()
                .body(new BudgetExceededResponse(snapshot.getTotalStartupMs(), budgetMs));
    }

    private String buildIdempotencyKey(UUID projectId, String environment, String commitSha) {
        return projectId + ":" + environment + ":" + commitSha;
    }
}
