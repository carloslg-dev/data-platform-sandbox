package com.telecom.trino.plugin.window;

import io.trino.spi.block.BlockBuilder;
import io.trino.spi.function.WindowFunction;
import io.trino.spi.function.WindowFunctionSignature;
import io.trino.spi.function.WindowIndex;
import io.trino.spi.type.BigintType;
import io.trino.spi.type.Type;

import java.util.List;

/**
 * Custom Window Function: native_running_sum.
 * Calculates running sum of a bigint channel across rows in a partition.
 */
@WindowFunctionSignature(
    name = "native_running_sum",
    returnType = "bigint",
    argumentTypes = {"bigint"}
)
public class NativeRunningSumWindowFunction implements WindowFunction {

    private final int inputChannel;
    private WindowIndex windowIndex;
    private int currentPosition = 0;
    private long runningSum = 0;

    public NativeRunningSumWindowFunction(List<Integer> inputs) {
        this.inputChannel = inputs.get(0);
    }

    @Override
    public void reset(WindowIndex windowIndex) {
        this.windowIndex = windowIndex;
        this.currentPosition = 0;
        this.runningSum = 0;
    }

    @Override
    public void processRow(BlockBuilder output, int peerGroupStart, int peerGroupEnd, int frameStart, int frameEnd) {
        // Read input argument at target channel for the current row position
        if (!windowIndex.isNull(inputChannel, currentPosition)) {
            runningSum += windowIndex.getLong(inputChannel, currentPosition);
        }
        
        // Write result to output
        BigintType.BIGINT.writeLong(output, runningSum);
        
        // Move to the next row position in the partition
        currentPosition++;
    }
}
