package dev.aicompanion;

/** Stable texture IDs; independent of network/model responses. */
public enum CompanionExpression {
    CALM("oleg"), SMIRK("oleg_smirk"), FOCUSED("oleg_focused"),
    COMBAT("oleg_combat"), WORRIED("oleg_worried"), PLEASED("oleg_pleased"),
    SURPRISED("oleg_worried"), DISPLEASED("oleg_focused");

    private final String texture;
    CompanionExpression(String texture) { this.texture = texture; }
    public String texture() { return texture; }
    public static CompanionExpression fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : CALM;
    }

    /** Explicit speech cues only. A promise or plain acknowledgement is not a success. */
    public static CompanionExpression speech(String text) {
        String s = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        if (s.matches("(?s).*(шучу|подкол|ха-ха|хаха|хех|ну ты даешь|ну ты и|гений|красавчик|я, конечно|кирка.*отпуск|лопата.*професс|😏|😉).*")) return SMIRK;
        if (s.matches("(?s).*(недоволен|раздражает|досадно|обидно|вот досада).*")) return DISPLEASED;
        if (s.matches("(?s).*(радуюсь|ура[!,. ]|отлично вышло|здорово получилось|красота[!.]|доволен).*")) return PLEASED;
        if (s.matches("(?s).*((?<![а-я])ого[!,. ]|ничего себе|вот это да|удивительно|не ожидал).*")) return SURPRISED;
        return CALM;
    }
}
