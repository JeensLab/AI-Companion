package dev.aicompanion;

import java.util.Locale;

/** Spoken problems describe what matters to the player; engine receipts stay in the task journal. */
public final class CompanionFailureMessages {
    public enum Kind { BAG, WOOD, MATERIALS, LAVA, WATER, ROUTE, ORE, STONE, FOOD, PLACE, TIMEOUT, HANDOVER, DIMENSION, NOT_LOADED, NO_PATH, GATHER, OTHER }
    /** A quoted name in an engine line («Метка «дом»…»): the player's own word, which a short line may repeat. */
    private static final java.util.regex.Pattern QUOTED = java.util.regex.Pattern.compile("«([^»]{1,24})»");
    /**
     * «Собрал «Блок редстоуна»: 1 из 4, больше рядом нет.» — a gather that gave its part: what came, of what was asked, and why.
     */
    private static final java.util.regex.Pattern GATHERED = java.util.regex.Pattern.compile("(?iu)Собрал «([^»]{1,40})»: (\\d+) из (\\d+)(?:, ([^.]*))?\\.");
    /**
     * The walk note CompanionWork.finish appends after another order's own reason («Домой дойти не смог: нет пути.»,
     * «До места на 1, 64, 2 дойти не смог: нет пути.»): it tells of the walk home or to a station, not of the order.
     */
    private static final java.util.regex.Pattern TRIP_NOTE = java.util.regex.Pattern.compile(
            "(?iu)\\s+(?:Домой дойти не смог|До места на -?\\d+, -?\\d+, -?\\d+ дойти не смог): нет пути\\.$");
    private CompanionFailureMessages() { }
    public static String primary(String report) {
        int stopped = report.lastIndexOf("План остановлен:");
        String text = stopped < 0 ? report : report.substring(stopped + "План остановлен:".length());
        String own = text.split("В сумке:|У меня в сумке:|Известные места:|Цепочка:|Координаты:|Не выполнено:", 2)[0].strip();
        // The order's own reason, without the walk note put after it: «нет пути» in that note is the walk's, not the order's.
        String withoutNote = TRIP_NOTE.matcher(own).replaceFirst("").strip();
        return withoutNote.isEmpty() ? own : withoutNote;
    }
    /** Lava's own reasons in a report («рядом лава», «над лавой», «лавопад»), not the «лавы рядом» of the no-safe-way sentence. */
    public static boolean mentionsLava(String report) {
        String text = report.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return text.contains("рядом лава") || text.contains("над лавой") || text.contains("лавопад");
    }
    public static Kind kind(String report) {
        String text = primary(report).toLowerCase(Locale.ROOT).replace('ё','е');
        if (text.contains("нет места") || text.contains("сумка заполнена") || text.contains("сумка забита")) return Kind.BAG;
        // Not there to do it at all: no walk of his own is at fault, so no excuse for one.
        if (text.contains("не загружен рядом") || text.contains("выгружен из мира")) return Kind.NOT_LOADED;
        // A place he would have to walk to, in a world he cannot walk to: the world's own name in brackets is no word for
        // the player, and the furnace batch's line (no «пешком») keeps its own words.
        if (text.contains("в другом измерении") && text.contains("пешком")) return Kind.DIMENSION;
        if (text.contains("к тебе подойти") || text.contains("отдать не могу") || text.contains("несу тебе вещи") || text.contains("к тебе пройти, вещи")) return Kind.HANDOVER;
        if (text.contains("доступного дерева") || text.contains("доступное дерево") || text.contains("доступных деревьев") || text.contains("бревна не удалось")
                || text.contains("не хватает дерева") || text.contains("не хватает бревен")) return Kind.WOOD;
        // The stone dig's own line names the faces it checked («Проверил доступные выходы камня … безопасного подхода … не
        // нашёл»): the stone was short, not the way to it (the owner, 20.11: a shovel's stone said as «не нашёл безопасного пути»).
        if (text.contains("выходы камня")) return Kind.STONE;
        // Not yet up from his own tunnel: said before any ore reason, the player should know he is still down there.
        if (text.contains("выбрался")) return Kind.ROUTE;
        // A walk that proved there is no way at all («нет пути»): said as no way, short, not as a lost one.
        if (text.contains("нет пути")) return Kind.NO_PATH;
        // A spring and nothing to plug it with (before the lava line: both reports end in «заткнуть нечем»). Lava beats it
        // only by its own phrases: the no-safe-way sentence always says «лавы рядом».
        if (text.contains("родник") && !mentionsLava(text)) return Kind.WATER;
        // Ahead of the route: the engine puts the lava reason beside «не нашёл безопасного хода», and lava is the one to name.
        if (text.contains("заткнуть нечем") || text.contains("над лавой") || text.contains("лавопад")) return Kind.LAVA;
        // A space that would not take a thing («Не нашёл безопасное место для …», «… не хватает свободного места»): the
        // place is the reason, not a way to it. The bench in such a line is what was being set down, not a bench to walk to.
        // A «ровное свободное место» of a golem or a portal keeps its own words, as any reason with no kind does.
        if (text.contains("безопасное место") || text.contains("безопасного места") || text.contains("не хватает свободного места")) return Kind.PLACE;
        // «Помню верстак неподалёку, но пройти к нему сейчас не могу, а дерева на новый не хватает.»: the bench was not
        // walked to, not the materials; its «не хватает» is the wood for a new bench, said before the materials line below.
        if (benchWithoutWoodNormalized(text)) return Kind.ROUTE;
        if (text.contains("безопасного хода") || text.contains("безопасно") || text.contains("не нашел пути")
                || text.contains("не добраться") || text.contains("подъем") || text.contains("выбрался") || text.contains("к тебе пройти")) return Kind.ROUTE;
        if (text.contains("не хватает") || text.contains("недостаточно")) return Kind.MATERIALS;
        if (text.contains("руды") || text.contains("руду") || text.contains("рудного")) return Kind.ORE;
        if (text.contains("камня") || text.contains("камень")) return Kind.STONE;
        if (text.contains("мяса") || text.contains("животн") || text.contains("никого не добыл")) return Kind.FOOD;
        if (text.contains("поставить") || text.contains("место для") || text.contains("помех")) return Kind.PLACE;
        if (text.contains("тайм-аут") || text.contains("не успел")) return Kind.TIMEOUT;
        // A gather that gave its part («Собрал «…»: 1 из 4, …»): the count it names is the answer, not a flat refusal.
        if (GATHERED.matcher(primary(report)).find()) return Kind.GATHER;
        return Kind.OTHER;
    }
    /**
     * «Помню верстак неподалёку, но пройти к нему сейчас не могу, а дерева на новый не хватает.»: the bench was not walked
     * to, and its «не хватает» is the wood for a new bench, not the materials of the order. Said as the bench, whether by the
     * player's answer or by autonomy.
     */
    public static boolean benchWithoutWood(String report) {
        return benchWithoutWoodNormalized(primary(report).toLowerCase(Locale.ROOT).replace('ё', 'е'));
    }
    private static boolean benchWithoutWoodNormalized(String text) {
        return text.contains("верстак") && text.contains("дерева на новый");
    }
    public static String explain(String action, String report) {
        return switch (kind(report)) {
            case BAG -> "У меня сумка забита. Нужно освободить немного места.";
            case WOOD -> "Доступного дерева здесь больше не нашёл.";
            case MATERIALS -> "Пока не хватает материалов, чтобы закончить.";
            case LAVA -> primary(report).contains("заткнуть нечем")
                    ? "Рядом лава, а заткнуть её нечем. Нужен булыжник или земля." + (primary(report).contains("над лавой") ? " И руда лежит над лавой, добыча сгорит." : "")
                    : primary(report).contains("лавопад") ? "Рядом лавопад, пробкой его не остановить. Руду не трогал."
                    : "Руда лежит над лавой, добыча сгорит. Не трогал.";
            case WATER -> "Рядом родник, а заткнуть его нечем. Нужен булыжник или земля.";
            // No way at all to the player, as the wide search proved it: said as such, not as a lost way.
            case ROUTE -> primary(report).contains("выбрался") ? "Я пока внизу, ищу безопасный выход."
                    : primary(report).contains("к тебе пройти") ? "Не могу к тебе пройти."
                    // The bench was not walked to: said as the bench, not as a path the world has none of. With no wood
                    // for a new one, the line's own sentence says why it was not set down here.
                    : primary(report).contains("дерева на новый") ? "Помню верстак неподалёку, но пройти к нему сейчас не могу, а дерева на новый не хватает."
                    : primary(report).contains("верстак") ? "Не смог дойти до верстака, вещи сохранил."
                    : action.equals("mine_ore") ? "До оставшейся руды безопасно не добраться."
                    : "Здесь не нашёл безопасного пути.";
            case ORE -> "Больше доступной руды поблизости не нашёл.";
            case STONE -> "Доступного камня здесь больше не нашёл.";
            case FOOD -> "Больше подходящей добычи поблизости не нашёл.";
            case PLACE -> "Здесь тесно, нужно место посвободнее.";
            case TIMEOUT -> "С этим затянулось. Пока отложу.";
            case DIMENSION -> dimensionLine(report);
            case NOT_LOADED -> "Я ещё не загрузился рядом с тобой — подожди чуть-чуть.";
            case NO_PATH -> noPathLine(report);
            case HANDOVER -> primary(report).toLowerCase(Locale.ROOT).contains("не вижу") ? "Не вижу тебя рядом, вещи остались у меня."
                    : primary(report).contains("к тебе пройти") ? "Не могу к тебе пройти, вещи остались у меня." : "Не дошёл до тебя, вещи остались у меня.";
            // The ore broken but its drop left where he cannot get at it (CompanionOreMining.finishOre).
            case OTHER -> primary(report).contains("Выпавшее не достал") ? "Руду сломал, но выпавшее не достал." : "С этим пока не справился.";
            // A gather that gave its part («Собрал «…»: 1 из 4, …»): the count it names is the answer, not a flat refusal.
            case GATHER -> gatheredLine(report);
        };
    }

