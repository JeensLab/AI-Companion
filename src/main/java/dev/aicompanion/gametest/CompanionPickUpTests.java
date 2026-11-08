package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionGame;
import dev.aicompanion.entity.CompanionPickUp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The owner's request: «подбери вон ту курицу плавающую» takes that chicken lying on the ground, in water and at any
 * height, instead of starting a hunt or a dig. Nothing of that kind near is answered honestly with what does lie
 * around, and only then does the usual work start; the scan radius and the switch in the settings are respected.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPickUpTests {
    /** A chicken on the water beside him, drifting: the order takes it and no hunt starts. */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void aChickenOnTheWaterIsPickedUpInsteadOfHunted(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        // Water over the floor: the drop floats in it and drifts a couple of blocks, out of the two-block loose pickup.
        for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) h.setBlock(new BlockPos(12 + dx, 1, 8 + dz), Blocks.WATER);
        ItemEntity chicken = item(h, 13, 1, 8, Items.CHICKEN, 1);
        chicken.setDeltaMovement(0.1, 0.0, 0.02);
        // The last ticks of the drop and of the order, for a failure that would otherwise read as «not taken» alone.
        var trail = new java.util.ArrayDeque<String>();
        h.onEachTick(() -> {
            if (trail.size() >= 12) trail.removeFirst();
            trail.addLast("t" + h.getTick() + (chicken.isRemoved() ? " taken" : String.format(java.util.Locale.ROOT, " %.1f,%.1f,%.1f water=%s", chicken.getX(), chicken.getY(), chicken.getZ(), chicken.isInWater()))
                    + " oleg " + companion.workPhase() + " bag=" + stored(companion, Items.CHICKEN));
        });
        h.startSequence().thenExecute(() -> {
            var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
            h.assertTrue(result.success(), "The pickup order was refused: " + result.message());
            h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        }).thenWaitUntil(() -> h.assertTrue(stored(companion, Items.CHICKEN) == 1 && chicken.isRemoved() && companion.workPhase().isEmpty(),
                "The chicken was not taken: bag " + stored(companion, Items.CHICKEN) + ", drop removed " + chicken.isRemoved()
                        + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult() + "; " + String.join(" | ", trail)))
          .thenExecute(() -> h.assertTrue(companion.getLastTaskResult().contains("Подобрал"),
                "No report of the pickup: " + companion.getLastTaskResult()))
          .thenSucceed();
    }

    /** Only stone around: the honest answer names it, nothing is taken for the chicken, and only then the hunt starts. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void nothingOfThatKindNearIsAnsweredHonestly(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity stone = item(h, 12, 1, 8, Items.COBBLESTONE, 3);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
        h.assertTrue(!result.success(), "A pickup with nothing of it near reported success");
        h.assertTrue(result.message().contains("нет курицы"), "No honest answer: " + result.message());
        h.assertTrue(result.message().contains("камень"), "What lies around is not named: " + result.message());
        // The honest answer is not the whole answer: the usual work for a chicken starts right after it.
        h.assertTrue(companion.workPhase().startsWith("GAME"), "The fallback did not start: " + companion.workPhase());
        h.assertTrue(result.message().contains("Иду охотиться"), "The fallback is not named: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(stored(companion, Items.CHICKEN) == 0, "A chicken appeared from nowhere");
            h.assertTrue(!stone.isRemoved(), "The stone was taken for the chicken");
        });
    }

    /**
     * The plan runner over the same order: the honest answer is reported once, its fallback starts, and the plan stops
     * without «Повтор шага» — the refusal is the answer, not a busy companion to wait for.
     */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void thePlanRunnerReportsTheHonestAnswerWithoutRetrying(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.COBBLESTONE, 3);
        boolean[] hunted = {false};
        h.onEachTick(() -> { if (companion.workPhase().startsWith("GAME")) hunted[0] = true; });
        var plan = CompanionPlanRunner.run(List.of(new PlannedAction("pick_up", 0, 1, "minecraft:chicken")),
                new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-pickup-plan", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The pickup plan is still running: " + companion.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.recoveries() == 0 && !outcome.report().contains("Повтор шага"),
                    "The honest answer was repeated: " + outcome.report());
            h.assertTrue(outcome.report().contains("нет курицы") && outcome.report().contains("камень"),
                    "No honest answer in the plan: " + outcome.report());
            h.assertTrue(outcome.report().contains("Иду охотиться") && hunted[0],
                    "The fallback did not start: " + outcome.report() + ", phase " + companion.workPhase());
        });
    }

    /** «Алмазы» are the gem and its ore: a diamond on the ground is taken. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void aDiamondOnTheGroundIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity diamond = item(h, 11, 1, 8, Items.DIAMOND, 2);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:diamond", 2);
        h.assertTrue(result.success(), "The pickup order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(stored(companion, Items.DIAMOND) == 2, "Diamonds in the bag: " + stored(companion, Items.DIAMOND));
            h.assertTrue(diamond.isRemoved(), "The diamond is still lying on the floor");
        });
    }

    /** The scan stops at the radius from the settings; the answer names only what lies inside it; a wider radius takes the drop. */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void theScanStopsAtTheRadius(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        // The stick lies about 5.7 blocks away: beyond a 4-block scan, inside a 16-block one.
        ItemEntity stick = item(h, 12, 1, 12, Items.STICK, 1);
        // The stone lies 3 blocks away: inside the 4-block scan and out of the two-block loose pickup, so the honest answer
        // has something true to name and the drop stays where it is.
        ItemEntity stone = item(h, 11, 1, 8, Items.COBBLESTONE, 3);
        companion.setPickUpNearby(true, 4);
        var outOfReach = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:stick", 1);
        h.assertTrue(!outOfReach.success(), "A drop beyond the radius was taken: " + outOfReach.message());
        h.assertTrue(outOfReach.message().contains("нет палки"), "No honest answer: " + outOfReach.message());
        h.assertTrue(outOfReach.message().contains("камень (3)"), "What lies inside the radius is not named: " + outOfReach.message());
        // The answer must not call the stick beyond the radius «рядом» while saying there is no stick: the same radius for both.
        int listed = outOfReach.message().indexOf("Лежит рядом:");
        h.assertTrue(listed >= 0 && !outOfReach.message().substring(listed).contains("палка"),
                "The stick beyond the radius is listed as lying near: " + outOfReach.message());
        h.startSequence().thenIdle(20).thenExecute(() -> {
            h.assertTrue(!stick.isRemoved(), "The stick beyond the radius was taken");
            h.assertTrue(!stone.isRemoved(), "The stone was taken for the stick");
            companion.setPickUpNearby(true, 16);
            var inReach = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:stick", 1);
            h.assertTrue(inReach.success(), "The wider radius did not take the order: " + inReach.message());
        }).thenWaitUntil(() -> h.assertTrue(stored(companion, Items.STICK) == 1 && stick.isRemoved(),
                "The stick was not taken with the wider radius: " + companion.workPhase())).thenSucceed();
    }

    /** «Подбери 8 брёвен» with no log lying: the fallback gathers the number the player named, not its own 16. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theFallbackGathersTheCountThePlayerNamed(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:oak_log", 8);
        h.assertTrue(!result.success(), "A pickup with nothing of it near reported success");
        h.assertTrue(result.message().contains("нет бревна"), "No honest answer: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("WOOD"), "The fallback did not start: " + companion.workPhase());
        h.assertTrue(companion.watchDetail().contains("count=8"), "The fallback does not gather the named count: " + companion.watchDetail());
        h.succeed();
    }

    /** A bag with no room: the chicken lying there is named and the full bag is said, not «нет курицы». */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void aFullBagIsSaidInsteadOfNoSuchThingNear(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        ItemEntity chicken = item(h, 12, 1, 8, Items.CHICKEN, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
        h.assertTrue(!result.success(), "A pickup with a full bag reported success");
        h.assertTrue(result.message().contains("сумка полна"), "The full bag is not said: " + result.message());
        h.assertTrue(result.message().contains("Лежит рядом: курица (1)"), "The chicken lying there is not named: " + result.message());
        h.assertTrue(!result.message().contains("нет курицы"), "A full bag was answered as «nothing lies there»: " + result.message());
        // Nothing to gather into: the answer is said at once and no fallback work starts.
        h.assertTrue(companion.workPhase().isEmpty(), "A fallback started for a full bag: " + companion.workPhase());
        h.assertTrue(!chicken.isRemoved(), "The chicken was taken into a full bag");
        h.succeed();
    }

    /** Switched off, «подбери курицу» starts the usual hunt at once and leaves the drop on the ground. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void switchedOffItHuntsAsBefore(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(false, 16);
        ItemEntity chicken = item(h, 12, 1, 8, Items.CHICKEN, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
        h.assertTrue(result.success(), "The hunt was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("GAME"), "Not the hunt: " + companion.workPhase());
        h.assertTrue(!chicken.isRemoved(), "The drop was taken with the setting off");
        h.succeed();
    }

    /** «Подбери вон ту курицу плавающую» is the pickup order; «принеси» and two things at once stay the model's. */
    @GameTest(template = "large_search")
    public static void thePhraseRoutesToThePickup(GameTestHelper h) {
        h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("Олег, подбери вон ту курицу плавающую", "Олег")),
                "Phrase: " + CompanionLocalCommands.route("Олег, подбери вон ту курицу плавающую", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("подбери алмазы", "Олег")),
                "Gems: " + CompanionLocalCommands.route("подбери алмазы", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:oak_log".equals(CompanionLocalCommands.route("подбери брёвна", "Олег")),
                "Logs: " + CompanionLocalCommands.route("подбери брёвна", "Олег"));
        h.assertTrue("companion work pick_up 3 minecraft:cobblestone".equals(CompanionLocalCommands.route("подбери 3 камня", "Олег")),
                "Stone with a number: " + CompanionLocalCommands.route("подбери 3 камня", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:rotten_flesh".equals(CompanionLocalCommands.route("подбери гниль в воде", "Олег")),
                "Rotten flesh: " + CompanionLocalCommands.route("подбери гниль в воде", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:rotten_flesh".equals(CompanionLocalCommands.route("подбери гнилую плоть", "Олег")),
                "Rotten flesh: " + CompanionLocalCommands.route("подбери гнилую плоть", "Олег"));
        h.assertTrue(CompanionLocalCommands.route("принеси мне курицу", "Олег") == null, "«Принеси» is not the pickup");
        h.assertTrue(CompanionLocalCommands.route("подбери курицу и брёвна", "Олег") == null, "Two things at once are a plan");
        PlannedAction step = CompanionLocalCommands.action("companion work pick_up 64 minecraft:chicken");
        h.assertTrue("pick_up".equals(step.type()) && step.count() == 64 && "minecraft:chicken".equals(step.item()), "Step: " + step);
        h.succeed();
    }

    /**
     * A drop five blocks away in deep water is found by the scan and swum to: water is no hole in the scan, and no cell of
     * a deep pool has dry ground under it, so a stand on the floor alone would give the drop up as out of reach.
     */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void aDropFiveBlocksAwayInDeepWaterIsFoundAndTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 2);
        ItemEntity flesh = floating(h, 13, 2, 8, Items.ROTTEN_FLESH, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(flesh.isRemoved() && stored(companion, Items.ROTTEN_FLESH) == 1,
                    "The drop five blocks away in the water was not taken: bag " + stored(companion, Items.ROTTEN_FLESH)
                            + ", drop removed " + flesh.isRemoved() + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(!companion.workPhase().startsWith("GAME"), "A hunt started over a drop in the water: " + companion.workPhase());
        });
    }

    /** The surface of a pool three deep lies two blocks over his feet: he swims up to it as a player does and takes it. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aDropOnTheSurfaceOfADeepPoolIsSwumTo(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 3);
        ItemEntity pork = floating(h, 13, 3, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1 && companion.workPhase().isEmpty(),
                    "The pork on the surface of the deep pool was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                            + ", drop removed " + pork.isRemoved() + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                            + "; " + companion.watchDetail());
        });
    }

    /** Three pieces of rotten flesh in the water: the order takes all three, not one, and says so. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void everyMatchingPieceInTheWaterIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 11, 5, 6, 2);
        ItemEntity first = floating(h, 12, 2, 8, Items.ROTTEN_FLESH, 1);
        ItemEntity second = floating(h, 13, 2, 8, Items.ROTTEN_FLESH, 1);
        ItemEntity third = floating(h, 13, 2, 9, Items.ROTTEN_FLESH, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(first.isRemoved() && second.isRemoved() && third.isRemoved() && stored(companion, Items.ROTTEN_FLESH) == 3,
                    "Not all three pieces were taken: bag " + stored(companion, Items.ROTTEN_FLESH) + ", drops left "
                            + (!first.isRemoved() || !second.isRemoved() || !third.isRemoved()) + ", phase " + companion.workPhase()
                            + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал гнилую плоть (3 шт.)"),
                    "The report is wrong: " + companion.getLastTaskResult());
        });
    }

    /** «Подбери одну» takes one and says how many remain; the repeated order takes the rest instead of claiming failure. */
    @GameTest(template = "large_search", timeoutTicks = 1200)
    public static void anOrderForOnePieceLeavesTheRestCounted(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        // Three pieces apart on the ground: the one nearest is taken, the two further on are left and counted.
        ItemEntity first = item(h, 12, 1, 8, Items.ROTTEN_FLESH, 1);
        ItemEntity second = item(h, 17, 1, 8, Items.ROTTEN_FLESH, 1);
        ItemEntity third = item(h, 21, 1, 8, Items.ROTTEN_FLESH, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 1);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(stored(companion, Items.ROTTEN_FLESH) == 1 && companion.workPhase().isEmpty(),
                    "The one piece was not taken: bag " + stored(companion, Items.ROTTEN_FLESH) + ", phase " + companion.workPhase());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал гнилую плоть (1 шт.), осталось 2 шт."),
                    "The pieces left are not counted: " + companion.getLastTaskResult());
            h.assertTrue(!companion.getLastTaskResult().contains("Не смог подобрать"),
                    "A partial pickup was reported as a failure: " + companion.getLastTaskResult());
        }).thenExecute(() -> {
            companion.setFollowing(false);
            var again = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
            h.assertTrue(again.success(), "The repeated order was refused: " + again.message());
        }).thenWaitUntil(() -> {
            h.assertTrue(first.isRemoved() && second.isRemoved() && third.isRemoved() && stored(companion, Items.ROTTEN_FLESH) == 3,
                    "The repeated order did not take the rest: bag " + stored(companion, Items.ROTTEN_FLESH) + ", phase " + companion.workPhase()
                            + ", " + companion.getLastTaskResult());
            h.assertTrue(!companion.getLastTaskResult().contains("Не смог подобрать"),
                    "The repeated order claimed failure while it took the rest: " + companion.getLastTaskResult());
        }).thenSucceed();
    }

    /** A full bag: the order takes what fits and counts what is left; the repeat names the drop instead of «не смог подобрать». */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void leftoversInAFullBagAreCountedNotFailed(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        // One slot with room for a single piece more: rotten flesh stacks, so a bare free slot would take both.
        companion.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 63));
        pool(h, 11, 5, 6, 2);
        ItemEntity first = floating(h, 12, 2, 8, Items.ROTTEN_FLESH, 1);
        ItemEntity second = floating(h, 13, 2, 8, Items.ROTTEN_FLESH, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(stored(companion, Items.ROTTEN_FLESH) == 64 && companion.workPhase().isEmpty(),
                    "The piece that fits was not taken: bag " + stored(companion, Items.ROTTEN_FLESH) + ", phase " + companion.workPhase());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал гнилую плоть (1 шт.), осталось 1 шт."),
                    "The piece left is not counted: " + companion.getLastTaskResult() + "; drops " + first.isRemoved() + "/" + second.isRemoved()
                            + " " + first.getItem() + " " + second.getItem() + "; " + companion.watchDetail());
            h.assertTrue(!companion.getLastTaskResult().contains("Не смог подобрать"),
                    "A partial pickup was reported as a failure: " + companion.getLastTaskResult());
            h.assertTrue(!first.isRemoved() || !second.isRemoved(), "The leftover piece disappeared");
        }).thenExecute(() -> {
            companion.setFollowing(false);
            var again = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
            h.assertTrue(!again.success(), "A pickup into a full bag reported success");
            h.assertTrue(again.message().contains("гнилая плоть (1)") && again.message().contains("сумка полна"),
                    "The piece left is not named with its count: " + again.message());
            h.assertTrue(!again.message().contains("Не смог подобрать"),
                    "The repeat claimed failure while the piece lies there: " + again.message());
        }).thenSucceed();
    }

    /** «Ведро» is a bucket: a bucket of water on the ground is taken for it. */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void aBucketOfWaterIsTakenForBucket(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.assertTrue("companion work pick_up 64 minecraft:bucket".equals(CompanionLocalCommands.route("подбери ведро", "Олег")),
                "Phrase: " + CompanionLocalCommands.route("подбери ведро", "Олег"));
        ItemEntity bucket = item(h, 12, 1, 8, Items.WATER_BUCKET, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:bucket", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(bucket.isRemoved() && stored(companion, Items.WATER_BUCKET) == 1,
                    "The bucket of water was not taken: phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал ведро (1 шт.)"),
                    "The report does not call it a bucket: " + companion.getLastTaskResult());
        });
    }

    /** «Чернила» and «чернильный мешок» are the ink sac: the one five blocks away in the water is taken, not hunted. */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void anInkSacIsTakenForTheInkWords(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.assertTrue("companion work pick_up 64 minecraft:ink_sac".equals(CompanionLocalCommands.route("подбери чернила", "Олег")),
                "Phrase: " + CompanionLocalCommands.route("подбери чернила", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:ink_sac".equals(CompanionLocalCommands.route("подбери чернильный мешок", "Олег")),
                "Phrase: " + CompanionLocalCommands.route("подбери чернильный мешок", "Олег"));
        pool(h, 12, 6, 5, 2);
        ItemEntity sac = floating(h, 13, 2, 8, Items.INK_SAC, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:ink_sac", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(sac.isRemoved() && stored(companion, Items.INK_SAC) == 1 && companion.workPhase().isEmpty(),
                    "The ink sac in the water was not taken: phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал чернильный мешок (1 шт.)"),
                    "The report does not call it an ink sac: " + companion.getLastTaskResult());
        });
    }

    /** «Жареная свинина» and «свининка» are the cooked pork lying in the water: taken, and no hunt starts. */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void cookedPorkInTheWaterIsTakenInsteadOfHunted(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.assertTrue("companion work pick_up 64 minecraft:porkchop".equals(CompanionLocalCommands.route("подбери свинину жареную", "Олег")),
                "Phrase: " + CompanionLocalCommands.route("подбери свинину жареную", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:porkchop".equals(CompanionLocalCommands.route("жареная свининка в воде кусок подбери", "Олег")),
                "Trailing verb: " + CompanionLocalCommands.route("жареная свининка в воде кусок подбери", "Олег"));
        boolean[] hunted = {false};
        h.onEachTick(() -> { if (companion.workPhase().startsWith("GAME")) hunted[0] = true; });
        pool(h, 12, 6, 5, 2);
        ItemEntity pork = floating(h, 13, 2, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1,
                    "The cooked pork in the water was not taken: phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(!hunted[0], "A hunt started over the cooked pork lying there");
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал свинину (1 шт.)"),
                    "The report does not name the pork in the right case: " + companion.getLastTaskResult());
        });
    }

    /** Four pieces of chicken: one report, in the right case — «Подобрал курицу (4 шт.)», not «Подобрал курица». */
    @GameTest(template = "large_search", timeoutTicks = 700)
    public static void theReportNamesTheMeatInTheRightCase(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity first = item(h, 11, 1, 8, Items.CHICKEN, 1);
        ItemEntity second = item(h, 12, 1, 8, Items.CHICKEN, 1);
        ItemEntity third = item(h, 12, 1, 9, Items.CHICKEN, 1);
        ItemEntity fourth = item(h, 11, 1, 9, Items.CHICKEN, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(first.isRemoved() && second.isRemoved() && third.isRemoved() && fourth.isRemoved()
                            && stored(companion, Items.CHICKEN) == 4,
                    "Not all four pieces were taken: bag " + stored(companion, Items.CHICKEN) + ", phase " + companion.workPhase()
                            + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал курицу (4 шт.)"),
                    "The report uses the wrong case: " + companion.getLastTaskResult());
        });
    }

    /** The honest list of what lies around is in Russian, not the server's English item names. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theHonestListNamesThingsInRussian(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.WATER_BUCKET, 1);
        item(h, 11, 1, 9, Items.DIAMOND_PICKAXE, 1);
        item(h, 12, 1, 10, Items.ROTTEN_FLESH, 2);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 64);
        h.assertTrue(!result.success(), "A pickup with nothing of it near reported success");
        h.assertTrue(result.message().contains("Рядом нет курицы"), "No honest answer: " + result.message());
        h.assertTrue(result.message().contains("ведро"), "The bucket is not named in Russian: " + result.message());
        h.assertTrue(result.message().contains("алмазная кирка"), "The pickaxe is not named in Russian: " + result.message());
        h.assertTrue(result.message().contains("гнилая плоть (2)"), "The rotten flesh is not named in Russian: " + result.message());
        h.assertTrue(!result.message().contains("Water Bucket") && !result.message().contains("Diamond Pickaxe")
                        && !result.message().contains("Rotten Flesh"),
                "The list names English item names: " + result.message());
        h.succeed();
    }

    /** A chicken lying five blocks away in the water: the pickup takes it, and the hunt never starts. */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void noHuntStartsWhenAMatchingDropLiesNear(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        boolean[] hunted = {false};
        h.onEachTick(() -> { if (companion.workPhase().startsWith("GAME")) hunted[0] = true; });
        pool(h, 12, 6, 5, 2);
        ItemEntity chicken = floating(h, 13, 2, 8, Items.CHICKEN, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(chicken.isRemoved() && stored(companion, Items.CHICKEN) == 1,
                    "The chicken in the water was not taken: phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(!hunted[0], "A hunt started while the chicken lay within the scan radius");
        });
    }

    /**
     * A drop at the bottom of a three-deep pool: a drop in water rises to the surface (it floats), so it starts under him
     * and must not be given up while it rises; he takes it once it floats up to him.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aDropAtTheBottomOfADeepPoolFloatsUpAndIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 3);
        ItemEntity pork = item(h, 13, 1, 8, Items.COOKED_PORKCHOP, 1);
        var trail = new java.util.ArrayDeque<String>();
        h.onEachTick(() -> {
            if (trail.size() >= 20) trail.removeFirst();
            var path = companion.getNavigation().getPath();
            trail.addLast("t" + h.getTick() + " y=" + String.format(java.util.Locale.ROOT, "%.2f", pork.getY())
                    + " oleg=" + companion.blockPosition().toShortString()
                    + " path=" + (path == null ? "none" : path.getTarget().toShortString() + (path.isDone() ? " done" : ""))
                    + " " + companion.workPhase() + " res=" + companion.getLastTaskResult());
        });
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1 && companion.workPhase().isEmpty(),
                    "The pork at the bottom of the deep pool was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                            + ", drop removed " + pork.isRemoved() + ", drop y " + pork.getY() + ", phase "
                            + companion.workPhase() + ", " + companion.getLastTaskResult() + "; " + companion.watchDetail()
                            + "; " + String.join(" | ", trail));
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал свинину (1 шт.)"),
                    "The report is wrong: " + companion.getLastTaskResult());
        });
    }

    /**
     * A drop he cannot reach at all: it lies in a pocket walled in on every side and roofed over, so there is no cell to
     * take it from. Nothing is gained and the piece is still there: the order says «Не достал … осталось 1 шт.», not a
     * bare «не смог».
     */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void aDropInASealedPocketIsReportedWithTheCountLeft(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        // A 3x3 wall of stone at his floor level around the drop, roofed over: its cell has no way in, no cell within a
        // step of it can be stood in, and the body does not fit under the roof.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) h.setBlock(new BlockPos(12 + dx, 1, 8 + dz), Blocks.STONE);
        h.setBlock(new BlockPos(12, 2, 8), Blocks.STONE);
        ItemEntity pork = item(h, 12, 1, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(companion.workPhase().isEmpty(),
                    "The order never ended: phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                            + "; " + companion.watchDetail());
            h.assertTrue(stored(companion, Items.COOKED_PORKCHOP) == 0,
                    "The pork was taken through the stone: bag " + stored(companion, Items.COOKED_PORKCHOP));
            h.assertTrue(!pork.isRemoved(), "The drop disappeared");
            h.assertTrue(companion.getLastTaskResult().contains("Не достал свинину, осталось 1 шт."),
                    "The leftover is not counted: " + companion.getLastTaskResult());
            h.assertTrue(!companion.getLastTaskResult().contains("Не смог подобрать"),
                    "A drop still lying was reported as «не смог подобрать»: " + companion.getLastTaskResult());
        });
    }

    /** A drop on a ledge two blocks over his feet: he takes it from the ground below it, as a player reaches up. */
    @GameTest(template = "large_search", timeoutTicks = 500)
    public static void aDropOnALedgeOverHisHeadIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.setBlock(new BlockPos(13, 1, 8), Blocks.STONE);
        h.setBlock(new BlockPos(13, 2, 8), Blocks.STONE);
        ItemEntity pork = item(h, 13, 3, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1 && companion.workPhase().isEmpty(),
                    "The pork on the ledge was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                            + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
        });
    }

    /** A drop at the bottom of a one-deep pit: he steps down into it and takes it. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aDropInAPitIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pit(h, 12, 6, 3);
        ItemEntity pork = item(h, 13, 1, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1 && companion.workPhase().isEmpty(),
                    "The pork in the pit was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                            + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                            + "; " + companion.watchDetail());
        });
    }

    /** A drop behind a wall: he walks around it and takes it, not through it. */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void aDropBehindAWallIsWalkedAroundAndTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        for (int z = 5; z <= 11; z++) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(11, y, z), Blocks.STONE);
        ItemEntity pork = item(h, 14, 1, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1 && companion.workPhase().isEmpty(),
                    "The pork behind the wall was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                            + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                            + "; " + companion.watchDetail());
        });
    }

    /** Three stacks of the same kind: one order takes all of them and reports the total, not one stack. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void severalStacksOfOneKindAreTakenInOneOrder(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity first = item(h, 11, 1, 8, Items.ROTTEN_FLESH, 2);
        ItemEntity second = item(h, 12, 1, 10, Items.ROTTEN_FLESH, 2);
        ItemEntity third = item(h, 14, 1, 9, Items.ROTTEN_FLESH, 2);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(first.isRemoved() && second.isRemoved() && third.isRemoved()
                            && stored(companion, Items.ROTTEN_FLESH) == 6,
                    "Not all three stacks were taken: bag " + stored(companion, Items.ROTTEN_FLESH) + ", drops left "
                            + (!first.isRemoved() || !second.isRemoved() || !third.isRemoved()) + ", phase "
                            + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал гнилую плоть (6 шт.)"),
                    "The total is not reported: " + companion.getLastTaskResult());
        });
    }

    /** A drop 5.7 blocks away: outside a 4-block scan, inside a 32-block one, which then takes it. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aRadiusOfThirtyTwoFindsADropFiveBlocksAway(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity stick = item(h, 12, 1, 12, Items.STICK, 1);
        companion.setPickUpNearby(true, 4);
        var missed = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:stick", 1);
        h.assertTrue(!missed.success(), "A drop beyond the 4-block radius was taken: " + missed.message());
        h.assertTrue(missed.message().contains("нет палки"), "No honest answer: " + missed.message());
        h.assertTrue(!stick.isRemoved(), "The stick beyond the radius was taken");
        companion.setPickUpNearby(true, 32);
        var found = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:stick", 1);
        h.assertTrue(found.success(), "The 32-block radius did not take the order: " + found.message());
        h.succeedWhen(() -> h.assertTrue(stick.isRemoved() && stored(companion, Items.STICK) == 1,
                "The stick was not taken with the wider radius: bag " + stored(companion, Items.STICK)
                        + ", phase " + companion.workPhase()));
    }

    /** «Подбери всё» and a bare «подбери» take every drop lying around, whatever it is. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void everythingLyingAroundIsTakenOnPickUpAll(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.assertTrue("companion work pick_up 64 minecraft:any".equals(CompanionLocalCommands.route("подбери всё", "Олег")),
                "«Подбери всё»: " + CompanionLocalCommands.route("подбери всё", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:any".equals(CompanionLocalCommands.route("Олег, подбери", "Олег")),
                "Bare «подбери»: " + CompanionLocalCommands.route("Олег, подбери", "Олег"));
        // A noun after «все» still names one thing: «все алмазы» is the diamonds, not everything.
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("подбери все алмазы", "Олег")),
                "«Подбери все алмазы»: " + CompanionLocalCommands.route("подбери все алмазы", "Олег"));
        ItemEntity chicken = item(h, 11, 1, 8, Items.CHICKEN, 1);
        ItemEntity diamond = item(h, 12, 1, 9, Items.DIAMOND, 2);
        ItemEntity stick = item(h, 13, 1, 8, Items.STICK, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:any", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(chicken.isRemoved() && diamond.isRemoved() && stick.isRemoved()
                            && stored(companion, Items.CHICKEN) == 1 && stored(companion, Items.DIAMOND) == 2
                            && stored(companion, Items.STICK) == 1,
                    "Not everything lying around was taken: chicken " + stored(companion, Items.CHICKEN)
                            + ", diamonds " + stored(companion, Items.DIAMOND) + ", sticks " + stored(companion, Items.STICK)
                            + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал вещи (4 шт.)"),
                    "The total is not reported: " + companion.getLastTaskResult());
        });
    }

    /** «Подбери бобы»: a word the order does not know is the honest answer, not a hunt, though a chicken lies near. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void anUnknownRussianNounIsAnsweredHonestly(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity chicken = item(h, 11, 1, 8, Items.CHICKEN, 1);
        h.assertTrue(CompanionLocalCommands.route("подбери бобы", "Олег") == null,
                "An unknown noun was turned into an order: " + CompanionLocalCommands.route("подбери бобы", "Олег"));
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "бобы", 1);
        h.assertTrue(!result.success(), "An unknown noun reported success: " + result.message());
        h.assertTrue(result.message().contains("Не понял, что подобрать") && result.message().contains("бобы"),
                "No honest answer: " + result.message());
        h.assertTrue(companion.workPhase().isEmpty(), "A fallback started for an unknown noun: " + companion.workPhase());
        h.startSequence().thenIdle(20).thenExecute(() -> {
            h.assertTrue(!chicken.isRemoved(), "The chicken was taken by a hunt for an unknown noun");
            h.assertTrue(stored(companion, Items.CHICKEN) == 0, "A chicken appeared from nowhere");
        }).thenSucceed();
    }

    /** A drop the player threw, aimed at nobody: it lies on the ground like any other and is taken. */
    @GameTest(template = "large_search", timeoutTicks = 500)
    public static void aDropThrownByThePlayerIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ServerPlayer owner = player(h, companion);
        ItemEntity chicken = item(h, 11, 1, 8, Items.CHICKEN, 1);
        chicken.setThrower(owner);
        h.assertTrue(chicken.getOwner() == owner, "The drop is not the player's throw: " + chicken.getOwner());
        h.assertTrue(chicken.getTarget() == null, "The drop is aimed at somebody");
        var result = CompanionEvents.executeAction(owner, "pick_up", "minecraft:chicken", 1);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> h.assertTrue(chicken.isRemoved() && stored(companion, Items.CHICKEN) == 1,
                "The player's throw was not taken: bag " + stored(companion, Items.CHICKEN)
                        + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()));
    }

    /** «Подбери» while another order runs: refused with the deed named, the order is not lost — the repeat takes it. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aPickUpWhileAnotherOrderRunsIsRefusedNotLost(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity chicken = item(h, 11, 1, 8, Items.CHICKEN, 1);
        var come = CompanionEvents.executeAction(player(h, companion), "come", "", 1);
        h.assertTrue(come.success(), "The come order was refused: " + come.message());
        h.assertTrue(companion.hasActiveWorkOrder(), "No order was running");
        var busy = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
        h.assertTrue(!busy.success(), "A pickup during another order reported success: " + busy.message());
        h.assertTrue(busy.message().contains("Погоди") && busy.message().contains("потом"),
                "The refusal does not say why: " + busy.message());
        h.assertTrue(companion.hasActiveWorkOrder() && companion.workPhase().startsWith("COME"),
                "The running order was lost: " + companion.workPhase());
        h.assertTrue(!chicken.isRemoved(), "The drop was taken while the other order ran");
        h.startSequence().thenExecute(() -> CompanionEvents.executeAction(player(h, companion), "wait", "", 1))
          .thenExecute(() -> {
              h.assertTrue(!companion.hasActiveWorkOrder(), "The order did not stop: " + companion.workPhase());
              var again = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
              h.assertTrue(again.success(), "The repeated pickup was refused: " + again.message());
          }).thenWaitUntil(() -> h.assertTrue(chicken.isRemoved() && stored(companion, Items.CHICKEN) == 1,
                  "The order lost the drop: bag " + stored(companion, Items.CHICKEN) + ", phase " + companion.workPhase()
                          + ", " + companion.getLastTaskResult())).thenSucceed();
    }

    /** Every meat Kind answers with words; a meat the table does not know falls back to the animal's forms, not an NPE. */
    @GameTest(template = "large_search")
    public static void everyMeatKindHasWordsAndAnUnknownOneFallsBack(GameTestHelper h) {
        for (CompanionGame.Kind kind : CompanionGame.Kind.values()) {
            String id = BuiltInRegistries.ITEM.getKey(kind.meat).toString();
            var meaning = CompanionPickUp.of(id);
            h.assertTrue(meaning != null, "No meaning for the meat of " + kind + ": " + id);
            h.assertTrue(meaning.name() != null && !meaning.name().isBlank()
                            && meaning.accusative() != null && !meaning.accusative().isBlank()
                            && meaning.genitive() != null && !meaning.genitive().isBlank(),
                    "Empty words for " + kind + ": " + meaning.name() + "/" + meaning.accusative() + "/" + meaning.genitive());
            h.assertTrue(meaning.is(new ItemStack(kind.meat)), "The raw meat of " + kind + " is not its meaning");
            var cooked = BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(
                    "cooked_" + BuiltInRegistries.ITEM.getKey(kind.meat).getPath()));
            if (cooked.isPresent()) h.assertTrue(meaning.is(new ItemStack(cooked.get())),
                    "The cooked meat of " + kind + " is not its meaning");
        }
        // The table's words win when it knows the meat; an unknown meat uses the animal's own forms instead of throwing.
        String[] known = CompanionPickUp.meatWords(Items.BEEF, "корова", "корову", "коровы");
        h.assertTrue("говядина".equals(known[0]) && "говядину".equals(known[1]) && "говядины".equals(known[2]),
                "The table's words are not used: " + String.join("/", known));
        String[] unknown = CompanionPickUp.meatWords(Items.DIAMOND, "корова", "корову", "коровы");
        h.assertTrue("корова".equals(unknown[0]) && "корову".equals(unknown[1]) && "коровы".equals(unknown[2]),
                "The fallback is not the animal's own forms: " + String.join("/", unknown));
        h.succeed();
    }

    /** The honest list names a bucket of water by what it is: «ведро воды», not just «ведро». */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theHonestListNamesABucketOfWaterInFull(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.WATER_BUCKET, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 64);
        h.assertTrue(!result.success(), "A pickup with nothing of it near reported success");
        h.assertTrue(result.message().contains("Лежит рядом: ведро воды (1)"),
                "The bucket of water is named short: " + result.message());
        h.succeed();
    }

    /**
     * A drop in water one deep: it floats on the surface of that cell, the bottom of the pond and the surface are the same
     * cell, and the swimmer's stand there takes it.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void aDropInShallowWaterIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 1);
        ItemEntity pork = item(h, 13, 1, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1,
                "The pork in the shallow water was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                        + ", drop y " + pork.getY() + ", phase " + companion.workPhase() + ", "
                        + companion.getLastTaskResult() + "; " + companion.watchDetail()));
    }

    /** A drop on the water at the far side of the pool: he swims across and takes it. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aDropAtTheFarSideOfThePoolIsSwumTo(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 2);
        ItemEntity pork = floating(h, 16, 2, 8, Items.COOKED_PORKCHOP, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1,
                "The pork at the far side of the pool was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                        + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                        + "; " + companion.watchDetail()));
    }

    /**
     * A drop drifting two blocks with the current while he swims to it: the chase follows the drop itself instead of
     * giving it up as out of reach, and it is taken where it comes to rest.
     */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aDropDriftingWithTheCurrentIsStillTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 2);
        ItemEntity pork = drifting(h, 13, 2, 8, Items.COOKED_PORKCHOP, 1, 2, 0, 40);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1,
                "The drifting pork was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                        + ", drop at " + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", pork.getX(), pork.getY(), pork.getZ())
                        + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                        + "; " + companion.watchDetail()));
    }

    /**
     * A drop held on the water surface that drifts {@code dx} by {@code dz} blocks over {@code ticks}: the test pool's own
     * water is still, so the current is given to the drop itself while the companion swims to it.
     */
    private static ItemEntity drifting(GameTestHelper h, int x, int y, int z, Item item, int count, int dx, int dz, int ticks) {
        ItemEntity drop = item(h, x, y, z, item, count);
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        double px = at.getX() + .5, py = at.getY() + .6, pz = at.getZ() + .5;
        double stepX = dx / (double) ticks, stepZ = dz / (double) ticks;
        int[] drift = {0};
        h.onEachTick(() -> {
            if (drift[0] < ticks) drift[0]++;
            drop.setPos(px + stepX * drift[0], py, pz + stepZ * drift[0]);
        });
        return drop;
    }

    /**
     * A drop floating in the water at a spot: the test pool's water flows out over the flat floor and carries what floats
     * in it away, so the drop is held where it was put (a pond of still water holds it there just the same).
     */
    private static ItemEntity floating(GameTestHelper h, int x, int y, int z, Item item, int count) {
        ItemEntity drop = item(h, x, y, z, item, count);
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        double px = at.getX() + .5, py = at.getY() + .6, pz = at.getZ() + .5;
        drop.setPos(px, py, pz);
        h.onEachTick(() -> drop.setPos(px, py, pz));
        return drop;
    }

    /**
     * A pool in a stone bank as high as its water, with steps up to it: a pool laid on the flat floor drains into a shallow
     * puddle over it and carries what floats in it away with the flow, so the drop would not stay where the test put it. The
     * bank keeps the water deep and still; every step up is one block, as the companion can climb.
     */
    private static void pool(GameTestHelper h, int x, int z, int size, int depth) {
        for (int step = 0; step < depth; step++) {
            int margin = depth - step;
            for (int dx = -margin; dx < size + margin; dx++) for (int dz = -margin; dz < size + margin; dz++)
                for (int dy = 1; dy <= step + 1; dy++) h.setBlock(new BlockPos(x + dx, dy, z + dz), Blocks.STONE);
        }
        for (int dx = 0; dx < size; dx++) for (int dz = 0; dz < size; dz++) for (int dy = 1; dy <= depth; dy++)
            h.setBlock(new BlockPos(x + dx, dy, z + dz), Blocks.WATER);
    }

    /** A one-deep pit: a stone rim around an inner hole at the floor, as a player digs one and steps down into. */
    private static void pit(GameTestHelper h, int x, int z, int size) {
        for (int dx = -1; dx <= size; dx++) for (int dz = -1; dz <= size; dz++) {
            boolean rim = dx < 0 || dx >= size || dz < 0 || dz >= size;
            if (rim) h.setBlock(new BlockPos(x + dx, 1, z + dz), Blocks.STONE);
        }
    }

    private static ItemEntity item(GameTestHelper h, int x, int y, int z, Item item, int count) {
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        ItemEntity drop = new ItemEntity(h.getLevel(), at.getX() + .5, at.getY() + .2, at.getZ() + .5, new ItemStack(item, count));
        drop.setPickUpDelay(0);
        h.getLevel().addFreshEntity(drop);
        return drop;
    }

    private static int stored(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static void arena(GameTestHelper h) {
        // Wide enough that the water of the pool spreads over the floor and the drop never reaches its edge.
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        // Idle, so the loose pickup of the ground runs: while a work order runs the ground belongs to the player.
        companion.setFollowing(false);
        return companion;
    }
}
