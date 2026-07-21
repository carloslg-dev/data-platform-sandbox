package com.telecom.analytics.platform.application.port.outbound;

public interface NativeAnalyticsPort {
    double calculateStdDev(long[] durations);
}
