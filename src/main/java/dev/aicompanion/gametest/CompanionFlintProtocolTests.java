package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The flint order on the model side: the plan codec bounds and the direct commands. No world is needed. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFlintProtocolTests {
    @GameTest(template = "empty")
    public static void flintOrdersAreValidatedAndParsed(GameTestHelper helper) {
        for (int count : new int[]{1, 8}) {
            var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{'action':'gather_loose','item':'minecraft:flint','count':" + count + "}]"));
            helper.assertTrue(plan.size() == 1 && "minecraft:flint".equals(plan.getFirst().item()) && plan.getFirst().count() == count, "Flint order x" + count + " was rejected or chunked");
        }
        for (String bad : new String[]{"[{'action':'gather_loose','item':'minecraft:flint','count':0}]", "[{'action':'gather_loose','item':'minecraft:flint','count':9}]"}) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid flint order accepted: " + bad);
        }
        expectFlint(helper, "Олег, добудь 3 кремня", 3);
        expectFlint(helper, "накопай кремень", 2);
        expectFlint(helper, "набери мне 5 кремня поблизости", 5);
        expectFlint(helper, "выкопай кремний", 2);
        helper.assertTrue(CompanionWorkCommands.parse("не копай кремень", "Олег") == null, "Negated flint command was parsed");
        helper.assertTrue(CompanionWorkCommands.parse("добудь кремень и вернись", "Олег") == null, "Compound flint command was parsed");
        helper.assertTrue(CompanionWorkCommands.parse("добудь 9 кремня", "Олег") == null, "Flint command beyond the step cap was parsed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flintWithoutCountDefaultsToTwo(GameTestHelper helper) {
        int flint = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{'action':'gather_loose','item':'minecraft:flint'}]")).getFirst().count();
        helper.assertTrue(flint == 2, "Flint without a count defaulted to " + flint);
        for (String item : new String[]{"minecraft:sand", "minecraft:red_sand", "minecraft:gravel"}) {
            int count = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{'action':'gather_loose','item':'" + item + "'}]")).getFirst().count();
            helper.assertTrue(count == 8, item + " without a count defaulted to " + count);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialFlintIsSaidHonestlyAndDoesNotFeedACraft(GameTestHelper helper) {
        PlannedAction gather = new PlannedAction("gather_loose", 0, 3, "minecraft:flint");
        PlannedAction steel = new PlannedAction("craft_item", 0, 1, "minecraft:flint_and_steel");
        PlannedAction table = new PlannedAction("craft_item", 0, 1, "minecraft:crafting_table");
        String some = CompanionDialogue.summarize(List.of(gather), "План остановлен: Выпало 1 из 3 кремня: время вышло. Попроси ещё раз.");
        helper.assertTrue(some.contains("1 из 3") && some.contains("Кремня") && some.contains("время вышло"), "Partial flint reply was not recognised: " + some);
        String none = CompanionDialogue.summarize(List.of(gather), "План остановлен: Выпало 0 из 3 кремня: разбил 90 блоков гравия, но кремень больше не выпал. Попроси ещё раз.");
        helper.assertTrue(none.startsWith("Кремень так и не выпал") && none.contains("разбил 90"), "Empty flint reply was not recognised: " + none);
        helper.assertTrue(!CompanionDialogue.continueAfterPartialGather(gather, List.of(steel), false, "Выпало 1 из 3 кремня: время вышло."), "Partial flint fed a flint craft");
        helper.assertTrue(CompanionDialogue.continueAfterPartialGather(gather, List.of(table), false, "Выпало 1 из 3 кремня: время вышло."), "Partial flint stopped a craft that needs no flint");
        helper.succeed();
    }

    private static void expectFlint(GameTestHelper helper, String phrase, int count) {
        PlannedAction action = CompanionWorkCommands.parse(phrase, "Олег");
        helper.assertTrue(action != null && "gather_loose".equals(action.type()) && "minecraft:flint".equals(action.item()) && action.count() == count, "Phrase not parsed as flint x" + count + ": " + phrase);
    }
}
