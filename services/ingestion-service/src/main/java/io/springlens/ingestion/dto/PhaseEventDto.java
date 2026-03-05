package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhaseEventDto(

        @NotBlank
        @Pattern(regexp = "context_refresh|bean_post_processors|application_listeners|context_loaded|started")
        @JsonProperty("phase_name")
        String phaseName,

        @Min(0)
        @JsonProperty("duration_ms")
        int durationMs,

        @Min(0)
        @JsonProperty("start_ms")
        int startMs) {
}
