package dev.aicompanion.client;

/** Cloud provider selected for both the microphone and Oleg's voice. */
public enum SpeechProvider {
    INWORLD("Inworld"),
    ELEVENLABS("ElevenLabs");

    private final String displayName;

    SpeechProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
