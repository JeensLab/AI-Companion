package dev.aicompanion.client;

/** The one line Oleg says when Windows has no Russian voice for him: once a game, never a manual. */
public final class WindowsVoiceHint {
    public static final String TEXT = "Олег молчит: в Windows нет русского голоса. Добавь русский язык с речью в параметрах Windows.";
    private boolean given;

    /** The hint the first time, null after that. */
    public synchronized String take() {
        if (given) return null;
        given = true;
        return TEXT;
    }
}
