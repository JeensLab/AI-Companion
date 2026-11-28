package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionParrotDialogueTests {
    @GameTest(template = "empty")
    public static void understandsParrotOrdersAndRepliesAboutSeeds(GameTestHelper h) {
        order(h, "Олег, давай заведём попугая", "tame_parrot", "resources");
        order(h, "Пойдём в джунгли искать попугая", "tame_parrot", "jungle");
        order(h, "У нас уже есть свой попугай?", "parrot_status", "resources");
        order(h, "Где наш попугайчик?", "pet_location", "parrot");
        order(h, "Найди нашего попугая", "find_parrot", "resources");
        order(h, "Оставь попугая дома", "sit_parrot", "home");
        order(h, "Позови попугая на плечо", "follow_parrot", "resources");
        order(h, "Не заводи попугая", "parrot_cancel", "resources");
        h.assertTrue("companion work walk_heading 0 north:jungle".equals(CompanionLocalCommands.route(
                "Веди на север, пока не найдём джунгли", "Олег")), "An explicit route to the jungle lost its destination");
        h.assertTrue(CompanionLocalCommands.route("Я не хочу завести попугая", "Олег") == null,
                "Treated a negative wish as an adoption");

        var parrot = CompanionPet.PARROT;
        h.assertTrue(CompanionWorkCommands.petAnswer("Нет, семян у меня нет", parrot, "Олег", false).equals("no"), "Missed absent seeds");
        h.assertTrue(CompanionWorkCommands.petAnswer("Да, у меня есть семечки", parrot, "Олег", false).equals("yes"), "Missed offered seeds");
        h.assertTrue(CompanionWorkCommands.petAnswer("Поищи семена в сундуках", parrot, "Олег", true).equals("search"), "Missed seed search consent");
        h.assertTrue(CompanionWorkCommands.petAnswer("Да, есть кости", parrot, "Олег", false).isEmpty(), "Accepted dog bait for a parrot");
        h.assertTrue(CompanionWorkCommands.petSighting("Вон попугай!", parrot, "Олег").equals("found"), "Missed parrot sighting");
        h.assertTrue(CompanionWorkCommands.petSighting("Я нашёл кошку", parrot, "Олег").isEmpty(), "Took a cat sighting for a parrot");
        h.succeed();
    }

    private static void order(GameTestHelper h, String text, String type, String item) {
        String command = CompanionLocalCommands.route(text, "Олег");
        h.assertTrue(("companion work " + type + " 1 " + item).equals(command), "Wrong local command for " + text + ": " + command);
    }

    @GameTest(template = "empty")
    public static void modelParrotSearchOffersOnlyAJungleRoute(GameTestHelper h) {
        var walk = new PlannedAction("walk_heading", 0, 0, "east:jungle");
        var tame = new PlannedAction("tame_parrot", 0, 1);
        String request = "Может, сходим вместе поискать попугая?";
        for (var steps : List.of(List.of(walk), List.of(walk, tame), List.of(tame))) {
            var reply = CompanionPlanRequirements.apply(request, new CompanionReply("Веду тебя!", steps));
            String target = steps.contains(walk) ? "jungle:east" : "jungle";
            h.assertTrue(reply.actions().equals(List.of(new PlannedAction("tame_parrot", 0, 1, target))),
                    "The model bypassed the jungle travel question: " + reply);
        }
        var unrelated = new CompanionReply("Иду на восток", List.of(walk));
        h.assertTrue(CompanionPlanRequirements.apply("Веди на восток с нашим попугаем", unrelated).actions().equals(List.of(walk)),
                "An unrelated walk with the player's parrot became a taming order");
        for (String type : List.of("tame_parrot", "parrot_status", "sit_parrot", "find_parrot", "follow_parrot")) {
            var valid = CompanionPlanCodec.parsePlan(modelPlan(type, "", 1));
            h.assertTrue(valid.size() == 1 && valid.getFirst().type().equals(type), "Codec lost " + type);
        }
        h.assertTrue(CompanionPlanCodec.parsePlan(modelPlan("tame_parrot", "jungle:east", 1)).getFirst().item().equals("jungle:east"),
                "Codec lost the jungle heading");
        h.assertTrue(CompanionPlanCodec.parsePlan(modelPlan("pet_location", "parrot", 1)).getFirst().item().equals("parrot"),
                "Codec lost the parrot location query");
        rejected(h, "tame_parrot", "village", 1);
        rejected(h, "tame_cat", "jungle", 1);
        rejected(h, "tame_parrot", "jungle:nowhere", 1);
        rejected(h, "follow_parrot", "jungle", 1);
        rejected(h, "tame_parrot", "", 2);
        h.succeed();
    }

    private static JsonArray modelPlan(String type, String item, int count) {
        var step = new JsonObject();
        step.addProperty("action", type);
        step.addProperty("item", item);
        step.addProperty("count", count);
        var steps = new JsonArray();
        steps.add(step);
        return steps;
    }

    private static void rejected(GameTestHelper h, String type, String item, int count) {
        try {
            CompanionPlanCodec.parsePlan(modelPlan(type, item, count));
        } catch (IllegalStateException expected) {
            return;
        }
        h.fail("Accepted an invalid pet plan: " + type + " " + item + " " + count);
    }
}
