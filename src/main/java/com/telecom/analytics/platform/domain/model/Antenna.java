package com.telecom.analytics.platform.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Antenna(
    String id,
    String location,
    String type, // '3G', '4G', '5G'
    int theoreticalCapacity, // in Mbps
    String status // 'ACTIVE', 'INACTIVE', 'MAINTENANCE'
) {
    /**
     * Business Invariant: Check if the antenna is active
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }
}
