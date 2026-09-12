package com.roy.worktoscroll;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class BatterySnapshotTest {
    @Test public void calculatesTemperatureRiseFromAvailableEndpoints() {
        BatterySnapshot start = new BatterySnapshot(1, 2, 329, 30, 3780, 2, 0, "");
        BatterySnapshot end = new BatterySnapshot(3, 4, 346, 26, 3740, 3, 0, "");

        assertEquals(17, BatterySnapshot.temperatureDeltaTenths(start, end));
    }

    @Test public void missingEndpointDoesNotInventTemperatureRise() {
        BatterySnapshot start = BatterySnapshot.unavailable(1, 2, "missing");
        BatterySnapshot end = new BatterySnapshot(3, 4, 346, 26, 3740, 3, 0, "");

        assertEquals(BatterySnapshot.UNKNOWN, BatterySnapshot.temperatureDeltaTenths(start, end));
    }
}
