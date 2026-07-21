package com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.postgres;

import com.telecom.analytics.platform.domain.model.Antenna;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "antennas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntennaJpaEntity {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "type", nullable = false, length = 10)
    private String type;

    @Column(name = "theoretical_capacity", nullable = false)
    private int theoreticalCapacity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public static AntennaJpaEntity fromDomain(Antenna antenna) {
        if (antenna == null) {
            return null;
        }
        return AntennaJpaEntity.builder()
                .id(antenna.id())
                .location(antenna.location())
                .type(antenna.type())
                .theoreticalCapacity(antenna.theoreticalCapacity())
                .status(antenna.status())
                .build();
    }

    public Antenna toDomain() {
        return Antenna.builder()
                .id(this.id)
                .location(this.location)
                .type(this.type)
                .theoreticalCapacity(this.theoreticalCapacity)
                .status(this.status)
                .build();
    }
}
