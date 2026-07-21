package com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.mongodb;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAntennaEventRepository extends MongoRepository<AntennaEventMongoDocument, String> {
    boolean existsByEventId(String eventId);
    Optional<AntennaEventMongoDocument> findByEventId(String eventId);
}
