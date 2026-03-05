package io.springlens.analysis.analyzer;

import io.springlens.analysis.event.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds a directed acyclic graph (DAG) from bean initialization events.
 * Identifies bottleneck beans where duration exceeds the configured threshold.
 */
@Component
public class BeanGraphAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(BeanGraphAnalyzer.class);

    @Value("${springlens.analysis.bottleneck-threshold-ms:200}")
    private int bottleneckThresholdMs;

    public BeanGraphResult analyze(List<StartupEvent.BeanEventData> beans) {
        log.info("Analyzing bean graph bean_count={} threshold_ms={}", beans.size(), bottleneckThresholdMs);

        List<BeanNode> nodes = new ArrayList<>();
        List<BeanEdge> edges = new ArrayList<>();
        List<String> bottlenecks = new ArrayList<>();

        for (StartupEvent.BeanEventData bean : beans) {
            boolean isBottleneck = bean.durationMs() >= bottleneckThresholdMs;
            if (isBottleneck) {
                bottlenecks.add(bean.beanName());
            }

            nodes.add(new BeanNode(
                    bean.beanName(),
                    bean.beanName(),
                    bean.className(),
                    bean.durationMs(),
                    isBottleneck));

            if (bean.dependencies() != null) {
                for (String dep : bean.dependencies()) {
                    edges.add(new BeanEdge(dep, bean.beanName()));
                }
            }
        }

        log.info("Bean graph built nodes={} edges={} bottlenecks={}",
                nodes.size(), edges.size(), bottlenecks.size());

        return new BeanGraphResult(nodes, edges, bottlenecks);
    }

    public record BeanNode(
            String id,
            String label,
            String className,
            int durationMs,
            boolean isBottleneck) {
    }

    public record BeanEdge(String source, String target) {
    }

    public record BeanGraphResult(
            List<BeanNode> nodes,
            List<BeanEdge> edges,
            List<String> bottleneckBeanNames) {
    }
}
