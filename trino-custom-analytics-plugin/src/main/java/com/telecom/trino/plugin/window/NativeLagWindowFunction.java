package com.telecom.trino.plugin.window;

import io.trino.spi.block.BlockBuilder;
import io.trino.spi.function.WindowFunction;
import io.trino.spi.function.WindowFunctionSignature;
import io.trino.spi.function.WindowIndex;
import io.trino.spi.type.BigintType;
import io.trino.spi.type.Type;

import java.util.List;

/**
 * Custom Window Function: native_lag.
 * Retrieves the value of a channel at a given offset position behind the current row.
 */
@WindowFunctionSignature(
    name = "native_lag",
    returnType = "bigint",
    argumentTypes = {"bigint", "bigint"}
)
public class NativeLagWindowFunction implements WindowFunction {

    private final int valueChannel;
    private final int offsetChannel;
    private WindowIndex windowIndex;
    private int currentPosition = 0;

    public NativeLagWindowFunction(List<Integer> inputs) {
        this.valueChannel = inputs.get(0);
        this.offsetChannel = inputs.get(1);
    }

    @Override
    public void reset(WindowIndex windowIndex) {
        this.windowIndex = windowIndex;
        this.currentPosition = 0;
    }

    @Override
    public void processRow(BlockBuilder output, int peerGroupStart, int peerGroupEnd, int frameStart, int frameEnd) {
        // Read offset parameter from target channel (default to 1 if null)
        int offset = 1;
        if (!windowIndex.isNull(offsetChannel, currentPosition)) {
            offset = (int) windowIndex.getLong(offsetChannel, currentPosition);
        }

        int targetPosition = currentPosition - offset;
        
        // If the target position is within the partition range, retrieve its value
        if (targetPosition >= 0 && !windowIndex.isNull(valueChannel, targetPosition)) {
            BigintType.BIGINT.writeLong(output, windowIndex.getLong(valueChannel, targetPosition));
        } else {
            output.appendNull();
        }

        currentPosition++;
    }
}
