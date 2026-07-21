package com.telecom.analytics.platform.domain.repository;

import com.telecom.analytics.platform.domain.model.AntennaEvent;
import java.util.Optional;

public interface AntennaEventRepository {
    boolean existsByEventId(String eventId);
    AntennaEvent save(AntennaEvent event);
    Optional<AntennaEvent> findByEventId(String eventId);
}
