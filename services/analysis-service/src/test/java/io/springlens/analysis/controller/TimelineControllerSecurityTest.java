package io.springlens.analysis.controller;

import io.springlens.analysis.entity.StartupTimeline;
import io.springlens.analysis.repository.StartupTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Security tests for TimelineController.
 * Verifies: Project filtering prevents cross-project data access.
 */
@DisplayName("TimelineController Security Tests - Project Filtering")
class TimelineControllerSecurityTest {

    private TimelineController controller;
    private StartupTimelineRepository mockRepository;
    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockRepository = mock(StartupTimelineRepository.class);
        controller = new TimelineController(mockRepository);

        // Mock JWT with workspace ID
        mockJwt = mock(Jwt.class);
        when(mockJwt.getClaimAsString("workspace_id")).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Should return timeline when project_id matches")
    void testGetTimelineWithCorrectProjectId() {
        // GIVEN: A valid timeline for a specific project
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID snapshotId = UUID.randomUUID();

        StartupTimeline timeline = StartupTimeline.builder()
                .id(UUID.randomUUID())
                .snapshotId(snapshotId)
                .projectId(projectId)
                .workspaceId(workspaceId)
                .environmentName("prod")
                .totalStartupMs(1500)
                .beanCount(42)
                .bottleneckCount(3)
                .timelineData(Map.of("beans", java.util.List.of()))
                .beanGraphData(Map.of("nodes", java.util.List.of()))
                .analyzedAt(Instant.now())
                .build();

        when(mockRepository.findBySnapshotIdAndProject(snapshotId, projectId, workspaceId))
                .thenReturn(Optional.of(timeline));

        // WHEN: The timeline endpoint is called with correct project_id
        ResponseEntity<Object> response = controller.getTimeline(projectId, snapshotId, 0, null, mockJwt);

        // THEN: Should return 200 OK with timeline data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // VERIFY: Repository was called with project_id (database-level filtering)
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(snapshotId, projectId, workspaceId);
    }

    @Test
    @DisplayName("Should return 404 when snapshot doesn't exist in the requested project")
    void testGetTimelineWithNonExistentSnapshot() {
        // GIVEN: A request for a snapshot that doesn't exist in the project
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID snapshotId = UUID.randomUUID();

        when(mockRepository.findBySnapshotIdAndProject(snapshotId, projectId, workspaceId))
                .thenReturn(Optional.empty());

        // WHEN: The timeline endpoint is called
        ResponseEntity<Object> response = controller.getTimeline(projectId, snapshotId, 0, null, mockJwt);

        // THEN: Should return 404 Not Found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // VERIFY: Repository was called with project_id
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(snapshotId, projectId, workspaceId);
    }

    @Test
    @DisplayName("Security: Should NOT return data from another project even if snapshot exists")
    void testGetTimelineWithWrongProjectId() {
        // GIVEN: A snapshot that exists but in a DIFFERENT project
        UUID requestedProjectId = UUID.randomUUID();
        UUID actualProjectId = UUID.randomUUID(); // Different project
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID snapshotId = UUID.randomUUID();

        // The snapshot exists but NOT in the requested project
        when(mockRepository.findBySnapshotIdAndProject(snapshotId, requestedProjectId, workspaceId))
                .thenReturn(Optional.empty());

        // WHEN: The timeline endpoint is called with wrong project_id
        ResponseEntity<Object> response = controller.getTimeline(
                requestedProjectId, snapshotId, 0, null, mockJwt);

        // THEN: Should return 404 (not the data from the actual project)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // VERIFY: Repository was queried with the correct project_id
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(snapshotId, requestedProjectId, workspaceId);
    }

    @Test
    @DisplayName("Should return bean graph when project_id matches")
    void testGetBeanGraphWithCorrectProjectId() {
        // GIVEN: A valid timeline with bean graph data
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID snapshotId = UUID.randomUUID();

        StartupTimeline timeline = StartupTimeline.builder()
                .id(UUID.randomUUID())
                .snapshotId(snapshotId)
                .projectId(projectId)
                .workspaceId(workspaceId)
                .beanGraphData(Map.of("nodes", java.util.List.of(
                        Map.of("id", "Spring", "label", "Spring", "level", 0)
                )))
                .analyzedAt(Instant.now())
                .build();

        when(mockRepository.findBySnapshotIdAndProject(snapshotId, projectId, workspaceId))
                .thenReturn(Optional.of(timeline));

        // WHEN: The bean graph endpoint is called
        ResponseEntity<Object> response = controller.getBeanGraph(projectId, snapshotId, mockJwt);

        // THEN: Should return 200 OK with bean graph
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // VERIFY: Repository was called with project_id filtering
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(snapshotId, projectId, workspaceId);
    }

