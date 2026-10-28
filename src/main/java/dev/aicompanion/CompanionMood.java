package dev.aicompanion;

/** Words for Oleg's mood panel: his face now and the traits his talks with the player have moved. No client code, so GameTests check it. */
public final class CompanionMood {
    private CompanionMood() { }

    public static String of(CompanionExpression expression) {
        return switch (expression) {
            case CALM -> "Спокоен";
            case SMIRK -> "Ехидничает";
            case FOCUSED -> "Сосредоточен";
            case COMBAT -> "В бою";
            case WORRIED -> "Встревожен";
            case PLEASED -> "Доволен";
            case SURPRISED -> "Удивлён";
            case DISPLEASED -> "Недоволен";
        };
    }

    /** ARGB, soft enough not to pull the eye from the game. */
    public static int color(CompanionExpression expression) {
        return switch (expression) {
            case CALM -> 0xFFB8C3D1;
            case SMIRK, PLEASED -> 0xFF9FD89A;
            case FOCUSED -> 0xFFE3D08A;
            case COMBAT, DISPLEASED -> 0xFFE38A8A;
            case WORRIED, SURPRISED -> 0xFFE0B27A;
        };
    }

    public static String of(CompanionPersonality.Trait trait) {
        return switch (trait) {
            case TEASE -> "Дерзость";
            case SWEARING -> "Мат";
            case HUMOR -> "Юмор";
            case WARMTH -> "Теплота";
            case CURIOSITY -> "Расспросы";
            case ENERGY -> "Энергия";
            case SLANG -> "Сленг";
        };
    }

    /** The bond tier with a capital letter, or a dash before the first sync. */
    public static String bond(String tier) {
        return tier == null || tier.isBlank() ? "Связь: —" : Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
    }
}
