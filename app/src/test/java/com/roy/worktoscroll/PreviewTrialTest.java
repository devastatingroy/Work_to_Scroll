package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewTrialTest {
    @Test public void autoSuppressionWaitsForTheFullTemporaryPreview() {
        PreviewTrial trial = new PreviewTrial();
        trial.previewBound(1_000);
        assertFalse(trial.suppress(10_999));
        assertTrue(trial.suppress(11_000));
        assertTrue(trial.suppressed());
        assertFalse(trial.suppress(20_000));
        assertTrue(trial.suppressed());
    }

    @Test public void reopeningStartsASeparateTenSecondTemporaryPreview() {
        PreviewTrial trial = new PreviewTrial();
        trial.previewBound(0);
        assertTrue(trial.suppress(10_000));
        assertTrue(trial.reopen());
        assertFalse(trial.canSuppress(20_000)); // Binding, not click time, starts the next ten seconds.
        trial.previewBound(20_000);
        assertFalse(trial.canSuppress(29_999));
        assertTrue(trial.canSuppress(30_000));
        assertTrue(trial.suppress(30_000));
    }

    @Test public void failedReopenReturnsToTheAnalysisOnlyState() {
        PreviewTrial trial = new PreviewTrial();
        trial.previewBound(0);
        trial.suppress(10_000);
        assertTrue(trial.reopen());
        trial.cancelReopen();
        assertTrue(trial.suppressed());
        assertFalse(trial.canSuppress(30_000));
    }

    @Test public void manualRestartRestoresCalibrationPreview() {
        PreviewTrial trial = new PreviewTrial();
        trial.previewBound(0);
        trial.suppress(10_000);
        trial.resetForManualStart();
        assertFalse(trial.suppressed());
        assertFalse(trial.canSuppress(20_000));
    }

    @Test public void previewMustRemainVisibleForTenSecondsAfterBinding() {
        PreviewTrial trial = new PreviewTrial();
        trial.previewBound(5_000);
        assertEquals(10_000, trial.closeRemainingMs(5_000));
        assertEquals(1, trial.closeRemainingMs(14_999));
        assertFalse(trial.canSuppress(14_999));
        assertTrue(trial.canSuppress(15_000));
        assertEquals(0, trial.closeRemainingMs(15_000));
    }
}
