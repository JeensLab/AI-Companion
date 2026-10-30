package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The find_diamonds order on the model side: the plan codec ranges and the direct commands. No world is needed. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDiamondOrderTests {
    private static final String NAME = "Олег";

    @GameTest(template = "empty")
    public static void codecAcceptsFindDiamondsFromOneToSixteen(GameTestHelper helper) {
        for (int count : new int[]{1, 3, 16}) {
            PlannedAction goal = goal(helper, "find_diamonds", count);
            helper.assertTrue(goal.count() == count && "find_diamonds".equals(goal.item()), "find_diamonds x" + count + " came out as " + goal);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aGoalWithoutCountAsksForThreeDiamonds(GameTestHelper helper) {
        var parsed = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{'action':'pursue_goal','item':'find_diamonds'}]"));
        helper.assertTrue(parsed.size() == 1 && parsed.getFirst().count() == 3, "find_diamonds without a count came out as " + parsed);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void codecRejectsFindDiamondsOutOfRange(GameTestHelper helper) {
        for (int count : new int[]{0, 17}) helper.assertTrue(rejected(plan("pursue_goal", "find_diamonds", count)), "find_diamonds x" + count + " was accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void codecKeepsOtherGoalsAtTwo(GameTestHelper helper) {
        for (String id : new String[]{"gear_up", "prepare_nether", "defeat_ender_dragon"}) {
            for (int count : new int[]{1, 2}) helper.assertTrue(goal(helper, id, count).count() == count, id + " x" + count + " lost its count");
            helper.assertTrue(rejected(plan("pursue_goal", id, 3)), id + " x3 was accepted");
            helper.assertTrue(rejected(plan("pursue_goal", id, 16)), id + " x16 was accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modelCannotCallTheMineStepDirectly(GameTestHelper helper) {
        helper.assertTrue(rejected(plan("dig_for_diamonds", "", 3)), "dig_for_diamonds was accepted from a model plan");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void miningDiamondsBecomesTheGoal(GameTestHelper helper) {
        String[] phrases = {"добудь 3 алмаза", "Олег, найди алмазы", "добудь алмазов", "накопай пять алмазов", "добудь 16 алмазов", "сходи в шахту за алмазами", "копай шахту до алмазов"};
        int[] counts = {3, 3, 3, 5, 16, 3, 3};
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < phrases.length; i++) {
            PlannedAction action = CompanionWorkCommands.parse(phrases[i], NAME);
            if (action == null || !"pursue_goal".equals(action.type()) || !"find_diamonds".equals(action.item()) || action.count() != counts[i])
                wrong.append("\n  ").append(phrases[i]).append(" -> ").append(action).append(" (wanted find_diamonds x").append(counts[i]).append(")");
        }
        helper.assertTrue(wrong.isEmpty(), "Not parsed as the find_diamonds goal:" + wrong);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tooManyDiamondsGoToTheModel(GameTestHelper helper) {
        helper.assertTrue(CompanionWorkCommands.parse("добудь 20 алмазов", NAME) == null, "An order beyond the goal's range was parsed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void negatedDiamondOrdersStartNothing(GameTestHelper helper) {
        for (String phrase : new String[]{"не ищи алмазы", "не копай шахту", "не добывай алмазы"})
            helper.assertTrue(CompanionWorkCommands.parse(phrase, NAME) == null, "Negated order was parsed: " + phrase);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nearbyDiamondsStayAnOreOrder(GameTestHelper helper) {
        for (String phrase : new String[]{"добудь алмазов рядом", "накопай алмазы поблизости"}) {
            PlannedAction action = CompanionWorkCommands.parse(phrase, NAME);
            helper.assertTrue(action != null && "mine_ore".equals(action.type()) && "minecraft:diamond_ore".equals(action.item()), "Not a nearby ore order: " + phrase + " -> " + action);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherOresAreUnchanged(GameTestHelper helper) {
        PlannedAction iron = CompanionWorkCommands.parse("добудь железа", NAME);
        helper.assertTrue(iron != null && "mine_ore".equals(iron.type()) && "minecraft:iron_ore".equals(iron.item()), "Iron order changed: " + iron);
        PlannedAction emerald = CompanionWorkCommands.parse("накопай изумруды", NAME);
        helper.assertTrue(emerald != null && "mine_ore".equals(emerald.type()) && "minecraft:emerald_ore".equals(emerald.item()), "Emerald order changed: " + emerald);
        helper.succeed();
    }

    private static String plan(String action, String item, int count) {
        return "[{'action':'" + action + "'" + (item.isEmpty() ? "" : ",'item':'" + item + "'") + ",'count':" + count + "}]";
    }

    private static PlannedAction goal(GameTestHelper helper, String id, int count) {
        var parsed = CompanionPlanCodec.parsePlan(JsonParser.parseString(plan("pursue_goal", id, count)));
        helper.assertTrue(parsed.size() == 1, id + " x" + count + " was split into " + parsed.size() + " steps");
        return parsed.getFirst();
    }

    private static boolean rejected(String json) {
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString(json)); return false; }
        catch (IllegalStateException expected) { return true; }
    }
}
