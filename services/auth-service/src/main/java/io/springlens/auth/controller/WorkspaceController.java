package io.springlens.auth.controller;

import io.springlens.auth.entity.Project;
import io.springlens.auth.entity.Workspace;
import io.springlens.auth.entity.WorkspaceMember;
import io.springlens.auth.repository.ProjectRepository;
import io.springlens.auth.repository.WorkspaceMemberRepository;
import io.springlens.auth.repository.WorkspaceRepository;
import io.springlens.auth.service.PlanQuotaEnforcer;
import io.springlens.shared.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/workspaces")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final PlanQuotaEnforcer quotaEnforcer;

    public WorkspaceController(WorkspaceRepository workspaceRepository,
                                WorkspaceMemberRepository memberRepository,
                                ProjectRepository projectRepository,
                                PlanQuotaEnforcer quotaEnforcer) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.quotaEnforcer = quotaEnforcer;
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<Object> getWorkspace(@PathVariable UUID workspaceId,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        return workspaceRepository.findById(workspaceId)
                .filter(w -> w.getDeletedAt() == null)
                .filter(w -> memberRepository.findByWorkspaceIdAndUserIdAndDeletedAtIsNull(workspaceId, userId).isPresent())
                .map(w -> ResponseEntity.ok((Object) workspaceToDto(w)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ErrorResponse.of("NOT_FOUND", "Workspace not found", null)));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<Object> listMembers(@PathVariable UUID workspaceId,
                                               @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        if (memberRepository.findByWorkspaceIdAndUserIdAndDeletedAtIsNull(workspaceId, userId).isEmpty()) {
            return ResponseEntity.status(403).body(ErrorResponse.of("FORBIDDEN", "Access denied", null));
        }
        var members = memberRepository.findActiveByWorkspaceId(workspaceId);
        return ResponseEntity.ok(Map.of("members", members.stream().map(this::memberToDto).toList()));
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<Object> inviteMember(@PathVariable UUID workspaceId,
                                                @RequestBody @Valid InviteMemberRequest body,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        var callerMember = memberRepository.findByWorkspaceIdAndUserIdAndDeletedAtIsNull(workspaceId, userId)
                .filter(m -> "admin".equals(m.getRole()))
                .orElse(null);
        if (callerMember == null) {
            return ResponseEntity.status(403).body(ErrorResponse.of("FORBIDDEN", "Admin role required", null));
        }

        var workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) return ResponseEntity.notFound().build();

        try {
            quotaEnforcer.checkMemberQuota(workspace);
        } catch (PlanQuotaEnforcer.QuotaExceededException e) {
            return ResponseEntity.status(422).body(ErrorResponse.of("QUOTA_EXCEEDED", e.getMessage(), null));
        }

        var member = WorkspaceMember.create(workspaceId, body.userId(), body.role());
        memberRepository.save(member);
        log.info("Invited member userId={} workspace={} role={}", body.userId(), workspaceId, body.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(memberToDto(member));
    }

    @GetMapping("/{workspaceId}/projects")
    public ResponseEntity<Object> listProjects(@PathVariable UUID workspaceId,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        if (memberRepository.findByWorkspaceIdAndUserIdAndDeletedAtIsNull(workspaceId, userId).isEmpty()) {
            return ResponseEntity.status(403).body(ErrorResponse.of("FORBIDDEN", "Access denied", null));
        }
        var projects = projectRepository.findActiveByWorkspaceId(workspaceId);
        return ResponseEntity.ok(Map.of("projects", projects.stream().map(this::projectToDto).toList()));
    }

    @PostMapping("/{workspaceId}/projects")
    public ResponseEntity<Object> createProject(@PathVariable UUID workspaceId,
                                                 @RequestBody @Valid CreateProjectRequest body,
                                                 @AuthenticationPrincipal Jwt jwt) {
        var workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) return ResponseEntity.notFound().build();

        try {
            quotaEnforcer.checkProjectQuota(workspace);
        } catch (PlanQuotaEnforcer.QuotaExceededException e) {
            return ResponseEntity.status(422).body(ErrorResponse.of("QUOTA_EXCEEDED", e.getMessage(), null));
        }

        String slug = body.name().toLowerCase().replaceAll("[^a-z0-9]", "-");
        var project = Project.create(workspaceId, body.name(), slug, body.description());
        projectRepository.save(project);
        log.info("Created project id={} workspace={} name={}", project.getId(), workspaceId, body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(projectToDto(project));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null) return UUID.randomUUID();
        try { return UUID.fromString(jwt.getSubject()); } catch (Exception e) { return UUID.randomUUID(); }
    }

    private Map<String, Object> workspaceToDto(Workspace w) {
        return Map.of(
                "id", w.getId().toString(),
                "name", w.getName(),
                "slug", w.getSlug(),
                "plan", w.getPlan(),
                "plan_project_limit", w.getPlanProjectLimit(),
                "plan_member_limit", w.getPlanMemberLimit());
    }

    private Map<String, Object> memberToDto(WorkspaceMember m) {
        return Map.of(
                "id", m.getId().toString(),
                "workspace_id", m.getWorkspaceId().toString(),
                "user_id", m.getUserId().toString(),
                "role", m.getRole(),
                "joined_at", m.getJoinedAt().toString());
    }

    private Map<String, Object> projectToDto(Project p) {
        return Map.of(
                "id", p.getId().toString(),
                "workspace_id", p.getWorkspaceId().toString(),
                "name", p.getName(),
                "slug", p.getSlug(),
                "description", p.getDescription() != null ? p.getDescription() : "",
                "created_at", p.getCreatedAt().toString());
    }

    // ─── Request DTOs ─────────────────────────────────────────────────────────

    public record InviteMemberRequest(
            @NotBlank String userId,
            @NotBlank String role) {

        UUID userId() { return java.util.UUID.fromString(this.userId); }
    }

    public record CreateProjectRequest(
            @NotBlank String name,
            String description) {
    }
}
