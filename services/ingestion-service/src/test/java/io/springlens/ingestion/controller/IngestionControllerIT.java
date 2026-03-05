package io.springlens.ingestion.controller;

import io.springlens.ingestion.dto.BeanEventDto;
import io.springlens.ingestion.dto.PhaseEventDto;
import io.springlens.ingestion.dto.StartupSnapshotRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngestionControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("springlens_ingestion")
            .withUsername("springlens")
            .withPassword("password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");  // Will skip Kafka in test
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestSnapshot_validRequest_returns201() throws Exception {
        var request = createRequest();

        mockMvc.perform(post("/api/v1/ingest")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001")
                .header("X-Project-ID", "00000000-0000-0000-0000-000000000002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshot_id").exists())
                .andExpect(jsonPath("$.status").value("queued"));
    }

    @Test
    void ingestSnapshot_missingWorkspaceHeader_returns400() throws Exception {
        var request = createRequest();

        mockMvc.perform(post("/api/v1/ingest")
                .header("X-Project-ID", "00000000-0000-0000-0000-000000000002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void snapshotStatus_validId_returns200() throws Exception {
        // First ingest a snapshot
        var request = createRequest();
        var response = mockMvc.perform(post("/api/v1/ingest")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001")
                .header("X-Project-ID", "00000000-0000-0000-0000-000000000002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var content = response.getResponse().getContentAsString();
        var snapshotId = objectMapper.readTree(content).get("snapshot_id").asText();

        // Then get its status
        mockMvc.perform(get("/api/v1/snapshots/" + snapshotId)
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void snapshotStatus_invalidId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/snapshots/invalid-uuid")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkBudget_withinBudget_returns200() throws Exception {
        var request = createRequest();
        var response = mockMvc.perform(post("/api/v1/ingest")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001")
                .header("X-Project-ID", "00000000-0000-0000-0000-000000000002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var content = response.getResponse().getContentAsString();
        var snapshotId = objectMapper.readTree(content).get("snapshot_id").asText();

        mockMvc.perform(post("/api/v1/snapshots/" + snapshotId + "/check-budget?budget_ms=5000")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.within_budget").value(true));
    }

    @Test
    void checkBudget_exceedsBudget_returns422() throws Exception {
        var request = createRequest();
        var response = mockMvc.perform(post("/api/v1/ingest")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001")
                .header("X-Project-ID", "00000000-0000-0000-0000-000000000002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var content = response.getResponse().getContentAsString();
        var snapshotId = objectMapper.readTree(content).get("snapshot_id").asText();

        mockMvc.perform(post("/api/v1/snapshots/" + snapshotId + "/check-budget?budget_ms=1000")
                .header("X-Workspace-ID", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnprocessableEntity());
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
