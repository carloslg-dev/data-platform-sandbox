package com.telecom.trino.plugin.aggregate.state;

import io.trino.spi.function.AccumulatorState;

/**
 * Accumulator state for custom average calculation.
 * Trino generates the backing bytecode implementation dynamically at runtime.
 */
public interface AvgState extends AccumulatorState {

    double getSum();

    void setSum(double sum);

    long getCount();

    void setCount(long count);
}
