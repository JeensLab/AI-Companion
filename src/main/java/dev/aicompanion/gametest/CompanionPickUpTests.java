package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

    /** Only stone around: the honest answer names it and nothing is taken for the chicken. */
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
        h.succeedWhen(() -> {
            h.assertTrue(stored(companion, Items.CHICKEN) == 0, "A chicken appeared from nowhere");
            h.assertTrue(!stone.isRemoved(), "The stone was taken for the chicken");
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

    /** The scan stops at the radius from the settings; a wider radius takes the same drop. */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void theScanStopsAtTheRadius(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity stick = item(h, 12, 1, 12, Items.STICK, 1);
        companion.setPickUpNearby(true, 4);
        var outOfReach = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:stick", 1);
        h.assertTrue(!outOfReach.success(), "A drop beyond the radius was taken: " + outOfReach.message());
        h.assertTrue(outOfReach.message().contains("нет палки"), "No honest answer: " + outOfReach.message());
        h.assertTrue(outOfReach.message().contains("Лежит рядом"), "What lies around is not named: " + outOfReach.message());
        h.startSequence().thenIdle(20).thenExecute(() -> {
            h.assertTrue(!stick.isRemoved(), "The stick beyond the radius was taken");
            companion.setPickUpNearby(true, 16);
            var inReach = CompanionEvents.executeAction(player(h, companion), "pick_up", "minecraft:stick", 1);
            h.assertTrue(inReach.success(), "The wider radius did not take the order: " + inReach.message());
        }).thenWaitUntil(() -> h.assertTrue(stored(companion, Items.STICK) == 1 && stick.isRemoved(),
                "The stick was not taken with the wider radius: " + companion.workPhase())).thenSucceed();
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
        h.assertTrue(CompanionLocalCommands.route("принеси мне курицу", "Олег") == null, "«Принеси» is not the pickup");
        h.assertTrue(CompanionLocalCommands.route("подбери курицу и брёвна", "Олег") == null, "Two things at once are a plan");
        PlannedAction step = CompanionLocalCommands.action("companion work pick_up 64 minecraft:chicken");
        h.assertTrue("pick_up".equals(step.type()) && step.count() == 64 && "minecraft:chicken".equals(step.item()), "Step: " + step);
        h.succeed();
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