    /** «Собрал «Блок редстоуна» 1 из 4 — больше рядом нет.»: what came, of what was asked, and why, as the engine said it. */
    private static String gatheredLine(String report) {
        var found = GATHERED.matcher(primary(report));
        if (!found.find()) return "Собрал не всё, что просили.";
        String why = found.group(4) == null || found.group(4).isBlank() ? "" : " — " + found.group(4).strip();
        return "Собрал «" + found.group(1) + "» " + found.group(2) + " из " + found.group(3) + why + ".";
    }

    /** «Метка «дом» в другом измерении (world), пешком отсюда туда не дойти.»: the world's name in brackets is no word for the player. */
    private static String dimensionLine(String report) {
        String text = primary(report);
        int at = text.indexOf("в другом измерении");
        String who = at > 0 ? text.substring(0, at).strip() : "";
        if (who.startsWith("Метка «")) return who + " в другом измерении — туда пешком не дойти.";
        if (who.startsWith("Дом")) return "Дом в другом измерении — туда пешком не дойти.";
        return "Это в другом измерении — туда пешком не дойти.";
    }

    /** «До метки «дача» дойти не смог: нет пути.»: no way at all, said as no way and without coordinates. */
    private static String noPathLine(String report) {
        String text = primary(report);
        // Only a marker walk's own line names a marker: a quoted item elsewhere («Сырая говядина») is no name of a place.
        if (text.startsWith("До метки «")) {
            var mark = QUOTED.matcher(text);
            if (mark.find()) return "К метке «" + mark.group(1) + "» пути нет.";
        }
        return text.startsWith("Домой") ? "Домой пути нет." : "Туда нет пути — пешком не дойти.";
    }
}
