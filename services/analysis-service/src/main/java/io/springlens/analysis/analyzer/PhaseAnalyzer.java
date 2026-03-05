package io.springlens.analysis.analyzer;

import io.springlens.analysis.event.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Computes phase breakdown percentages from raw phase events.
 */
@Component
public class PhaseAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PhaseAnalyzer.class);

    public List<PhaseResult> analyze(List<StartupEvent.PhaseEventData> phases, int totalStartupMs) {
        log.info("Analyzing phases phase_count={} total_ms={}", phases.size(), totalStartupMs);

        return phases.stream()
                .map(phase -> {
                    double percentage = totalStartupMs > 0
                            ? (phase.durationMs() * 100.0) / totalStartupMs
                            : 0.0;
                    return new PhaseResult(
                            phase.phaseName(),
                            phase.durationMs(),
                            phase.startMs(),
                            Math.round(percentage * 100.0) / 100.0);
                })
                .toList();
    }

    public record PhaseResult(
            String phaseName,
            int durationMs,
            int startMs,
            double percentageOfTotal) {
    }
}
