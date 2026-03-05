package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AutoconfigurationEventDto(

        @NotBlank
        @JsonProperty("class_name")
        String className,

        boolean matched,

        @Min(0)
        @JsonProperty("duration_ms")
        int durationMs,

        @Min(0)
        @JsonProperty("condition_evaluation_ms")
        int conditionEvaluationMs) {
}
