package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Marathon arena «пещера»: a hilly meadow (surface about y 22) over solid stone. Under it, eight to ten blocks down, a dark cave:
 * chamber A in the north-west, a tunnel two blocks high east and then south with two dead-end branches, chamber B in the
 * south-east with iron and coal in its walls, and the only way out — a passage climbing a block a step to the surface in the east.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMarathonCaveTests {
    static final int X = 64, Y = 36, Z = 64, FLOOR = 13;

    static void cave(GameTestHelper h) {
        var t = new MarathonTerrain(h, X, Y, Z);
        t.frame(30);
        for (int x = 1; x < X - 1; x++) for (int z = 1; z < Z - 1; z++)
            t.column(x, z, 22 + (int) Math.round(MarathonTerrain.hills(x, z, 1.5, 2.9)), 3, Blocks.GRASS_BLOCK.defaultBlockState());
        var air = Blocks.AIR.defaultBlockState();
        t.fill(8, FLOOR, 8, 14, FLOOR + 2, 14, air);                       // chamber A
        t.fill(14, FLOOR, 11, 40, FLOOR + 1, 11, air);                     // tunnel east, two high
        t.fill(40, FLOOR, 11, 40, FLOOR + 1, 38, air);                     // tunnel south
        t.fill(28, FLOOR, 4, 28, FLOOR + 1, 11, air);                      // dead end north
        t.fill(32, FLOOR, 25, 40, FLOOR + 1, 25, air);                     // dead end west
        t.fill(36, FLOOR, 38, 46, FLOOR + 2, 46, air);                     // chamber B
        // The way out: a block up each step eastwards from chamber B to the meadow.
        for (int k = 0; k <= 12; k++) {
            int x = 47 + k, floor = FLOOR + k;
            int top = t.top(x, 42, Y - 1);
            if (floor > top) break;
            t.fill(x, floor, 42, x, Math.min(top, floor + 2), 42, air);
            if (floor + 2 >= top) t.fill(x, floor, 42, x, top, 42, air);
        }
        for (int[] ore : new int[][]{{46, FLOOR + 1, 40}, {46, FLOOR + 1, 41}, {36, FLOOR, 44}, {36, FLOOR + 1, 44}})
            t.set(ore[0], ore[1], ore[2], Blocks.IRON_ORE);
        for (int[] ore : new int[][]{{42, FLOOR + 1, 37}, {43, FLOOR + 1, 37}, {44, FLOOR + 1, 37}})
            t.set(ore[0], ore[1], ore[2], Blocks.COAL_ORE);
        // A rock outcrop with coal on the meadow by the cave mouth (the coal in the cave is out of one route search from the meadow,
        // see docs/verification-0.0.1-marathon.md).
        for (int dz = 0; dz < 2; dz++) { int top = t.top(61, 36 + dz, Y - 1); for (int y = 1; y <= 3; y++) t.set(61, top + y, 36 + dz, y == 2 ? Blocks.COAL_ORE : Blocks.STONE); }
        for (int[] oak : new int[][]{{20, 40}, {12, 50}, {50, 20}, {30, 52}, {56, 52}, {52, 49}, {60, 49}})
            t.oak(oak[0], t.top(oak[0], oak[1], Y - 1) + 1, oak[1], 5, false);
    }

    /** Called from the far end of the cave: out through the tunnels (or up, the way a player would dig) to the player above. */
    @GameTest(template = "marathon_cave", batch = "marathon", timeoutTicks = 8000)
    public static void outOfTheCaveToThePlayer(GameTestHelper h) {
        cave(h);
        var a = new MarathonArena(h, X, Y, Z, 10, 10, 12, 20, 32);
        // He is down in chamber A, not on the meadow where the arena put him.
        var below = h.absoluteVec(a.spot(10.5, FLOOR, 10.5));
        a.oleg.teleportTo(below.x, below.y, below.z);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        var watch = a.watch;
        a.script
                .ask("Иди ко мне", null, 20 * 150)
                .check("came out of the cave", () -> {
                    a.expect(a.chat.said.getLast().report().contains("рядом"), "No «рядом» || " + a.chat.history());
                    watch.assertWithPlayer(4);
                    watch.assertNoLongIdle(10);
                    watch.assertNoCircling(40);
                    watch.assertNoTeleport();
                });
        a.script.run("cave-come-out", watch::summary);
    }

    /** «Сделай факелы» with an empty bag: wood from the meadow, a pickaxe, coal from the cave, the torches. */
    @GameTest(template = "marathon_cave", batch = "marathon", timeoutTicks = 20000)
    public static void torchesFromAnEmptyBag(GameTestHelper h) {
        cave(h);
        var a = new MarathonArena(h, X, Y, Z, 54, 46, 56, 47, 32);
        var watch = a.watch;
        a.script
                .ask("Сделай факелы", "[{\"action\":\"obtain_item\",\"item\":\"minecraft:torch\",\"count\":8}]", 20 * 800)
                .check("torches in the bag", () -> {
                    a.expect(a.count(Items.TORCH) >= 8, "Torches: " + a.count(Items.TORCH) + " || " + a.chat.history());
                    a.expect(a.chat.said.getLast().report().contains("Готово") == (a.count(Items.TORCH) >= 8), "Report does not match the bag: " + a.chat.history());
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(12);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 90);
        a.script.run("cave-torches", watch::summary);
    }

    /**
     * No x-ray: from the meadow the iron deep in the dark cave is out of sight. He has been down in chamber B before (with the
     * player) and saw it there; back on the meadow, the order leads him to what he remembers.
     */
    static MarathonScript sawTheIronBefore(GameTestHelper h, MarathonArena a, int meadowX, int meadowZ) {
        return a.script
                .then("he has seen the iron in chamber B", () -> {
                    var below = h.absoluteVec(a.spot(41.5, FLOOR, 42.5));
                    a.oleg.teleportTo(below.x, below.y, below.z);
                }, () -> a.oleg.remembersOre(h.absolutePos(a.cell(46, FLOOR + 1, 40))), 20 * 10)
                .then("back on the meadow", () -> {
                    var meadow = h.absoluteVec(a.at(meadowX, meadowZ));
                    a.oleg.teleportTo(meadow.x, meadow.y, meadow.z);
                }, () -> true, 20);
    }

    /** «Сделай железную кирку» with an empty bag: wood, stone, the iron he saw in the cave, a furnace, the pickaxe — and back up. */
    @GameTest(template = "marathon_cave", batch = "marathon", timeoutTicks = 36000)
    public static void anIronPickaxeFromAnEmptyBag(GameTestHelper h) {
        cave(h);
        var a = new MarathonArena(h, X, Y, Z, 54, 46, 56, 47, 32);
        var watch = a.watch;
        sawTheIronBefore(h, a, 54, 46)
                .ask("Сделай железную кирку", null, 20 * 1500)
                .check("the iron pickaxe", () -> {
                    a.expect(a.count(Items.IRON_PICKAXE) == 1, "No iron pickaxe || " + a.chat.history());
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(15);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 120);
        a.script.run("cave-iron-pickaxe", watch::summary);
    }

    /**
     * «Добудь железо и переплавь» from the meadow, never down in the cave before: no iron in sight, so he looks down the cave
     * mouth, walks down the passage look by look to the far chamber, sees the iron there; ingots by the bag, then back up.
     */
    @GameTest(template = "marathon_cave", batch = "marathon", timeoutTicks = 20000)
    public static void ironFromTheCaveSmelted(GameTestHelper h) {
        cave(h);
        var a = new MarathonArena(h, X, Y, Z, 56, 44, 57, 46, 32);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        a.oleg.setItem(0, new ItemStack(Items.OAK_LOG, 6));
        var watch = a.watch;
        a.script
                .ask("Добудь железо и переплавь", "[{\"action\":\"mine_ore\",\"item\":\"minecraft:iron_ore\",\"count\":3}]", 20 * 600)
                .check("ingots in the bag", () -> {
                    a.expect(a.chat.said.getLast().plan().stream().anyMatch(step -> step.type().equals("smelt_items")), "No smelting step: " + a.chat.history());
                    a.expect(a.count(Items.IRON_INGOT) >= 3, "Ingots: " + a.count(Items.IRON_INGOT) + " || " + a.chat.history());
                    watch.assertNothingLeft();
                    watch.assertNoLongIdle(12);
                })
                .waitUntil("back with the player", () -> a.toPlayer() < 8, 20 * 90);
        a.script.run("cave-iron-smelted", watch::summary);
    }
}
