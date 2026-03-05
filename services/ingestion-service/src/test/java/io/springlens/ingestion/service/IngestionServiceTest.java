package io.springlens.ingestion.service;

import io.springlens.ingestion.dto.BeanEventDto;
import io.springlens.ingestion.dto.PhaseEventDto;
import io.springlens.ingestion.dto.StartupSnapshotRequest;
import io.springlens.ingestion.entity.StartupSnapshot;
import io.springlens.ingestion.event.StartupEvent;
import io.springlens.ingestion.repository.StartupSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock private StartupSnapshotRepository snapshotRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private KafkaTemplate<String, StartupEvent> kafkaTemplate;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new IngestionService(snapshotRepository, redisTemplate, kafkaTemplate);
    }

    @Test
    void ingest_newSnapshot_persistsAndPublishes() {
        var projectId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var request = createRequest();
        var savedSnapshot = StartupSnapshot.from(request, projectId, workspaceId);

        when(valueOps.get(anyString())).thenReturn(null);
        when(snapshotRepository.save(any())).thenReturn(savedSnapshot);
        when(kafkaTemplate.send(anyString(), anyString(), any(StartupEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        var response = service.ingest(request, projectId, workspaceId);

        assertThat(response.snapshotId()).isNotNull();
        assertThat(response.status()).isEqualTo("queued");
        verify(snapshotRepository).save(any(StartupSnapshot.class));
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
        verify(kafkaTemplate).send(eq("startup.events"), anyString(), any(StartupEvent.class));
    }

    @Test
    void ingest_deduplicateWithinWindow_returnsExisting() {
        var projectId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var request = createRequest();
        var existingSnapshotId = UUID.randomUUID().toString();

        when(valueOps.get(anyString())).thenReturn(existingSnapshotId);

        var response = service.ingest(request, projectId, workspaceId);

        assertThat(response.snapshotId()).isEqualTo(UUID.fromString(existingSnapshotId));
        assertThat(response.status()).isEqualTo("deduplicated");
        verify(snapshotRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void ingest_storesIdempotencyKey() {
        var projectId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var request = createRequest();
        var savedSnapshot = StartupSnapshot.from(request, projectId, workspaceId);

        when(valueOps.get(anyString())).thenReturn(null);
        when(snapshotRepository.save(any())).thenReturn(savedSnapshot);
        when(kafkaTemplate.send(anyString(), anyString(), any(StartupEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.ingest(request, projectId, workspaceId);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), durationCaptor.capture());

        assertThat(keyCaptor.getValue())
                .contains(projectId.toString())
                .contains(request.environment())
                .contains(request.gitCommitSha());
        assertThat(durationCaptor.getValue().toSeconds()).isEqualTo(60);
    }

    @Test
    void getStatus_existingSnapshot_returnsStatus() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var snapshot = new StartupSnapshot();

        when(snapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId))
                .thenReturn(java.util.Optional.of(snapshot));

        var response = service.getStatus(snapshotId, workspaceId);

        assertThat(response).isNotNull();
        verify(snapshotRepository).findByIdAndWorkspaceId(snapshotId, workspaceId);
    }

    @Test
    void getStatus_notFound_throwsException() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();

        when(snapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getStatus(snapshotId, workspaceId))
                .isInstanceOf(io.springlens.ingestion.exception.ResourceNotFoundException.class);
    }

    @Test
    void checkBudget_withinBudget_returns200() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var snapshot = new StartupSnapshot();

        when(snapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId))
                .thenReturn(java.util.Optional.of(snapshot));

        // Reflect to set totalStartupMs
        try {
            var field = StartupSnapshot.class.getDeclaredField("totalStartupMs");
            field.setAccessible(true);
            field.setInt(snapshot, 1000);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        var response = service.checkBudget(snapshotId, 2000, workspaceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void checkBudget_exceedsBudget_returns422() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var snapshot = new StartupSnapshot();

        when(snapshotRepository.findByIdAndWorkspaceId(snapshotId, workspaceId))
                .thenReturn(java.util.Optional.of(snapshot));

        try {
            var field = StartupSnapshot.class.getDeclaredField("totalStartupMs");
            field.setAccessible(true);
            field.setInt(snapshot, 3000);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        var response = service.checkBudget(snapshotId, 2000, workspaceId);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    private StartupSnapshotRequest createRequest() {
        return new StartupSnapshotRequest(
                "staging",
                "abc123def456",
                2500,
                "3.3.2",
                "21.0.1",
                "1.0.0",
                "localhost",
                List.of(new BeanEventDto("bean1", "com.example.Bean1", 100, 0, List.of())),
                List.of(new PhaseEventDto("bootstrap", 500, 0)),
                List.of()
        );
    }
}
