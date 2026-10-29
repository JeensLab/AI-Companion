package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionSortGroups.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionSortingTests.bagOf;
import static dev.aicompanion.gametest.CompanionSortingTests.chest;
import static dev.aicompanion.gametest.CompanionSortingTests.count;
import static dev.aicompanion.gametest.CompanionSortingTests.mixedThree;
import static dev.aicompanion.gametest.CompanionSortingTests.setup;
import static dev.aicompanion.gametest.CompanionSortingTests.totals;

/** What the player does in the middle of sorting the chests (issue 32, ticket 03): stop, break a chest, move things, walk away, leave the world. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSortingInterruptionTests {
    /** Takes him out of the reach of every chest (they lie at z = 13, he goes to the far corner), as a player's arm does not reach them. */
    static void outOfReach(GameTestHelper helper, CompanionEntity companion) {
        Vec3 corner = helper.absoluteVec(new Vec3(30.5, 1, 30.5));
        companion.teleportTo(corner.x, corner.y, corner.z);
    }

    /** «Стоп» while he carries things and no chest is in reach: they stay in his bag, the answer says so, and he does not touch the chests any more. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stopWhileCarryingLeavesThingsInBagAndSaysSo(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        Object[] stopped = {null, null}; long[] at = {0};
        helper.onEachTick(() -> {
            if (stopped[0] != null || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            long id = companion.activeWorkId();
            outOfReach(helper, companion);
            companion.setFollowing(true);
            stopped[0] = companion.workResult(id); stopped[1] = totals(chests); at[0] = helper.getTick();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(stopped[0] != null, "He never carried anything to stop on");
            helper.assertTrue(helper.getTick() > at[0] + 60, "Waiting to see he leaves the chests alone");
            String said = ((CompanionActionResult)stopped[0]).message();
            helper.assertTrue(said.contains("в сумке"), "The stop does not say things are in his bag: " + said);
            helper.assertTrue(!companion.hasActiveWorkOrder() && totals(chests).equals(stopped[1]), "He kept working the chests after the stop");
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
        });
    }

    /** «Стоп» at a chest of the set: what he carried for the player is in the chests at once (nothing lost or doubled), his bag is as before, and the answer has no note. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stopNextToAChestPutsThingsBackAndSaysNothing(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        String[] said = {null, null, null}; long[] at = {0};
        helper.onEachTick(() -> {
            if (said[0] != null || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
            long id = companion.activeWorkId();
            companion.stopGathering();
            said[0] = companion.workResult(id).message();
            said[1] = CompanionEvents.executeAction(player, "stop", "", 1).message();
            said[2] = CompanionDialogue.summarize(List.of(new PlannedAction("stop", 0, 1)), said[1]);
            at[0] = helper.getTick();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(said[0] != null, "He never carried anything to stop on");
            helper.assertTrue(helper.getTick() > at[0] + 10, "Waiting to see nothing more happens");
            helper.assertTrue(!companion.hasActiveWorkOrder() && totals(companion).equals(bagBefore), "Things of the player stay in his bag: " + totals(companion) + " vs " + bagBefore);
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
            helper.assertTrue(!said[0].contains("в сумке") && !said[1].contains("в сумке") && !said[2].contains("в сумке"), "A note with nothing left: " + said[0] + " / " + said[1] + " / " + said[2]);
            helper.assertTrue(said[2].equals("Ладно, остановился."), "The stop answer is not plain: " + said[2]);
        });
    }

    /** The player took what he carried out of his bag (his inventory screen), then «стоп» out of reach: nothing of it is left, so no note. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stopAfterThePlayerTookWhatHeCarriedSaysNothing(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); mixedThree(helper); bagOf(companion);
        Map<Item, Integer> bagBefore = totals(companion);
        String[] said = {null};
        helper.onEachTick(() -> {
            if (said[0] != null || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            long id = companion.activeWorkId();
            for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, ItemStack.EMPTY);
            bagOf(companion);
            outOfReach(helper, companion);
            companion.stopGathering();
            said[0] = companion.workResult(id).message();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(said[0] != null, "He never carried anything to stop on");
            helper.assertTrue(!said[0].contains("в сумке"), "A note of things the player already took: " + said[0]);
        });
    }

    /**
     * The real path of «стоп»: the plan port cancels the work first (stopGathering), then the command arrives. The command's own answer is what the
     * player hears, and it must carry the note: the work is already gone by then.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void stopCommandAfterTheCancelStillSaysThingsAreInTheBag(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        String[] said = {null, null, null};
        helper.onEachTick(() -> {
            if (said[0] != null || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
            outOfReach(helper, companion);
            companion.stopGathering();
            said[0] = CompanionEvents.executeAction(player, "stop", "", 1).message();
            said[1] = CompanionDialogue.summarize(List.of(new PlannedAction("stop", 0, 1)), said[0]);
            said[2] = CompanionEvents.executeAction(player, "stop", "", 1).message();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(said[0] != null, "He never carried anything to stop on");
            helper.assertTrue(said[0].contains("в сумке") && said[1].contains("в сумке"), "The player hears: " + said[0] + " / " + said[1]);
            helper.assertTrue(!said[2].contains("в сумке"), "The note is said twice: " + said[2]);
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
        });
    }

    /** Review round 4, finding 1: «Отменить» in the tasks window while he carries things out of reach of the chests: the player hears the note with the stop. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void cancelFromTheTasksWindowSaysThingsAreInTheBag(GameTestHelper helper) { cancelFromTheTasksWindow(helper, true); }

    /** The same at a chest: the things are back in it, the player hears no note. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void cancelFromTheTasksWindowNextToAChestSaysNothing(GameTestHelper helper) { cancelFromTheTasksWindow(helper, false); }

    private static void cancelFromTheTasksWindow(GameTestHelper helper, boolean far) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        List<PlannedAction> plan = List.of(new PlannedAction("sort_chests", 0, 1));
        CompanionParkLink link = new CompanionParkLink(); link.bind(helper.getLevel().getServer(), companion.getOwnerId());
        var report = link.submit(plan, "gametest-sort-window-cancel", () -> false);
        boolean[] cancelled = {false};
        helper.onEachTick(() -> {
            if (cancelled[0] || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            if (far) outOfReach(helper, companion);
            cancelled[0] = link.cancel(link.tasks().getFirst().id());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cancelled[0] && report.isDone(), "The plan was not cancelled while he carried things");
            String heard = CompanionDialogue.heard(plan, CompanionDialogue.summarize(plan, report.join()), true, false);
            link.close();
            helper.assertTrue(heard.contains(CompanionDialogue.BAG_NOTE) == far, "The player hears «" + heard + "», the report: " + report.join());
            helper.assertTrue(totals(companion).equals(bagBefore) != far, "The bag after the cancel: " + totals(companion) + " vs " + bagBefore);
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
        });
    }

    /** Review round 4, finding 3: «стоп» out of reach of the chests, then «разложи» again: what he carried for the player goes back into the chests, his bag is as before. */
    @GameTest(template = "tall_tree", timeoutTicks = 4000)
    public static void sortingAgainAfterTheStopPutsBackWhatHeCarried(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        boolean[] again = {false};
        helper.onEachTick(() -> {
            if (again[0] || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            outOfReach(helper, companion);
            companion.stopGathering();
            helper.assertTrue(!totals(companion).equals(bagBefore), "He put the things back with no chest in reach");
            companion.startWork("sort_chests", "", 1); again[0] = true;
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(again[0] && !companion.hasActiveWorkOrder(), "Still sorting: " + companion.getLastTaskResult());
            helper.assertTrue(totals(companion).equals(bagBefore), "Things of the chests stay in his bag: " + totals(companion) + " after «" + companion.getLastTaskResult() + "»");
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
        });
    }

    /** Review round 3, finding 1: «иди ко мне» ends the sorting, and the plan port waits for the walk; the note of the bag outlives the wait. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void comeRightAfterTheStopStillSaysThingsAreInTheBag(GameTestHelper helper) { orderAfterTheStop(helper, "come", true); }

    /** «Иди ко мне» at a chest: the things are put back first, and nothing is said about the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void comeRightAfterTheStopNextToAChestSaysNothing(GameTestHelper helper) { orderAfterTheStop(helper, "come", false); }

    /** The same for «убей мобов»: its work is waited for too. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void huntMobsRightAfterTheStopStillSaysThingsAreInTheBag(GameTestHelper helper) { orderAfterTheStop(helper, "hunt_mobs", true); }

    private static void orderAfterTheStop(GameTestHelper helper, String order, boolean far) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        List<PlannedAction> plan = List.of(new PlannedAction(order, 0, 1));
        List<java.util.concurrent.CompletableFuture<CompanionPlanRunner.Outcome>> run = new java.util.ArrayList<>();
        helper.onEachTick(() -> {
            if (!run.isEmpty() || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            if (far) outOfReach(helper, companion);
            companion.stopGathering();
            run.add(CompanionPlanRunner.run(plan, new CompanionServerPlanPort(helper.getLevel().getServer(), companion.getOwnerId(), "gametest-sort-then-" + order, () -> false)));
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!run.isEmpty() && run.getFirst().isDone() && !run.getFirst().isCompletedExceptionally(), "The order after the stop is not done: " + companion.getLastTaskResult());
            String report = run.getFirst().join().report(), heard = CompanionDialogue.summarize(plan, report);
            helper.assertTrue(heard.contains("в сумке") == far, "The player hears «" + heard + "», the report: " + report);
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled: " + totals(companion, chests) + " vs " + before);
        });
    }

    /** A chest of the set is broken while he carries things: he goes on with the others, says he skipped one, nothing is lost (what fell out lies on the ground). */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void brokenChestIsSkippedAndSaidSo(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests);
        Map<Item, Integer> bagBefore = totals(companion); boolean[] broken = {false};
        helper.onEachTick(() -> {
            if (broken[0] || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(12, 1, 13)), true); broken[0] = true;
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(broken[0], "He never carried anything to break a chest on");
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still sorting: " + companion.getLastTaskResult());
            Map<Item, Integer> now = totals(companion, new Container[]{chests[0], chests[2]});
            for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) now.merge(drop.getItem().getItem(), drop.getItem().getCount(), Integer::sum);
            now.remove(Items.CHEST);
            helper.assertTrue(now.equals(before), "Items lost or doubled: " + now + " vs " + before);
            helper.assertTrue(companion.getLastTaskResult().contains("Пропустил"), "The answer does not say a chest was skipped: " + companion.getLastTaskResult());
        });
    }

    /** Protective: the player puts things into and takes things out of chests of the set mid-way: the chests are read again at each opening, nothing is doubled, the groups still end up together. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void playerMovingThingsInChestsMidWayIsNotDoubled(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        boolean[] done = {false}; ItemStack[] taken = {ItemStack.EMPTY};
        helper.onEachTick(() -> {
            if (done[0] || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            for (int slot = 0; slot < 27 && taken[0].isEmpty(); slot++) taken[0] = chests[0].removeItem(slot, 5); chests[2].setItem(10, new ItemStack(Items.DIRT, 20)); chests[1].setItem(11, new ItemStack(Items.APPLE, 9)); done[0] = true;
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0] && !companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), "Not done: " + companion.getLastTaskResult());
            Map<Item, Integer> want = new HashMap<>(before);
            want.merge(taken[0].getItem(), -taken[0].getCount(), Integer::sum); want.merge(Items.DIRT, 20, Integer::sum); want.merge(Items.APPLE, 9, Integer::sum);
            helper.assertTrue(totals(companion, chests).equals(want), "Items doubled or lost: " + totals(companion, chests) + " vs " + want);
            for (Group group : Group.values()) helper.assertTrue(CompanionSortingTests.chestsHolding(chests, group) <= 1, group + " is in several chests");
        });
    }

    /** Protective: the player walks 30 blocks away mid-way: the set was fixed at the request, he finishes. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void playerWalkingFarAwayDoesNotStopTheSorting(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        boolean[] away = {false};
        helper.onEachTick(() -> {
            if (away[0] || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            var player = helper.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
            player.teleportTo(player.getX() + 30, player.getY(), player.getZ()); away[0] = true;
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(away[0] && !companion.hasActiveWorkOrder() && companion.didLastTaskSucceed(), "Not done: " + companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
            for (Group group : Group.values()) helper.assertTrue(CompanionSortingTests.chestsHolding(chests, group) <= 1, group + " is in several chests");
        });
    }

    /** A chest walled in: an honest answer with the reason in limited time, no endless circling; the carried things stay in his bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void walledInChestEndsWithHonestAnswer(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) { helper.setBlock(new BlockPos(12, 3, 13), Blocks.BEDROCK); continue; }
            helper.setBlock(new BlockPos(12 + dx, 1, 13 + dz), Blocks.BEDROCK); helper.setBlock(new BlockPos(12 + dx, 2, 13 + dz), Blocks.BEDROCK);
        }
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(!companion.hasActiveWorkOrder(), "Still trying: " + companion.getLastTaskResult());
            helper.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Не смог дойти"), "Not an honest answer: " + companion.getLastTaskResult());
            helper.assertTrue(totals(companion, chests).equals(before), "Items lost or doubled");
        });
    }

    /** Protective: leaving the world mid-way: the bag is saved as it is, the loaded companion does not go on sorting, nothing is doubled. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void leavingTheWorldMidWayKeepsThingsAndDoesNotResume(GameTestHelper helper) {
        CompanionEntity companion = setup(helper); Container[] chests = mixedThree(helper); bagOf(companion);
        Map<Item, Integer> before = totals(companion, chests), bagBefore = totals(companion);
        boolean[] saved = {false};
        helper.onEachTick(() -> {
            if (saved[0] || !companion.hasActiveWorkOrder() || totals(companion).equals(bagBefore)) return;
            CompoundTag tag = new CompoundTag(); companion.addAdditionalSaveData(tag);
            CompanionEntity restored = AICompanionMod.COMPANION.get().create(helper.getLevel()); restored.readAdditionalSaveData(tag);
            helper.assertTrue(!restored.hasActiveWorkOrder() && !restored.isWorking(), "The loaded companion goes on with the work");
            helper.assertTrue(totals(restored, chests).equals(before), "Items lost or doubled after loading: " + totals(restored, chests) + " vs " + before);
            saved[0] = true; companion.discard();
        });
        companion.startWork("sort_chests", "", 1);
        helper.succeedWhen(() -> helper.assertTrue(saved[0], "He never carried anything"));
    }
}
