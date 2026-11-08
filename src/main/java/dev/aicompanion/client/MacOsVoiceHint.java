package dev.aicompanion.client;

/** Explain unavailable local speech once per game, including how to use the existing cloud voice. */
public final class MacOsVoiceHint {
    public static final String TEXT = "Не удалось озвучить Олега голосом macOS. Проверь русский голос в настройках «Универсальный доступ → Устный контент» или укажи ключ Inworld в настройках мода.";
    private boolean given;
    public synchronized String take() {
        if (given) return null;
        given = true;
        return TEXT;
    }
}
