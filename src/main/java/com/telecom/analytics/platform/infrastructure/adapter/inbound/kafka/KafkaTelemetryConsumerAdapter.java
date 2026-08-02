package com.telecom.analytics.platform.infrastructure.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.analytics.platform.application.port.inbound.IngestTelemetryEventUseCase;
import com.telecom.analytics.platform.domain.model.AntennaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaTelemetryConsumerAdapter {

    private final IngestTelemetryEventUseCase ingestTelemetryEventUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "telemetry.antenna.raw",
        groupId = "cg-telemetry-ingestion",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        log.info("Received Kafka telemetry event from topic {} [Partition: {}, Offset: {}] with Key: {}",
                record.topic(), record.partition(), record.offset(), record.key());
        
        try {
            AntennaEvent event;
            if (record.value() instanceof AntennaEvent antennaEvent) {
                event = antennaEvent;
            } else if (record.value() instanceof String jsonString) {
                event = objectMapper.readValue(jsonString, AntennaEvent.class);
            } else {
                event = objectMapper.convertValue(record.value(), AntennaEvent.class);
            }

            log.info("Processing telemetry event ID: {} for antenna ID: {}", event.eventId(), event.antennaId());
            ingestTelemetryEventUseCase.ingest(event);
            
            // Commit offset after successful persistence and domain execution
            ack.acknowledge();
            log.debug("Successfully acknowledged offset {} for partition {}", record.offset(), record.partition());
        } catch (Exception e) {
            log.error("Failed to process Kafka telemetry event at offset {}: {}", record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process Kafka telemetry event", e);
        }
    }
}
