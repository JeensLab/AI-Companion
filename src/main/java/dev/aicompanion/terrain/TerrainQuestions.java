package dev.aicompanion.terrain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Whole local questions only: longer, compound orders are parsed as plans. */
public final class TerrainQuestions {
    private TerrainQuestions() { }

    private static final Pattern GUIDE = Pattern.compile("(?:покажи|проводи|отведи)(?: мне| меня| нас)? (?:где (?:ты )?(?:видел |заметил )?|к |ко |в )?(.+)");
    private static final Pattern RECALL = Pattern.compile("(?:помнишь |помнишь ли ты )?(?:где (?:ты |мы )?(?:видел[иа]? |заметил[иа]? |встречал[иа]? )?|(?:ты )?(?:видел|заметил|встречал)(?: ли ты)? )(.+)");
    private static final Pattern ON_WAY = Pattern.compile("(?:мы |ты )?(.+?) (?:по дороге |по пути )?(?:не )?(?:видели|видел|встречали|встречал)");

    public static String command(String normalized, String companionName) {
        String words = normalized.replaceAll("[\\p{P}\\s]+", " ").strip();
        if (companionName != null) {
            String name = companionName.toLowerCase(Locale.ROOT).replace('ё', 'е') + " ";
            if (words.startsWith(name)) words = words.substring(name.length());
        }
        words = words.replaceFirst("^(?:ну |давай |пожалуйста )+", "").replaceFirst(" пожалуйста$", "");
        String topic = switch (words) {
            case "осмотрись", "осмотрись вокруг", "оглядись", "посмотри вокруг", "что вокруг", "что ты видишь",
                    "где мы и что рядом", "что здесь за местность", "где гребень", "видишь овраг", "где овраг", "видишь врага" -> "around";
            case "где спуск", "где здесь спуск", "где спуститься", "где здесь спуститься", "где тут спуститься",
                    "где можно спуститься", "где здесь можно спуститься", "как спуститься вниз" -> "descent";
            case "где брод", "где здесь брод", "где тут брод", "где берег", "где перейти реку", "где перейти вброд" -> "ford";
            case "где вход в пещеру", "где здесь вход в пещеру", "видишь пещеру", "есть ли тут пещера" -> "cave";
            case "какой тут биом", "какой здесь биом", "какой это биом", "в каком мы биоме", "что за биом", "определи биом", "где граница биомов", "где сменяется биом" -> "biome";
            default -> null;
        };
        if (topic != null) return "companion work survey_terrain 1 " + topic;
        if (words.equals("покажи") || words.equals("покажи мне") || words.equals("веди туда")
                || words.equals("покажи место") || words.equals("покажи где") || words.equals("покажи это место"))
            return "companion work guide_landmark 1 last";
        if (words.equals("что ты запомнил по пути") || words.equals("что ты видел по дороге"))
            return "companion work recall_landmark 1 last";
        var guide = GUIDE.matcher(words);
        var recall = RECALL.matcher(words);
        var onWay = ON_WAY.matcher(words);
        if (guide.matches()) return landmark("guide_landmark", guide.group(1));
        if (recall.matches()) return landmark("recall_landmark", recall.group(1));
        if (onWay.matches()) return landmark("recall_landmark", onWay.group(1));
        return null;
    }

    private static String landmark(String action, String target) {
        String noun = target.replaceFirst(" (?:по дороге|по пути)$", "")
                .replaceFirst("^(?:ту |тот |эту |этот |какую нибудь )", "");
        String kind = switch (noun) {
            case "пещера", "пещеру", "пещере", "вход в пещеру", "входу в пещеру" -> "cave";
            case "руда", "руду", "руде", "жила", "жилу" -> "ore";
            case "уголь", "углю", "угольная руда", "угольную руду" -> "coal";
            case "железо", "железу", "железная руда", "железную руду" -> "iron";
            case "медь", "меди", "медная руда", "медную руду" -> "copper";
            case "алмаз", "алмазы", "алмазам", "алмазная руда", "алмазную руду" -> "diamond";
            case "золото", "золоту", "золотая руда", "золотую руду" -> "gold";
            case "лазурит", "лазуриту", "лазуритовую руду" -> "lapis";
            case "редстоун", "редстоуну", "красная руда", "красную руду" -> "redstone";
            case "изумруд", "изумруды", "изумрудам", "изумрудную руду" -> "emerald";
            case "кварц", "кварцу", "кварцевую руду" -> "quartz";
            case "древние обломки", "древним обломкам" -> "debris";
            default -> null;
        };
        return kind == null ? null : "companion work " + action + " 1 " + kind;
    }
}
