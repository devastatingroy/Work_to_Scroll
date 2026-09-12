package com.roy.worktoscroll;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersonObservationTest {
    @Test public void personAndAbsenceAreDistinctFromUncertainty() {
        assertEquals(1, new PersonObservation(1, 1, .8f).clockSignal());
        assertEquals(1, new PersonObservation(2, 1, .8f).clockSignal());
        assertEquals(1, new PersonObservation(3, 1, .8f).clockSignal());
        assertEquals(0, new PersonObservation(0, 0, 0).clockSignal());
        assertEquals(WorkClock.WEAK_ONLY, new PersonObservation(1, 0, .3f).clockSignal());
        assertEquals(WorkClock.WEAK_ONLY, new PersonObservation(2, 0, .3f).clockSignal());
        assertEquals(1, new PersonObservation(2, 2, .8f).clockSignal());
    }
    @Test public void diagnosticKindsExplainWhyTheClockWillNotCount() {
        assertEquals("single_reliable_person", PersonObservation.diagnosticKind(1, 1));
        assertEquals("no_person", PersonObservation.diagnosticKind(0, 0));
        assertEquals("weak_person_candidate", PersonObservation.diagnosticKind(1, 0));
        assertEquals("single_reliable_person_with_weak_candidates", PersonObservation.diagnosticKind(2, 1));
        assertEquals("multiple_weak_candidates", PersonObservation.diagnosticKind(2, 0));
        assertEquals("multiple_reliable_people", PersonObservation.diagnosticKind(2, 2));
        assertEquals("invalid_observation", PersonObservation.diagnosticKind(-1, -1));
    }
    @Test public void weakObservationOnlyUsesTheBoundedHoldAfterPresence() {
        WorkClock clock = new WorkClock();
        clock.observe(0, 1); clock.observe(500, 1); clock.observe(1000, 1);
        assertEquals(500, clock.observe(1500, 1));
        assertEquals(500, clock.observe(2000, new PersonObservation(1, 0, .3f).clockSignal()));
        assertEquals(WorkClock.State.WEAK_HOLD, clock.state());
        WorkClock unconfirmed = new WorkClock();
        assertEquals(0, unconfirmed.observe(0, new PersonObservation(1, 0, .3f).clockSignal()));
        assertEquals(WorkClock.State.UNCERTAIN, unconfirmed.state());
    }
    @Test public void soundsDoNotBypassWorkConfirmation() {
        WorkClock clock = new WorkClock(); DetectionCue cue = new DetectionCue();
        assertEquals(DetectionCue.Cue.RETURN, cue.observe(1000, 1050, 1, 1));
        assertEquals(0, clock.observe(1000, new PersonObservation(1, 1, .8f).clockSignal()));
        assertEquals(WorkClock.State.CHECKING, clock.state());
    }
}
