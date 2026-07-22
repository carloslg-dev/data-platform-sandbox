package com.telecom.trino.plugin.aggregate.state;

import io.trino.spi.function.AccumulatorState;

/**
 * Accumulator state interface for custom sum aggregation.
 */
public interface LongState extends AccumulatorState {

    long getLong();

    void setLong(long value);
}
