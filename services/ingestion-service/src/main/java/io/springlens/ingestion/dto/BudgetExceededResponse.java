package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BudgetExceededResponse(
        String code,
        String message,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("actual_ms") int actualMs,
        @JsonProperty("budget_ms") int budgetMs,
        @JsonProperty("excess_ms") int excessMs,
        @JsonProperty("top_bottlenecks") List<Bottleneck> topBottlenecks) {

    public BudgetExceededResponse(int actualMs, int budgetMs) {
        this("BUDGET_EXCEEDED",
                String.format("Startup time %dms exceeds budget of %dms", actualMs, budgetMs),
                null,
                actualMs,
                budgetMs,
                actualMs - budgetMs,
                List.of());
    }

    public BudgetExceededResponse withTraceId(String traceId) {
        return new BudgetExceededResponse(code, message, traceId, actualMs, budgetMs, excessMs, topBottlenecks);
    }

    public BudgetExceededResponse withBottlenecks(List<Bottleneck> bottlenecks) {
        return new BudgetExceededResponse(code, message, traceId, actualMs, budgetMs, excessMs, bottlenecks);
    }

    public record Bottleneck(
            @JsonProperty("bean_name") String beanName,
            @JsonProperty("duration_ms") int durationMs) {
    }
}
