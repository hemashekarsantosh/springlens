package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Incoming request DTO from the SpringLens JVM agent.
 * Maps directly to the OpenAPI StartupSnapshotRequest schema.
 */
public record StartupSnapshotRequest(

        @NotNull(message = "project_id is required")
        @JsonProperty("project_id")
        UUID projectId,

        @NotBlank(message = "environment is required")
        @Pattern(regexp = "dev|staging|production|ci|local", message = "Invalid environment value")
        String environment,

        @NotBlank(message = "agent_version is required")
        @JsonProperty("agent_version")
        String agentVersion,

        @NotBlank(message = "spring_boot_version is required")
        @JsonProperty("spring_boot_version")
        String springBootVersion,

        @NotBlank(message = "java_version is required")
        @JsonProperty("java_version")
        String javaVersion,

        @Min(value = 0, message = "total_startup_ms must be >= 0")
        @JsonProperty("total_startup_ms")
        int totalStartupMs,

        @NotBlank(message = "git_commit_sha is required")
        @Pattern(regexp = "^[0-9a-f]{40}$", message = "git_commit_sha must be 40 lowercase hex chars")
        @JsonProperty("git_commit_sha")
        String gitCommitSha,

        String hostname,

        @NotEmpty(message = "beans must not be empty")
        @Valid
        List<BeanEventDto> beans,

        @NotEmpty(message = "phases must not be empty")
        @Valid
        List<PhaseEventDto> phases,

        List<@Valid AutoconfigurationEventDto> autoconfigurations) {
}
