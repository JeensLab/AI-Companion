package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionBobber;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCatTests {
    @GameTest(template = "large_search", timeoutTicks = 600, attempts = 2)
    public static void luresRealCatAndItFollowsCompanionAwayFromPlayer(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        Cat cat = stray(h);
        Vec3 start = cat.position();
        Cat playersCat = h.spawn(EntityType.CAT, 7, 3, 10);
        playersCat.tame(player);
        playersCat.setOrderedToSit(true);
        companion.setItem(0, new ItemStack(Items.SALMON, 12));
        order(h, companion, "Олег, давай заведём котика");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(cat.isTame(), companion.getLastTaskResult()))
                .thenExecute(() -> {
                    h.assertTrue(companion.getUUID().equals(cat.getOwnerUUID()) && cat.distanceToSqr(start) > 4,
                            "The stray did not walk to Oleg and become his");
                    h.assertTrue(player.getUUID().equals(playersCat.getOwnerUUID()), "Took the player's cat");
                    h.assertTrue(count(companion, Items.SALMON) < 12 && companion.didLastTaskSucceed(), "No real fish was consumed");
                    companion.setFollowing(false);
                    move(h, companion, 18, 8);
                    move(h, player, 3, 8);
                }).thenWaitUntil(() -> h.assertTrue(cat.distanceTo(companion) < 4 && cat.distanceTo(player) > 9,
                         "The cat did not follow Oleg away from the player"))
                .thenExecute(() -> {
                    h.assertTrue(CompanionEvents.executeAction(player, "sit_cat", "", 1).success(), "Sit command refused");
                    companion.setFollowing(false);
                    move(h, companion, 9, 8);
                }).thenIdle(25)
                .thenExecute(() -> h.assertTrue(cat.isInSittingPose() && cat.distanceTo(companion) > 5, "Sitting cat followed"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 1000, attempts = 2)
    public static void keepsSameCatWhileUnloadedAndAfterOlegReturns(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        Cat cat = stray(h);
        var catId = cat.getUUID();
        var oldOleg = companion.getUUID();
        companion.setItem(0, new ItemStack(Items.COD, 12));
        order(h, companion, "найди кошку");
        CompoundTag savedCat = new CompoundTag();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(cat.isTame(), "Cat was not adopted: " + companion.getLastTaskResult()))
                .thenExecute(() -> {
                    cat.saveWithoutId(savedCat);
                    cat.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    h.assertTrue(CompanionEvents.executeAction(player, "tame_cat", "", 1).success() && !companion.hasActiveWorkOrder(),
                            "Oleg forgot the unloaded cat");
                    companion.kill();
                }).thenWaitUntil(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(back != null && !back.getUUID().equals(oldOleg), "Oleg has not returned");
                }).thenExecute(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(CompanionEvents.executeAction(player, "tame_cat", "", 1).success() && !back.hasActiveWorkOrder(),
                            "New Oleg offered to adopt a second cat");
                    Cat loaded = EntityType.CAT.create(h.getLevel());
                    loaded.load(savedCat);
                    h.assertTrue(h.getLevel().addFreshEntity(loaded), "Could not reload the same cat");
                }).thenWaitUntil(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(h.getLevel().getEntity(catId) instanceof Cat reloaded && back.getUUID().equals(reloaded.getOwnerUUID()),
                            "The same cat did not reconnect to Oleg");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 550, attempts = 2)
    public static void asksBeforeTakingFishFromOwnChest(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        player.getInventory().setItem(0, new ItemStack(Items.COD, 16));
        Cat cat = stray(h);
        var other = chest(h, 9);
        other.setItem(0, new ItemStack(Items.SALMON, 13));
        var allowed = chest(h, 10);
        allowed.setItem(0, new ItemStack(Items.COD, 12));
        companion.rememberPlaced(h.absolutePos(new BlockPos(10, 3, 6)));
        order(h, companion, "давай заведём кота");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask for fish"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(allowed.getItem(0).getCount() == 12 && !cat.isTame(), "Searched the chest before the player answered");
                    h.assertTrue(!companion.awaitsPetSearchPermission(), "The first question should ask about having fish");
                    h.assertTrue(CompanionWorkCommands.petAnswer("Сходи.", CompanionPet.CAT, "Олег", false).isEmpty(),
                            "Search consent was accepted as a reply about having fish");
                    String reply = CompanionWorkCommands.petAnswer(
                            "У меня есть, но я не могу тебе их дать, к сожалению.", CompanionPet.CAT, "Олег", false);
                    h.assertTrue(reply.equals("no"), "The player has fish but declined to share it");
                    answer(player, companion, reply);
                    h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0 && companion.autonomyLine().contains("Сходить?"),
                            "Did not ask permission to look for fish");
                }).thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(allowed.getItem(0).getCount() == 12 && !cat.isTame(), "Searched the chest without permission");
                    h.assertTrue(companion.awaitsPetSearchPermission(), "Search permission was not published");
                    h.assertTrue(CompanionWorkCommands.petAnswer("И чё? Окей, чё не идёшь?", CompanionPet.CAT, "Олег", true).isEmpty(),
                            "A status question was accepted as permission");
                    h.assertTrue(CompanionWorkCommands.petAnswer("М-м-м, ну сходи.", CompanionPet.CAT, "Олег", true).equals("search"),
                            "A hesitation prevented spoken permission");
                    String reply = CompanionWorkCommands.petAnswer("Ну давай.", CompanionPet.CAT, "Олег",
                            companion.awaitsPetSearchPermission());
                    h.assertTrue(reply.equals("search"), "Spoken permission was not interpreted as a pet reply");
                    answer(player, companion, reply);
                    h.assertTrue(companion.petQuestion(CompanionPet.CAT) == 0 && !companion.awaitsPetSearchPermission()
                            && companion.autonomyLine().contains("Ищу сырую рыбу"), "The search did not start after spoken permission");
                }).thenWaitUntil(() -> h.assertTrue(cat.isTame(), companion.getLastTaskResult()))
                .thenExecute(() -> h.assertTrue(other.getItem(0).getCount() == 13 && player.getInventory().getItem(0).getCount() == 16,
                        "Took fish from the player or an unrelated chest"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 350, batch = "cat_bait_on_ground")
    public static void picksUpNearbyRawSalmonBeforeTakingFishFromChest(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        stray(h);
        var chest = chest(h, 10);
        chest.setItem(0, new ItemStack(Items.COD, 12));
        companion.rememberPlaced(h.absolutePos(new BlockPos(10, 3, 6)));
        var at = h.absolutePos(new BlockPos(16, 3, 11));
        var salmon = new ItemEntity(h.getLevel(), at.getX() + .15, at.getY(), at.getZ() + .8, new ItemStack(Items.SALMON, 4));
        salmon.setDeltaMovement(Vec3.ZERO);
        h.assertTrue(h.getLevel().addFreshEntity(salmon), "Could not place salmon on the ground");

        order(h, companion, "приручи кота");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask for fish"))
                .thenExecute(() -> answer(player, companion, "no"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(salmon.isAlive() && chest.getItem(0).getCount() == 12, "Looked for bait without permission");
                    answer(player, companion, "search");
                }).thenWaitUntil(() -> {
                    h.assertTrue(chest.getItem(0).getCount() == 12, "Took fish from the chest while raw salmon lay nearby");
                    h.assertTrue(!salmon.isAlive() && companion.carriedCount("minecraft:salmon") > 0,
                            "Did not physically collect the salmon");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 250)
    public static void cancelsImmediatelyAfterSpokenSearchRefusal(GameTestHelper h) {
        var companion = fixture(h, true, true);
        var player = player(h, companion);
        player.getInventory().setItem(0, new ItemStack(Items.COD, 16));
        var cat = stray(h);
        var chest = chest(h, 10);
        chest.setItem(0, new ItemStack(Items.SALMON, 8));
        companion.rememberPlaced(h.absolutePos(new BlockPos(10, 3, 6)));
        order(h, companion, "Привет, Олег. Хочу кота завести.");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask whether the player has fish"))
                .thenExecute(() -> {
                    String reply = CompanionWorkCommands.petAnswer("Не, у меня нет.", CompanionPet.CAT, "Олег", false);
                    h.assertTrue(reply.equals("no"), "Missed the spoken reply about having fish");
                    answer(player, companion, reply);
                    h.assertTrue(companion.awaitsPetSearchPermission(), "Did not ask whether to look for fish");
                }).thenExecute(() -> {
                    String reply = CompanionWorkCommands.petAnswer("Не, не надо.", CompanionPet.CAT, "Олег", true);
                    h.assertTrue(reply.equals("cancel"), "Missed the spoken refusal to look for fish");
                    answer(player, companion, reply);
                    h.assertTrue(!companion.hasActiveWorkOrder() && companion.petQuestion(CompanionPet.CAT) == 0
                            && !companion.awaitsPetSearchPermission(), "The refusal left an active order or question");
                    h.assertTrue(companion.getLastTaskResult().contains("займёмся котом в другой раз"), "Missing cancellation reply");
                }).thenIdle(25)
                .thenExecute(() -> h.assertTrue(chest.getItem(0).getCount() == 8 && player.getInventory().getItem(0).getCount() == 16
                        && !hasBobber(h, companion) && !cat.isTame(), "A refused search still moved or used fish"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void waitsForActualRawFishAndRejectsStaleReply(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        Cat cat = stray(h);
        order(h, companion, "приручи кота");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask for fish"))
                .thenExecute(() -> {
                    answer(player, companion, "yes");
                    companion.setItem(1, new ItemStack(Items.COOKED_SALMON, 2));
                }).thenWaitUntil(() -> h.assertTrue(companion.autonomyLine().contains("сырая треска"), "Did not explain raw fish"))
                .thenExecute(() -> {
                    long oldQuestion = companion.petQuestion(CompanionPet.CAT);
                    companion.stopGatheringForPlayer();
                    companion.setItem(2, new ItemStack(Items.COD, 12));
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.CAT, oldQuestion, "yes");
                    h.assertTrue(!companion.hasActiveWorkOrder() && !cat.isTame(), "Late reply resumed the canceled job");
                    h.assertTrue(count(companion, Items.COOKED_SALMON) == 2 && count(companion, Items.COD) == 12, "Gift was consumed after cancellation");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 450)
    public static void emptyChestEndsCatOrderEvenWithRodAndWater(GameTestHelper h) {
        var companion = fixture(h, true, true);
        var player = player(h, companion);
        Cat cat = h.spawn(EntityType.CAT, 9, 3, 5);
        chest(h, 10);
        companion.rememberPlaced(h.absolutePos(new BlockPos(10, 3, 6)));
        order(h, companion, "найди кота");
        h.onEachTick(() -> h.assertTrue(!hasBobber(h, companion),
                "The cat order started fishing"));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask for fish"))
                .thenExecute(() -> {
                    answer(player, companion, "no");
                    h.assertTrue(companion.awaitsPetSearchPermission() && companion.autonomyLine().equals("Могу посмотреть рядом и в сундуках. Сходить?"),
                            "Did not offer to check nearby ground and chests");
                    h.assertTrue(CompanionWorkCommands.petAnswer("Да, поймай рыбу.", CompanionPet.CAT, "Олег", true).isEmpty(),
                            "A fishing request was accepted as chest-search consent");
                    String reply = CompanionWorkCommands.petAnswer("Да, поищи в сундуках.", CompanionPet.CAT, "Олег", true);
                    h.assertTrue(reply.equals("search"), "Did not understand consent to check the chests");
                    answer(player, companion, reply);
                })
                .thenWaitUntil(() -> h.assertTrue(!companion.hasActiveWorkOrder(), "Kept working after checking empty chests"))
                .thenExecute(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) == 0 && !cat.isTame()
                        && companion.getMainHandItem().is(Items.FISHING_ROD) && companion.getMainHandItem().getDamageValue() == 0
                        && companion.getLastTaskResult().contains("Сырой рыбы так и не нашёл"),
                        "The order repeated its question, tamed without fish or used the rod"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 520)
    public static void unansweredCatQuestionReleasesTheOrder(GameTestHelper h) {
        var companion = fixture(h, true, true);
        stray(h);
        order(h, companion, "приручи кота");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask for fish"))
                .thenIdle(200)
                .thenExecute(() -> h.assertTrue(companion.hasActiveWorkOrder() && !hasBobber(h, companion),
                        "Did not wait for the player to answer"))
                .thenWaitUntil(() -> h.assertTrue(!companion.hasActiveWorkOrder(), "The unanswered pet order still occupies the slot"))
                .thenExecute(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) == 0 && !hasBobber(h, companion)
                        && companion.getLastTaskResult().contains("Не дождался ответа"), "Silence started fishing or kept a stale question"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 120, batch = "cat_no_stray")
    public static void keepsRodDryWhenOnlyPlayersCatIsNearby(GameTestHelper h) {
        var companion = fixture(h, true, true);
        var player = player(h, companion);
        Cat cat = stray(h);
        cat.tame(player);
        cat.setOrderedToSit(true);
        var reply = CompanionEvents.executeAction(player, "tame_cat", "", 1);
        h.assertTrue(!reply.success() && reply.message().contains("у каждого уже есть дом"), "Tried to adopt an owned cat");
        h.runAfterDelay(30, () -> {
            h.assertTrue(!companion.hasActiveWorkOrder() && !hasBobber(h, companion) && companion.petQuestion(CompanionPet.CAT) == 0,
                    "A search with no stray began collecting fish");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void missingCatReportKeepsTheVillageSuggestion(GameTestHelper h) {
        String reason = "Что-то ни одного усатого не видно. Может, в деревне посмотрим?";
        var order = new PlannedAction("tame_cat", 0, 1);
        String said = CompanionDialogue.summarize(java.util.List.of(order), "План остановлен: " + reason);
        h.assertTrue(said.equals(reason), "The report lost the actual reason the cat order stopped: " + said);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spokenCatRequestsStayLocal(GameTestHelper h) {
        for (String said : java.util.List.of("Давай искать кота", "Олег, пошли искать кошку", "В смысле, давай заведём кота",
                "Здорово, Олег! Хочу кота завести.", "Я говорю: «Я хочу завести кота».")) {
            var command = CompanionLocalCommands.route(said, "Олег");
            h.assertTrue(command != null && action(said).equals("tame_cat"), "Cat request fell through to the model: " + said);
        }
        h.assertTrue(CompanionLocalCommands.route("Я не хочу завести кота", "Олег") == null,
                "A negative wish was turned into an adoption order");
        h.assertTrue(CompanionLocalCommands.route("Давай искать кота и добудь камень", "Олег") == null,
                "A compound request lost its other action");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void understandsRepliesAboutLookingForVillage(GameTestHelper h) {
        for (String positive : java.util.List.of("Да", "Ну давай", "Ага, пойдём", "М-м-м, погнали", "Олег, веди"))
            h.assertTrue(CompanionWorkCommands.petVillageAnswer(positive, "Олег").equals("go"), "Missed consent: " + positive);
        for (String negative : java.util.List.of("Нет", "Не, не надо", "Да не, останемся здесь", "Нет, спасибо", "Давай потом"))
            h.assertTrue(CompanionWorkCommands.petVillageAnswer(negative, "Олег").equals("cancel"), "Missed refusal: " + negative);
        for (String other : java.util.List.of("На юг?", "А почему на юг?", "Мона юг", "Да, если найдётся карта", "Да, есть лосось", "Стой"))
            h.assertTrue(CompanionWorkCommands.petVillageAnswer(other, "Олег").isEmpty(), "Treated a different message as an answer: " + other);
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"tame_cat\",\"item\":\"village\"}]"));
        h.assertTrue(plan.size() == 1 && plan.getFirst().item().equals("village"), "The village proposal did not survive plan validation");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void modelChosenHeadingForCatSearchBecomesAProposal(GameTestHelper h) {
        String request = "А может, сходим вместе поискать себе кошку?";
        h.assertTrue(CompanionLocalCommands.route(request, "Олег") == null, "This request did not exercise the model fallback");
        var model = CompanionPlanCodec.decode(JsonParser.parseString("{\"content\":\"Веду на юг.\",\"tool_calls\":[{\"function\":{\"name\":\"queue_companion_plan\","
                + "\"arguments\":{\"steps\":[{\"action\":\"walk_heading\",\"item\":\"south\"},{\"action\":\"tame_cat\"},{\"action\":\"walk_heading\",\"item\":\"east:village\"}]}}}]}")
                .getAsJsonObject());
        var reply = CompanionPlanRequirements.apply(request, model);
        h.assertTrue(reply.actions().equals(java.util.List.of(new PlannedAction("tame_cat", 0, 1, "village:south")))
                && !reply.text().contains("Веду"), "The model fallback could still start a walk directly: " + reply);
        h.assertTrue(CompanionPlanRequirements.apply("Веди на юг", new dev.aicompanion.client.CompanionReply("", java.util.List.of(model.actions().getFirst())))
                .actions().equals(java.util.List.of(model.actions().getFirst())), "An unrelated explicit route now requires confirmation");
        h.succeed();
    }

    @GameTest(template = "large_search", timeoutTicks = 520, batch = "cat_village_unanswered")
    public static void unansweredVillageProposalExpiresWhereHeStands(GameTestHelper h) {
        var companion = fixture(h, false, false);
        var player = player(h, companion);
        var start = companion.position();
        order(h, companion, "Пойдём искать кота");
        long[] question = {0};
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestionMode() == CompanionPet.Question.VILLAGE_PERMISSION, "Did not ask about the village trip"))
                .thenExecute(() -> question[0] = companion.petQuestion(CompanionPet.CAT))
                .thenWaitUntil(() -> h.assertTrue(!companion.hasActiveWorkOrder(), "Still waiting for an answer"))
                .thenExecute(() -> {
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.CAT, question[0], "go");
                    h.assertTrue(!companion.hasActiveWorkOrder() && companion.petQuestion(CompanionPet.CAT) == 0
                            && companion.getLastTaskResult().contains("Не дождался ответа") && companion.position().distanceToSqr(start) < .1,
                            "Expired consent moved Oleg or left a pending question");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 120, batch = "cat_search_missing")
    public static void missingCatEndsTheOrderWithoutStartingATrip(GameTestHelper h) {
        var companion = fixture(h, true, true);
        var reply = CompanionEvents.executeAction(player(h, companion), action("Найди кошку"), "", 1);
        h.assertTrue(!reply.success() && reply.message().contains("Рядом не вижу бездомной кошки"),
                "Did not report the local search result: " + reply.message());
        h.runAfterDelay(30, () -> {
            h.assertTrue(!companion.hasActiveWorkOrder() && !hasBobber(h, companion)
                    && companion.petQuestion(CompanionPet.CAT) == 0, "Missing cat started a trip or bait search");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void parsesPetRequestsAndRejectsInvalidModelPlans(GameTestHelper h) {
        h.assertTrue(action("Олег, найди кошку").equals("tame_cat") && action("давай заведём котика").equals("tame_cat")
                && action("хочу, чтобы у тебя был кот").equals("tame_cat"), "Missed conversational requests");
        h.assertTrue(action("У тебя есть кот?").equals("cat_status") && action("найди нашего кота").equals("find_cat")
                && action("не приручай кота").equals("cat_cancel"), "Pet commands were confused");
        var valid = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"tame_cat\"},{\"action\":\"sit_cat\",\"item\":\"home\"}]"));
        h.assertTrue(valid.size() == 2 && valid.getLast().item().equals("home"), "Model could not ask Oleg to adopt or sit");
        try {
            CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"tame_cat\",\"count\":2}]"));
            h.fail("An invalid cat plan was accepted");
        } catch (IllegalStateException expected) {
            h.succeed();
        }
    }

    private static CompanionEntity fixture(GameTestHelper h, boolean water, boolean rod) {
        return CompanionPetSearchTests.fixture(h, water, rod);
    }

    private static boolean hasBobber(GameTestHelper h, CompanionEntity companion) {
        return !h.getLevel().getEntitiesOfClass(CompanionBobber.class, companion.getBoundingBox().inflate(32)).isEmpty();
    }

    private static Cat stray(GameTestHelper h) { return h.spawn(EntityType.CAT, 12, 3, 5); }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static String action(String speech) { return CompanionLocalCommands.action(CompanionLocalCommands.route(speech, "Олег")).type(); }

    private static void order(GameTestHelper h, CompanionEntity companion, String speech) {
        var action = CompanionLocalCommands.action(CompanionLocalCommands.route(speech, "Олег"));
        var result = CompanionEvents.executeAction(player(h, companion), action.type(), action.item(), action.count());
        h.assertTrue(result.success(), result.message());
    }

    private static void answer(ServerPlayer player, CompanionEntity companion, String token) {
        CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.CAT, companion.petQuestion(CompanionPet.CAT), token);
    }

    private static int count(CompanionEntity companion, Item item) {
        int amount = companion.getMainHandItem().is(item) ? companion.getMainHandItem().getCount() : 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++)
            if (companion.getItem(slot).is(item)) amount += companion.getItem(slot).getCount();
        return amount;
    }

    private static Container chest(GameTestHelper h, int x) {
        BlockPos pos = new BlockPos(x, 3, 6);
        h.setBlock(pos, Blocks.CHEST);
        return (Container) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    private static void move(GameTestHelper h, net.minecraft.world.entity.Entity entity, int x, int z) {
        var pos = h.absolutePos(new BlockPos(x, 3, z));
        entity.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }
}
