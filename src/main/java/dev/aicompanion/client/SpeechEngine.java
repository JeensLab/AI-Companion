package dev.aicompanion.client;

/** Who voices Oleg's line. Pure, so a GameTest checks the rule without a client or a network. */
public enum SpeechEngine {
    INWORLD, WINDOWS, MACOS, NONE;

    /** Existing Windows callers keep their platform selection. */
    public static SpeechEngine choose(String inworldKey, boolean localVoiceOn, boolean windows) {
        return choose(inworldKey, localVoiceOn, windows, false);
    }

    /** A configured Inworld key has priority; the local voice is used only on its own platform when enabled. */
    public static SpeechEngine choose(String inworldKey, boolean localVoiceOn, boolean windows, boolean macos) {
        if (!inworldKey.isBlank()) return INWORLD;
        if (!localVoiceOn) return NONE;
        if (windows) return WINDOWS;
        return macos ? MACOS : NONE;
    }
}
