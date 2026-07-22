package com.telecom.trino.plugin.scalar;

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.spi.function.Description;
import io.trino.spi.function.ScalarFunction;
import io.trino.spi.function.SqlType;
import io.trino.spi.type.StandardTypes;

/**
 * UDF Scalar Function: classify_antenna_utilization.
 * Categorizes antenna network load based on current aggregate traffic vs theoretical capacity.
 */
public final class ClassifyAntennaFunction {

    private ClassifyAntennaFunction() {}

    @ScalarFunction("classify_antenna_utilization")
    @Description("Classifies antenna traffic load based on total traffic bytes and capacity in Mbps")
    @SqlType(StandardTypes.VARCHAR)
    public static Slice classify(
            @SqlType(StandardTypes.BIGINT) long totalTrafficBytes,
            @SqlType(StandardTypes.BIGINT) long theoreticalCapacityMbps) {
        
        if (theoreticalCapacityMbps <= 0) {
            return Slices.utf8Slice("UNKNOWN_CAPACITY");
        }

        // Convert traffic bytes to Megabits to compare against capacity (Mbps)
        // 1 byte = 8 bits
        double trafficMegabits = (totalTrafficBytes * 8.0) / (1000.0 * 1000.0);
        double utilizationRatio = trafficMegabits / theoreticalCapacityMbps;

        if (utilizationRatio > 0.8) {
            return Slices.utf8Slice("HIGH_LOAD");
        } else if (utilizationRatio < 0.1) {
            return Slices.utf8Slice("UNDER_UTILIZED");
        } else {
            return Slices.utf8Slice("NORMAL");
        }
    }
}
