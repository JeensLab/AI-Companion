package dev.aicompanion;

import java.util.Locale;

/** Spoken problems describe what matters to the player; engine receipts stay in the task journal. */
public final class CompanionFailureMessages {
    public enum Kind { BAG, WOOD, MATERIALS, ROUTE, ORE, STONE, FOOD, PLACE, TIMEOUT, HANDOVER, OTHER }
    private CompanionFailureMessages() { }
    public static String primary(String report) {
        int stopped = report.lastIndexOf("План остановлен:");
        String text = stopped < 0 ? report : report.substring(stopped + "План остановлен:".length());
        return text.split("В сумке:|У меня в сумке:|Известные места:|Цепочка:|Координаты:|Не выполнено:", 2)[0].strip();
    }
    public static Kind kind(String report) {
        String text = primary(report).toLowerCase(Locale.ROOT).replace('ё','е');
        if (text.contains("нет места") || text.contains("сумка заполнена") || text.contains("сумка забита")) return Kind.BAG;
        if (text.contains("к тебе подойти")) return Kind.HANDOVER;
        if (text.contains("доступного дерева") || text.contains("доступное дерево") || text.contains("доступных деревьев") || text.contains("бревна не удалось")
                || text.contains("не хватает дерева") || text.contains("не хватает бревен")) return Kind.WOOD;
        if (text.contains("безопасного хода") || text.contains("безопасно") || text.contains("не нашел пути")
                || text.contains("не добраться") || text.contains("подъем") || text.contains("выбрался")) return Kind.ROUTE;
        if (text.contains("не хватает") || text.contains("недостаточно")) return Kind.MATERIALS;
        if (text.contains("руды") || text.contains("руду") || text.contains("рудного")) return Kind.ORE;
        if (text.contains("камня") || text.contains("камень")) return Kind.STONE;
        if (text.contains("мяса") || text.contains("животн") || text.contains("никого не добыл")) return Kind.FOOD;
        if (text.contains("поставить") || text.contains("место для") || text.contains("помех")) return Kind.PLACE;
        if (text.contains("тайм-аут") || text.contains("не успел")) return Kind.TIMEOUT;
        return Kind.OTHER;
    }
    public static String explain(String action, String report) {
        return switch (kind(report)) {
            case BAG -> "У меня сумка забита. Нужно освободить немного места.";
            case WOOD -> "Доступного дерева здесь больше не нашёл.";
            case MATERIALS -> "Пока не хватает материалов, чтобы закончить.";
            case ROUTE -> primary(report).contains("выбрался") ? "Я пока внизу, ищу безопасный выход."
                    : action.equals("mine_ore") ? "До оставшейся руды безопасно не добраться."
                    : "Здесь не нашёл безопасного пути.";
            case ORE -> "Больше доступной руды поблизости не нашёл.";
            case STONE -> "Доступного камня здесь больше не нашёл.";
            case FOOD -> "Больше подходящей добычи поблизости не нашёл.";
            case PLACE -> "Здесь тесно, нужно место посвободнее.";
            case TIMEOUT -> "С этим затянулось. Пока отложу.";
            case HANDOVER -> "Не дошёл до тебя, вещи остались у меня.";
            case OTHER -> "С этим пока не справился.";
        };
    }
}
