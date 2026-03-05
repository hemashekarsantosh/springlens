package io.springlens.analysis.consumer;

import io.springlens.analysis.analyzer.BeanGraphAnalyzer;
import io.springlens.analysis.analyzer.PhaseAnalyzer;
import io.springlens.analysis.entity.StartupTimeline;
import io.springlens.analysis.event.AnalysisCompleteEvent;
import io.springlens.analysis.event.StartupEvent;
import io.springlens.analysis.mapper.TimelineMapper;
import io.springlens.analysis.repository.StartupTimelineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kafka consumer for startup.events topic.
 * Runs analysis (bean graph + phase breakdown) and persists results.
 * Publishes AnalysisCompleteEvent to analysis.complete topic.
 */
@Component
public class StartupEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(StartupEventConsumer.class);
    private static final String ANALYSIS_COMPLETE_TOPIC = "analysis.complete";

    private final BeanGraphAnalyzer beanGraphAnalyzer;
    private final PhaseAnalyzer phaseAnalyzer;
    private final StartupTimelineRepository timelineRepository;
    private final KafkaTemplate<String, AnalysisCompleteEvent> kafkaTemplate;
    private final TimelineMapper timelineMapper;

    public StartupEventConsumer(BeanGraphAnalyzer beanGraphAnalyzer,
                                 PhaseAnalyzer phaseAnalyzer,
                                 StartupTimelineRepository timelineRepository,
                                 KafkaTemplate<String, AnalysisCompleteEvent> kafkaTemplate,
                                 TimelineMapper timelineMapper) {
        this.beanGraphAnalyzer = beanGraphAnalyzer;
        this.phaseAnalyzer = phaseAnalyzer;
        this.timelineRepository = timelineRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.timelineMapper = timelineMapper;
    }

    @KafkaListener(topics = "startup.events", groupId = "analysis-service",
            containerFactory = "startupEventListenerContainerFactory")
    @Transactional
    public void consume(StartupEvent event) {
        log.info("Consuming StartupEvent snapshot={} project={} env={}",
                event.snapshotId(), event.projectId(), event.environmentName());

        try {
            // Check for existing timeline (idempotency)
            if (timelineRepository.findBySnapshotId(event.snapshotId()).isPresent()) {
                log.info("Timeline already exists for snapshot={}, skipping", event.snapshotId());
                return;
            }

            // Run analysis with null-safe defaults
            var beanEvents = Optional.ofNullable(event.beans()).orElse(List.of());
            var graphResult = beanGraphAnalyzer.analyze(beanEvents);
            var phaseResults = phaseAnalyzer.analyze(
                    Optional.ofNullable(event.phases()).orElse(List.of()),
                    event.totalStartupMs());

            // Map to timeline entity using TimelineMapper
            var timeline = timelineMapper.mapToTimeline(event, graphResult, phaseResults);

            timelineRepository.save(timeline);

            // Publish downstream event
            var completeEvent = buildAnalysisCompleteEvent(event, graphResult, phaseResults);
            kafkaTemplate.send(ANALYSIS_COMPLETE_TOPIC, event.snapshotId().toString(), completeEvent)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish AnalysisCompleteEvent snapshot={}", event.snapshotId(), ex);
                        } else {
                            log.debug("Published AnalysisCompleteEvent snapshot={}", event.snapshotId());
                        }
                    });

            log.info("Analysis complete snapshot={} bottlenecks={} beans={}",
                    event.snapshotId(), graphResult.bottleneckBeanNames().size(), beanEvents.size());

        } catch (Exception ex) {
            log.error("Analysis failed snapshot={}", event.snapshotId(), ex);
            throw ex;
        }
    }

    private AnalysisCompleteEvent buildAnalysisCompleteEvent(StartupEvent event,
                                                              BeanGraphAnalyzer.BeanGraphResult graphResult,
                                                              List<PhaseAnalyzer.PhaseResult> phaseResults) {
        var bottleneckSet = new java.util.HashSet<>(graphResult.bottleneckBeanNames());

        List<AnalysisCompleteEvent.BeanAnalysis> beanAnalyses = new ArrayList<>();
        if (event.beans() != null) {
            for (var bean : event.beans()) {
                beanAnalyses.add(new AnalysisCompleteEvent.BeanAnalysis(
                        bean.beanName(),
                        bean.className(),
                        bean.durationMs(),
                        bottleneckSet.contains(bean.beanName()),
                        bean.dependencies() != null ? bean.dependencies() : List.of()));
            }
        }

        List<AnalysisCompleteEvent.PhaseBreakdown> phases = phaseResults.stream()
                .map(p -> new AnalysisCompleteEvent.PhaseBreakdown(
                        p.phaseName(), p.durationMs(), p.startMs(), p.percentageOfTotal()))
                .toList();

        List<AnalysisCompleteEvent.AutoconfigAnalysis> autoconfigs = new ArrayList<>();
        if (event.autoconfigurations() != null) {
            for (var ac : event.autoconfigurations()) {
                autoconfigs.add(new AnalysisCompleteEvent.AutoconfigAnalysis(
                        ac.className(), ac.matched(), ac.durationMs()));
            }
        }

        return new AnalysisCompleteEvent(
                event.snapshotId(),
                event.workspaceId(),
                event.projectId(),
                event.environmentName(),
                event.gitCommitSha(),
                event.totalStartupMs(),
                graphResult.bottleneckBeanNames().size(),
                beanAnalyses.stream().filter(AnalysisCompleteEvent.BeanAnalysis::isBottleneck).toList(),
                phases,
                autoconfigs,
                Instant.now());
    }
}
