package com.telecom.trino.plugin.aggregate;

import com.telecom.trino.plugin.aggregate.state.LongState;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.function.*;
import io.trino.spi.type.BigintType;
import io.trino.spi.type.StandardTypes;

/**
 * UDAF Aggregation Function: sum_bytes_native.
 * Sums a stream of bytes using custom LongState accumulator.
 */
@AggregationFunction("sum_bytes_native")
public final class SumBytesNativeAggregation {

    private SumBytesNativeAggregation() {}

    @InputFunction
    public static void input(
            LongState state,
            @SqlType(StandardTypes.BIGINT) long value) {
        state.setLong(state.getLong() + value);
    }

    @CombineFunction
    public static void combine(
            LongState state,
            LongState otherState) {
        state.setLong(state.getLong() + otherState.getLong());
    }

    @OutputFunction(StandardTypes.BIGINT)
    public static void output(
            LongState state,
            BlockBuilder out) {
        BigintType.BIGINT.writeLong(out, state.getLong());
    }
}
