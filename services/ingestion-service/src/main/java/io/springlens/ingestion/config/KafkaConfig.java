package io.springlens.ingestion.config;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Configuration;
import org.springframework.context.annotation.Bean;

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
                (record, ex) -> {
                    // Append .dead-letter suffix to the original topic
                    String dlqTopic = record.topic() + ".dead-letter";
                    return new org.apache.kafka.clients.producer.ProducerRecord<>(
                            dlqTopic,
                            record.partition(),
                            record.timestamp(),
                            record.key(),
                            record.value(),
                            record.headers()
                    );
                });
    }
}
