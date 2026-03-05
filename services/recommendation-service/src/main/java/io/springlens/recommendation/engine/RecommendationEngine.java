package io.springlens.recommendation.engine;

import io.springlens.recommendation.entity.Recommendation;
import io.springlens.recommendation.event.AnalysisCompleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based recommendation engine.
 * Evaluates 5 rules: LAZY_LOADING, AOT_COMPILATION, CLASS_DATA_SHARING,
 * GRAALVM_NATIVE, DEPENDENCY_REMOVAL.
 */
@Component
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);
    private static final int BOTTLENECK_THRESHOLD_MS = 200;
    private static final int AOT_THRESHOLD_MS = 5000;
    private static final int MIN_LAZY_BEANS = 5;

    public List<Recommendation> generateRecommendations(AnalysisCompleteEvent event) {
        log.info("Generating recommendations snapshot={} total_ms={} bottlenecks={}",
                event.snapshotId(), event.totalStartupMs(), event.bottleneckCount());

        List<RuleResult> results = new ArrayList<>();

        results.addAll(evaluateLazyLoading(event));
        results.addAll(evaluateAotCompilation(event));
        results.addAll(evaluateClassDataSharing(event));
        results.addAll(evaluateGraalvmNative(event));
        results.addAll(evaluateDependencyRemoval(event));

        // Sort by estimated savings descending, assign rank
        results.sort(Comparator.comparingInt(RuleResult::estimatedSavingsMs).reversed());

        List<Recommendation> recommendations = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            double savingsPercent = event.totalStartupMs() > 0
                    ? (r.estimatedSavingsMs() * 100.0) / event.totalStartupMs()
                    : 0.0;
            recommendations.add(Recommendation.create(
                    event.snapshotId(),
                    event.workspaceId(),
                    event.projectId(),
                    event.environmentName(),
                    i + 1,
                    r.category(),
                    r.title(),
                    r.description(),
                    r.estimatedSavingsMs(),
                    Math.round(savingsPercent * 100.0) / 100.0,
                    r.effort(),
                    r.codeSnippet(),
                    r.configSnippet(),
                    r.warnings(),
                    r.affectedBeans(),
                    r.graalvmFeasibility()));
        }

        log.info("Generated recommendations count={} snapshot={}", recommendations.size(), event.snapshotId());
        return recommendations;
    }

    // ─── Rule: LAZY_LOADING ───────────────────────────────────────────────────

    private List<RuleResult> evaluateLazyLoading(AnalysisCompleteEvent event) {
        if (event.bottlenecks() == null) return List.of();

        List<String> eligibleBeans = event.bottlenecks().stream()
                .filter(b -> b.durationMs() >= BOTTLENECK_THRESHOLD_MS)
                .filter(b -> !b.beanName().toLowerCase().contains("lazy"))
                .map(AnalysisCompleteEvent.BeanAnalysis::beanName)
                .collect(Collectors.toList());

        if (eligibleBeans.size() < MIN_LAZY_BEANS) return List.of();

        int totalEligibleMs = event.bottlenecks().stream()
                .filter(b -> eligibleBeans.contains(b.beanName()))
                .mapToInt(AnalysisCompleteEvent.BeanAnalysis::durationMs)
                .sum();
        int estimatedSavings = (int) (totalEligibleMs * 0.4);

        return List.of(new RuleResult(
                "lazy_loading",
                String.format("Enable lazy initialization for %d beans", eligibleBeans.size()),
                String.format(
                        "%d beans take over %dms to initialize and are not marked @Lazy. " +
                        "Enabling lazy initialization will defer their creation until first use, " +
                        "reducing startup time by approximately %dms.",
                        eligibleBeans.size(), BOTTLENECK_THRESHOLD_MS, estimatedSavings),
                estimatedSavings,
                "low",
                null,
                "spring.main.lazy-initialization=true",
                List.of("Some beans may fail at request-time if misconfigured — run full integration tests after enabling"),
                eligibleBeans,
                null));
    }

    // ─── Rule: AOT_COMPILATION ────────────────────────────────────────────────

    private List<RuleResult> evaluateAotCompilation(AnalysisCompleteEvent event) {
        if (event.totalStartupMs() < AOT_THRESHOLD_MS) return List.of();

        int estimatedSavings = (int) (event.totalStartupMs() * 0.15);

        return List.of(new RuleResult(
                "aot_compilation",
                "Enable AOT (Ahead-of-Time) processing",
                String.format(
                        "Your application startup exceeds %ds. Spring Boot 3 AOT processing pre-computes " +
                        "bean definitions at build time, reducing startup by approximately 10–20%% (est. %dms saved). " +
                        "Requires Spring Boot 3.x and Gradle/Maven AOT plugin.",
                        AOT_THRESHOLD_MS / 1000, estimatedSavings),
                estimatedSavings,
                "medium",
                """
                // build.gradle — enable AOT
                tasks.withType(BootBuildImage).configureEach {
                    environment = ["BP_SPRING_AOT_ENABLED": "true"]
                }
                """,
                null,
                List.of(
                        "AOT may break applications using dynamic reflection without hints",
                        "Dynamic proxies (CGLIB) require native hints or proxy interfaces",
                        "Test thoroughly with spring.aot.enabled=true before deploying"),
                List.of(),
                null));
    }

    // ─── Rule: CLASS_DATA_SHARING ─────────────────────────────────────────────

    private List<RuleResult> evaluateClassDataSharing(AnalysisCompleteEvent event) {
        int estimatedSavings = (int) (event.totalStartupMs() * 0.33);

        return List.of(new RuleResult(
                "classpath_optimization",
                "Enable JVM Class Data Sharing (CDS)",
                String.format(
                        "Class Data Sharing pre-loads class metadata into a shared archive, " +
                        "reducing class loading time by ~33%% (est. %dms saved). " +
                        "Zero code changes required — JVM flag only.",
                        estimatedSavings),
                estimatedSavings,
                "low",
                """
                # Add to JVM startup flags:
                -XX:ArchiveClassesAtExit=app.jsa \\
                -XX:SharedArchiveFile=app.jsa

                # Or with Spring Boot:
                JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=app.jsa"
                """,
                null,
                List.of("Requires a training run to generate the shared archive (.jsa file)"),
                List.of(),
                null));
    }

    // ─── Rule: GRAALVM_NATIVE ─────────────────────────────────────────────────

    private List<RuleResult> evaluateGraalvmNative(AnalysisCompleteEvent event) {
        List<String> blockers = new ArrayList<>();

        if (event.bottlenecks() != null) {
            List<String> problematicBeans = event.bottlenecks().stream()
                    .filter(b -> {
                        String name = b.beanName().toLowerCase();
                        return name.contains("cglib") || name.contains("proxy");
                    })
                    .map(AnalysisCompleteEvent.BeanAnalysis::beanName)
                    .collect(Collectors.toList());

            if (!problematicBeans.isEmpty()) {
                blockers.add("Dynamic proxy/CGLIB beans detected: " + String.join(", ", problematicBeans));
            }
        }

        boolean feasible = blockers.isEmpty();
        int estimatedNativeMs = feasible ? (int) (event.totalStartupMs() * 0.05) : -1;
        int estimatedSavings = feasible ? (int) (event.totalStartupMs() * 0.95) : 0;

        Map<String, Object> graalvmFeasibility = new HashMap<>();
        graalvmFeasibility.put("feasible", feasible);
        graalvmFeasibility.put("blockers", blockers);
        if (feasible) {
            graalvmFeasibility.put("estimated_native_startup_ms", estimatedNativeMs);
        }

        String description = feasible
                ? String.format("No blockers detected. GraalVM native image compilation would reduce startup from %dms to ~%dms (95%% reduction).",
                        event.totalStartupMs(), estimatedNativeMs)
                : String.format("GraalVM native image compilation has %d blocker(s) that must be resolved first. " +
                        "Resolve dynamic proxy usage before proceeding.", blockers.size());

        return List.of(new RuleResult(
                "graalvm_native",
                feasible ? "Compile to GraalVM native image (95% startup reduction)" : "GraalVM native — blockers detected",
                description,
                estimatedSavings,
                "high",
                """
                # build.gradle — native image
                plugins {
                    id 'org.graalvm.buildtools.native' version '0.10.2'
                }

                graalvmNative {
                    binaries {
                        main {
                            imageName = 'app'
                            buildArgs.add('--no-fallback')
                        }
                    }
                }
                """,
                null,
                feasible ? List.of("Requires GraalVM JDK 21+", "Build time significantly increases (5–15 min)") : blockers,
                List.of(),
                graalvmFeasibility));
    }

    // ─── Rule: DEPENDENCY_REMOVAL ─────────────────────────────────────────────

    private List<RuleResult> evaluateDependencyRemoval(AnalysisCompleteEvent event) {
        if (event.autoconfigurations() == null) return List.of();

        // Detect DataSourceAutoConfiguration loaded without any DataSource beans
        boolean hasDataSourceAutoconfig = event.autoconfigurations().stream()
                .anyMatch(a -> a.matched() && a.className().contains("DataSourceAutoConfiguration"));

        boolean hasDataSourceBean = event.bottlenecks() != null && event.bottlenecks().stream()
                .anyMatch(b -> b.className().toLowerCase().contains("datasource"));

        List<RuleResult> results = new ArrayList<>();

        if (hasDataSourceAutoconfig && !hasDataSourceBean) {
            int savings = event.autoconfigurations().stream()
                    .filter(a -> a.className().contains("DataSource"))
                    .mapToInt(AnalysisCompleteEvent.AutoconfigAnalysis::durationMs)
                    .sum();

            results.add(new RuleResult(
                    "dependency_removal",
                    "Exclude unnecessary DataSourceAutoConfiguration",
                    "DataSourceAutoConfiguration is loaded but no DataSource beans were detected. " +
                    "Excluding this autoconfiguration reduces startup overhead.",
                    Math.max(savings, 50),
                    "low",
                    null,
                    """
                    # application.yml
                    spring:
                      autoconfigure:
                        exclude:
                          - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
                    """,
                    List.of("Only exclude if your application truly does not use a database"),
                    List.of("DataSourceAutoConfiguration"),
                    null));
        }

        return results;
    }

    // ─── Internal record ──────────────────────────────────────────────────────

    private record RuleResult(
            String category,
            String title,
            String description,
            int estimatedSavingsMs,
            String effort,
            String codeSnippet,
            String configSnippet,
            List<String> warnings,
            List<String> affectedBeans,
            Map<String, Object> graalvmFeasibility) {
    }
}
