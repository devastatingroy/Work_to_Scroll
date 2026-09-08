package com.roy.presenceexperiment;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresenceGateTest {
    private PresenceGate present() {
        PresenceGate gate = new PresenceGate();
        gate.observe(1000, 1); gate.observe(1500, 1); gate.observe(2000, 1);
        assertEquals("PRESENT", gate.state(2000)); return gate;
    }
    @Test public void confirmationNeedsContinuousNewResults() {
        PresenceGate gate = new PresenceGate(); gate.observe(1000, 1);
        assertEquals("CHECKING", gate.state(1000));
        gate.observe(3000, 1); assertEquals("CHECKING", gate.state(3000));
    }
    @Test public void staleResultCannotKeepPresence() {
        PresenceGate gate = present(); assertEquals("NO_FRESH_RESULT", gate.state(3501));
        gate.observe(4000, 1); assertEquals("CHECKING", gate.state(4000));
    }
    @Test public void absenceHasBoundedGrace() {
        PresenceGate gate = present();
        for (long at = 2500; at <= 5000; at += 500) { gate.observe(at, 0); assertEquals("GRACE", gate.state(at)); }
        gate.observe(5500, 0); assertEquals("NOT_SEEN", gate.state(5500));
    }
    @Test public void returnAfterGraceMustReconfirm() {
        PresenceGate gate = present();
        for (long at = 2500; at <= 5000; at += 500) gate.observe(at, 0);
        gate.observe(5500, 1); assertEquals("CHECKING", gate.state(5500));
    }
    @Test public void multipleAndWeakAreUncertainImmediately() {
        PresenceGate gate = present(); gate.observe(2500, 2); assertEquals("UNCERTAIN", gate.state(2500));
        gate.observe(3000, -1); assertEquals("UNCERTAIN", gate.state(3000));
        gate.observe(3500, 1); assertEquals("CHECKING", gate.state(3500));
    }
    @Test public void duplicateOrOlderFramesCannotChangeState() {
        PresenceGate gate = present(); gate.observe(1500, 0); gate.observe(2000, 0);
        assertEquals("PRESENT", gate.state(2000));
    }
    @Test public void explicitResetRemovesPresence() {
        PresenceGate gate = present(); gate.reset(); assertEquals("WAITING", gate.state(3000));
    }
}
