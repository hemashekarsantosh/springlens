package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BudgetCheckResponse(
        @JsonProperty("within_budget") boolean withinBudget,
        @JsonProperty("actual_ms") int actualMs,
        @JsonProperty("budget_ms") int budgetMs) {
}
