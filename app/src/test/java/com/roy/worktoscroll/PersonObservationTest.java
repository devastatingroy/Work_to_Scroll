package com.roy.worktoscroll;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersonObservationTest {
    @Test public void personAndAbsenceAreDistinctFromUncertainty() {
        assertEquals(1, new PersonObservation(1, 1, .8f).clockSignal());
        assertEquals(0, new PersonObservation(0, 0, 0).clockSignal());
        assertEquals(-1, new PersonObservation(1, 0, .3f).clockSignal());
        assertEquals(-1, new PersonObservation(2, 2, .8f).clockSignal());
    }
    @Test public void weakObservationStopsWorkWithoutAbsenceGrace() {
        WorkClock clock = new WorkClock();
        clock.observe(0, 1); clock.observe(500, 1); clock.observe(1000, 1);
        assertEquals(500, clock.observe(1500, 1));
        assertEquals(0, clock.observe(2000, new PersonObservation(1, 0, .3f).clockSignal()));
        assertEquals(WorkClock.State.UNCERTAIN, clock.state());
        assertEquals(0, clock.observe(2500, 1));
        assertEquals(WorkClock.State.CHECKING, clock.state());
    }
    @Test public void soundsDoNotBypassWorkConfirmation() {
        WorkClock clock = new WorkClock(); DetectionCue cue = new DetectionCue();
        assertEquals(DetectionCue.Cue.RETURN, cue.observe(1000, 1050, 1, 1));
        assertEquals(0, clock.observe(1000, new PersonObservation(1, 1, .8f).clockSignal()));
        assertEquals(WorkClock.State.CHECKING, clock.state());
    }
}
