package dev.aicompanion;

/** Shared identity rules used by settings, the server entity and dialogue. */
public final class CompanionIdentity {
    public static final String DEFAULT_NAME = "Олег";
    private CompanionIdentity() { }

    public static String cleanName(String value) {
        if (value == null) return DEFAULT_NAME;
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(c -> Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_')
                .limit(24).forEach(result::appendCodePoint);
        String cleaned = result.toString().trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? DEFAULT_NAME : cleaned;
    }

    public static boolean isNameQuestion(String text) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        return normalized.contains("как тебя зовут") || normalized.contains("твое имя")
                || normalized.contains("как тебя звать") || normalized.contains("как к тебе обращаться");
    }
}