    @Test
    @DisplayName("Security: Bean graph should not be accessible from another project")
    void testGetBeanGraphWithWrongProjectId() {
        // GIVEN: A request for bean graph with wrong project_id
        UUID requestedProjectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID snapshotId = UUID.randomUUID();

        when(mockRepository.findBySnapshotIdAndProject(snapshotId, requestedProjectId, workspaceId))
                .thenReturn(Optional.empty());

        // WHEN: The bean graph endpoint is called with wrong project_id
        ResponseEntity<Object> response = controller.getBeanGraph(requestedProjectId, snapshotId, mockJwt);

        // THEN: Should return 404
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // VERIFY: Repository was queried with the wrong project_id (security boundary)
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(snapshotId, requestedProjectId, workspaceId);
    }

    @Test
    @DisplayName("Should compare snapshots only within same project")
    void testCompareSnapshotsWithCorrectProjectId() {
        // GIVEN: Two snapshots from the same project
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID baselineSnapshotId = UUID.randomUUID();
        UUID targetSnapshotId = UUID.randomUUID();

        StartupTimeline baseline = StartupTimeline.builder()
                .id(UUID.randomUUID())
                .snapshotId(baselineSnapshotId)
                .projectId(projectId)
                .workspaceId(workspaceId)
                .totalStartupMs(1000)
                .timelineData(Map.of("beans", java.util.List.of()))
                .analyzedAt(Instant.now().minusSeconds(3600))
                .build();

        StartupTimeline target = StartupTimeline.builder()
                .id(UUID.randomUUID())
                .snapshotId(targetSnapshotId)
                .projectId(projectId)
                .workspaceId(workspaceId)
                .totalStartupMs(1500)
                .timelineData(Map.of("beans", java.util.List.of()))
                .analyzedAt(Instant.now())
                .build();

        when(mockRepository.findBySnapshotIdAndProject(baselineSnapshotId, projectId, workspaceId))
                .thenReturn(Optional.of(baseline));
        when(mockRepository.findBySnapshotIdAndProject(targetSnapshotId, projectId, workspaceId))
                .thenReturn(Optional.of(target));

        // WHEN: Snapshots are compared
        ResponseEntity<Object> response = controller.compareSnapshots(
                projectId, baselineSnapshotId, targetSnapshotId, mockJwt);

        // THEN: Should return comparison data
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // VERIFY: Repository was called with correct project_id for both snapshots
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(baselineSnapshotId, projectId, workspaceId);
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(targetSnapshotId, projectId, workspaceId);
    }

    @Test
    @DisplayName("Security: Compare should fail if baseline is from a different project")
    void testCompareSnapshotsWithMismatchedProjectIds() {
        // GIVEN: Baseline from projectA and target from projectB (attacker trying to compare across projects)
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID baselineSnapshotId = UUID.randomUUID();
        UUID targetSnapshotId = UUID.randomUUID();

        // Baseline doesn't exist in the requested project
        when(mockRepository.findBySnapshotIdAndProject(baselineSnapshotId, projectId, workspaceId))
                .thenReturn(Optional.empty());

        // WHEN: Compare is attempted with mismatched projects
        ResponseEntity<Object> response = controller.compareSnapshots(
                projectId, baselineSnapshotId, targetSnapshotId, mockJwt);

        // THEN: Should return 404 (security boundary enforced)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // VERIFY: Repository was queried with project_id filtering
        verify(mockRepository, times(1))
                .findBySnapshotIdAndProject(baselineSnapshotId, projectId, workspaceId);
        // Target should not be queried if baseline failed
        verify(mockRepository, never())
                .findBySnapshotIdAndProject(targetSnapshotId, projectId, workspaceId);
    }

    @Test
    @DisplayName("Database-level security: Project filtering happens at query time")
    void testProjectFilteringAtDatabaseLevel() {
        // GIVEN: A request with project_id parameter
        UUID projectId = UUID.randomUUID();
        UUID workspaceId = UUID.fromString(mockJwt.getClaimAsString("workspace_id"));
        UUID snapshotId = UUID.randomUUID();

        when(mockRepository.findBySnapshotIdAndProject(snapshotId, projectId, workspaceId))
                .thenReturn(Optional.empty());

        // WHEN: Timeline is requested
        ResponseEntity<Object> response = controller.getTimeline(projectId, snapshotId, 0, null, mockJwt);

        // THEN: Repository's findBySnapshotIdAndProject was called (database-level filtering)
        // This ensures the filtering happens at SQL level, not in application code
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // VERIFY: The correct method with projectId filtering was called
        verify(mockRepository).findBySnapshotIdAndProject(snapshotId, projectId, workspaceId);
    }
}
