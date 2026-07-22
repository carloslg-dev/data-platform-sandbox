package com.telecom.trino.plugin;

import com.google.common.collect.ImmutableSet;
import com.telecom.trino.plugin.scalar.StdDevNativeFunction;
import com.telecom.trino.plugin.scalar.ClassifyAntennaFunction;
import com.telecom.trino.plugin.aggregate.SumBytesNativeAggregation;
import com.telecom.trino.plugin.aggregate.AvgDurationNativeAggregation;
import com.telecom.trino.plugin.window.NativeRunningSumWindowFunction;
import com.telecom.trino.plugin.window.NativeLagWindowFunction;
import io.trino.spi.Plugin;

import java.util.Set;

/**
 * Entry point of the custom Trino UDF plugin.
 * Registers Scalar, Aggregation, and Window functions.
 */
public class CustomAnalyticsPlugin implements Plugin {

    @Override
    public Set<Class<?>> getFunctions() {
        return ImmutableSet.<Class<?>>builder()
                // Scalar UDFs
                .add(StdDevNativeFunction.class)
                .add(ClassifyAntennaFunction.class)
                // Aggregation UDFs (UDAFs)
                .add(SumBytesNativeAggregation.class)
                .add(AvgDurationNativeAggregation.class)
                // Window Functions (Trino generates factories dynamically based on @WindowFunctionSignature)
                .add(NativeRunningSumWindowFunction.class)
                .add(NativeLagWindowFunction.class)
                .build();
    }
}
