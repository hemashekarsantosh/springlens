package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record BeanEventDto(

        @NotBlank
        @JsonProperty("bean_name")
        String beanName,

        @NotBlank
        @JsonProperty("class_name")
        String className,

        @Min(0)
        @JsonProperty("duration_ms")
        int durationMs,

        @Min(0)
        @JsonProperty("start_ms")
        int startMs,

        List<String> dependencies,

        @JsonProperty("context_id")
        String contextId) {
}
