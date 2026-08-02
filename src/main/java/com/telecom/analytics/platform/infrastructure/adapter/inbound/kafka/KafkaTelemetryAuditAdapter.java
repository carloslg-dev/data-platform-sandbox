package com.telecom.analytics.platform.infrastructure.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.analytics.platform.domain.model.AntennaEvent;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaTelemetryAuditAdapter {

    private final ObjectMapper objectMapper;
    
    @Getter
    private final AtomicLong auditedEventCount = new AtomicLong(0);

    @KafkaListener(
        topics = "telemetry.antenna.raw",
        groupId = "cg-telemetry-audit",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void auditReplay(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            AntennaEvent event = parseEvent(record.value());
            long totalAudited = auditedEventCount.incrementAndGet();
            log.info("[AUDIT CONSUMER REPLAY] Processed event ID: {} for antenna: {} [Total Audited: {}]",
                    event.eventId(), event.antennaId(), totalAudited);
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[AUDIT CONSUMER ERROR] Failed to process offset {}: {}", record.offset(), e.getMessage());
            ack.acknowledge();
        }
    }

    private AntennaEvent parseEvent(Object value) throws Exception {
        if (value instanceof AntennaEvent antennaEvent) {
            return antennaEvent;
        } else if (value instanceof String jsonString) {
            return objectMapper.readValue(jsonString, AntennaEvent.class);
        } else {
            return objectMapper.convertValue(value, AntennaEvent.class);
        }
    }
}
