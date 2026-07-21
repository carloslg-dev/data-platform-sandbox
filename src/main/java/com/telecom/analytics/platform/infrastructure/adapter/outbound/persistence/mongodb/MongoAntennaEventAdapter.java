package com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.mongodb;

import com.telecom.analytics.platform.domain.model.AntennaEvent;
import com.telecom.analytics.platform.domain.repository.AntennaEventRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MongoAntennaEventAdapter implements AntennaEventRepository {

    private final SpringDataAntennaEventRepository springDataAntennaEventRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return springDataAntennaEventRepository.existsByEventId(eventId);
    }

    @Override
    public Optional<AntennaEvent> findByEventId(String eventId) {
        return springDataAntennaEventRepository.findByEventId(eventId)
                .map(AntennaEventMongoDocument::toDomain);
    }

    @Override
    public AntennaEvent save(AntennaEvent event) {
        // Check if the document already exists in MongoDB by its business key (eventId)
        Optional<AntennaEventMongoDocument> existingDocOpt = springDataAntennaEventRepository.findByEventId(event.eventId());

        AntennaEventMongoDocument docToSave;
        if (existingDocOpt.isPresent()) {
            AntennaEventMongoDocument existingDoc = existingDocOpt.get();
            // Preserve the internal database ObjectId and copy the fields from the domain model
            docToSave = AntennaEventMongoDocument.builder()
                    .id(existingDoc.getId()) // Crucial for updating and triggering version verification
                    .eventId(event.eventId())
                    .antennaId(event.antennaId())
                    .eventType(event.eventType())
                    .durationMs(event.durationMs())
                    .bytesTransferred(event.bytesTransferred())
                    .timestamp(event.timestamp())
                    .version(event.version()) // Set the version to check/increment
                    .build();
        } else {
            docToSave = AntennaEventMongoDocument.fromDomain(event);
            docToSave.setVersion(null);
        }

        // Saves or updates document. If version does not match, throws OptimisticLockingFailureException.
        AntennaEventMongoDocument savedDoc = springDataAntennaEventRepository.save(docToSave);
        return savedDoc.toDomain();
    }
}
