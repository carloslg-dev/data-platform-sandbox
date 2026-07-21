package com.telecom.analytics.platform.domain.model;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record AntennaEvent(
    String eventId,
    String antennaId,
    String eventType, // 'VOICE_CALL' | 'DATA_SESSION'
    Long durationMs,
    Long bytesTransferred,
    Instant timestamp,
    Integer version
) {}
