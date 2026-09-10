package com.roy.worktoscroll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Test;

public final class MonitoringReturnTicketTest {
    @After public void clear() { MonitoringReturnTicket.clear(); }

    @Test public void matchingTicketIsConsumedOnce() {
        MonitoringReturnTicket.arm(100L, 1_000L);
        assertTrue(MonitoringReturnTicket.consume(100L, 1_001L));
        assertFalse(MonitoringReturnTicket.consume(100L, 1_002L));
    }

    @Test public void mismatchConsumesTicketWithoutForwarding() {
        MonitoringReturnTicket.arm(100L, 1_000L);
        assertFalse(MonitoringReturnTicket.consume(101L, 1_001L));
        assertFalse(MonitoringReturnTicket.consume(100L, 1_002L));
    }

    @Test public void expiredTicketCannotForward() {
        MonitoringReturnTicket.arm(100L, 1_000L);
        assertFalse(MonitoringReturnTicket.consume(100L, 1_000L + MonitoringReturnTicket.MAX_AGE_MS));
    }

    @Test public void clearPreventsAnyLaterForward() {
        MonitoringReturnTicket.arm(100L, 1_000L);
        MonitoringReturnTicket.clear();
        assertFalse(MonitoringReturnTicket.consume(100L, 1_001L));
    }
}
