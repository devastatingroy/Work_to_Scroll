package com.roy.worktoscroll;

/** Pure decision rules for explaining, rather than guessing at, boot-recovery evidence. */
final class BootRecoveryPolicy {
    enum Outcome {
        NONE,
        RECEIVED,
        START_REQUESTED,
        STARTED,
        MANUAL_STOPPED,
        MISSING_PERMISSION,
        START_REJECTED,
        STORAGE_FAILED,
        NO_BOOT_RECORD
    }

    private BootRecoveryPolicy() { }

    static boolean missingRecordAfterNewBoot(boolean protectionEnabled, int lastSeenBoot,
                                             int currentBoot, int recordedBoot) {
        return protectionEnabled && currentBoot >= 0 && lastSeenBoot >= 0
            && lastSeenBoot != currentBoot && recordedBoot != currentBoot;
    }

    static boolean needsAttention(Outcome outcome) {
        return outcome == Outcome.MISSING_PERMISSION || outcome == Outcome.START_REJECTED
            || outcome == Outcome.STORAGE_FAILED || outcome == Outcome.NO_BOOT_RECORD;
    }
}
