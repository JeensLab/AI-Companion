package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Marathon arena «густой лес»: gentle ground under a forest of dark oaks (2×2 trunks, flat crowns that shut the light), tall
 * spruces, two giant jungle trees and bushy oaks whose leaves come down to the ground. A small clearing in the middle.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarathonForestTests {
    static final int X = 64, Y = 44, Z = 64;

    static void forest(GameTestHelper h) {
        var t = new MarathonTerrain(h, X, Y, Z);
        t.frame(12);
        for (int x = 1; x < X - 1; x++) for (int z = 1; z < Z - 1; z++)
            t.column(x, z, 6 + (int) Math.round(MarathonTerrain.hills(x, z, 1.2, 5.3)), 3, Blocks.GRASS_BLOCK.defaultBlockState());
        t.jungleGiant(18, t.top(18, 18, Y - 1) + 1, 18, 22);
        t.jungleGiant(44, t.top(44, 42, Y - 1) + 1, 42, 24);
        for (int gx = 4; gx < X - 4; gx += 6) for (int gz = 4; gz < Z - 4; gz += 6) {
            int x = gx + Math.floorMod(gx * 7 + gz * 13, 3) - 1, z = gz + Math.floorMod(gx * 11 + gz * 5, 3) - 1;
            if (Math.hypot(x - 32, z - 32) < 6 || Math.hypot(x - 19, z - 19) < 6 || Math.hypot(x - 45, z - 43) < 6) continue;
            int y = t.top(x, z, Y - 1) + 1;
            switch (Math.floorMod(gx * 3 + gz * 7, 5)) {
                case 0, 1 -> t.darkOak(x, y, z, 7 + Math.floorMod(gx + gz, 3));
                case 2 -> t.spruce(x, y, z, 13 + Math.floorMod(gx * gz, 6));
                case 3 -> t.oak(x, y, z, 5, true);
                default -> t.oak(x, y, z, 6, false);
            }
        }
    }

    /** «Добудь дерева» among tall trees and dense crowns, then «иди за мной» on a winding walk through the forest. */
    @GameTest(template = "marathon_forest", batch = "marathon", timeoutTicks = 16000)
    public static void woodInTheDenseForestAndAWalkThroughIt(GameTestHelper h) {
        forest(h);
        var a = new MarathonArena(h, X, Y, Z, 31, 31, 33, 33, 32);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        var watch = a.watch;
        a.script
                .ask("Олег, добудь дерева", "[{\"action\":\"gather_wood\",\"count\":16}]", 20 * 400)
                .check("wood from the tall trees", () -> {
                    a.expect(a.countTag(ItemTags.LOGS) >= 16, "Wood: " + a.countTag(ItemTags.LOGS) + " || " + a.chat.history());
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(12);
                    watch.assertStayedNear(30);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 60)
                .order("Иди за мной")
                .then("the player winds through the forest", () -> a.walkPlayer(.2, 12, 32, 10, 10, 50, 10, 54, 52, 32, 54), a::playerArrived, 20 * 120)
                .waitUntil("caught up", () -> a.toPlayer() < 8, 20 * 40)
                .check("followed through the forest", () -> { watch.assertNoRescue(); watch.assertNoLongIdle(10); watch.assertNoTeleport(); });
        a.script.run("forest-wood-and-walk", watch::summary);
    }
}
