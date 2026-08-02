package com.telecom.analytics.platform.infrastructure.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.analytics.platform.domain.model.AntennaEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaTelemetryMonitoringAdapter {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "telemetry.antenna.raw",
        groupId = "cg-telemetry-monitoring",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void monitor(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            AntennaEvent event = parseEvent(record.value());
            log.debug("Monitoring consumer processing event ID: {} for cell/antenna: {}", event.eventId(), event.antennaId());

            // Saturation alert rule: bandwidth/capacity usage > 80%
            boolean isSaturated = false;
            if (event.bytesTransferred() != null && event.bytesTransferred() > 800_000_000L) { // Example threshold calculation
                isSaturated = true;
            }

            if (isSaturated) {
                log.warn("CELL CONGESTION DETECTED on antenna {}! Emitting alert payload to telemetry.antenna.alerts topic.", event.antennaId());
                Map<String, Object> alertPayload = Map.of(
                    "alertId", "alt_" + event.eventId(),
                    "antennaId", event.antennaId(),
                    "timestamp", event.timestamp().toString(),
                    "alertType", "CELL_SATURATION_WARNING",
                    "details", "Bandwidth usage breached 80% capacity threshold"
                );
                kafkaTemplate.send("telemetry.antenna.alerts", event.antennaId(), alertPayload);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Monitoring listener encountered error processing event offset {}: {}", record.offset(), e.getMessage());
            ack.acknowledge(); // Acknowledge to prevent blocking saturation pipeline monitoring
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
