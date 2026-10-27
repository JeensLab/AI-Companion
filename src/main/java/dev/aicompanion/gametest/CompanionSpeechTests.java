package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionNumbers;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Game log 22.10 (build f21ae3e): «добудь пять руды», «добудь 20», «сколько сможешь», «бесконечно» — always one block and
 * «готово»; «+1 «Рудное железо»» after every order; a minute of «minecraft:furnace на 21, 70, -129 …» read aloud.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSpeechTests {
    private static final PlannedAction ONE_IRON = new PlannedAction("mine_ore", 0, 1, "minecraft:iron_ore");

    @GameTest(template = "empty")
    public static void spokenAmountsAreWhatHeDigs(GameTestHelper h) {
        h.assertTrue(CompanionNumbers.parse("добудь пять руды").count() == 5, "«пять»");
        h.assertTrue(CompanionNumbers.parse("Хорошо. Теперь дабудь 20 камня.").count() == 20, "«20»");
        h.assertTrue(CompanionNumbers.parse("накопай двадцать пять угля").count() == 25, "«двадцать пять»");
        h.assertTrue(CompanionNumbers.parse("добудь столько железной руды, сколько сможешь").count() == CompanionNumbers.AS_MANY, "«сколько сможешь»");
        h.assertTrue(CompanionNumbers.parse("Братан, добывай нам железа бесконечно").count() == CompanionNumbers.AS_MANY, "«бесконечно»");
        h.assertTrue(CompanionNumbers.parse("Добудь нам больше железа").count() == CompanionNumbers.MORE, "«больше»");
        h.assertTrue(CompanionNumbers.parse("добудь 5 железа и 3 угля").count() == CompanionNumbers.UNCLEAR, "two numbers");

        h.assertTrue(counts("А дабудька пять руды", ONE_IRON).equals(List.of(5)), "five: " + counts("А дабудька пять руды", ONE_IRON));
        h.assertTrue(counts("дабудь 20 железа", ONE_IRON).equals(List.of(16, 4)), "twenty: " + counts("дабудь 20 железа", ONE_IRON));
        h.assertTrue(counts("Хорошо, добудь этой киркой столько железной руды, сколько сможешь", ONE_IRON).equals(List.of(16)), "as many");
        h.assertTrue(counts("Добудь нам больше железа и пойди его переплавь в печке", ONE_IRON).equals(List.of(8)), "more");
        h.assertTrue(counts("добудь железо", ONE_IRON).equals(List.of(4)), "no number for ore is four");
        h.assertTrue(counts("добудь одну железную руду", ONE_IRON).equals(List.of(1)), "one is one");
        var craft = new CompanionReply("", List.of(new PlannedAction("mine_ore", 0, 6, "minecraft:iron_ore"), new PlannedAction("craft_item", 0, 2, "minecraft:iron_pickaxe")));
        h.assertTrue(CompanionPlanRequirements.apply("сделай 2 железных кирки", craft).actions().equals(craft.actions()), "Craft count went to the ore");

        var smelt = CompanionPlanRequirements.apply("Добудь нам больше железа и пойди его переплавь в печке", new CompanionReply("", List.of(ONE_IRON))).actions();
        h.assertTrue(smelt.size() == 2 && smelt.get(1).type().equals("smelt_items") && smelt.get(1).item().equals("minecraft:raw_iron")
                && smelt.get(1).count() == PlannedAction.ALL_CARRIED, "Smelting lost from «добудь и переплавь»: " + smelt);

        var noCount = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"mine_ore\",\"item\":\"minecraft:iron_ore\"}]"));
        h.assertTrue(noCount.getFirst().count() == 4, "Model step without count: " + noCount);

        h.assertTrue(CompanionWorkCommands.parse("Олег, дабудь-ка двадцать камня", "Олег").count() == 20, "local stone in words");
        h.assertTrue(CompanionWorkCommands.parse("добудь пять железа", "Олег").count() == 5, "local ore in words");
        h.assertTrue(CompanionWorkCommands.parse("добудь железа сколько сможешь", "Олег").count() == 16, "local ore as many");
        h.assertTrue(CompanionWorkCommands.parse("переплавь всё железо", "Олег").count() == PlannedAction.ALL_CARRIED, "local smelt all");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void resultsAreSaidLikeAPersonNotAMachine(GameTestHelper h) {
        var five = List.of(new PlannedAction("mine_ore", 0, 5, "minecraft:iron_ore"));
        String done = CompanionDialogue.summarize(five, "Добыл 5 блоков «железная руда»: в сумке прибавилось 5 «Рудное железо». В сумке: 3 Палка.");
        h.assertTrue(done.contains("накопал железа") && done.contains("5 блоков") && !done.contains("+") && !done.contains("Рудное"), done);
        String partial = CompanionDialogue.summarize(five, "План остановлен: Добыл 1 из 5 блоков «железная руда»: в сумке прибавилось 1 «Рудное железо». "
                + "Больше доступной руды «железная руда» рядом нет. В сумке: 3 Палка.");
        h.assertTrue(partial.startsWith("Накопал только 1 из 5") && !partial.contains("Готово") && !partial.contains("«"), partial);

        String house = "План остановлен: Нужно подойти к известному верстаку. Известные места: minecraft:furnace на 21, 70, -129 в minecraft:overworld (поставил) "
                + "(проверено); minecraft:crafting_table на 23, 70, -130 в minecraft:overworld (поставил) (проверено)";
        String said = CompanionDialogue.summarize(List.of(new PlannedAction("build_house", 0, 1)), house);
        h.assertTrue(!said.contains("minecraft:") && !said.matches("(?s).*-?\\d+, -?\\d+.*") && said.length() <= 200, "Technical text spoken: " + said);

        var model = new com.google.gson.JsonObject();
        model.addProperty("content", "Верстак рядом: minecraft:crafting_table.");
        String reply = CompanionPlanCodec.decode(model).text();
        h.assertTrue(!reply.contains("minecraft:") && reply.contains("Верстак"), "ID in a spoken reply: " + reply);
        h.succeed();
    }

    private static List<Integer> counts(String text, PlannedAction step) {
        return CompanionPlanRequirements.apply(text, new CompanionReply("", List.of(step))).actions().stream()
                .filter(action -> action.type().equals(step.type())).map(PlannedAction::count).toList();
    }
    @GameTest(template = "empty")
    public static void characterKeepsAcceptanceFailureCancellationAndSuccessDistinct(GameTestHelper h) {
        var house=List.of(new PlannedAction("build_house",0,1));
        String accepted=CompanionDialogue.acknowledge(house);
        h.assertTrue(!accepted.contains("готов")&&!accepted.contains("построил"),"Acceptance became success");
        String success=CompanionDialogue.summarize(house,"Дом построен.");
        h.assertTrue(success.contains("Дом готов")&&success.contains("заебись"),"Confirmed joy lost");
        String failure=CompanionDialogue.summarize(house,"План остановлен: В сумке нет места.");
        h.assertTrue(failure.contains("сумка забита")&&!failure.contains("готов")&&!failure.contains("Дом"),"Failure gained a false result");
        String cancelled=CompanionDialogue.summarize(house,"План остановлен: Заказ отменён.");
        h.assertTrue(cancelled.equals("Ладно, остановился."),"Cancellation became a complaint or success");
        h.succeed();
    }
}
