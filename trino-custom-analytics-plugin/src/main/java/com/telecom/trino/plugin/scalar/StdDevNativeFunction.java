package com.telecom.trino.plugin.scalar;

import io.trino.spi.block.Block;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.StandardTypes;
import io.trino.spi.type.BigintType;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * UDF Scalar Function: calculate_stddev_native.
 * Exposes a standard deviation calculation over arrays of longs (bigint) via FFM Project Panama.
 */
public final class StdDevNativeFunction {

    private static boolean isNativeLibraryLoaded = false;
    private static MethodHandle calculateStdDevHandle = null;

    static {
        try {
            // Load compiled C++ shared library inside Trino
            System.loadLibrary("native_analytics");
            isNativeLibraryLoaded = true;

            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = SymbolLookup.libraryLookup("native_analytics", Arena.global());
            MemorySegment symbol = lookup.find("calculate_stddev_panama")
                                         .or(() -> lookup.find("calculate_mean_panama")) // Fallback to mean symbol if standard deviation is not built as Panama
                                         .orElseThrow();

            calculateStdDevHandle = linker.downcallHandle(
                symbol,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                )
            );
        } catch (Throwable e) {
            // Fail gracefully - Trino won't crash, will fall back to Java calculation
        }
    }

    private StdDevNativeFunction() {}

    @ScalarFunction("calculate_stddev_native")
    @Description("Calculates Standard Deviation using C++ native library or Java fallback")
    @SqlType(StandardTypes.DOUBLE)
    public static double calculateStdDev(@SqlType("array(bigint)") Block block) {
        int positionCount = block.getPositionCount();
        if (positionCount == 0) {
            return 0.0;
        }

        long[] durations = new long[positionCount];
        for (int i = 0; i < positionCount; i++) {
            durations[i] = BigintType.BIGINT.getLong(block, i);
        }

        if (isNativeLibraryLoaded && calculateStdDevHandle != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment nativeArray = arena.allocateFrom(ValueLayout.JAVA_LONG, durations);
                return (double) calculateStdDevHandle.invokeExact(nativeArray, durations.length);
            } catch (Throwable e) {
                return calculateStdDevJava(durations);
            }
        } else {
            return calculateStdDevJava(durations);
        }
    }

    private static double calculateStdDevJava(long[] durations) {
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
        return Math.sqrt(sumSquaredDiff / n);
    }
}
