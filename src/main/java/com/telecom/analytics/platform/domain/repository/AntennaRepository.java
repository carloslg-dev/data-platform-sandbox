package com.telecom.analytics.platform.domain.repository;

import com.telecom.analytics.platform.domain.model.Antenna;
import java.util.Optional;

public interface AntennaRepository {
    Optional<Antenna> findById(String id);
    Antenna save(Antenna antenna);
}
