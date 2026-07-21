package com.telecom.analytics.platform.application.service;

import com.telecom.analytics.platform.application.port.inbound.IngestTelemetryEventUseCase;
import com.telecom.analytics.platform.domain.model.AntennaEvent;
import com.telecom.analytics.platform.domain.repository.AntennaEventRepository;
import com.telecom.analytics.platform.domain.repository.AntennaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryIngestionService implements IngestTelemetryEventUseCase {

    private final AntennaRepository antennaRepository;
    private final AntennaEventRepository antennaEventRepository;

    @Override
    public void ingest(AntennaEvent event) {
        log.info("Receiving telemetry event: {}", event.eventId());

        // 1. Verify that the antenna exists in relational master data (PostgreSQL)
        boolean antennaExists = antennaRepository.findById(event.antennaId()).isPresent();
        if (!antennaExists) {
            log.error("Antenna {} does not exist in master data. Discarding event {}.", 
                event.antennaId(), event.eventId());
            throw new IllegalArgumentException("Referenced antenna " + event.antennaId() + " does not exist.");
        }

        if (event.version() == null && antennaEventRepository.existsByEventId(event.eventId())) {
            log.warn("Telemetry event {} is a duplicate. Discarding.", event.eventId());
            return; // Discard silently to satisfy the idempotency Gherkin spec
        }

        // 3. Save telemetry event with exponential backoff retry for optimistic locking conflicts
        int maxRetries = 3;
        int retryCount = 0;
        long backoffMs = 100;

        while (true) {
            try {
                AntennaEvent savedEvent = antennaEventRepository.save(event);
                log.info("Telemetry event {} successfully persisted in MongoDB. Version: {}", 
                    savedEvent.eventId(), savedEvent.version());
                break;
            } catch (OptimisticLockingFailureException e) {
                retryCount++;
                if (retryCount > maxRetries) {
                    log.error("Max retries exceeded for event ingestion concurrency. Saving failed for {}.", event.eventId());
                    throw e;
                }
                log.warn("Optimistic locking conflict on event {}. Triggering backoff retry {}/{}...", 
                    event.eventId(), retryCount, maxRetries);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoffMs *= 2; // Exponential backoff

                // Reload the latest document state from MongoDB and update version to resolve locking conflict
                Optional<AntennaEvent> latestDocOpt = antennaEventRepository.findByEventId(event.eventId());
                if (latestDocOpt.isPresent()) {
                    AntennaEvent latestDoc = latestDocOpt.get();
                    event = event.toBuilder().version(latestDoc.version()).build(); // Re-create event with current version to bypass version mismatch check
                }
            }
        }
    }
}
