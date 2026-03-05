package io.springlens.ingestion.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Configuration;

/**
 * HIGH-003 Remediation: Kafka Dead-Letter Queue Configuration
 * Configures error handling for failed Kafka messages.
 */
@Configuration
public class KafkaConfig {

    /**
     * Bean for publishing failed messages to dead-letter topics.
     * Failed messages are routed to {originalTopic}.dead-letter automatically.
     */
    @Bean
    public ConsumerRecordRecoverer deadLetterRecoverer(KafkaTemplate<String, ?> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".dead-letter", record.partition()));
    }
}
