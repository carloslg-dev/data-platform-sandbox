package com.telecom.analytics.platform.infrastructure.adapter.outbound.nativeopt;

import com.telecom.analytics.platform.application.port.outbound.NativeAnalyticsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NativeAnalyticsAdapter implements NativeAnalyticsPort {

    private static boolean isNativeLibraryLoaded = false;

    static {
        try {
            // Attempt to load the native library from java.library.path or system
            System.loadLibrary("native_analytics");
            isNativeLibraryLoaded = true;
            log.info("Successfully loaded C++ native analytics library (native_analytics).");
        } catch (UnsatisfiedLinkError e) {
            log.warn("Could not load C++ native library (native_analytics). Falling back to Java execution. Error: {}", e.getMessage());
        }
    }

    @Override
    public double calculateStdDev(long[] durations) {
        if (isNativeLibraryLoaded) {
            try {
                return calculateStdDevNative(durations);
            } catch (UnsatisfiedLinkError e) {
                log.error("JNI call failed. Falling back to Java calculation. Error: {}", e.getMessage());
                return calculateStdDevJava(durations);
            }
        } else {
            return calculateStdDevJava(durations);
        }
    }

    /**
     * JNI native method declaration.
     */
    private native double calculateStdDevNative(long[] durations);

    /**
     * Pure Java fallback calculation for standard deviation.
     */
    private double calculateStdDevJava(long[] durations) {
        if (durations == null || durations.length == 0) {
            return 0.0;
        }

        int n = durations.length;
        double sum = 0.0;
        for (long val : durations) {
            sum += val;
        }
        double mean = sum / n;

        double sumSquaredDiff = 0.0;
        for (long val : durations) {
            double diff = val - mean;
            sumSquaredDiff += diff * diff;
        }

        double variance = sumSquaredDiff / n;
        return Math.sqrt(variance);
    }
}
