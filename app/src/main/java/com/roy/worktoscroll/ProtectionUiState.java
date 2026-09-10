package com.roy.worktoscroll;

/** Presentation only: user intent, required access and observed health stay distinct. */
enum ProtectionUiState {
    STOPPED, MISSING_PERMISSION, INTERRUPTED, FAILED, BACKGROUND_RESTRICTED, RUNNING;

    static ProtectionUiState from(boolean enabled, boolean usage, boolean overlay,
                                  boolean running, boolean healthy, boolean restricted) {
        if (!enabled) return STOPPED;
        if (!usage || !overlay) return MISSING_PERMISSION;
        if (!running) return INTERRUPTED;
        if (!healthy) return FAILED;
        if (restricted) return BACKGROUND_RESTRICTED;
        return RUNNING;
    }
}
