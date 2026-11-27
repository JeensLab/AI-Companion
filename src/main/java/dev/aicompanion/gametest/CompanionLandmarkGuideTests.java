package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLandmarkGuideTests {
    private record Walk(CompanionEntity companion, ServerPlayer player, BlockPos viewpoint, BlockPos ore, String dimension) { }

    private static Walk arena(GameTestHelper h) {
        for (int x = 2; x <= 26; x++) for (int z = 10; z <= 14; z++) {
            h.setBlock(new BlockPos(x, 6, z), Blocks.SEA_LANTERN);
            for (int y = 7; y <= 10; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        var c = h.spawn(AICompanionMod.COMPANION.get(), 7, 7, 12);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() - 1, c.getY(), c.getZ());
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        var view = h.absolutePos(new BlockPos(17, 7, 12));
        var ore = new BlockPos(21, 8, 12);
        h.setBlock(ore, Blocks.IRON_ORE);
        String dimension = c.level().dimension().location().toString();
        c.landmarks().observe(dimension, "iron", h.absolutePos(ore), view, c.level().getGameTime());
        return new Walk(c, player, view, ore, dimension);
    }

    private static long show(GameTestHelper h, Walk w) {
        var order = CompanionEvents.executeAction(w.player(), "guide_landmark", "iron", 1);
        long id = w.companion().activeWorkId();
        h.assertTrue(order.status() == CompanionActionResult.Status.ACCEPTED && id > 0, order.message());
        return id;
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void guidesOnFootAndWaitsForPlayerToShowRememberedOre(GameTestHelper h) {
        Walk w = arena(h);
        long id = show(h, w);
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(w.companion().distanceToSqr(w.viewpoint().getCenter()) <= 3, "Has not walked to the viewpoint");
            h.assertTrue(w.companion().workResult(id) == null, "Reported arrival before player caught up");
        }).thenExecute(() -> w.player().teleportTo(w.companion().getX() - 1, w.companion().getY(), w.companion().getZ()))
                .thenWaitUntil(() -> {
                    var result = w.companion().workResult(id);
                    h.assertTrue(result != null && result.status() == CompanionActionResult.Status.SUCCESS
                            && result.message().contains("желез"), "The visible ore was not shown");
                }).thenSucceed();
    }

    @GameTest(template = "tall_tree", timeoutTicks = 300)
    public static void minedLandmarkIsCorrectedOnArrival(GameTestHelper h) {
        Walk w = arena(h);
        h.setBlock(w.ore(), Blocks.AIR);
        w.player().teleportTo(w.viewpoint().getX() + .5, w.viewpoint().getY(), w.viewpoint().getZ() + 1.5);
        long id = show(h, w);
        h.succeedWhen(() -> {
            var result = w.companion().workResult(id);
            h.assertTrue(result != null && result.status() == CompanionActionResult.Status.FAILED, "Old ore was reported as seen");
            h.assertTrue(w.companion().landmarks().recall(w.dimension(), "iron") == null, "The mined ore is still offered");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 80)
    public static void guideCanBeStoppedWithoutLosingItsLandmark(GameTestHelper h) {
        Walk w = arena(h);
        long id = show(h, w);
        CompanionEvents.executeAction(w.player(), "stop", "", 1);
        h.runAfterDelay(12, () -> {
            var result = w.companion().workResult(id);
            h.assertTrue(result != null && result.status() == CompanionActionResult.Status.CANCELLED, "Guide ignored stop");
            h.assertTrue(w.companion().landmarks().recall(w.dimension(), "iron") != null, "Stopping erased the place");
            h.succeed();
        });
    }
}
