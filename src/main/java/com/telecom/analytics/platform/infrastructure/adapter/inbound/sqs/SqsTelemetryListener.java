package com.telecom.analytics.platform.infrastructure.adapter.inbound.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.analytics.platform.application.port.inbound.IngestTelemetryEventUseCase;
import com.telecom.analytics.platform.domain.model.AntennaEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsTelemetryListener {

    private final IngestTelemetryEventUseCase ingestTelemetryEventUseCase;
    private final ObjectMapper objectMapper;

    @SqsListener("${telemetry.queue-name:telemetry-events.fifo}")
    public void listen(String messageBody) {
        log.info("Received event raw payload from SQS: {}", messageBody);
        try {
            AntennaEvent event = objectMapper.readValue(messageBody, AntennaEvent.class);
            log.info("Successfully deserialized telemetry event: {}", event.eventId());
            ingestTelemetryEventUseCase.ingest(event);
        } catch (Exception e) {
            log.error("Failed to deserialize or process telemetry event: {}", e.getMessage());
            throw new RuntimeException("Failed to process SQS message", e); // Trigger visibility retry
        }
    }
}
