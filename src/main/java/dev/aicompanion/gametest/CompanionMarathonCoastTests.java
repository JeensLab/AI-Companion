package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Marathon arena «берег»: a mainland without a single tree in the south, a beach, a sandbar of shallow water (one block deep)
 * running north from the beach, four blocks of deep water after it, and an island with four oaks. Wood is only on the island;
 * the player walks the sandbar and swims to it, and back.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarathonCoastTests {
    static final int X = 96, Y = 28, Z = 96, SEA = 7;

    static void coast(GameTestHelper h) {
        var t = new MarathonTerrain(h, X, Y, Z);
        t.frame(14);
        var sand = Blocks.SAND.defaultBlockState();
        for (int x = 1; x < X - 1; x++) for (int z = 1; z < Z - 1; z++) {
            double island = Math.hypot(x - 48, z - 30);
            int top;
            var surface = sand;
            if (z >= 64) {
                top = 8 + Math.min(5, (z - 64) / 3) + (int) Math.round(MarathonTerrain.hills(x, z, .8, 1.3));
                if (z >= 68) surface = Blocks.GRASS_BLOCK.defaultBlockState();
            } else if (x >= 45 && x <= 50 && z >= 44) top = SEA - 1;              // the sandbar: wade, head above the water
            else if (island <= 10) {
                top = island > 7 ? 8 : island < 4 ? 10 : 9;
                if (island <= 7) surface = Blocks.GRASS_BLOCK.defaultBlockState();
            } else {
                top = 2 + (int) Math.round(MarathonTerrain.hills(x, z, .7, 4.1));
                if ((x * 7 + z * 3) % 5 == 0) surface = Blocks.GRAVEL.defaultBlockState();
            }
            t.column(x, z, top, 3, surface);
            for (int y = top + 1; y <= SEA; y++) t.set(x, y, z, Blocks.WATER);
        }
        int[][] oaks = {{45, 27}, {51, 28}, {47, 34}, {52, 33}};
        for (int[] oak : oaks) t.oak(oak[0], t.top(oak[0], oak[1], Y - 1) + 1, oak[1], 5, false);
    }

    /** «Добудь дерева» with trees only on the island; «иди за мной» over the sandbar and the deep water; «иди ко мне» from the island. */
    @GameTest(template = "marathon_coast", batch = "marathon", timeoutTicks = 16000)
    public static void woodFromTheIslandAndAWalkThroughTheWater(GameTestHelper h) {
        coast(h);
        var a = new MarathonArena(h, X, Y, Z, 43, 76, 40, 76, 48);
        var watch = a.watch;
        a.script
                .ask("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":8}]", 20 * 300)
                .check("wood from the island", () -> {
                    a.expect(a.countTag(ItemTags.LOGS) >= 8, "Wood not in the bag: " + a.oleg.getStoredItemsSummary() + " || " + a.chat.history());
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(10);
                    watch.assertNoRescue();
                    watch.assertNoTeleport();
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 60)
                .order("Иди за мной")
                .then("the player wades the sandbar and swims to the island", () -> a.walkPlayer(.2, 47, 64, 47, 46, 48, 40, 48, 31), a::playerArrived, 20 * 60)
                .waitUntil("caught up on the island", () -> a.toPlayer() < 8, 20 * 40)
                .check("followed through the water", () -> { watch.assertNoRescue(); watch.assertNoLongIdle(10); watch.assertNoTeleport(); })
                .order("Стой")
                .then("the player goes back to the mainland", () -> a.walkPlayer(.2, 48, 40, 47, 46, 47, 64, 40, 76), a::playerArrived, 20 * 60)
                .pause(100)
                .check("stayed on the island", () -> a.expect(a.toPlayer() > 20, "Did not stay: " + a.toPlayer()))
                .ask("Иди ко мне", null, 20 * 90)
                .check("came from the island", () -> {
                    a.expect(a.chat.said.getLast().report().contains("рядом"), "No «рядом» || " + a.chat.history());
                    watch.assertWithPlayer(4);
                    watch.assertNoRescue();
                    watch.assertNoTeleport();
                });
        h.onEachTick(() -> {
            if (!a.oleg.isInWater() || h.getTick() % 10 != 0) return;
            var p = a.oleg.getNavigation().getPath();
            watch.note("swim hc=" + a.oleg.horizontalCollision + " fh=" + String.format("%.2f", a.oleg.getFluidHeight(net.minecraft.tags.FluidTags.WATER))
                    + (p == null || p.isDone() ? " nopath" : " next=" + watch.where(p.getNextNodePos()) + " end=" + watch.where(p.getEndNode().asBlockPos()) + (p.canReach() ? " full" : " part"))
                    + " " + a.oleg.workPhase() + " y=" + String.format("%.2f", a.oleg.getY()) + " g=" + a.oleg.onGround() + " zza=" + String.format("%.2f", a.oleg.zza) + " wanted=" + a.oleg.getMoveControl().hasWanted()
                    + " v=" + String.format("%.3f,%.3f", a.oleg.getDeltaMovement().x, a.oleg.getDeltaMovement().z));
        });
        a.script.run("coast-island-wood-and-walk", watch::summary);
    }
}
