package dev.aicompanion.client;

/** Who voices Oleg's line. Pure, so a GameTest checks the rule without a client or a network. */
public enum SpeechEngine {
    INWORLD, ELEVENLABS, WINDOWS, MACOS, NONE;

    /** Use the selected cloud provider when configured, otherwise use the enabled system voice. */
    public static SpeechEngine choose(SpeechProvider provider, String key, boolean localVoiceOn, boolean windows, boolean macos) {
        if (!key.isBlank()) return switch (provider) {
            case INWORLD -> INWORLD;
            case ELEVENLABS -> ELEVENLABS;
        };
        if (!localVoiceOn) return NONE;
        if (windows) return WINDOWS;
        return macos ? MACOS : NONE;
    }
}
