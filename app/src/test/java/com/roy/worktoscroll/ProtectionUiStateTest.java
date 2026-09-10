package com.roy.worktoscroll;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionUiStateTest {
    @Test public void manualStopWinsEvenIfServiceOrPermissionStateChanges() {
        assertEquals(ProtectionUiState.STOPPED, ProtectionUiState.from(false, true, true, true, true, false));
        assertEquals(ProtectionUiState.STOPPED, ProtectionUiState.from(false, false, false, false, false, true));
    }
    @Test public void eitherRequiredPermissionMissingMustNotAdvertiseProtection() {
        assertEquals(ProtectionUiState.MISSING_PERMISSION, ProtectionUiState.from(true, false, true, true, true, false));
        assertEquals(ProtectionUiState.MISSING_PERMISSION, ProtectionUiState.from(true, true, false, true, true, false));
    }
    @Test public void permissionsAloneDoNotMeanServiceIsRunning() {
        assertEquals(ProtectionUiState.INTERRUPTED, ProtectionUiState.from(true, true, true, false, true, false));
    }
    @Test public void freshHeartbeatDoesNotHideServiceFailure() {
        assertEquals(ProtectionUiState.FAILED, ProtectionUiState.from(true, true, true, true, false, false));
    }
    @Test public void backgroundRestrictionRemainsVisibleWithHealthyService() {
        assertEquals(ProtectionUiState.BACKGROUND_RESTRICTED, ProtectionUiState.from(true, true, true, true, true, true));
    }
    @Test public void runningRequiresIntentPermissionsHeartbeatAndHealth() {
        assertEquals(ProtectionUiState.RUNNING, ProtectionUiState.from(true, true, true, true, true, false));
    }
}
