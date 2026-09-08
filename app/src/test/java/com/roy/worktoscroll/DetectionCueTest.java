package com.roy.worktoscroll;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.roy.worktoscroll.DetectionCue.Cue.*;

public class DetectionCueTest {
    @Test public void firstResultAudibleAndSameStateDoesNotRepeat() {
        DetectionCue cue = new DetectionCue();
        assertEquals(RETURN, cue.observe(1000, 1050, 1, 1));
        assertEquals(NONE, cue.observe(1500, 1550, 1, 1));
    }
    @Test public void firstEmptyResultIsAudibleOnce() {
        DetectionCue cue = new DetectionCue();
        assertEquals(AWAY, cue.observe(1000, 1050, 0, 0));
        assertEquals(NONE, cue.observe(1500, 1550, 0, 0));
    }
    @Test public void transitionsDoNotWaitForGateGrace() {
        DetectionCue cue = new DetectionCue(); cue.observe(1000, 1050, 1, 1);
        assertEquals(AWAY, cue.observe(1500, 1550, 0, 0));
        assertEquals(RETURN, cue.observe(2000, 2050, 1, 1));
    }
    @Test public void weakCandidatesDoNotAnnounceAbsenceOrRepeatReturn() {
        DetectionCue cue = new DetectionCue(); cue.observe(1000, 1050, 1, 1);
        assertEquals(NONE, cue.observe(1500, 1550, 1, 0));
        assertEquals(NONE, cue.observe(2000, 2050, 1, 1));
    }
    @Test public void multiplePeopleAreDetectedWithoutIdentityClaim() {
        DetectionCue cue = new DetectionCue();
        assertEquals(RETURN, cue.observe(1000, 1050, 2, 2));
    }
    @Test public void oldAndDuplicateResultsCannotSpeak() {
        DetectionCue cue = new DetectionCue(); cue.observe(1000, 1050, 1, 1);
        assertEquals(NONE, cue.observe(1000, 1100, 0, 0));
        assertEquals(NONE, cue.observe(900, 1100, 0, 0));
        assertEquals(NONE, cue.observe(1500, 3001, 0, 0));
    }
    @Test public void restartAnnouncesNewInitialState() {
        DetectionCue cue = new DetectionCue(); cue.observe(1000, 1050, 1, 1); cue.reset();
        assertEquals(RETURN, cue.observe(2000, 2050, 1, 1));
    }
}
