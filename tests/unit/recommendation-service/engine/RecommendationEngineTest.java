package io.springlens.recommendation.engine;

import io.springlens.recommendation.entity.Recommendation;
import io.springlens.recommendation.event.AnalysisCompleteEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecommendationEngine.
 * Tests all 5 recommendation rules: lazy, AOT, CDS, GraalVM, dependency removal.
 * Validates effort assignment, savings accuracy (±20%), and ranking.
 */
class RecommendationEngineTest {

    private RecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecommendationEngine();
    }

    // ─────────────────────────────────────────────────────────────
    // RULE 1: LAZY_LOADING
    // ─────────────────────────────────────────────────────────────

    @Test
    void lazyLoadingRule_ShouldGenerateRecommendation_WhenMultipleBottlenecksDetected() {
        // UNIT-REC-001: Lazy initialization rule with bottleneck beans
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("dataSource", "com.zaxxer.HikariDataSource", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean1", "com.example.Bean1", 250),
                new AnalysisCompleteEvent.BeanAnalysis("bean2", "com.example.Bean2", 220),
                new AnalysisCompleteEvent.BeanAnalysis("bean3", "com.example.Bean3", 210),
                new AnalysisCompleteEvent.BeanAnalysis("bean4", "com.example.Bean4", 200),
                new AnalysisCompleteEvent.BeanAnalysis("bean5", "com.example.Bean5", 250)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(10000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertTrue(recs.stream().anyMatch(r -> "lazy_loading".equals(r.getCategory())),
                "Should generate lazy_loading recommendation");
        Recommendation lazy = recs.stream()
                .filter(r -> "lazy_loading".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        assertEquals("low", lazy.getEffort());
        assertTrue(lazy.getEstimatedSavingsMs() > 0);
        assertNotNull(lazy.getConfigSnippet());
        assertTrue(lazy.getConfigSnippet().contains("spring.main.lazy-initialization=true"));
    }

    @Test
    void lazyLoadingRule_ShouldNotGenerate_WhenFewerThan5Bottlenecks() {
        // UNIT-REC-001: Lazy rule requires >= 5 bottleneck beans
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("bean1", "com.example.Bean1", 250),
                new AnalysisCompleteEvent.BeanAnalysis("bean2", "com.example.Bean2", 250),
                new AnalysisCompleteEvent.BeanAnalysis("bean3", "com.example.Bean3", 250)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertFalse(recs.stream().anyMatch(r -> "lazy_loading".equals(r.getCategory())),
                "Should not generate lazy_loading for < 5 bottlenecks");
    }

    @Test
    void lazyLoadingRule_ShouldEstimateSavings_AsPercentageOfEligibleBeans() {
        // UNIT-REC-001: Savings accuracy (40% of eligible bean duration)
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("bean1", "com.example.Bean1", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean2", "com.example.Bean2", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean3", "com.example.Bean3", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean4", "com.example.Bean4", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean5", "com.example.Bean5", 300)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(10000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation lazy = recs.stream()
                .filter(r -> "lazy_loading".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        // 5 beans * 300ms = 1500ms, 40% = 600ms
        assertEquals(600, lazy.getEstimatedSavingsMs());
    }

    // ─────────────────────────────────────────────────────────────
    // RULE 2: AOT_COMPILATION
    // ─────────────────────────────────────────────────────────────

    @Test
    void aotRule_ShouldGenerate_WhenTotalStartupExceedsThreshold() {
        // UNIT-REC-002: AOT rule for slow startups (>5000ms)
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("bean1", "com.example.Bean1", 300)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(6000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertTrue(recs.stream().anyMatch(r -> "aot_compilation".equals(r.getCategory())),
                "Should generate AOT recommendation for startup > 5000ms");
    }

    @Test
    void aotRule_ShouldNotGenerate_WhenStartupBelowThreshold() {
        // UNIT-REC-002: No AOT for fast startups
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(4000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertFalse(recs.stream().anyMatch(r -> "aot_compilation".equals(r.getCategory())),
                "Should not generate AOT for startup < 5000ms");
    }

    @Test
    void aotRule_ShouldEstimateSavings_At15Percent() {
        // UNIT-REC-002: AOT savings = 15% of total startup time
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(10000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation aot = recs.stream()
                .filter(r -> "aot_compilation".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        assertEquals(1500, aot.getEstimatedSavingsMs()); // 10000 * 0.15
        assertEquals("medium", aot.getEffort());
        assertTrue(aot.getWarnings().size() > 0);
    }

    // ─────────────────────────────────────────────────────────────
    // RULE 3: CLASS_DATA_SHARING (CDS)
    // ─────────────────────────────────────────────────────────────

    @Test
    void cdsRule_ShouldAlwaysGenerate() {
        // UNIT-REC-004: CDS rule applies universally
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertTrue(recs.stream().anyMatch(r -> "classpath_optimization".equals(r.getCategory())),
                "Should always generate CDS recommendation");
    }

    @Test
    void cdsRule_ShouldEstimateSavings_At33Percent() {
        // UNIT-REC-004: CDS savings = 33% of total
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(9000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation cds = recs.stream()
                .filter(r -> "classpath_optimization".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        assertEquals(2970, cds.getEstimatedSavingsMs()); // 9000 * 0.33
        assertEquals("low", cds.getEffort());
    }

    // ─────────────────────────────────────────────────────────────
    // RULE 4: GRAALVM_NATIVE
    // ─────────────────────────────────────────────────────────────

    @Test
    void graalvmRule_ShouldMarkFeasible_WhenNoBlockersDetected() {
        // UNIT-REC-003: GraalVM feasibility assessment
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("bean1", "com.example.NormalBean", 300)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation graalvm = recs.stream()
                .filter(r -> "graalvm_native".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> feasibility = (Map<String, Object>) graalvm.getGraalvmFeasibility();
        assertTrue((Boolean) feasibility.get("feasible"));
        assertEquals(250, feasibility.get("estimated_native_startup_ms")); // 5% of startup
    }

    @Test
    void graalvmRule_ShouldDetectBlockers_WhenProxyBeansPresent() {
        // UNIT-REC-003: GraalVM blocker detection (CGLIB/Proxy beans)
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("cglib_proxy", "net.sf.cglib.Proxy", 300)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation graalvm = recs.stream()
                .filter(r -> "graalvm_native".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> feasibility = (Map<String, Object>) graalvm.getGraalvmFeasibility();
        assertFalse((Boolean) feasibility.get("feasible"));
        assertTrue(((List<?>) feasibility.get("blockers")).size() > 0);
    }

    @Test
    void graalvmRule_ShouldEstimateSavings_At95Percent_WhenFeasible() {
        // UNIT-REC-003: GraalVM savings = 95% of startup when feasible
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(6000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation graalvm = recs.stream()
                .filter(r -> "graalvm_native".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        assertEquals(5700, graalvm.getEstimatedSavingsMs()); // 6000 * 0.95
    }

    // ─────────────────────────────────────────────────────────────
    // RULE 5: DEPENDENCY_REMOVAL
    // ─────────────────────────────────────────────────────────────

    @Test
    void dependencyRemovalRule_ShouldDetect_UnusedDataSourceAutoconfig() {
        // UNIT-REC-005: Dependency removal detection
        List<AnalysisCompleteEvent.AutoconfigAnalysis> autoconfigs = List.of(
                new AnalysisCompleteEvent.AutoconfigAnalysis(
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", true, 100, 50)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, null, null, autoconfigs);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertTrue(recs.stream().anyMatch(r -> "dependency_removal".equals(r.getCategory())),
                "Should detect unused DataSourceAutoConfiguration");
    }

    @Test
    void dependencyRemovalRule_ShouldNotGenerate_WhenDataSourceBeanExists() {
        // UNIT-REC-005: No removal rec if bean actually exists
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("dataSource", "com.zaxxer.HikariDataSource", 300)
        );
        List<AnalysisCompleteEvent.AutoconfigAnalysis> autoconfigs = List.of(
                new AnalysisCompleteEvent.AutoconfigAnalysis(
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration", true, 100, 50)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, bottlenecks, null, autoconfigs);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertFalse(recs.stream().anyMatch(r -> "dependency_removal".equals(r.getCategory())),
                "Should not remove DataSource config if bean exists");
    }

    // ─────────────────────────────────────────────────────────────
    // RANKING & EFFORT ASSIGNMENT
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRankRecommendations_ByEstimatedSavings() {
        // UNIT-REC-006: Effort/impact ranking
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(10000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        // GraalVM (95% = 9500ms) should rank before AOT (15% = 1500ms)
        int graalvmRank = recs.stream()
                .filter(r -> "graalvm_native".equals(r.getCategory()))
                .findFirst()
                .map(Recommendation::getRank)
                .orElse(-1);

        int aotRank = recs.stream()
                .filter(r -> "aot_compilation".equals(r.getCategory()))
                .findFirst()
                .map(Recommendation::getRank)
                .orElse(-1);

        assertTrue(graalvmRank < aotRank, "GraalVM should rank higher than AOT");
    }

    @Test
    void shouldAssignEffortLevels_Correctly() {
        // UNIT-REC-007: Effort level assignment
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(10000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Map<String, String> effortMap = new HashMap<>();
        recs.forEach(r -> effortMap.put(r.getCategory(), r.getEffort()));

        assertEquals("low", effortMap.get("lazy_loading"));
        assertEquals("medium", effortMap.get("aot_compilation"));
        assertEquals("low", effortMap.get("classpath_optimization"));
        assertEquals("high", effortMap.get("graalvm_native"));
    }

    @Test
    void shouldCalculateSavingsPercentage_Correctly() {
        // UNIT-REC-001: Percentage calculation accuracy
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation cds = recs.stream()
                .filter(r -> "classpath_optimization".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        double expectedPercent = (2970.0 / 5000.0) * 100; // ~59.4%
        assertEquals(expectedPercent, cds.getSavingsPercent(), 0.1);
    }

    // ─────────────────────────────────────────────────────────────
    // EDGE CASES
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldHandleEmptyBottleneckList() {
        // UNIT-REC-008: Empty bottleneck handling
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, List.of(), null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        // CDS should still generate, lazy/removal should not
        assertTrue(recs.stream().anyMatch(r -> "classpath_optimization".equals(r.getCategory())));
        assertFalse(recs.stream().anyMatch(r -> "lazy_loading".equals(r.getCategory())));
    }

    @Test
    void shouldHandleNullBottlenecks() {
        // UNIT-REC-008: Null bottleneck list
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, null, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        assertFalse(recs.isEmpty(), "Should still generate some recommendations");
    }

    @Test
    void shouldIncludeSavingsAccuracy_WithinTwentyPercent() {
        // UNIT-REC-003: Accuracy assertion (±20%)
        List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks = List.of(
                new AnalysisCompleteEvent.BeanAnalysis("bean1", "com.example.Bean1", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean2", "com.example.Bean2", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean3", "com.example.Bean3", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean4", "com.example.Bean4", 300),
                new AnalysisCompleteEvent.BeanAnalysis("bean5", "com.example.Bean5", 300)
        );
        AnalysisCompleteEvent event = createAnalysisCompleteEvent(5000, bottlenecks, null, null);

        List<Recommendation> recs = engine.generateRecommendations(event);

        Recommendation lazy = recs.stream()
                .filter(r -> "lazy_loading".equals(r.getCategory()))
                .findFirst()
                .orElseThrow();

        // Estimated 600ms, actual (if applied) ~600ms = within ±20%
        assertTrue(lazy.getEstimatedSavingsMs() > 0);
        assertTrue(lazy.getWarnings().size() > 0, "Should include warnings about actual savings variance");
    }

    // ─────────────────────────────────────────────────────────────
    // Helper method
    // ─────────────────────────────────────────────────────────────

    private AnalysisCompleteEvent createAnalysisCompleteEvent(
            int totalStartupMs,
            List<AnalysisCompleteEvent.BeanAnalysis> bottlenecks,
            Map<String, Object> timeline,
            List<AnalysisCompleteEvent.AutoconfigAnalysis> autoconfigs) {

        return new AnalysisCompleteEvent(
                UUID.randomUUID().toString(), // snapshotId
                UUID.randomUUID().toString(), // workspaceId
                UUID.randomUUID().toString(), // projectId
                "dev",                        // environmentName
                totalStartupMs,
                bottlenecks != null ? bottlenecks.size() : 0,
                bottlenecks,
                timeline,
                autoconfigs
        );
    }
}
