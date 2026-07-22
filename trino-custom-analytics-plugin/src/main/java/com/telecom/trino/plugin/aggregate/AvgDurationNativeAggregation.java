package com.telecom.trino.plugin.aggregate;

import com.telecom.trino.plugin.aggregate.state.AvgState;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.function.*;
import io.trino.spi.type.DoubleType;
import io.trino.spi.type.StandardTypes;

/**
 * UDAF Aggregation Function: avg_duration_native.
 * Aggregates a stream of durations to compute their average.
 */
@AggregationFunction("avg_duration_native")
public final class AvgDurationNativeAggregation {

    private AvgDurationNativeAggregation() {}

    @InputFunction
    public static void input(
            AvgState state,
            @SqlType(StandardTypes.BIGINT) long value) {
        state.setSum(state.getSum() + value);
        state.setCount(state.getCount() + 1);
    }

    @CombineFunction
    public static void combine(
            AvgState state,
            AvgState otherState) {
        state.setSum(state.getSum() + otherState.getSum());
        state.setCount(state.getCount() + otherState.getCount());
    }

    @OutputFunction(StandardTypes.DOUBLE)
    public static void output(
            AvgState state,
            BlockBuilder out) {
        long count = state.getCount();
        if (count == 0) {
            out.appendNull();
        } else {
            double average = state.getSum() / count;
            DoubleType.DOUBLE.writeDouble(out, average);
        }
    }
}
