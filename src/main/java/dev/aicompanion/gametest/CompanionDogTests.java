package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionPet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDogTests {
    @GameTest(template = "empty", timeoutTicks = 10)
    public static void recognizesSpokenBaitRefusals(GameTestHelper h) {
        for (String reply : java.util.List.of("Я говорю, нет костей.", "Не, у меня нет, Кость.",
                "Блядь, я тебе говорю [смеётся], нет костей, сука.", "Нет, у меня нет костей.",
                "Нет у меня костей.", "Костей у меня нет.", "У меня ни одной кости нет.",
                "Кости у меня закончились.", "У меня не осталось косточек.",
                "Ну, я же тебе сказал, что у меня вообще нет костей.", "Да нет, костей нету.",
                "Я сказал: у меня нету костей, ептель!", "Олег, нет костей.", "Неа.",
                "Кости у меня есть, но я тебе их не отдам.", "У меня есть кости, но отдать их не могу.", "Не хочу отдавать кости.")) {
            h.assertTrue(CompanionWorkCommands.petAnswer(reply, CompanionPet.DOG, "Олег", false).equals("no"), "Missed refusal: " + reply);
        }
        h.assertTrue(CompanionWorkCommands.petAnswer("Нет, трески у меня нет.", CompanionPet.CAT, "Олег", false).equals("no"), "Missed cat bait refusal");
        h.assertTrue(CompanionWorkCommands.petAnswer("Да, есть кость.", CompanionPet.DOG, "Олег", false).equals("yes"), "Missed confirmation");
        h.assertTrue(CompanionWorkCommands.petAnswer("Вот тебе кость.", CompanionPet.DOG, "Олег", false).equals("gift"), "Missed offering");
        h.assertTrue(CompanionWorkCommands.petAnswer("Не надо.", CompanionPet.DOG, "Олег", false).equals("cancel"), "Missed cancellation");
        for (String other : java.util.List.of("Нет костей?", "Нет костей, добудь камень.", "Рыбы нет.",
                "У меня нет удочки.", "У меня нет костей, но есть рыба.",
                "Кости есть, но не могу дать рыбу.", "Не дам кости, добудь камень.")) {
            h.assertTrue(CompanionWorkCommands.petAnswer(other, CompanionPet.DOG, "Олег", false).isEmpty(), "Handled unrelated reply: " + other);
        }
        h.assertTrue(CompanionWorkCommands.petAnswer("Кости есть, но я их не отдам.", CompanionPet.DOG, "Олег", true).isEmpty(),
                "Refusing a gift answered the permission to search");
        h.succeed();
    }

    @GameTest(template = "large_search", timeoutTicks = 900, attempts = 2)
    public static void dogFollowsOlegDefendsHimAndCanStayHome(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        var wolf = h.spawn(EntityType.WOLF, 12, 3, 8);
        var playersWolf = h.spawn(EntityType.WOLF, 9, 3, 10);
        playersWolf.tame(player);
        playersWolf.setOrderedToSit(true);
        companion.setItem(0, new ItemStack(Items.BONE, 12));
        order(h, player, "Олег, давай заведём собаку");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(wolf.isTame(), companion.getLastTaskResult()))
                .thenExecute(() -> {
                    h.assertTrue(companion.getUUID().equals(wolf.getOwnerUUID()) && companion.hasPet(CompanionPet.DOG),
                            "The wild wolf did not bond with Oleg");
                    h.assertTrue(player.getUUID().equals(playersWolf.getOwnerUUID()), "Took the player's dog");
                    h.assertTrue(bonesCarried(companion) < 12, "No real bone was used");
                    companion.setFollowing(false);
                    move(h, companion, 18, 8);
                    move(h, player, 3, 8);
                }).thenWaitUntil(() -> h.assertTrue(wolf.distanceTo(companion) < 4 && wolf.distanceTo(player) > 9,
                        "The dog did not follow Oleg away from the player"))
                .thenExecute(() -> {
                    var attacker = h.spawn(EntityType.ZOMBIE, 19, 3, 8);
                    attacker.setNoAi(true);
                    companion.hurt(companion.damageSources().mobAttack(attacker), 2);
                }).thenWaitUntil(() -> h.assertTrue(wolf.getTarget() != null && wolf.getTarget().getType() == EntityType.ZOMBIE,
                        "Dog did not come to Oleg's defense"))
                .thenExecute(() -> {
                    wolf.getTarget().discard();
                    order(h, player, "это наш дом");
                    order(h, player, "оставь собаку дома");
                }).thenWaitUntil(() -> h.assertTrue(companion.didLastTaskSucceed() && !companion.hasActiveWorkOrder()
                        && wolf.isInSittingPose() && wolf.distanceTo(player) < 7, companion.getLastTaskResult()))
                .thenExecute(() -> {
                    order(h, player, "найди нашу собаку");
                }).thenWaitUntil(() -> h.assertTrue(!companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(),
                        "Could not find the dog: " + companion.getLastTaskResult()))
                .thenExecute(() -> {
                    order(h, player, "возьми собаку с собой");
                    h.assertTrue(!wolf.isOrderedToSit(), "Dog was not called back");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 1100, attempts = 2)
    public static void catAndDogBondsAreIndependentAndDogReconnects(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        var wolf = h.spawn(EntityType.WOLF, 12, 3, 8);
        var cat = h.spawn(EntityType.CAT, 6, 3, 8);
        cat.setNoAi(true);
        companion.setPetSearchRadius(CompanionPet.MIN_SEARCH_RADIUS);
        companion.setItem(0, new ItemStack(Items.BONE, 12));
        companion.setItem(1, new ItemStack(Items.COD, 12));
        order(h, player, "приручи волка");
        var savedDog = new CompoundTag();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(wolf.isTame() && !companion.hasActiveWorkOrder(), companion.getLastTaskResult()))
                .thenExecute(() -> {
                    // This scenario tests independent bonds, so the cat starts within reach when its order begins.
                    cat.teleportTo(companion.getX() + 0.5, companion.getY(), companion.getZ());
                    cat.setNoAi(false);
                    order(h, player, "приручи кота");
                }).thenWaitUntil(() -> h.assertTrue(cat.isTame() && companion.hasPet(CompanionPet.CAT)
                        && companion.hasPet(CompanionPet.DOG), "Both pets must coexist: cat tamed=" + cat.isTame()
                        + ", cat bond=" + companion.hasPet(CompanionPet.CAT) + ", dog bond=" + companion.hasPet(CompanionPet.DOG)
                        + ", last result=" + companion.getLastTaskResult()))
                .thenExecute(() -> {
                    wolf.saveWithoutId(savedDog);
                    wolf.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    order(h, player, "заведи собаку");
                    h.assertTrue(!companion.hasActiveWorkOrder(), "Forgot about the unloaded dog");
                    companion.kill();
                }).thenWaitUntil(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(back != null && back != companion, "Oleg has not returned");
                }).thenExecute(() -> {
                    var dog = EntityType.WOLF.create(h.getLevel());
                    dog.load(savedDog);
                    h.assertTrue(h.getLevel().addFreshEntity(dog), "Failed to reload the same dog");
                }).thenWaitUntil(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(h.getLevel().getEntity(wolf.getUUID()) instanceof Wolf loaded
                            && back.getUUID().equals(loaded.getOwnerUUID()) && back.hasPet(CompanionPet.DOG)
                            && back.hasPet(CompanionPet.CAT), "Dog did not reconnect or lost the cat bond");
                }).thenExecute(() -> h.getLevel().getEntity(wolf.getUUID()).kill())
                .thenWaitUntil(() -> {
                    var back = CompanionEvents.getOwnedCompanion(player);
                    h.assertTrue(!back.hasPet(CompanionPet.DOG) && back.hasPet(CompanionPet.CAT), "Dog death changed both bonds");
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 600, attempts = 2)
    public static void dogOrderCanPickUpRealBones(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        var wolf = h.spawn(EntityType.WOLF, 12, 3, 8);
        var at = h.absolutePos(new BlockPos(16, 3, 11));
        var bones = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY(), at.getZ() + .5, new ItemStack(Items.BONE, 12));
        h.assertTrue(h.getLevel().addFreshEntity(bones), "Could not drop bones in the test");
        order(h, player, "приручи волка");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0, "Did not ask before looking for loose bones"))
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(bones.isAlive() && bonesCarried(companion) == 0, "Collected bones before the player answered");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG,
                            companion.petQuestion(CompanionPet.DOG), "no");
                }).thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(bones.isAlive() && bonesCarried(companion) == 0, "Collected bones without permission");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG,
                            companion.petQuestion(CompanionPet.DOG), "search");
                }).thenWaitUntil(() -> h.assertTrue(wolf.isTame(), companion.getLastTaskResult()))
                .thenExecute(() -> h.assertTrue(!bones.isAlive() && companion.petQuestion(CompanionPet.DOG) == 0,
                        "The ground stack was not collected or the question is still pending"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 350, batch = "dog_bait_on_the_way")
    public static void noticesNewBonesWhileWalkingToChest(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        h.spawn(EntityType.WOLF, 12, 3, 8);
        var chestPos = new BlockPos(17, 3, 6);
        var chest = chest(h, chestPos);
        chest.setItem(0, new ItemStack(Items.BONE, 12));
        companion.rememberPlaced(h.absolutePos(chestPos));

        var at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(13, 3, 13)));
        var bones = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.BONE, 4));
        bones.setDeltaMovement(0, 0, 0);
        var start = companion.position();
        order(h, player, "приручи пса");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0, "Did not ask for bones"))
                .thenExecute(() -> {
                    long question = companion.petQuestion(CompanionPet.DOG);
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, "no");
                    h.assertTrue(companion.awaitsPetSearchPermission(), "Did not ask for permission to find bones");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, "search");
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) == 0, "Did not accept the search permission");
                }).thenWaitUntil(() -> h.assertTrue(companion.distanceToSqr(start) > 1,
                        "Did not start walking toward the chest: " + companion.getLastTaskResult()))
                .thenExecute(() -> h.assertTrue(h.getLevel().addFreshEntity(bones), "Could not drop bones during the walk"))
                .thenWaitUntil(() -> {
                    h.assertTrue(chest.getItem(0).getCount() == 12, "Kept walking to the chest after bones appeared nearby");
                    h.assertTrue(!bones.isAlive() && bonesCarried(companion) > 0,
                            "Did not collect the dropped bones: " + companion.getActivityForAi() + " / " + companion.getLastTaskResult()
                                    + "; relative positions " + h.relativePos(companion.blockPosition()) + " / " + h.relativePos(bones.blockPosition()));
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 700, attempts = 2)
    public static void dogOrderAsksBeforeUsingOwnChestAndOnlyAcceptsRealBones(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        var wolf = h.spawn(EntityType.WOLF, 12, 3, 8);
        var unrelated = chest(h, new BlockPos(9, 3, 6));
        unrelated.setItem(0, new ItemStack(Items.BONE, 12));
        var pos = new BlockPos(10, 3, 6);
        var ownChest = chest(h, pos);
        ownChest.setItem(0, new ItemStack(Items.BONE, 12));
        companion.rememberPlaced(h.absolutePos(pos));
        order(h, player, "приручи собаку");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0, "Did not ask for bones"))
                .thenExecute(() -> {
                    long question = companion.petQuestion(CompanionPet.DOG);
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.CAT, question, "no");
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) == question, "A reply for the cat changed the dog order");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, "yes");
                    companion.setItem(0, new ItemStack(Items.BONE_MEAL, 4));
                }).thenWaitUntil(() -> h.assertTrue(companion.autonomyLine().contains("целые кости") && !wolf.isTame(),
                        "Feed must be a whole bone"))
                .thenExecute(() -> {
                    h.assertTrue(ownChest.getItem(0).getCount() == 12 && bonesCarried(companion) == 0,
                            "Searched the chest while waiting for the player");
                    String reply = CompanionWorkCommands.petAnswer("Не, у меня нет, Кость.", CompanionPet.DOG, "Олег", false);
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG,
                            companion.petQuestion(CompanionPet.DOG), reply);
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0 && companion.autonomyLine().equals("Могу посмотреть рядом и в сундуках. Сходить?"),
                            "Did not ask permission to look for bones");
                }).thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(ownChest.getItem(0).getCount() == 12 && bonesCarried(companion) == 0,
                            "Searched the chest without permission");
                    String reply = CompanionWorkCommands.petAnswer("Да, поищи кости в сундуках.", CompanionPet.DOG, "Олег",
                            companion.awaitsPetSearchPermission());
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG,
                            companion.petQuestion(CompanionPet.DOG), reply);
                }).thenWaitUntil(() -> h.assertTrue(wolf.isTame() && !companion.hasActiveWorkOrder(), companion.getLastTaskResult()))
                .thenExecute(() -> h.assertTrue(unrelated.getItem(0).getCount() == 12 && companion.getItem(0).getCount() == 4,
                        "Took bones from an unrelated chest or spent bone meal"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 150)
    public static void dogOrderStopsIfPlayerDeclinesTheSearch(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        var wolf = h.spawn(EntityType.WOLF, 12, 3, 8);
        var pos = new BlockPos(10, 3, 6);
        var ownChest = chest(h, pos);
        ownChest.setItem(0, new ItemStack(Items.BONE, 12));
        companion.rememberPlaced(h.absolutePos(pos));
        order(h, player, "приручи собаку");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0, "Did not ask for bones"))
                .thenExecute(() -> {
                    long question = companion.petQuestion(CompanionPet.DOG);
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, "no");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, "yes");
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) == question && companion.awaitsPetSearchPermission(),
                            "A reply about having bones answered the permission question");
                    String reply = CompanionWorkCommands.petAnswer("Нет, не ходи.", CompanionPet.DOG, "Олег",
                            companion.awaitsPetSearchPermission());
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, reply);
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) == 0 && !companion.awaitsPetSearchPermission() && !companion.hasActiveWorkOrder(),
                            "Refusing the search did not stop the order");
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, question, "search");
                }).thenIdle(30)
                .thenExecute(() -> h.assertTrue(!companion.hasActiveWorkOrder() && !wolf.isTame()
                        && ownChest.getItem(0).getCount() == 12 && bonesCarried(companion) == 0,
                        "A declined or stale order still looked for bones"))
                .thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 180)
    public static void queuedDogOrderReplacesUnansweredCatQuestion(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        h.spawn(EntityType.CAT, 10, 3, 5);
        h.spawn(EntityType.WOLF, 12, 3, 8);
        var link = new CompanionParkLink();
        link.bind(h.getLevel().getServer(), player.getUUID());
        var catOrder = link.submit(java.util.List.of(action("приручи кота")), "", () -> false);
        long[] catQuestion = {0};
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.CAT) > 0, "Did not ask for fish"))
                .thenExecute(() -> {
                    catQuestion[0] = companion.petQuestion(CompanionPet.CAT);
                    link.submit(java.util.List.of(action("приручи собаку")), "", () -> false);
                }).thenWaitUntil(() -> h.assertTrue(catOrder.isDone() && companion.petQuestion(CompanionPet.CAT) == 0
                        && companion.petQuestion(CompanionPet.DOG) > 0, "The new order waited for the unanswered cat question"))
                .thenExecute(() -> {
                    long dogQuestion = companion.petQuestion(CompanionPet.DOG);
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.CAT, catQuestion[0], "no");
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) == dogQuestion
                            && companion.autonomyLine().contains("кости"), "The old answer changed the new pet order");
                    link.close();
                }).thenSucceed();
    }

    @GameTest(template = "large_search", timeoutTicks = 250)
    public static void dogToggleCancelsOnlyItsActiveOrderAndIgnoresStaleReplies(GameTestHelper h) {
        var companion = CompanionPetSearchTests.fixture(h, false, false);
        var player = player(h, companion);
        var wolf = h.spawn(EntityType.WOLF, 12, 3, 8);
        order(h, player, "заведи собаку");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(companion.petQuestion(CompanionPet.DOG) > 0, "Did not ask for bones"))
                .thenExecute(() -> {
                    long oldQuestion = companion.petQuestion(CompanionPet.DOG);
                    companion.setPetEnabled(CompanionPet.CAT, false);
                    h.assertTrue(companion.petQuestion(CompanionPet.DOG) == oldQuestion, "Cat toggle canceled the dog order");
                    companion.setPetEnabled(CompanionPet.DOG, false);
                    var disabled = CompanionEvents.executeAction(player, "tame_dog", "", 1);
                    h.assertTrue(!disabled.success() && companion.petQuestion(CompanionPet.DOG) == 0, "Dog toggle did not stop the order");
                    companion.setItem(0, new ItemStack(Items.BONE, 12));
                    CompanionEvents.answerPetQuestion(player, companion.getUUID(), CompanionPet.DOG, oldQuestion, "yes");
                    h.assertTrue(!companion.hasActiveWorkOrder() && !wolf.isTame() && companion.getItem(0).getCount() == 12,
                            "A stale answer restarted the canceled order");
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 10)
    public static void validatesDogCommandsAndModelPlans(GameTestHelper h) {
        h.assertTrue(action("У тебя есть собака?").type().equals("dog_status")
                && action("найди нашу собаку").type().equals("find_dog")
                && action("не приручай волка").type().equals("dog_cancel")
                && action("оставь собаку дома").item().equals("home"), "Could not route dog chat commands");
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("""
                [{"action":"tame_dog"},{"action":"dog_status"},{"action":"find_dog"},
                 {"action":"sit_dog","item":"home"},{"action":"follow_dog"}]
                """));
        h.assertTrue(plan.size() == 5 && plan.get(3).item().equals("home"), "Valid dog plan was rejected");
        for (String invalid : new String[]{"{\"action\":\"tame_dog\",\"count\":2}", "{\"action\":\"follow_dog\",\"item\":\"home\"}"}) {
            boolean rejected = false;
            try {
                CompanionPlanCodec.parsePlan(JsonParser.parseString("[" + invalid + "]"));
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            h.assertTrue(rejected, "Accepted invalid dog action " + invalid);
        }
        h.succeed();
    }

    private static PlannedAction action(String speech) {
        return CompanionLocalCommands.action(CompanionLocalCommands.route(speech, "Олег"));
    }

    private static int bonesCarried(CompanionEntity companion) {
        int total = companion.getMainHandItem().is(Items.BONE) ? companion.getMainHandItem().getCount() : 0;
        if (companion.getOffhandItem().is(Items.BONE)) total += companion.getOffhandItem().getCount();
        for (int slot = 0; slot < companion.getContainerSize(); slot++)
            if (companion.getItem(slot).is(Items.BONE)) total += companion.getItem(slot).getCount();
        return total;
    }

    private static void order(GameTestHelper h, ServerPlayer player, String speech) {
        var action = action(speech);
        h.assertTrue(action != null, "Could not parse: " + speech);
        var result = CompanionEvents.executeAction(player, action.type(), action.item(), action.count());
        h.assertTrue(result.success(), result.message());
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static Container chest(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, Blocks.CHEST);
        return (Container) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    private static void move(GameTestHelper h, Entity entity, int x, int z) {
        var pos = h.absolutePos(new BlockPos(x, 3, z));
        entity.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }
}
