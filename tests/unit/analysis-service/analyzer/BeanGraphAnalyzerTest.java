package io.springlens.analysis.analyzer;

import io.springlens.analysis.event.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BeanGraphAnalyzer.
 * Tests DAG construction, bottleneck detection, dependency handling.
 */
class BeanGraphAnalyzerTest {

    private BeanGraphAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BeanGraphAnalyzer();
        // Inject bottleneck threshold via reflection or constructor
        // For now, assumes default 200ms
    }

    @Test
    void shouldIdentifyBottlenecks_WhenBeanDurationExceedsThreshold() {
        // UNIT-ANALYSIS-002: Bottleneck detection (>200ms)
        List<StartupEvent.BeanEventData> beans = List.of(
                new StartupEvent.BeanEventData("bean1", "com.example.Bean1", 150, 0, null),
                new StartupEvent.BeanEventData("bean2", "com.example.Bean2", 300, 150, null),
                new StartupEvent.BeanEventData("bean3", "com.example.Bean3", 250, 450, null)
        );

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        assertEquals(2, result.bottleneckBeanNames().size());
        assertTrue(result.bottleneckBeanNames().contains("bean2"));
        assertTrue(result.bottleneckBeanNames().contains("bean3"));
        assertFalse(result.bottleneckBeanNames().contains("bean1"));
    }

    @Test
    void shouldConstructDAG_WithCorrectNodes() {
        // UNIT-ANALYSIS-001: Bean DAG construction
        List<StartupEvent.BeanEventData> beans = List.of(
                new StartupEvent.BeanEventData("bean1", "com.example.Bean1", 100, 0, null),
                new StartupEvent.BeanEventData("bean2", "com.example.Bean2", 200, 100, List.of("bean1"))
        );

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        assertEquals(2, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.id().equals("bean1")));
        assertTrue(result.nodes().stream().anyMatch(n -> n.id().equals("bean2")));
    }

    @Test
    void shouldConstructDAG_WithCorrectEdges() {
        // UNIT-ANALYSIS-001: Bean dependency graph
        List<StartupEvent.BeanEventData> beans = List.of(
                new StartupEvent.BeanEventData("bean1", "com.example.Bean1", 100, 0, null),
                new StartupEvent.BeanEventData("bean2", "com.example.Bean2", 200, 100, List.of("bean1")),
                new StartupEvent.BeanEventData("bean3", "com.example.Bean3", 150, 300, List.of("bean1", "bean2"))
        );

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        // 3 edges total: bean1->bean2, bean1->bean3, bean2->bean3
        assertEquals(3, result.edges().size());
        assertTrue(result.edges().stream().anyMatch(e -> e.source().equals("bean1") && e.target().equals("bean2")));
        assertTrue(result.edges().stream().anyMatch(e -> e.source().equals("bean1") && e.target().equals("bean3")));
        assertTrue(result.edges().stream().anyMatch(e -> e.source().equals("bean2") && e.target().equals("bean3")));
    }

    @Test
    void shouldHandleEmptyBeanList() {
        // UNIT-ANALYSIS-008: Empty bean list handling
        List<StartupEvent.BeanEventData> beans = List.of();

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        assertEquals(0, result.nodes().size());
        assertEquals(0, result.edges().size());
        assertEquals(0, result.bottleneckBeanNames().size());
    }

    @Test
    void shouldHandleNullDependencies() {
        // UNIT-ANALYSIS-001: Graceful null handling
        List<StartupEvent.BeanEventData> beans = List.of(
                new StartupEvent.BeanEventData("bean1", "com.example.Bean1", 100, 0, null),
                new StartupEvent.BeanEventData("bean2", "com.example.Bean2", 200, 100, null)
        );

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        assertEquals(2, result.nodes().size());
        assertEquals(0, result.edges().size()); // No dependencies
    }

    @Test
    void shouldHandleZeroThresholdCase() {
        // Edge case: bean exactly at 200ms threshold
        List<StartupEvent.BeanEventData> beans = List.of(
                new StartupEvent.BeanEventData("bean1", "com.example.Bean1", 200, 0, null),
                new StartupEvent.BeanEventData("bean2", "com.example.Bean2", 199, 200, null)
        );

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        assertEquals(1, result.bottleneckBeanNames().size());
        assertTrue(result.bottleneckBeanNames().contains("bean1")); // >= threshold
        assertFalse(result.bottleneckBeanNames().contains("bean2")); // < threshold
    }

    @Test
    void shouldPreserveBeanMetadata() {
        // UNIT-ANALYSIS-001: Verify node contains correct metadata
        String className = "com.example.DataSourceBean";
        int duration = 250;
        List<StartupEvent.BeanEventData> beans = List.of(
                new StartupEvent.BeanEventData("dataSource", className, duration, 0, null)
        );

        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);

        BeanGraphAnalyzer.BeanNode node = result.nodes().get(0);
        assertEquals("dataSource", node.label());
        assertEquals(className, node.className());
        assertEquals(duration, node.durationMs());
        assertTrue(node.isBottleneck());
    }

    @Test
    void shouldHandleLargeBeanGraphPerformantly() {
        // Stress test: 1000 beans
        List<StartupEvent.BeanEventData> beans = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            List<String> deps = i > 0 ? List.of("bean" + (i - 1)) : null;
            beans.add(new StartupEvent.BeanEventData(
                    "bean" + i,
                    "com.example.Bean" + i,
                    100 + (i % 200),
                    i * 100,
                    deps
            ));
        }

        long start = System.currentTimeMillis();
        BeanGraphAnalyzer.BeanGraphResult result = analyzer.analyze(beans);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(1000, result.nodes().size());
        assertEquals(999, result.edges().size()); // Linear chain
        assertTrue(elapsed < 1000, "Analysis should complete in < 1 second for 1000 beans");
    }
}
