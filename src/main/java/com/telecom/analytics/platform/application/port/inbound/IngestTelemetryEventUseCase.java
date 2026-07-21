package com.telecom.analytics.platform.application.port.inbound;

import com.telecom.analytics.platform.domain.model.AntennaEvent;

public interface IngestTelemetryEventUseCase {
    void ingest(AntennaEvent event);
}
