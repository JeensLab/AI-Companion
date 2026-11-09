package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPickUpCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionGame;
import dev.aicompanion.entity.CompanionItemLookup;
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
import java.util.function.Function;

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
        h.assertTrue(result.success() && result.status() == CompanionActionResult.Status.ACCEPTED,
                "A pickup whose fallback started is not accepted: " + result.status() + " " + result.message());
        h.assertTrue(result.evidence() != null && result.evidence().workId() > 0,
                "The fallback work is not tracked by its workId: " + result.evidence());
        h.assertTrue(result.message().contains("нет предмета «minecraft:chicken»"), "No honest answer: " + result.message());
        h.assertTrue(result.message().contains("minecraft:cobblestone (3)"), "What lies around is not named: " + result.message());
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
            h.assertTrue(outcome.report().contains("нет предмета «minecraft:chicken»") && outcome.report().contains("minecraft:cobblestone (3)"),
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
        h.assertTrue(outOfReach.message().contains("нет предмета «minecraft:stick»"), "No honest answer: " + outOfReach.message());
        h.assertTrue(outOfReach.message().contains("minecraft:cobblestone (3)"), "What lies inside the radius is not named: " + outOfReach.message());
        // The answer must not call the stick beyond the radius «рядом» while saying there is no stick: the same radius for both.
        int listed = outOfReach.message().indexOf("Лежит рядом:");
        h.assertTrue(listed >= 0 && !outOfReach.message().substring(listed).contains("minecraft:stick"),
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
        h.assertTrue(result.success() && result.status() == CompanionActionResult.Status.ACCEPTED,
                "A pickup whose fallback started is not accepted: " + result.status() + " " + result.message());
        h.assertTrue(result.message().contains("нет брёвен"), "No honest answer: " + result.message());
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
        h.assertTrue(result.message().contains("Лежит рядом: «minecraft:chicken» (1)"), "The chicken lying there is not named: " + result.message());
        h.assertTrue(!result.message().contains("нет предмета «minecraft:chicken»"), "A full bag was answered as «nothing lies there»: " + result.message());
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
        h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("Олег, подбери вон ту курицу плавающую", "Олег", RU)),
                "Phrase: " + CompanionLocalCommands.route("Олег, подбери вон ту курицу плавающую", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("подбери алмазы", "Олег", RU)),
                "Gems: " + CompanionLocalCommands.route("подбери алмазы", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:oak_log".equals(CompanionLocalCommands.route("подбери брёвна", "Олег", RU)),
                "Logs: " + CompanionLocalCommands.route("подбери брёвна", "Олег", RU));
        // «Камень» is the stone block itself and the blocks a stone tool is made of: one group, taken as one.
        h.assertTrue("companion work pick_up 3 minecraft:stone".equals(CompanionLocalCommands.route("подбери 3 камня", "Олег", RU)),
                "Stone with a number: " + CompanionLocalCommands.route("подбери 3 камня", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:rotten_flesh".equals(CompanionLocalCommands.route("подбери гниль в воде", "Олег", RU)),
                "Rotten flesh: " + CompanionLocalCommands.route("подбери гниль в воде", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:rotten_flesh".equals(CompanionLocalCommands.route("подбери гнилую плоть", "Олег", RU)),
                "Rotten flesh: " + CompanionLocalCommands.route("подбери гнилую плоть", "Олег", RU));
        // The word is the game's own name in the language it runs in; a test server runs en_us, and so does this route.
        h.assertTrue("companion work pick_up 64 minecraft:spyglass".equals(CompanionLocalCommands.route("подбери Spyglass", "Олег")),
                "The game's own name: " + CompanionLocalCommands.route("подбери Spyglass", "Олег"));
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
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:rotten_flesh» (3 шт.)"),
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
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:rotten_flesh» (1 шт.), осталось 2 шт."),
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
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:rotten_flesh» (1 шт.), осталось 1 шт."),
                    "The piece left is not counted: " + companion.getLastTaskResult() + "; drops " + first.isRemoved() + "/" + second.isRemoved()
                            + " " + first.getItem() + " " + second.getItem() + "; " + companion.watchDetail());
            h.assertTrue(!companion.getLastTaskResult().contains("Не смог подобрать"),
                    "A partial pickup was reported as a failure: " + companion.getLastTaskResult());
            h.assertTrue(!first.isRemoved() || !second.isRemoved(), "The leftover piece disappeared");
        }).thenExecute(() -> {
            companion.setFollowing(false);
            var again = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:rotten_flesh", 64);
            h.assertTrue(!again.success(), "A pickup into a full bag reported success");
            h.assertTrue(again.message().contains("«minecraft:rotten_flesh» (1)") && again.message().contains("сумка полна"),
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
        h.assertTrue("companion work pick_up 64 minecraft:bucket".equals(CompanionLocalCommands.route("подбери ведро", "Олег", RU)),
                "Phrase: " + CompanionLocalCommands.route("подбери ведро", "Олег", RU));
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
        h.assertTrue("companion work pick_up 64 minecraft:ink_sac".equals(CompanionLocalCommands.route("подбери чернила", "Олег", RU)),
                "Phrase: " + CompanionLocalCommands.route("подбери чернила", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:ink_sac".equals(CompanionLocalCommands.route("подбери чернильный мешок", "Олег", RU)),
                "Phrase: " + CompanionLocalCommands.route("подбери чернильный мешок", "Олег", RU));
        pool(h, 12, 6, 5, 2);
        ItemEntity sac = floating(h, 13, 2, 8, Items.INK_SAC, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:ink_sac", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(sac.isRemoved() && stored(companion, Items.INK_SAC) == 1 && companion.workPhase().isEmpty(),
                    "The ink sac in the water was not taken: phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:ink_sac» (1 шт.)"),
                    "The report does not call it an ink sac: " + companion.getLastTaskResult());
        });
    }

    /** «Жареная свинина» and «свининка» are the cooked pork lying in the water: taken, and no hunt starts. */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void cookedPorkInTheWaterIsTakenInsteadOfHunted(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.assertTrue("companion work pick_up 64 minecraft:porkchop".equals(CompanionLocalCommands.route("подбери свинину жареную", "Олег", RU)),
                "Phrase: " + CompanionLocalCommands.route("подбери свинину жареную", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:porkchop".equals(CompanionLocalCommands.route("жареная свининка в воде кусок подбери", "Олег", RU)),
                "Trailing verb: " + CompanionLocalCommands.route("жареная свининка в воде кусок подбери", "Олег", RU));
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
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:porkchop» (1 шт.)"),
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
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:chicken» (4 шт.)"),
                    "The report uses the wrong case: " + companion.getLastTaskResult());
        });
    }

    /** The honest list names what lies around by the game's ids: the client says their names in the player's language. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theHonestListNamesThingsInRussian(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.WATER_BUCKET, 1);
        item(h, 11, 1, 9, Items.DIAMOND_PICKAXE, 1);
        item(h, 12, 1, 10, Items.ROTTEN_FLESH, 2);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 64);
        h.assertTrue(result.success() && result.status() == CompanionActionResult.Status.ACCEPTED,
                "A pickup whose fallback started is not accepted: " + result.status() + " " + result.message());
        h.assertTrue(result.message().contains("нет предмета «minecraft:chicken»"), "No honest answer: " + result.message());
        h.assertTrue(result.message().contains("minecraft:water_bucket (1)"), "The bucket is not named: " + result.message());
        h.assertTrue(result.message().contains("minecraft:diamond_pickaxe (1)"), "The pickaxe is not named: " + result.message());
        h.assertTrue(result.message().contains("minecraft:rotten_flesh (2)"), "The rotten flesh is not named: " + result.message());
        h.assertTrue(!result.message().contains("Water Bucket") && !result.message().contains("Diamond Pickaxe")
                        && !result.message().contains("Rotten Flesh"),
                "The list names the server's English names: " + result.message());
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
        long work = companion.activeWorkId();
        h.succeedWhen(() -> {
            h.assertTrue(pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 1 && companion.workPhase().isEmpty(),
                    "The pork at the bottom of the deep pool was not taken: bag " + stored(companion, Items.COOKED_PORKCHOP)
                            + ", drop removed " + pork.isRemoved() + ", drop y " + pork.getY() + ", phase "
                            + companion.workPhase() + ", " + companion.getLastTaskResult() + "; " + companion.watchDetail()
                            + "; " + String.join(" | ", trail));
            // The order's own result, not the last status: the chase's «Подбираю добытые вещи.» overwrites the status
            // after the order ends, and the pork may be taken by the loose pickup just after it (judge, round 2).
            var recorded = companion.workResult(work);
            h.assertTrue(recorded != null && recorded.success() && recorded.message().contains("Подобрал предмет «minecraft:porkchop» (1 шт.)"),
                    "The order's own result is wrong: " + recorded + "; last status " + companion.getLastTaskResult());
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
            h.assertTrue(companion.getLastTaskResult().contains("Не достал предмет «minecraft:porkchop», осталось 1 шт."),
                    "The leftover is not counted: " + companion.getLastTaskResult());
            h.assertTrue(!companion.getLastTaskResult().contains("Не смог подобрать"),
                    "A drop still lying was reported as «не смог подобрать»: " + companion.getLastTaskResult());
        });
    }

    /**
     * A drop floating in a flooded pocket under a stone roof: its cell has no stand (the body does not fit under the
     * roof) and no cell beside it is within reach, so there is no way to it at all. It bobs on the water every tick;
     * that bob is no progress of the chase by itself, so the attempts count and the order ends with «Не достал свинину,
     * осталось 1 шт.» instead of swimming the whole minute and saying «Не успел подобрать» (judge, round 2).
     */
    @GameTest(template = "large_search", timeoutTicks = 1400)
    public static void aBobbingDropWithNoStandIsGivenUpHonestly(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        // A flooded pocket under a stone roof, walled in two high: its cell has no stand (the body does not fit under
        // the roof), and the nearest stand beside it is on the wall top, too high to reach. Four blocks from him, so the
        // missing way is the chase's own business and no route recovery is started over it.
        h.setBlock(new BlockPos(12, 1, 8), Blocks.WATER);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 2; dy++)
            if (dx != 0 || dz != 0) h.setBlock(new BlockPos(12 + dx, dy, 8 + dz), Blocks.STONE);
        h.setBlock(new BlockPos(12, 2, 8), Blocks.STONE);
        ItemEntity pork = bobbing(h, 12, 1, 8, Items.COOKED_PORKCHOP, 1);
        long[] work = {0};
        h.startSequence().thenExecute(() -> {
            var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
            h.assertTrue(result.success(), "The order was refused: " + result.message());
            work[0] = companion.activeWorkId();
        }).thenWaitUntil(() -> h.assertTrue(companion.workPhase().isEmpty(),
                "The order never ended: phase " + companion.workPhase() + " at tick " + h.getTick()
                        + ", " + companion.workResult(work[0]) + "; " + companion.watchDetail()))
          .thenExecute(() -> {
              var recorded = companion.workResult(work[0]);
              h.assertTrue(recorded != null && recorded.message().contains("Не достал предмет «minecraft:porkchop», осталось 1 шт."),
                      "The drop was not reported as left: " + recorded + "; last status " + companion.getLastTaskResult());
              h.assertTrue(!recorded.message().contains("Не успел подобрать"), "The order swam the whole minute: " + recorded);
              h.assertTrue(!pork.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 0,
                      "The pork was taken through the roof");
          }).thenSucceed();
    }

    /**
     * Several drops of one kind in the water with one sealed in a stone pocket beside the pool: the two in the water are
     * taken and the one he cannot reach is counted — «Подобрал свинину (2 шт.), осталось 1 шт.» (game 08.11: only the
     * sealed dry pocket covered a leftover count).
     */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void aPoolWithOneSealedDropCountsTheLeftover(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 2);
        ItemEntity first = floating(h, 13, 2, 8, Items.COOKED_PORKCHOP, 1);
        ItemEntity second = floating(h, 14, 2, 8, Items.COOKED_PORKCHOP, 1);
        // A sealed dry pocket on the floor beside the pool: walled in on every side and roofed over, so there is no
        // stand within reach of the drop in it.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) h.setBlock(new BlockPos(19 + dx, 1, 8 + dz), Blocks.STONE);
        h.setBlock(new BlockPos(19, 2, 8), Blocks.STONE);
        ItemEntity sealed = item(h, 19, 1, 8, Items.COOKED_PORKCHOP, 1);
        long[] work = {0};
        h.startSequence().thenExecute(() -> {
            var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:porkchop", 64);
            h.assertTrue(result.success(), "The order was refused: " + result.message());
            work[0] = companion.activeWorkId();
        }).thenWaitUntil(() -> h.assertTrue(companion.workPhase().isEmpty(),
                "The order never ended: phase " + companion.workPhase() + ", bag " + stored(companion, Items.COOKED_PORKCHOP)
                        + ", " + companion.workResult(work[0])))
          .thenExecute(() -> {
              var recorded = companion.workResult(work[0]);
              h.assertTrue(first.isRemoved() && second.isRemoved() && stored(companion, Items.COOKED_PORKCHOP) == 2,
                      "The two in the water were not taken: bag " + stored(companion, Items.COOKED_PORKCHOP) + ", drops "
                              + first.isRemoved() + "/" + second.isRemoved() + ", " + recorded);
              h.assertTrue(!sealed.isRemoved(), "The sealed pork was taken through the stone");
              h.assertTrue(recorded != null && recorded.message().contains("Подобрал предмет «minecraft:porkchop» (2 шт.), осталось 1 шт."),
                      "The leftover is not counted: " + recorded + "; last status " + companion.getLastTaskResult());
          }).thenSucceed();
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
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:rotten_flesh» (6 шт.)"),
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
        h.assertTrue(missed.message().contains("нет предмета «minecraft:stick»"), "No honest answer: " + missed.message());
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
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("подбери все алмазы", "Олег", RU)),
                "«Подбери все алмазы»: " + CompanionLocalCommands.route("подбери все алмазы", "Олег", RU));
        ItemEntity chicken = item(h, 11, 1, 8, Items.CHICKEN, 1);
        ItemEntity diamond = item(h, 12, 1, 9, Items.DIAMOND, 2);
        ItemEntity stick = item(h, 13, 1, 8, Items.STICK, 1);
        // A spyglass is in no list of ours: «всё» takes it as any other drop.
        ItemEntity spyglass = item(h, 11, 1, 9, Items.SPYGLASS, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:any", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(chicken.isRemoved() && diamond.isRemoved() && stick.isRemoved() && spyglass.isRemoved()
                            && stored(companion, Items.CHICKEN) == 1 && stored(companion, Items.DIAMOND) == 2
                            && stored(companion, Items.STICK) == 1 && stored(companion, Items.SPYGLASS) == 1,
                    "Not everything lying around was taken: chicken " + stored(companion, Items.CHICKEN)
                            + ", diamonds " + stored(companion, Items.DIAMOND) + ", sticks " + stored(companion, Items.STICK)
                            + ", spyglass " + stored(companion, Items.SPYGLASS)
                            + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал вещи (5 шт.)"),
                    "The total is not reported: " + companion.getLastTaskResult());
        });
    }

    /** «Подбери всё», a bare «подбери», «все предметы», «все плавающие предметы которые лежат»: every drop, locally. */
    @GameTest(template = "large_search")
    public static void everyItemWordRoutesToTheLocalPickup(GameTestHelper h) {
        CompanionPickUpCommands.forget();
        for (String phrase : List.of("подбери", "Олег, подбери", "подбери всё", "подбери все предметы",
                "подбери все плавающие предметы которые лежат", "подбери все лежащие предметы",
                "подбери всё, что лежит на полу", "подбери все вещи в воде")) {
            String routed = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue("companion work pick_up 64 minecraft:any".equals(routed), "Phrase: «" + phrase + "» → " + routed);
        }
        // A noun after «все» still names one thing.
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("подбери все алмазы", "Олег", RU)),
                "«Подбери все алмазы»: " + CompanionLocalCommands.route("подбери все алмазы", "Олег", RU));
        h.succeed();
    }

    /** «Ещё» right after a pickup takes the rest of that same thing; another phrase ends that window. */
    @GameTest(template = "large_search")
    public static void moreRepeatsTheLastPickup(GameTestHelper h) {
        CompanionPickUpCommands.forget();
        h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("Олег, подбери всю плавающую курицу", "Олег", RU)),
                "The first pickup: " + CompanionLocalCommands.route("Олег, подбери всю плавающую курицу", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("ещё", "Олег")),
                "«Ещё» after chicken: " + CompanionLocalCommands.route("ещё", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("ещё подбери", "Олег")),
                "«Ещё подбери»: " + CompanionLocalCommands.route("ещё подбери", "Олег"));
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("подбери алмаз", "Олег", RU)),
                "The diamond pickup: " + CompanionLocalCommands.route("подбери алмаз", "Олег", RU));
        h.assertTrue("companion work pick_up 64 minecraft:diamond".equals(CompanionLocalCommands.route("ещё", "Олег")),
                "«Ещё» after the diamond: " + CompanionLocalCommands.route("ещё", "Олег"));
        h.assertTrue(CompanionLocalCommands.route("стой", "Олег") != null, "«Стой» is not a local order");
        h.assertTrue(CompanionLocalCommands.route("ещё", "Олег") == null,
                "«Ещё» after another order still went to the pickup: " + CompanionLocalCommands.route("ещё", "Олег"));
        h.succeed();
    }

    /** The live case: «подбери курицу» takes one, «ещё» takes the rest of the chicken and never starts a hunt. */
    @GameTest(template = "large_search", timeoutTicks = 1200)
    public static void moreTakesTheRestOfTheLastPickup(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity first = item(h, 12, 1, 8, Items.CHICKEN, 1);
        ItemEntity second = item(h, 17, 1, 8, Items.CHICKEN, 1);
        boolean[] hunted = {false};
        String[] more = {null};
        h.onEachTick(() -> { if (companion.workPhase().startsWith("GAME")) hunted[0] = true; });
        CompanionPickUpCommands.forget();
        h.startSequence().thenExecute(() -> {
            h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("Олег, подбери курицу", "Олег", RU)),
                    "The first pickup: " + CompanionLocalCommands.route("Олег, подбери курицу", "Олег", RU));
            // «Ещё» is routed in the same breath as the order it repeats: another GameTest shares this static memory.
            more[0] = CompanionLocalCommands.route("ещё", "Олег");
            h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(more[0]),
                    "«Ещё» did not repeat the last pickup: " + more[0]);
            var one = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 1);
            h.assertTrue(one.success(), "The first order was refused: " + one.message());
        }).thenWaitUntil(() -> h.assertTrue(stored(companion, Items.CHICKEN) == 1 && companion.workPhase().isEmpty(),
                "The first chicken was not taken: bag " + stored(companion, Items.CHICKEN) + ", phase " + companion.workPhase()))
          .thenExecute(() -> {
              PlannedAction step = CompanionLocalCommands.action(more[0]);
              var rest = CompanionEvents.executeAction(player(h, companion), step.type(), step.item(), step.count());
              h.assertTrue(rest.success(), "The repeated pickup was refused: " + rest.message());
          }).thenWaitUntil(() -> h.assertTrue(stored(companion, Items.CHICKEN) == 2 && first.isRemoved() && second.isRemoved()
                  && companion.workPhase().isEmpty(),
                  "«Ещё» did not take the rest of the chicken: bag " + stored(companion, Items.CHICKEN) + ", drops "
                          + first.isRemoved() + "/" + second.isRemoved() + ", phase " + companion.workPhase()
                          + ", " + companion.getLastTaskResult()))
          .thenExecute(() -> h.assertTrue(!hunted[0], "«Ещё» fell back to hunting instead of taking the rest"))
          .thenSucceed();
    }

    /** The model's snapshot lists what lies around him, in Russian, with counts — the answer to «что лежит на полу?». */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theSnapshotListsTheDropsLyingAround(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        pool(h, 12, 6, 5, 2);
        floating(h, 13, 2, 8, Items.CHICKEN, 2);
        floating(h, 14, 2, 8, Items.DIAMOND, 1);
        floating(h, 13, 2, 9, Items.WATER_BUCKET, 1);
        String list = CompanionPickUp.nearby(h.getLevel(), companion.position(), 16);
        h.assertTrue(list.contains(new ItemStack(Items.CHICKEN).getHoverName().getString() + " (2)"), "The chicken is not listed: " + list);
        h.assertTrue(list.contains(new ItemStack(Items.DIAMOND).getHoverName().getString() + " (1)"), "The diamond is not listed: " + list);
        h.assertTrue(list.contains(new ItemStack(Items.WATER_BUCKET).getHoverName().getString() + " (1)"), "The bucket is not listed: " + list);
        // The names are the game's own in the caller's language: a Russian client reads «Курица», the test server «Raw Chicken».
        h.assertTrue(!list.contains("minecraft:"), "The list leaks raw ids instead of the game's names: " + list);
        h.assertTrue("ничего не лежит".equals(CompanionPickUp.nearby(h.getLevel(), new net.minecraft.world.phys.Vec3(0, 100, 0), 4)),
                "An empty spot is not said as empty");
        h.succeed();
    }

    /** A diamond floating in water five blocks away is found by the scan and taken, like any other drop there. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aDiamondFloatingInWaterIsFoundAndTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 2);
        ItemEntity diamond = floating(h, 13, 2, 8, Items.DIAMOND, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:diamond", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> h.assertTrue(diamond.isRemoved() && stored(companion, Items.DIAMOND) == 1 && companion.workPhase().isEmpty(),
                "The diamond in the water was not taken: bag " + stored(companion, Items.DIAMOND) + ", drop removed "
                        + diamond.isRemoved() + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                        + "; " + companion.watchDetail()));
    }

    /** A diamond under the surface of a shallow pool floats up and is taken, not given up as out of reach. */
    @GameTest(template = "large_search", timeoutTicks = 900)
    public static void aDiamondBelowTheSurfaceOfAShallowPoolIsFoundAndTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        pool(h, 12, 6, 5, 2);
        ItemEntity diamond = item(h, 12, 1, 8, Items.DIAMOND, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:diamond", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.succeedWhen(() -> h.assertTrue(diamond.isRemoved() && stored(companion, Items.DIAMOND) == 1 && companion.workPhase().isEmpty(),
                "The diamond under the surface was not taken: bag " + stored(companion, Items.DIAMOND) + ", drop y "
                        + diamond.getY() + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()
                        + "; " + companion.watchDetail()));
    }

    /** A rejected model plan for a «подбери» phrase falls back to the local pickup, and the codec accepts «any drop». */
    @GameTest(template = "large_search")
    public static void aRejectedPlanForPickUpFallsBackLocally(GameTestHelper h) {
        PlannedAction fallback = CompanionPickUpCommands.fallback("подбери все плавающие предметы которые лежат", "Олег");
        h.assertTrue(fallback != null && "pick_up".equals(fallback.type()) && CompanionPickUp.ANY_ITEM.equals(fallback.item())
                        && fallback.count() == 64,
                "The fallback is not the whole ground: " + fallback);
        // A noun this order does not know still becomes a pickup of the ground, not a red error.
        fallback = CompanionPickUpCommands.fallback("подбери бобы", "Олег");
        h.assertTrue(fallback != null && CompanionPickUp.ANY_ITEM.equals(fallback.item()), "An unknown noun got no fallback: " + fallback);
        h.assertTrue(CompanionPickUpCommands.fallback("принеси курицу", "Олег") == null, "«Принеси» is not the pickup");
        h.assertTrue(CompanionPickUpCommands.fallback("подбери из сундука камень", "Олег") == null, "A container take is not the pickup");
        h.assertTrue(CompanionPlanCodec.rejected(new IllegalStateException("Модель передала некорректный план. Никакие его шаги не запущены; повтори просьбу.")),
                "A rejected plan is not recognised");
        h.assertTrue(CompanionPlanCodec.rejected(new java.util.concurrent.CompletionException(
                        new IllegalStateException("Модель передала некорректный план. Никакие его шаги не запущены; повтори просьбу."))),
                "A rejected plan wrapped in a failed future is not recognised");
        h.assertTrue(!CompanionPlanCodec.rejected(new IllegalStateException("Сервер модели не отвечает: проверь, что он запущен.")),
                "A transport failure was taken for a rejected plan");
        h.assertTrue(!CompanionPlanCodec.rejected(new IllegalStateException("Модель не ответила за 90 секунд. Попробуй ещё раз.")),
                "A timeout was taken for a rejected plan");
        var steps = CompanionPlanCodec.parsePlan(com.google.gson.JsonParser.parseString("[{\"action\":\"pick_up\",\"count\":64}]"));
        h.assertTrue(steps.size() == 1 && CompanionPickUp.ANY_ITEM.equals(steps.getFirst().item()),
                "A pick_up without an item is not the whole ground: " + steps);
        h.succeed();
    }

    /**
     * The PR #133 remark: «подбери» with nothing of it near starts its fallback work, and the following step of the same
     * plan must still run. The honest answer stays in the report, the fallback's own outcome follows it, and the step is
     * not repeated as «companion busy».
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void thePlanGoesOnAfterAPickupFallsBackToHunting(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.COBBLESTONE, 3);
        var plan = CompanionPlanRunner.run(List.of(
                        new PlannedAction("pick_up", 0, 1, "minecraft:chicken"),
                        new PlannedAction("wait", 1, 1)),
                new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-pickup-fallback-plan", () -> false));
        h.succeedWhen(() -> {
            h.assertTrue(plan.isDone() && !plan.isCompletedExceptionally(), "The plan is still running: " + companion.getLastTaskResult());
            var outcome = plan.join();
            h.assertTrue(outcome.skipped().isEmpty(), "The following step was skipped: " + outcome.skipped() + "; " + outcome.report());
            h.assertTrue(outcome.recoveries() == 0 && !outcome.report().contains("Повтор шага"),
                    "The fallback step was repeated: " + outcome.report());
            h.assertTrue(outcome.report().contains("нет предмета «minecraft:chicken»") && outcome.report().contains("minecraft:cobblestone (3)"),
                    "The honest answer is not in the report: " + outcome.report());
            h.assertTrue(outcome.report().contains("Иду охотиться") && outcome.report().contains("Никого не добыл"),
                    "The fallback work is not reported: " + outcome.report());
            h.assertTrue(outcome.report().contains("Остановился и жду."), "The second step did not run: " + outcome.report());
            // What the player hears: the pickup's own facts and the hunt's outcome first, then the rest of the plan.
            String spoken = CompanionDialogue.summarize(List.of(
                    new PlannedAction("pick_up", 0, 1, "minecraft:chicken"),
                    new PlannedAction("wait", 1, 1)), outcome.report());
            h.assertTrue(spoken.toLowerCase(java.util.Locale.ROOT).contains("нет предмета"),
                    "The honest answer is not heard: " + spoken);
            h.assertTrue(spoken.contains("Иду охотиться"), "The fallback is not heard: " + spoken);
            h.assertTrue(spoken.contains("Никого не добыл"), "The hunt's outcome is not heard: " + spoken);
            h.assertTrue(spoken.contains("Остановился и жду."), "The rest of the plan is not heard: " + spoken);
            // The pickup's own facts come first, the rest of the plan after them: not only present, but in that order.
            int honest = spoken.toLowerCase(java.util.Locale.ROOT).indexOf("нет предмета");
            int fallback = spoken.indexOf("Иду охотиться");
            int hunt = spoken.indexOf("Никого не добыл");
            int rest = spoken.indexOf("Остановился и жду.");
            h.assertTrue(honest >= 0 && fallback >= 0 && hunt >= 0 && rest >= 0 && honest < fallback && fallback < hunt && hunt < rest,
                    "The pickup's facts are not before the rest of the plan: " + spoken);
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

    /** Every meat Kind is one meaning, raw and cooked, named by the game's own id — no table of ours in the way. */
    @GameTest(template = "large_search")
    public static void everyMeatKindIsItsRawAndCookedDrop(GameTestHelper h) {
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
        // The name is the game's id, which the client says in the player's language; a thing with no meat pair is itself.
        h.assertTrue("«minecraft:beef»".equals(CompanionPickUp.of("minecraft:beef").name()),
                "The beef is not named by its id: " + CompanionPickUp.of("minecraft:beef").name());
        var plain = CompanionPickUp.of("minecraft:netherite_ingot");
        h.assertTrue(plain != null && plain.is(new ItemStack(Items.NETHERITE_INGOT)) && plain.fallbackAction() == null,
                "A plain item is not itself: " + plain);
        h.succeed();
    }

    /**
     * Judge finding 1: a word names every form of the thing, not the single tie the resolver put first. «Железо» is
     * the ore, the raw drop, the ingot and the block; «уголь», «золото» and «медь» take their own forms, and
     * «чернила» both ink sacs.
     */
    @GameTest(template = "large_search")
    public static void aWordTakesEveryFormOfTheThing(GameTestHelper h) {
        var iron = CompanionPickUp.of(CompanionItemLookup.itemFor("железо", RU));
        h.assertTrue(iron != null, "«Железо» has no meaning at all");
        for (Item form : List.of(Items.IRON_ORE, Items.RAW_IRON, Items.IRON_INGOT, Items.IRON_BLOCK))
            h.assertTrue(iron.is(new ItemStack(form)), "«Железо» does not take " + BuiltInRegistries.ITEM.getKey(form) + ": " + iron);
        var coal = CompanionPickUp.of(CompanionItemLookup.itemFor("уголь", RU));
        h.assertTrue(coal != null && coal.is(new ItemStack(Items.COAL)) && coal.is(new ItemStack(Items.COAL_BLOCK)),
                "«Уголь» does not take the coal block: " + coal);
        var gold = CompanionPickUp.of(CompanionItemLookup.itemFor("золото", RU));
        h.assertTrue(gold != null && gold.is(new ItemStack(Items.GOLD_ORE)) && gold.is(new ItemStack(Items.GOLD_INGOT))
                        && gold.is(new ItemStack(Items.GOLD_BLOCK)),
                "«Золото» does not take the ore, the ingot and the block: " + gold);
        var copper = CompanionPickUp.of(CompanionItemLookup.itemFor("медь", RU));
        h.assertTrue(copper != null && copper.is(new ItemStack(Items.COPPER_ORE)) && copper.is(new ItemStack(Items.COPPER_INGOT))
                        && copper.is(new ItemStack(Items.COPPER_BLOCK)),
                "«Медь» does not take the ore, the ingot and the block: " + copper);
        var ink = CompanionPickUp.of(CompanionItemLookup.itemFor("чернила", RU));
        h.assertTrue(ink != null && ink.is(new ItemStack(Items.INK_SAC)) && ink.is(new ItemStack(Items.GLOW_INK_SAC)),
                "«Чернила» does not take the glow ink sac: " + ink);
        h.succeed();
    }

    /** Judge finding 1: an iron ore and an iron block lying near are both taken by the one order «подбери железо» names. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void everyFormOfIronLyingNearIsTaken(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity ore = item(h, 11, 1, 8, Items.IRON_ORE, 2);
        ItemEntity block = item(h, 12, 1, 9, Items.IRON_BLOCK, 1);
        String iron = CompanionItemLookup.itemFor("железо", RU);
        h.assertTrue(iron != null, "«Железо» was not resolved by the registry");
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", iron, 64);
        h.assertTrue(result.success(), "The iron order was refused: " + result.message());
        h.succeedWhen(() -> h.assertTrue(ore.isRemoved() && block.isRemoved() && stored(companion, Items.IRON_ORE) == 2
                        && stored(companion, Items.IRON_BLOCK) == 1,
                "The iron forms were not all taken: ore " + stored(companion, Items.IRON_ORE) + ", block "
                        + stored(companion, Items.IRON_BLOCK) + ", phase " + companion.workPhase() + ", " + companion.getLastTaskResult()));
    }

    /**
     * Judge follow-up: a word that names one form is that form alone. «Угольный блок» is the block and no loose coal,
     * «светящийся чернильный мешок» the glow one and no plain ink sac, «железная руда» the ore and no ingot — while the
     * plainest form of the thing still names the whole family.
     */
    @GameTest(template = "large_search")
    public static void aWordNamingOneFormIsOnlyThatForm(GameTestHelper h) {
        String block = CompanionItemLookup.itemFor("угольный блок", RU);
        h.assertTrue("minecraft:coal_block".equals(block), "«Угольный блок» is not the block: " + block);
        var coalBlock = CompanionPickUp.of(block);
        h.assertTrue(coalBlock != null && coalBlock.is(new ItemStack(Items.COAL_BLOCK)), "The coal block is not itself: " + coalBlock);
        h.assertTrue(!coalBlock.is(new ItemStack(Items.COAL)), "The coal block took the loose coal: " + coalBlock);
        String glow = CompanionItemLookup.itemFor("светящийся чернильный мешок", RU);
        h.assertTrue("minecraft:glow_ink_sac".equals(glow), "«Светящийся чернильный мешок» is not the glow sac: " + glow);
        var glowSac = CompanionPickUp.of(glow);
        h.assertTrue(glowSac != null && glowSac.is(new ItemStack(Items.GLOW_INK_SAC)), "The glow ink sac is not itself: " + glowSac);
        h.assertTrue(!glowSac.is(new ItemStack(Items.INK_SAC)), "The glow ink sac took the plain ink sac: " + glowSac);
        String ore = CompanionItemLookup.itemFor("железную руду", RU);
        h.assertTrue("minecraft:iron_ore".equals(ore), "«Железную руду» is not the ore: " + ore);
        var ironOre = CompanionPickUp.of(ore);
        h.assertTrue(ironOre != null && ironOre.is(new ItemStack(Items.IRON_ORE)), "The iron ore is not itself: " + ironOre);
        h.assertTrue(!ironOre.is(new ItemStack(Items.IRON_INGOT)) && !ironOre.is(new ItemStack(Items.IRON_BLOCK)),
                "The iron ore took the ingot or the block: " + ironOre);
        // The plainest form is still the whole family: «уголь» takes the block, «железо» the ore and the ingot.
        var coal = CompanionPickUp.of(CompanionItemLookup.itemFor("уголь", RU));
        h.assertTrue(coal != null && coal.is(new ItemStack(Items.COAL)) && coal.is(new ItemStack(Items.COAL_BLOCK)),
                "«Уголь» stopped taking the block: " + coal);
        var iron = CompanionPickUp.of(CompanionItemLookup.itemFor("железо", RU));
        h.assertTrue(iron != null && iron.is(new ItemStack(Items.IRON_ORE)) && iron.is(new ItemStack(Items.IRON_INGOT)),
                "«Железо» stopped taking the ore and the ingot: " + iron);
        h.succeed();
    }

    /**
     * Lapis lazuli is the plainest form of lapis, though the game's id carries the «_lazuli» affix: «лазурит» is the
     * loose drop, the ore, the deepslate ore and the block, while «лазуритовая руда» is the ore alone — as is the ore's
     * own id, whatever word named it.
     */
    @GameTest(template = "large_search")
    public static void aWordTakesEveryFormOfLapis(GameTestHelper h) {
        String lapis = CompanionItemLookup.itemFor("лазурит", RU);
        h.assertTrue("minecraft:lapis_lazuli".equals(lapis), "«Лазурит» is not the loose lapis: " + lapis);
        var meaning = CompanionPickUp.of(lapis);
        h.assertTrue(meaning != null, "«Лазурит» has no meaning at all");
        for (Item form : List.of(Items.LAPIS_LAZULI, Items.LAPIS_ORE, Items.LAPIS_BLOCK, Items.DEEPSLATE_LAPIS_ORE))
            h.assertTrue(meaning.is(new ItemStack(form)),
                    "«Лазурит» does not take " + BuiltInRegistries.ITEM.getKey(form) + ": " + meaning);
        String ore = CompanionItemLookup.itemFor("лазуритовая руда", RU);
        h.assertTrue("minecraft:lapis_ore".equals(ore), "«Лазуритовая руда» is not the ore: " + ore);
        var oreOnly = CompanionPickUp.of(ore);
        h.assertTrue(oreOnly != null && oreOnly.is(new ItemStack(Items.LAPIS_ORE)), "The lapis ore is not itself: " + oreOnly);
        h.assertTrue(!oreOnly.is(new ItemStack(Items.LAPIS_LAZULI)) && !oreOnly.is(new ItemStack(Items.LAPIS_BLOCK))
                        && !oreOnly.is(new ItemStack(Items.DEEPSLATE_LAPIS_ORE)),
                "«Лазуритовая руда» took another form of lapis: " + oreOnly);
        var byId = CompanionPickUp.of("minecraft:lapis_ore");
        h.assertTrue(byId != null && byId.is(new ItemStack(Items.LAPIS_ORE)) && !byId.is(new ItemStack(Items.LAPIS_LAZULI)),
                "The lapis ore narrowed to the loose lapis: " + byId);
        h.succeed();
    }

    /**
     * A family with no suffix-free form is no family: «незеритовый слиток» is the ingot alone, though the ingot and the
     * block tie in length and the block comes second in the registry — and the block word is the block alone.
     */
    @GameTest(template = "large_search")
    public static void aNetheriteIngotOrderTakesOnlyIngots(GameTestHelper h) {
        String ingot = CompanionItemLookup.itemFor("незеритовый слиток", RU);
        h.assertTrue("minecraft:netherite_ingot".equals(ingot), "«Незеритовый слиток» is not the ingot: " + ingot);
        var meaning = CompanionPickUp.of(ingot);
        h.assertTrue(meaning != null && meaning.is(new ItemStack(Items.NETHERITE_INGOT)),
                "The netherite ingot is not itself: " + meaning);
        h.assertTrue(!meaning.is(new ItemStack(Items.NETHERITE_BLOCK)),
                "«Незеритовый слиток» took the netherite block: " + meaning);
        String block = CompanionItemLookup.itemFor("незеритовый блок", RU);
        h.assertTrue("minecraft:netherite_block".equals(block), "«Незеритовый блок» is not the block: " + block);
        var blockOnly = CompanionPickUp.of(block);
        h.assertTrue(blockOnly != null && blockOnly.is(new ItemStack(Items.NETHERITE_BLOCK))
                        && !blockOnly.is(new ItemStack(Items.NETHERITE_INGOT)),
                "«Незеритовый блок» took the netherite ingot: " + blockOnly);
        h.succeed();
    }

    /** «Подбери угольный блок» takes the block lying there and leaves the loose coal four blocks on. */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void theCoalBlockOrderLeavesTheLooseCoal(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        h.assertTrue("companion work pick_up 64 minecraft:coal_block".equals(CompanionLocalCommands.route("подбери угольный блок", "Олег", RU)),
                "Phrase: " + CompanionLocalCommands.route("подбери угольный блок", "Олег", RU));
        ItemEntity block = item(h, 11, 1, 8, Items.COAL_BLOCK, 1);
        // Four blocks from the block: out of the two-block loose pickup, so only the order could take it.
        ItemEntity coal = item(h, 15, 1, 8, Items.COAL, 3);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:coal_block", 64);
        h.assertTrue(result.success(), "The order was refused: " + result.message());
        h.assertTrue(companion.workPhase().startsWith("PICK_UP"), "Not the pickup: " + companion.workPhase());
        h.succeedWhen(() -> {
            h.assertTrue(block.isRemoved() && stored(companion, Items.COAL_BLOCK) == 1 && companion.workPhase().isEmpty(),
                    "The coal block was not taken: bag " + stored(companion, Items.COAL_BLOCK) + ", phase " + companion.workPhase()
                            + ", " + companion.getLastTaskResult());
            h.assertTrue(!coal.isRemoved() && stored(companion, Items.COAL) == 0,
                    "The loose coal was taken for the coal block: coal " + stored(companion, Items.COAL) + ", drop removed "
                            + coal.isRemoved() + ", " + companion.getLastTaskResult());
        });
    }

    /**
     * Judge follow-up: the affixes that build another form of a material merge no unrelated things. «Кирпич» is no
     * nether brick and «рамка» no glowing frame, while «чернила» is still both ink sacs.
     */
    @GameTest(template = "large_search")
    public static void anAffixIsNoOtherThing(GameTestHelper h) {
        var brick = CompanionPickUp.of(CompanionItemLookup.itemFor("кирпич", RU));
        h.assertTrue(brick != null && brick.is(new ItemStack(Items.BRICK)), "«Кирпич» is not the brick: " + brick);
        h.assertTrue(!brick.is(new ItemStack(Items.NETHER_BRICK)), "«Кирпич» took a nether brick: " + brick);
        var frame = CompanionPickUp.of(CompanionItemLookup.itemFor("рамка", RU));
        h.assertTrue(frame != null && frame.is(new ItemStack(Items.ITEM_FRAME)), "«Рамка» is not the frame: " + frame);
        h.assertTrue(!frame.is(new ItemStack(Items.GLOW_ITEM_FRAME)), "«Рамка» took a glowing frame: " + frame);
        var slab = CompanionPickUp.of(CompanionItemLookup.itemFor("кирпичная плита", RU));
        h.assertTrue(slab != null && slab.is(new ItemStack(Items.BRICK_SLAB)), "«Кирпичная плита» is not the brick slab: " + slab);
        h.assertTrue(!slab.is(new ItemStack(Items.DEEPSLATE_BRICK_SLAB)), "«Кирпичная плита» took the deepslate brick slab: " + slab);
        var ink = CompanionPickUp.of(CompanionItemLookup.itemFor("чернила", RU));
        h.assertTrue(ink != null && ink.is(new ItemStack(Items.INK_SAC)) && ink.is(new ItemStack(Items.GLOW_INK_SAC)),
                "«Чернила» stopped taking both ink sacs: " + ink);
        h.succeed();
    }

    /** Judge finding 2: a mod's fried meat is its own raw meat, not the game's item of the same path. */
    @GameTest(template = "large_search")
    public static void aModsCookedMeatKeepsItsNamespace(GameTestHelper h) {
        var modded = ResourceLocation.fromNamespaceAndPath("mymod", "cooked_beef");
        h.assertTrue(ResourceLocation.fromNamespaceAndPath("mymod", "beef").equals(CompanionItemLookup.uncooked(modded)),
                "A mod's cooked beef was renamed into another namespace: " + CompanionItemLookup.uncooked(modded));
        h.assertTrue(ResourceLocation.withDefaultNamespace("beef").equals(
                        CompanionItemLookup.uncooked(ResourceLocation.withDefaultNamespace("cooked_beef"))),
                "The game's cooked beef is not its raw one");
        h.succeed();
    }

    /** Judge finding 3: the model's answer takes the exact id it named, not the shorter id inside it. */
    @GameTest(template = "large_search")
    public static void theModelsAnswerTakesTheExactDrop(GameTestHelper h) {
        var ask = new CompanionPickUpCommands.Ask("уголь", 64);
        var drops = List.of(new CompanionPickUp.Drop("minecraft:coal", "Уголь", 1),
                new CompanionPickUp.Drop("minecraft:coal_block", "Угольный блок", 1));
        var exact = CompanionPickUpCommands.chosen(ask, drops, "minecraft:coal_block");
        h.assertTrue(exact != null && "minecraft:coal_block".equals(exact.item()), "«coal_block» was read as coal: " + exact);
        var coal = CompanionPickUpCommands.chosen(ask, drops, "minecraft:coal");
        h.assertTrue(coal != null && "minecraft:coal".equals(coal.item()), "«coal» was not taken: " + coal);
        var sentence = CompanionPickUpCommands.chosen(ask, drops, "Думаю, это minecraft:coal_block.");
        h.assertTrue(sentence != null && "minecraft:coal_block".equals(sentence.item()), "The longer id in a sentence lost to coal: " + sentence);
        var byName = CompanionPickUpCommands.chosen(ask, drops, "Угольный блок");
        h.assertTrue(byName != null && "minecraft:coal_block".equals(byName.item()), "The longer name lost to coal: " + byName);
        h.succeed();
    }

    /** Judge finding 4: a gift, junk he let go and another player's drop are not in the model's list. */
    @GameTest(template = "large_search")
    public static void theModelsListLeavesOutWhatTheOrderWouldRefuse(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ServerPlayer owner = player(h, companion);
        item(h, 11, 1, 8, Items.CHICKEN, 1);
        ItemEntity gift = item(h, 12, 1, 8, Items.DIAMOND, 1);
        gift.getPersistentData().putBoolean("aicompanion:gift", true);
        ItemEntity junk = item(h, 12, 1, 9, Items.STICK, 1);
        junk.getPersistentData().putBoolean("aicompanion:junk", true);
        ItemEntity aimed = item(h, 11, 1, 9, Items.COBBLESTONE, 1);
        aimed.setTarget(java.util.UUID.randomUUID());
        var drops = CompanionPickUp.drops(h.getLevel(), companion.position(), 16, owner.getUUID());
        h.assertTrue(drops.size() == 1 && "minecraft:chicken".equals(drops.getFirst().id()),
                "The list is not just the chicken: " + drops);
        h.succeed();
    }

    /** Judge finding 1: the tasks window and «Не выполнено» say «вещи», not the raw «minecraft:any». */
    @GameTest(template = "large_search")
    public static void thePickUpAllStepNamesTheGroundInWords(GameTestHelper h) {
        PlannedAction all = new PlannedAction("pick_up", 0, 64, CompanionPickUp.ANY_ITEM);
        String step = CompanionDialogue.describe(all);
        h.assertTrue(step.contains("вещи") && !step.contains("minecraft:any"), "The tasks window shows the raw id: " + step);
        String skipped = CompanionDialogue.describeAll(List.of(all, new PlannedAction("wait", 1, 1)));
        h.assertTrue(skipped.contains("вещи") && !skipped.contains("minecraft:any"), "«Не выполнено» shows the raw id: " + skipped);
        h.succeed();
    }

    /** Judge finding 3: «ещё» with filler words around it still repeats the last pickup. */
    @GameTest(template = "large_search")
    public static void moreTakesTheRestAfterFillerWords(GameTestHelper h) {
        CompanionPickUpCommands.forget();
        h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(CompanionLocalCommands.route("подбери курицу", "Олег", RU)),
                "The first pickup: " + CompanionLocalCommands.route("подбери курицу", "Олег", RU));
        for (String phrase : List.of("ещё, пожалуйста", "ещё чуть-чуть", "ещё подбери всё", "давай ещё", "ну ещё подбери", "ещё немного")) {
            String routed = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue("companion work pick_up 64 minecraft:chicken".equals(routed), "Phrase: «" + phrase + "» → " + routed);
        }
        h.succeed();
    }

    /** Judge finding 4: the snapshot scans the radius from the settings, and says nothing at all with the setting off. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theSnapshotUsesTheCompanionsOwnRadiusAndSwitch(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        // The stick lies about 5.7 blocks away: beyond a 4-block scan, inside a 16-block one.
        item(h, 12, 1, 12, Items.STICK, 1);
        companion.setPickUpNearby(true, 4);
        String narrow = CompanionPickUp.snapshotLine(h.getLevel(), companion.position(), companion);
        h.assertTrue(narrow.contains("на полу рядом=") && !narrow.contains(new ItemStack(Items.STICK).getHoverName().getString()),
                "The line ignores the 4-block radius: " + narrow);
        companion.setPickUpNearby(true, 16);
        String wide = CompanionPickUp.snapshotLine(h.getLevel(), companion.position(), companion);
        h.assertTrue(wide.contains(new ItemStack(Items.STICK).getHoverName().getString() + " (1)"),
                "The wider radius is not used: " + wide);
        companion.setPickUpNearby(false, 16);
        h.assertTrue(CompanionPickUp.snapshotLine(h.getLevel(), companion.position(), companion).isEmpty(),
                "The line is said with the ground pickup switched off");
        h.succeed();
    }

    /**
     * The registry is the list: an amethyst shard and a spyglass are in no hand-written table, yet «осколок» and
     * «подзорная труба» find them by the names a Russian client shows, and the order takes them off the ground.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void theGamesOwnRegistryNamesItemsNoListKnows(GameTestHelper h) {
        h.assertTrue("minecraft:amethyst_shard".equals(CompanionItemLookup.itemFor("осколок аметиста", RU)),
                "The shard is not resolved by its name: " + CompanionItemLookup.itemFor("осколок аметиста", RU));
        h.assertTrue("minecraft:spyglass".equals(CompanionItemLookup.itemFor("подзорную трубу", RU)),
                "The spyglass is not resolved by its name: " + CompanionItemLookup.itemFor("подзорную трубу", RU));
        h.assertTrue("minecraft:amethyst_shard".equals(CompanionItemLookup.itemFor("Amethyst Shard", CompanionItemLookup.GAME_NAMES)),
                "The shard is not resolved by the game's own name");
        h.assertTrue("minecraft:spyglass".equals(CompanionItemLookup.itemFor("Spyglass", CompanionItemLookup.GAME_NAMES)),
                "The spyglass is not resolved by the game's own name");
        var shard = CompanionPickUp.of("minecraft:amethyst_shard");
        h.assertTrue(shard != null && shard.is(new ItemStack(Items.AMETHYST_SHARD)) && shard.fallbackAction() == null,
                "The shard has no meaning of its own: " + shard);
        var spyglass = CompanionPickUp.of("minecraft:spyglass");
        h.assertTrue(spyglass != null && spyglass.is(new ItemStack(Items.SPYGLASS)), "The spyglass has no meaning of its own: " + spyglass);

        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        ItemEntity shardDrop = item(h, 12, 1, 8, Items.AMETHYST_SHARD, 2);
        h.assertTrue("companion work pick_up 64 minecraft:amethyst_shard".equals(CompanionLocalCommands.route("подбери осколки", "Олег", RU)),
                "«Осколки»: " + CompanionLocalCommands.route("подбери осколки", "Олег", RU));
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:amethyst_shard", 64);
        h.assertTrue(result.success(), "The shard order was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(shardDrop.isRemoved() && stored(companion, Items.AMETHYST_SHARD) == 2,
                    "The shard was not taken: bag " + stored(companion, Items.AMETHYST_SHARD) + ", phase " + companion.workPhase()
                            + ", " + companion.getLastTaskResult());
            h.assertTrue(companion.getLastTaskResult().contains("Подобрал предмет «minecraft:amethyst_shard» (2 шт.)"),
                    "The report does not name the shard: " + companion.getLastTaskResult());
        });
    }

    /**
     * «Кура» is no name of the registry («курица» is), so the model is asked which of the drops lying here the player
     * meant: it may pick only one of them, and the order takes exactly that. «Тухлое мясо» is the rotten flesh lying
     * there; a pick outside the list and free text are no pick at all.
     */
    @GameTest(template = "large_search", timeoutTicks = 600)
    public static void theModelPicksWhichDropThePlayerMeant(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.CHICKEN, 2);
        item(h, 11, 1, 9, Items.ROTTEN_FLESH, 1);
        var drops = CompanionPickUp.drops(h.getLevel(), companion.position(), 16, companion.getOwnerId());
        h.assertTrue(drops.size() == 2, "The drops are not listed: " + drops);
        // The registry does not answer «кура»: a four-letter word needs its whole stem, and «кур» is not «куриц».
        h.assertTrue(CompanionItemLookup.itemFor("кура", RU) == null, "«Кура» was resolved by the registry after all");
        h.assertTrue("minecraft:chicken".equals(CompanionItemLookup.itemFor("курицу", RU)), "«Курицу» is not the registry's chicken");
        var ask = CompanionPickUpCommands.ask("подбери куру", "Олег", RU);
        h.assertTrue(ask != null && "куру".equals(ask.noun()), "«Куру» did not become a question: " + ask);
        String prompt = CompanionPickUpCommands.prompt(ask, drops);
        h.assertTrue(prompt.contains("minecraft:chicken") && prompt.contains("minecraft:rotten_flesh"),
                "The question does not list the drops lying here: " + prompt);
        h.assertTrue(prompt.contains(new ItemStack(Items.CHICKEN).getHoverName().getString()),
                "The question does not name the drops as the game does: " + prompt);
        var chicken = CompanionPickUpCommands.chosen(ask, drops, "minecraft:chicken");
        h.assertTrue(chicken != null && "minecraft:chicken".equals(chicken.item()) && chicken.count() == 64,
                "The model's pick is lost: " + chicken);
        var fleshAsk = CompanionPickUpCommands.ask("подбери тухлое мясо", "Олег", RU);
        h.assertTrue(fleshAsk != null && "тухлое мясо".equals(fleshAsk.noun()), "«Тухлое мясо» did not become a question: " + fleshAsk);
        var flesh = CompanionPickUpCommands.chosen(fleshAsk, drops, "minecraft:rotten_flesh");
        h.assertTrue(flesh != null && "minecraft:rotten_flesh".equals(flesh.item()), "The model's pick is lost: " + flesh);
        // Nothing outside the list and no free text: a pick of something not lying here is no pick.
        h.assertTrue(CompanionPickUpCommands.chosen(fleshAsk, drops, "minecraft:diamond") == null,
                "An item that does not lie here was picked");
        h.assertTrue(CompanionPickUpCommands.chosen(fleshAsk, drops, "наверное, что-то мясное") == null,
                "Free text was taken for a pick");
        // The chosen drop is taken, and no hunt starts over it.
        boolean[] hunted = {false};
        h.onEachTick(() -> { if (companion.workPhase().startsWith("GAME")) hunted[0] = true; });
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", chicken.item(), chicken.count());
        h.assertTrue(result.success(), "The picked drop was refused: " + result.message());
        h.succeedWhen(() -> {
            h.assertTrue(stored(companion, Items.CHICKEN) == 2, "The chicken was not taken: " + companion.getLastTaskResult());
            h.assertTrue(!hunted[0], "A hunt started over the chicken lying there");
        });
    }

    /** The honest list names a bucket of water by its own id: «ведро воды», not the bare bucket. */
    @GameTest(template = "large_search", timeoutTicks = 300)
    public static void theHonestListNamesABucketOfWaterInFull(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        companion.setPickUpNearby(true, 16);
        item(h, 12, 1, 8, Items.WATER_BUCKET, 1);
        var result = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:chicken", 64);
        h.assertTrue(result.success() && result.status() == CompanionActionResult.Status.ACCEPTED,
                "A pickup whose fallback started is not accepted: " + result.status() + " " + result.message());
        h.assertTrue(result.message().contains("Лежит рядом: minecraft:water_bucket (1)"),
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
     * A drop floating on the water under a roof, bobbing on the surface: it moves more than a twentieth of a block every
     * tick, as a floating drop does, so the chase's route to it is stale every tick. The position is pinned tick by tick
     * so the bob is the same in every run.
     */
    private static ItemEntity bobbing(GameTestHelper h, int x, int y, int z, Item item, int count) {
        ItemEntity drop = item(h, x, y, z, item, count);
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        double px = at.getX() + .5, py = at.getY() + .55, pz = at.getZ() + .5;
        h.onEachTick(() -> drop.setPos(px, py + (h.getTick() % 2 == 0 ? .07 : -.07), pz));
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

    /**
     * Judge finding 6: the Russian stand-in is not a table of ours that drifts from the real translation — every name
     * it gives is read from the shipped ru_ru lang file the client reads, and checked against it here.
     */
    @GameTest(template = "large_search")
    public static void theRussianNamesAreTheShippedOnes(GameTestHelper h) {
        java.util.Map<String, String> lang = shippedRussianLang();
        int checked = 0;
        for (var entry : RU_NAMES.entrySet()) {
            var id = ResourceLocation.withDefaultNamespace(entry.getKey());
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            h.assertTrue(item != null, "No item «" + id + "» for the stand-in name «" + entry.getValue() + "»");
            String shipped = lang.get("item.minecraft." + entry.getKey());
            if (shipped == null) shipped = lang.get("block.minecraft." + entry.getKey());
            h.assertTrue(shipped != null, "The shipped ru_ru has no name for «" + id + "»");
            h.assertTrue(entry.getValue().equals(shipped),
                    "The stand-in drifted from ru_ru for «" + id + "»: «" + entry.getValue() + "» is «" + shipped + "»");
            h.assertTrue(entry.getValue().equals(russianName(item)), "The resolver does not read the stand-in for «" + id + "»");
            checked++;
        }
        h.assertTrue(checked >= 2, "Too few names checked against the shipped lang: " + checked);
        h.succeed();
    }

    /**
     * The names a Russian client shows, as the game's own ru_ru lang file gives them. The GameTest server runs en_us,
     * so these stand in for the player's language: the resolver is the same one the client runs, only the names come
     * from here instead of the client's lang. Every one of them is checked against the shipped ru_ru file
     * ({@link #theRussianNamesAreTheShippedOnes}), so a drift from the real translation fails a test.
     */
    private static final java.util.Map<String, String> RU_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("chicken", "Сырая курица"),
            java.util.Map.entry("cooked_chicken", "Жареная курица"),
            java.util.Map.entry("porkchop", "Сырая свинина"),
            java.util.Map.entry("cooked_porkchop", "Жареная свинина"),
            java.util.Map.entry("rotten_flesh", "Гнилая плоть"),
            java.util.Map.entry("diamond", "Алмаз"),
            java.util.Map.entry("amethyst_shard", "Осколок аметиста"),
            java.util.Map.entry("spyglass", "Подзорная труба"),
            java.util.Map.entry("bucket", "Ведро"),
            java.util.Map.entry("water_bucket", "Ведро воды"),
            java.util.Map.entry("ink_sac", "Чернильный мешок"),
            java.util.Map.entry("glow_ink_sac", "Светящийся чернильный мешок"),
            java.util.Map.entry("oak_log", "Дубовое бревно"),
            java.util.Map.entry("birch_log", "Берёзовое бревно"),
            java.util.Map.entry("stone", "Камень"),
            java.util.Map.entry("cobblestone", "Булыжник"),
            java.util.Map.entry("stick", "Палка"),
            java.util.Map.entry("oak_planks", "Дубовые доски"),
            java.util.Map.entry("white_wool", "Белая шерсть"),
            // The forms of the ores and metals the word names as one thing.
            java.util.Map.entry("iron_ore", "Железная руда"),
            java.util.Map.entry("raw_iron", "Рудное железо"),
            java.util.Map.entry("iron_ingot", "Железный слиток"),
            java.util.Map.entry("iron_block", "Железный блок"),
            java.util.Map.entry("coal", "Уголь"),
            java.util.Map.entry("coal_block", "Угольный блок"),
            java.util.Map.entry("gold_ore", "Золотая руда"),
            java.util.Map.entry("raw_gold", "Рудное золото"),
            java.util.Map.entry("gold_ingot", "Золотой слиток"),
            java.util.Map.entry("gold_block", "Золотой блок"),
            java.util.Map.entry("copper_ore", "Медная руда"),
            java.util.Map.entry("raw_copper", "Рудная медь"),
            java.util.Map.entry("copper_ingot", "Медный слиток"),
            java.util.Map.entry("copper_block", "Медный блок"),
            // Lapis is named by its own drop, though every form of it carries an affix in the game's id.
            java.util.Map.entry("lapis_lazuli", "Лазурит"),
            java.util.Map.entry("lapis_ore", "Лазуритовая руда"),
            java.util.Map.entry("deepslate_lapis_ore", "Глубинносланцевая лазуритовая руда"),
            java.util.Map.entry("lapis_block", "Лазуритовый блок"),
            // The metal with no suffix-free form: the ingot and the block are each themselves.
            java.util.Map.entry("netherite_ingot", "Незеритовый слиток"),
            java.util.Map.entry("netherite_block", "Незеритовый блок"),
            // The things whose id carries an affix that is no form of them: «кирпич» is no nether brick, «рамка» no
            // glowing frame.
            java.util.Map.entry("brick", "Кирпич"),
            java.util.Map.entry("nether_brick", "Незерский кирпич"),
            java.util.Map.entry("item_frame", "Рамка"),
            java.util.Map.entry("glow_item_frame", "Светящаяся рамка"),
            java.util.Map.entry("brick_slab", "Кирпичная плита"),
            java.util.Map.entry("deepslate_brick_slab", "Плита из глубинносланцевого кирпича"));

    private static final Function<Item, String> RU = CompanionPickUpTests::russianName;

    private static String russianName(Item item) {
        String name = RU_NAMES.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        return name != null ? name : new ItemStack(item).getHoverName().getString();
    }

    /** The shipped Russian lang file the client reads, as a map of keys to the player's words. */
    private static java.util.Map<String, String> shippedRussianLang() {
        try (var stream = CompanionPickUpTests.class.getResourceAsStream("/assets/minecraft/lang/ru_ru.json")) {
            if (stream == null) throw new IllegalStateException("The shipped ru_ru.json is not on the classpath");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            java.util.Map<String, String> lang = new java.util.HashMap<>();
            for (var entry : json.getAsJsonObject().entrySet()) lang.put(entry.getKey(), entry.getValue().getAsString());
            return lang;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
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
