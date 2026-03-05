package io.springlens.analysis.analyzer;

import io.springlens.analysis.event.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BeanGraphAnalyzerTest {

    private BeanGraphAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BeanGraphAnalyzer();
        // Use reflection to inject bottleneck threshold for testing
        try {
            var field = BeanGraphAnalyzer.class.getDeclaredField("bottleneckThresholdMs");
            field.setAccessible(true);
            field.setInt(analyzer, 200);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void analyze_emptyBeans_returnsEmptyGraph() {
        var result = analyzer.analyze(List.of());

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.bottleneckBeanNames()).isEmpty();
    }

    @Test
    void analyze_singleBean_underThreshold() {
        var bean = new StartupEvent.BeanEventData(
                "myBean", "com.example.MyBean", 100, 0, List.of());

        var result = analyzer.analyze(List.of(bean));

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().get(0).isBottleneck()).isFalse();
        assertThat(result.bottleneckBeanNames()).isEmpty();
    }

    @Test
    void analyze_singleBean_overThreshold() {
        var bean = new StartupEvent.BeanEventData(
                "slowBean", "com.example.SlowBean", 300, 0, List.of());

        var result = analyzer.analyze(List.of(bean));

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().get(0).isBottleneck()).isTrue();
        assertThat(result.bottleneckBeanNames()).containsExactly("slowBean");
    }

    @Test
    void analyze_withDependencies_buildsEdges() {
        var beanA = new StartupEvent.BeanEventData(
                "beanA", "com.example.A", 150, 0, List.of());
        var beanB = new StartupEvent.BeanEventData(
                "beanB", "com.example.B", 250, 150, List.of("beanA"));

        var result = analyzer.analyze(List.of(beanA, beanB));

        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().get(0).source()).isEqualTo("beanA");
        assertThat(result.edges().get(0).target()).isEqualTo("beanB");
    }

    @Test
    void analyze_multipleBeans_identifiesBottlenecks() {
        var beans = List.of(
                new StartupEvent.BeanEventData("fast1", "com.Fast1", 50, 0, List.of()),
                new StartupEvent.BeanEventData("slow1", "com.Slow1", 250, 50, List.of()),
                new StartupEvent.BeanEventData("fast2", "com.Fast2", 100, 300, List.of()),
                new StartupEvent.BeanEventData("slow2", "com.Slow2", 350, 400, List.of())
        );

        var result = analyzer.analyze(beans);

        assertThat(result.bottleneckBeanNames()).containsExactlyInAnyOrder("slow1", "slow2");
    }

    @Test
    void analyze_nullDependencies_handled() {
        var bean = new StartupEvent.BeanEventData(
                "beanNoDepends", "com.example.Bean", 250, 0, null);

        var result = analyzer.analyze(List.of(bean));

        assertThat(result.edges()).isEmpty();
        assertThat(result.bottleneckBeanNames()).containsExactly("beanNoDepends");
    }
}
