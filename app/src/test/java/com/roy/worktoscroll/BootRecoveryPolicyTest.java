package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class BootRecoveryPolicyTest {
    @Test public void newBootWithoutRecordIsReportedOnlyWhenProtectionWasEnabled() {
        assertTrue(BootRecoveryPolicy.missingRecordAfterNewBoot(true, 31, 32, 31));
        assertFalse(BootRecoveryPolicy.missingRecordAfterNewBoot(false, 31, 32, 31));
    }

    @Test public void currentBootRecordAndFirstSeenBootAreNotFalseFailures() {
        assertFalse(BootRecoveryPolicy.missingRecordAfterNewBoot(true, 31, 32, 32));
        assertFalse(BootRecoveryPolicy.missingRecordAfterNewBoot(true, -1, 32, -1));
        assertFalse(BootRecoveryPolicy.missingRecordAfterNewBoot(true, 31, -1, -1));
    }

    @Test public void onlyActionableOutcomesUseWarningPresentation() {
        assertTrue(BootRecoveryPolicy.needsAttention(BootRecoveryPolicy.Outcome.NO_BOOT_RECORD));
        assertTrue(BootRecoveryPolicy.needsAttention(BootRecoveryPolicy.Outcome.START_REJECTED));
        assertFalse(BootRecoveryPolicy.needsAttention(BootRecoveryPolicy.Outcome.STARTED));
        assertFalse(BootRecoveryPolicy.needsAttention(BootRecoveryPolicy.Outcome.MANUAL_STOPPED));
    }
}
