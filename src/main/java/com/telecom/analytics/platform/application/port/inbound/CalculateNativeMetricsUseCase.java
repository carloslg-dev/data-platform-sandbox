package com.telecom.analytics.platform.application.port.inbound;

import java.util.List;

public interface CalculateNativeMetricsUseCase {
    double calculateDurationStandardDeviation(List<Long> durations);
}
