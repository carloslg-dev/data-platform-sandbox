package com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAntennaRepository extends JpaRepository<AntennaJpaEntity, String> {
}
