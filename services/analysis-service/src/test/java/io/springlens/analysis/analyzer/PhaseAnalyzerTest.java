package io.springlens.analysis.analyzer;

import io.springlens.analysis.event.StartupEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PhaseAnalyzerTest {

    private final PhaseAnalyzer analyzer = new PhaseAnalyzer();

    @Test
    void analyze_emptyPhases_returnsEmpty() {
        var result = analyzer.analyze(List.of(), 1000);
        assertThat(result).isEmpty();
    }

    @Test
    void analyze_singlePhase_calculatesPercentage() {
        var phase = new StartupEvent.PhaseEventData("initialization", 500, 0);

        var result = analyzer.analyze(List.of(phase), 1000);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).phaseName()).isEqualTo("initialization");
        assertThat(result.get(0).durationMs()).isEqualTo(500);
        assertThat(result.get(0).percentageOfTotal()).isEqualTo(50.0);
    }

    @Test
    void analyze_multiplePhases_sumsTo100Percent() {
        var phases = List.of(
                new StartupEvent.PhaseEventData("bootstrap", 200, 0),
                new StartupEvent.PhaseEventData("classpath_scanning", 300, 200),
                new StartupEvent.PhaseEventData("bean_instantiation", 500, 500)
        );

        var result = analyzer.analyze(phases, 1000);

        assertThat(result).hasSize(3);
        double totalPercent = result.stream()
                .mapToDouble(PhaseAnalyzer.PhaseResult::percentageOfTotal)
                .sum();
        assertThat(totalPercent).isCloseTo(100.0, within(0.01));
    }

    @Test
    void analyze_zeroTotalStartupMs_handlesGracefully() {
        var phase = new StartupEvent.PhaseEventData("init", 100, 0);

        var result = analyzer.analyze(List.of(phase), 0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).percentageOfTotal()).isZero();
    }

    @Test
    void analyze_preservesStartMs() {
        var phases = List.of(
                new StartupEvent.PhaseEventData("phase1", 100, 0),
                new StartupEvent.PhaseEventData("phase2", 200, 100)
        );

        var result = analyzer.analyze(phases, 300);

        assertThat(result.get(0).startMs()).isZero();
        assertThat(result.get(1).startMs()).isEqualTo(100);
    }

    @Test
    void analyze_largePhase_precisioning() {
        var phase = new StartupEvent.PhaseEventData("analysis", 333, 0);

        var result = analyzer.analyze(List.of(phase), 1000);

        // Should be ~33.3%, rounded to 2 decimal places
        assertThat(result.get(0).percentageOfTotal()).isEqualTo(33.3);
    }
}
