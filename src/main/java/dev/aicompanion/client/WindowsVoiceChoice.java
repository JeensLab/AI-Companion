package dev.aicompanion.client;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Which installed Windows voice reads Oleg's Russian lines. Pure, so a GameTest checks it without Windows. */
public final class WindowsVoiceChoice {
    /** A voice token: its id, the Language attribute (hex LCIDs, ';'-separated), Gender, and whether it is a OneCore voice. */
    public record Candidate(String id, String language, String gender, boolean oneCore) {
        boolean russian() {
            for (String code : language.split(";")) {
                try { if (Integer.parseInt(code.trim(), 16) == 0x419) return true; }
                catch (NumberFormatException notAnLcid) { /* another language tag: not Russian */ }
            }
            return false;
        }
    }
    private WindowsVoiceChoice() { }

    /** A Russian male OneCore voice (Pavel) first, then any Russian one; never another language reading Russian text. */
    public static Optional<Candidate> pick(List<Candidate> voices) {
        return voices.stream().filter(Candidate::russian)
                .min(Comparator.comparing((Candidate voice) -> !(voice.oneCore() && "Male".equalsIgnoreCase(voice.gender()))));
    }
}
