package io.springlens.recommendation.engine;

import io.springlens.recommendation.event.AnalysisCompleteEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RecommendationEngineTest {

    private final RecommendationEngine engine = new RecommendationEngine();

    @Test
    void generateRecommendations_emptyEvent_returnsEmpty() {
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 1000, 0, List.of(), List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        assertThat(recommendations).isEmpty();
    }

    @Test
    void generateRecommendations_shortStartup_noAotRecommendation() {
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 2000, 0, List.of(), List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        var aotRec = recommendations.stream()
                .filter(r -> r.getCategory().equals("aot_compilation"))
                .findFirst();
        assertThat(aotRec).isEmpty();
    }

    @Test
    void generateRecommendations_longStartup_suggestsAot() {
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 6000, 0, List.of(), List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        var aotRec = recommendations.stream()
                .filter(r -> r.getCategory().equals("aot_compilation"))
                .findFirst();
        assertThat(aotRec).isPresent();
        assertThat(aotRec.get().getTitle()).contains("AOT");
    }

    @Test
    void generateRecommendations_withBottlenecks_suggestsLazyLoading() {
        var bottleneck = new AnalysisCompleteEvent.BeanAnalysis(
                "dataSourceBean", "com.DataSource", 250, true, List.of());
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 5000, 1,
                List.of(bottleneck, bottleneck, bottleneck, bottleneck, bottleneck, bottleneck),
                List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        var lazyRec = recommendations.stream()
                .filter(r -> r.getCategory().equals("lazy_loading"))
                .findFirst();
        assertThat(lazyRec).isPresent();
    }

    @Test
    void generateRecommendations_sortedByEstimatedSavings() {
        var bottleneck = new AnalysisCompleteEvent.BeanAnalysis(
                "slowBean", "com.Slow", 300, true, List.of());
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 6000, 1,
                List.of(bottleneck, bottleneck, bottleneck, bottleneck, bottleneck, bottleneck),
                List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        // Should be sorted by savings descending
        for (int i = 0; i < recommendations.size() - 1; i++) {
            assertThat(recommendations.get(i).getEstimatedSavingsMs())
                    .isGreaterThanOrEqualTo(recommendations.get(i + 1).getEstimatedSavingsMs());
        }
    }

    @Test
    void generateRecommendations_ranksAscending() {
        var bottleneck = new AnalysisCompleteEvent.BeanAnalysis(
                "bean", "com.Bean", 250, true, List.of());
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 5000, 1,
                List.of(bottleneck, bottleneck, bottleneck, bottleneck, bottleneck, bottleneck),
                List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        for (int i = 0; i < recommendations.size(); i++) {
            assertThat(recommendations.get(i).getRank()).isEqualTo(i + 1);
        }
    }

    @Test
    void generateRecommendations_cdsAlwaysSuggested() {
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 1000, 0, List.of(), List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        var cdsRec = recommendations.stream()
                .filter(r -> r.getCategory().equals("classpath_optimization"))
                .findFirst();
        assertThat(cdsRec).isPresent();
        assertThat(cdsRec.get().getTitle()).contains("Class Data Sharing");
    }

    @Test
    void generateRecommendations_noCglibProxies_graalvmFeasible() {
        var cleanBean = new AnalysisCompleteEvent.BeanAnalysis(
                "cleanBean", "com.Clean", 100, false, List.of());
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 5000, 0, List.of(cleanBean), List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        var graalRec = recommendations.stream()
                .filter(r -> r.getCategory().equals("graalvm_native"))
                .findFirst();
        assertThat(graalRec).isPresent();
        var feasibility = graalRec.get().getGraalvmFeasibility();
        assertThat(feasibility).containsEntry("feasible", true);
    }

    @Test
    void generateRecommendations_percentageCalculation() {
        var bottleneck = new AnalysisCompleteEvent.BeanAnalysis(
                "bean", "com.Bean", 250, true, List.of());
        var event = new AnalysisCompleteEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "prod", "abc123", 1000, 1,
                List.of(bottleneck, bottleneck, bottleneck, bottleneck, bottleneck, bottleneck),
                List.of(), List.of(), Instant.now()
        );

        var recommendations = engine.generateRecommendations(event);

        for (var rec : recommendations) {
            assertThat(rec.getSavingsPercentage())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(100.0);
        }
    }
}
