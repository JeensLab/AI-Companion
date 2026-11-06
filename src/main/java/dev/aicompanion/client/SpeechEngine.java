package dev.aicompanion.client;

/** Who voices Oleg's line. Pure, so a GameTest checks the rule without a client or a network. */
public enum SpeechEngine {
    INWORLD, WINDOWS, NONE;

    /** Inworld whenever its key is set; without one, the Windows voice if it is turned on and this is Windows; else silence. */
    public static SpeechEngine choose(String inworldKey, boolean windowsVoiceOn, boolean windows) {
        if (!inworldKey.isBlank()) return INWORLD;
        return windowsVoiceOn && windows ? WINDOWS : NONE;
    }
}
