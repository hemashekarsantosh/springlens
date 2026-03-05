package io.springlens.analysis.consumer;

import io.springlens.analysis.entity.StartupTimeline;
import io.springlens.analysis.event.StartupEvent;
import io.springlens.analysis.repository.StartupTimelineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class StartupEventConsumerIT {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("springlens_analysis")
            .withUsername("springlens")
            .withPassword("password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KafkaTemplate<String, StartupEvent> kafkaTemplate;

    @Autowired
    private StartupTimelineRepository timelineRepository;

    @Test
    void consumeStartupEvent_persists Timeline() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var event = createStartupEvent(snapshotId, workspaceId, projectId);

        kafkaTemplate.send("startup.events", snapshotId.toString(), event);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var timeline = timelineRepository.findBySnapshotId(snapshotId);
                    assertThat(timeline).isPresent();
                    assertThat(timeline.get().getSnapshotId()).isEqualTo(snapshotId);
                });
    }

    @Test
    void consumeStartupEvent_idempotent_doesNotDuplicate() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var event = createStartupEvent(snapshotId, workspaceId, projectId);

        // Send same event twice
        kafkaTemplate.send("startup.events", snapshotId.toString(), event);
        kafkaTemplate.send("startup.events", snapshotId.toString(), event);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var timelines = timelineRepository.findAll();
                    var count = timelines.stream()
                            .filter(t -> t.getSnapshotId().equals(snapshotId))
                            .count();
                    assertThat(count).isEqualTo(1);  // Idempotent — only 1
                });
    }

    @Test
    void consumeStartupEvent_populatesTimelineData() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var event = createStartupEvent(snapshotId, workspaceId, projectId);

        kafkaTemplate.send("startup.events", snapshotId.toString(), event);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var timeline = timelineRepository.findBySnapshotId(snapshotId);
                    assertThat(timeline).isPresent();
                    assertThat(timeline.get().getTotalStartupMs()).isEqualTo(5000);
                    assertThat(timeline.get().getBottleneckCount()).isGreaterThanOrEqualTo(0);
                    assertThat(timeline.get().getTimelineData()).isNotNull();
                    assertThat(timeline.get().getBeanGraphData()).isNotNull();
                });
    }

    @Test
    void consumeStartupEvent_withMultipleBeans_createsGraph() {
        var snapshotId = UUID.randomUUID();
        var workspaceId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var event = createStartupEventWithBeans(snapshotId, workspaceId, projectId);

        kafkaTemplate.send("startup.events", snapshotId.toString(), event);

        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var timeline = timelineRepository.findBySnapshotId(snapshotId);
                    assertThat(timeline).isPresent();
                    var beanGraph = timeline.get().getBeanGraphData();
                    assertThat(beanGraph).containsKeys("nodes", "edges");
                });
    }

    private StartupEvent createStartupEvent(UUID snapshotId, UUID workspaceId, UUID projectId) {
        return new StartupEvent(
                snapshotId,
                workspaceId,
                projectId,
                "staging",
                "abc123",
                5000,
                "3.3.2",
                "21.0.1",
                List.of(),  // No beans
                List.of(),  // No phases
                List.of(),  // No autoconfigs
                java.time.Instant.now()
        );
    }

    private StartupEvent createStartupEventWithBeans(UUID snapshotId, UUID workspaceId, UUID projectId) {
        var beans = List.of(
                new StartupEvent.BeanEventData("bean1", "com.example.Bean1", 100, 0, List.of()),
                new StartupEvent.BeanEventData("bean2", "com.example.Bean2", 250, 100, List.of("bean1"))
        );
        var phases = List.of(
                new StartupEvent.PhaseEventData("bootstrap", 2500, 0),
                new StartupEvent.PhaseEventData("analysis", 2500, 2500)
        );

        return new StartupEvent(
                snapshotId,
                workspaceId,
                projectId,
                "prod",
                "def456",
                5000,
                "3.3.2",
                "21.0.1",
                beans,
                phases,
                List.of(),
                java.time.Instant.now()
        );
    }
}
