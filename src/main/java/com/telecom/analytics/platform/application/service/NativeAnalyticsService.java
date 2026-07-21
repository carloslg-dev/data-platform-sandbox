package com.telecom.analytics.platform.application.service;

import com.telecom.analytics.platform.application.port.inbound.CalculateNativeMetricsUseCase;
import com.telecom.analytics.platform.application.port.outbound.NativeAnalyticsPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NativeAnalyticsService implements CalculateNativeMetricsUseCase {

    private final NativeAnalyticsPort nativeAnalyticsPort;

    @Override
    public double calculateDurationStandardDeviation(List<Long> durations) {
        if (durations == null || durations.isEmpty()) {
            return 0.0;
        }

        // Convert List<Long> to primitive array long[]
        long[] array = durations.stream().mapToLong(Long::longValue).toArray();

        log.info("Calculating standard deviation of {} events durations using native port", array.length);
        double result = nativeAnalyticsPort.calculateStdDev(array);
        log.info("Calculation completed. Standard deviation: {}", result);

        return result;
    }
}
