package com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.postgres;

import com.telecom.analytics.platform.domain.model.Antenna;
import com.telecom.analytics.platform.domain.repository.AntennaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresAntennaAdapter implements AntennaRepository {

    private final SpringDataAntennaRepository springDataAntennaRepository;

    @Override
    public Optional<Antenna> findById(String id) {
        return springDataAntennaRepository.findById(id)
                .map(AntennaJpaEntity::toDomain);
    }

    @Override
    public Antenna save(Antenna antenna) {
        AntennaJpaEntity entity = AntennaJpaEntity.fromDomain(antenna);
        AntennaJpaEntity savedEntity = springDataAntennaRepository.saveAndFlush(entity);
        return savedEntity.toDomain();
    }
}
