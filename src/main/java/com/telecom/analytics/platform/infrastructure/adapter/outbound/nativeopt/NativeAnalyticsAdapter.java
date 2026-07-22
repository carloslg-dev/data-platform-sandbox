package com.telecom.analytics.platform.infrastructure.adapter.outbound.nativeopt;

import com.telecom.analytics.platform.application.port.outbound.NativeAnalyticsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Javadoc: NativeAnalyticsAdapter handles JNI and Project Panama (FFM API) calls to C++ code.
 * 
 * <h2>Two Approaches of Java Native Interoperability</h2>
 * 
 * <h3>1. JNI (Java Native Interface) - standard deviation</h3>
 * - Bound directly to JVM implementation: requires JNIEnv pointers, jlongArray wrapper types, and strict naming conventions.
 * - Slower due to JNI boundary overhead and potential JVM-managed array pinning/copying.
 * 
 * <h3>2. Project Panama / FFM API (Java 21+) - mean</h3>
 * - Pure C++ function integration without standard JNI bindings (zero dependencies on Java in C++ source).
 * - Faster Downcalls: calls native functions directly via MethodHandle, managing off-heap MemorySegments using Arena lifecycles.
 */
@Slf4j
@Component
public class NativeAnalyticsAdapter implements NativeAnalyticsPort {

    private static boolean isNativeLibraryLoaded = false;
    private static MethodHandle calculateMeanHandle = null;

    static {
        try {
            // Attempt to load the native library from java.library.path or system
            System.loadLibrary("native_analytics");
            isNativeLibraryLoaded = true;
            log.info("Successfully loaded C++ native analytics library (native_analytics).");
            
            // Resolve Project Panama FFM Linker
            initializePanamaLinker();
        } catch (UnsatisfiedLinkError e) {
            log.warn("Could not load C++ native library (native_analytics). Falling back to Java execution. Error: {}", e.getMessage());
        }
    }

    private static void initializePanamaLinker() {
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = SymbolLookup.libraryLookup("native_analytics", Arena.global());
            MemorySegment symbol = lookup.find("calculate_mean_panama").orElseThrow();

            calculateMeanHandle = linker.downcallHandle(
                symbol,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_DOUBLE, // return double
                    ValueLayout.ADDRESS,     // const int64_t* durations
                    ValueLayout.JAVA_INT      // int32_t size
                )
            );
            log.info("Successfully linked native function 'calculate_mean_panama' via Project Panama FFM API.");
        } catch (Exception | LinkageError e) {
            log.warn("Could not initialize FFM API / Project Panama: {}. Fallback will be used.", e.getMessage());
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
     * Executes the mean calculation offloaded to C++ via Java 21+ FFM API (Project Panama).
     * Falls back to a pure Java calculation if the native library or linker is not loaded.
     */
    public double calculateMean(long[] durations) {
        if (isNativeLibraryLoaded && calculateMeanHandle != null) {
            try (Arena arena = Arena.ofConfined()) {
                // Allocate off-heap native memory for the array
                MemorySegment nativeArray = arena.allocateFrom(ValueLayout.JAVA_LONG, durations);
                // Call the pure C++ function directly
                return (double) calculateMeanHandle.invokeExact(nativeArray, durations.length);
            } catch (Throwable e) {
                log.error("FFM API / Project Panama invocation failed. Falling back to Java. Error: {}", e.getMessage());
                return calculateMeanJava(durations);
            }
        } else {
            return calculateMeanJava(durations);
        }
    }

    private native double calculateStdDevNative(long[] durations);

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

    private double calculateMeanJava(long[] durations) {
        if (durations == null || durations.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (long val : durations) {
            sum += val;
        }
        return sum / durations.length;
    }
}
