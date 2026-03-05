package io.springlens.analysis.analyzer;

import io.springlens.analysis.event.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PhaseAnalyzer.
 * Tests phase percentage calculations, edge cases.
 */
class PhaseAnalyzerTest {

    private PhaseAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PhaseAnalyzer();
    }

    @Test
    void shouldCalculatePhasePercentages_Correctly() {
        // UNIT-ANALYSIS-004: Percentage breakdown
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("context_refresh", 2000, 0),
                new StartupEvent.PhaseEventData("bean_post_processors", 1500, 2000),
                new StartupEvent.PhaseEventData("application_listeners", 500, 3500),
                new StartupEvent.PhaseEventData("context_loaded", 1000, 4000)
        );
        int totalStartupMs = 5000;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        assertEquals(4, results.size());

        // context_refresh: 2000/5000 = 40%
        assertEquals(40.0, results.get(0).percentageOfTotal());
        assertEquals(2000, results.get(0).durationMs());

        // bean_post_processors: 1500/5000 = 30%
        assertEquals(30.0, results.get(1).percentageOfTotal());

        // application_listeners: 500/5000 = 10%
        assertEquals(10.0, results.get(2).percentageOfTotal());

        // context_loaded: 1000/5000 = 20%
        assertEquals(20.0, results.get(3).percentageOfTotal());
    }

    @Test
    void shouldSumPercentagesToOneHundred() {
        // UNIT-ANALYSIS-004: Phase breakdown sum verification
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("phase1", 1000, 0),
                new StartupEvent.PhaseEventData("phase2", 2000, 1000),
                new StartupEvent.PhaseEventData("phase3", 2000, 3000)
        );
        int totalStartupMs = 5000;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        double totalPercentage = results.stream()
                .mapToDouble(PhaseAnalyzer.PhaseResult::percentageOfTotal)
                .sum();

        assertEquals(100.0, totalPercentage, 0.01); // Allow tiny rounding error
    }

    @Test
    void shouldHandleZeroTotalStartup() {
        // UNIT-ANALYSIS-003: Graceful handling of edge case
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("phase1", 0, 0)
        );
        int totalStartupMs = 0;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        assertEquals(1, results.size());
        assertEquals(0.0, results.get(0).percentageOfTotal());
    }

    @Test
    void shouldHandleEmptyPhaseList() {
        // UNIT-ANALYSIS-003: Empty phases
        List<StartupEvent.PhaseEventData> phases = List.of();
        int totalStartupMs = 5000;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        assertEquals(0, results.size());
    }

    @Test
    void shouldPreservePhaseMetadata() {
        // UNIT-ANALYSIS-003: Verify phase data is preserved
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("context_refresh", 2500, 100)
        );
        int totalStartupMs = 5000;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        PhaseAnalyzer.PhaseResult result = results.get(0);
        assertEquals("context_refresh", result.phaseName());
        assertEquals(2500, result.durationMs());
        assertEquals(100, result.startMs());
        assertEquals(50.0, result.percentageOfTotal());
    }

    @Test
    void shouldRoundPercentagesToTwoDecimals() {
        // UNIT-ANALYSIS-004: Precision testing
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("phase1", 1, 0)
        );
        int totalStartupMs = 3; // 1/3 = 0.333...

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        double percentage = results.get(0).percentageOfTotal();
        // Should be rounded to 2 decimals: 33.33
        assertEquals(33.33, percentage);
    }

    @Test
    void shouldHandlePhaseGreaterThanTotal() {
        // Edge case: single phase duration > total (shouldn't happen but test robustness)
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("phase1", 6000, 0)
        );
        int totalStartupMs = 5000;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        // Calculation: 6000/5000 = 120%
        assertEquals(120.0, results.get(0).percentageOfTotal());
    }

    @Test
    void shouldProcessMultiplePhasesWithVaryingDurations() {
        // UNIT-ANALYSIS-003: Multiple phases with realistic durations
        List<StartupEvent.PhaseEventData> phases = List.of(
                new StartupEvent.PhaseEventData("context_refresh", 3000, 0),
                new StartupEvent.PhaseEventData("bean_post_processors", 1200, 3000),
                new StartupEvent.PhaseEventData("application_listeners", 600, 4200),
                new StartupEvent.PhaseEventData("context_loaded", 400, 4800),
                new StartupEvent.PhaseEventData("started", 800, 5200)
        );
        int totalStartupMs = 6000;

        List<PhaseAnalyzer.PhaseResult> results = analyzer.analyze(phases, totalStartupMs);

        assertEquals(5, results.size());

        // Verify each is correctly calculated
        assertEquals(50.0, results.get(0).percentageOfTotal()); // 3000/6000
        assertEquals(20.0, results.get(1).percentageOfTotal()); // 1200/6000
        assertEquals(10.0, results.get(2).percentageOfTotal()); // 600/6000
        assertEquals(6.67, results.get(3).percentageOfTotal()); // 400/6000
        assertEquals(13.33, results.get(4).percentageOfTotal()); // 800/6000
    }

    @Test
    void shouldReturnNewList_NotModifyInput() {
        // UNIT-ANALYSIS-003: Immutability test
        List<StartupEvent.PhaseEventData> phases = new java.util.ArrayList<>(List.of(
                new StartupEvent.PhaseEventData("phase1", 1000, 0)
        ));

        List<PhaseAnalyzer.PhaseResult> result1 = analyzer.analyze(phases, 1000);
        List<PhaseAnalyzer.PhaseResult> result2 = analyzer.analyze(phases, 1000);

        // Both should be independent
        assertEquals(result1.size(), result2.size());
        assertNotSame(result1, result2);
    }
}
