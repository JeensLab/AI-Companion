package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ore at lava like a player takes it: the cell that lets lava in is plugged at once with a block from his bag; ore on top of lava
 * (its drop would burn) and lava with nothing to plug it are left and said so; stone at lava stays skipped (Q7).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLavaPlugTests {
    /** The lava is given this long to stop (a fluid in the Overworld is touched every 30 ticks). */
    private static final int SETTLE = 60;
    /** Lava in the Nether is touched every 10 ticks: the plug must be in before that. */
    private static final int NETHER_LAVA_TICKS = 10;

    private CompanionLavaPlugTests() { }

    /**
     * Iron with a lava source right behind it in the wall: mined, a cobblestone stands where the lava was, one cobblestone gone, no lava, no burn.
     * The plug stands within a few ticks of the cell being opened: far inside the 10 ticks after which lava of the Nether moves.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void lavaBesideTheOreIsPluggedAndTheOreMined(GameTestHelper h) {
        CompanionEntity c = wallArena(h);
        BlockPos ore = new BlockPos(20, 1, 16), lava = new BlockPos(21, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(lava, Blocks.LAVA);
        h.setBlock(ore.above(), Blocks.AIR); // open above: no stone is dug first, so the cobblestone count is the plugs' alone
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        Watch watch = watch(h, c, ore, lava);
        order(h, c);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && idle(c) && c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            h.assertTrue(h.getBlockState(lava).is(Blocks.COBBLESTONE), "No plug where the lava was: " + h.getBlockState(lava));
            h.assertTrue(count(c, Items.COBBLESTONE) == 2, "Plugs left in the bag: " + count(c, Items.COBBLESTONE));
            h.assertTrue(watch.opened > 0 && watch.plugged > 0, "Ore not opened or lava not plugged: " + report(c));
            h.assertTrue(watch.plugged - watch.opened < NETHER_LAVA_TICKS / 2, "Plug came " + (watch.plugged - watch.opened) + " ticks after the opening");
            noLava(h, watch, c);
        });
    }

    /**
     * A lake of lava lies inside the wall, one block above the roof of every way to a buried ore (from either side): each cell of his tunnel that
     * has lava over it is dug, plugged on the spot, and the ore is reached and mined without a drop of lava coming in.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void lavaAboveTunnelCellsIsPlugged(GameTestHelper h) {
        CompanionEntity c = wallArena(h);
        BlockPos ore = new BlockPos(23, 1, 16), over = new BlockPos(21, 3, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        box(h, 21, 3, 11, 25, 3, 21, Blocks.LAVA);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 6));
        Watch watch = watch(h, c, ore, over);
        order(h, c);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && idle(c) && c.didLastTaskSucceed() && count(c, Items.RAW_IRON) == 1, report(c));
            int plugs = 0;
            for (int x = 21; x <= 25; x++) for (int z = 11; z <= 21; z++) if (h.getBlockState(new BlockPos(x, 3, z)).is(Blocks.COBBLESTONE)) plugs++;
            h.assertTrue(plugs >= 1, "No plug in the lake over his tunnel: " + report(c));
            noLava(h, watch, c);
        });
    }

    /** Ore lying on lava: the drop would fall in and burn. Not touched, even with plugs in the bag; the reason is said. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void oreOnTopOfLavaIsLeftAndTheReasonIsSaid(GameTestHelper h) {
        CompanionEntity c = wallArena(h);
        BlockPos ore = new BlockPos(20, 2, 16), lava = new BlockPos(20, 1, 16);
        h.setBlock(ore, Blocks.DIAMOND_ORE); h.setBlock(lava, Blocks.LAVA);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        order(h, c, "minecraft:diamond_ore");
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && idle(c), "Work still active: " + c.getLastTaskResult());
            String said = c.getLastTaskResult();
            h.assertTrue(!c.didLastTaskSucceed() && said.contains("над лавой"), said);
            h.assertTrue(h.getBlockState(ore).is(Blocks.DIAMOND_ORE) && count(c, Items.COBBLESTONE) == 5, "Ore dug or plugs spent: " + report(c));
        });
    }

    /** Lava beside the ore and nothing in the bag fit to plug it (sand falls through lava, gold is no plug): left alone, told what he needs, the bag untouched. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void withNothingToPlugWithTheOreIsLeftAndHeAsksForCobblestone(GameTestHelper h) {
        CompanionEntity c = wallArena(h);
        BlockPos ore = new BlockPos(20, 1, 16), lava = new BlockPos(21, 1, 16);
        h.setBlock(ore, Blocks.IRON_ORE); h.setBlock(lava, Blocks.LAVA);
        c.setItem(0, new ItemStack(Items.SAND, 5)); c.setItem(1, new ItemStack(Items.GRAVEL, 3)); c.setItem(2, new ItemStack(Items.GOLD_INGOT, 4));
        order(h, c);
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && idle(c), "Work still active: " + c.getLastTaskResult());
            String said = c.getLastTaskResult();
            h.assertTrue(!c.didLastTaskSucceed() && said.contains("заткнуть нечем") && said.contains("булыжник") && said.contains("земля"), said);
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE) && h.getBlockState(lava).is(Blocks.LAVA), "Ore dug or lava changed");
            h.assertTrue(count(c, Items.SAND) == 5 && count(c, Items.GRAVEL) == 3 && count(c, Items.GOLD_INGOT) == 4, "Bag changed: " + c.getStoredItemsSummary());
        });
    }

    /** Stone at lava is still skipped, not plugged (Q7): both stones touch lava, and the dirt in the bag stays in the bag. */
    @GameTest(template = "tall_tree", timeoutTicks = 900)
    public static void stoneBesideLavaIsSkippedNotPlugged(GameTestHelper h) {
        CompanionEntity c = wallArena(h);
        BlockPos stone = new BlockPos(20, 1, 16), lava = new BlockPos(21, 1, 16);
        box(h, 20, 1, 10, 26, 6, 22, Blocks.AIR);
        h.setBlock(stone, Blocks.STONE); h.setBlock(lava, Blocks.LAVA);
        box(h, 21, 2, 16, 21, 2, 16, Blocks.STONE);
        c.setItem(0, new ItemStack(Items.DIRT, 3));
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        h.runAfterDelay(SETTLE, () -> h.assertTrue(c.startWork("gather_stone", "", 2).success(), "Stone order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > SETTLE && idle(c), "Work still active: " + c.getLastTaskResult());
            h.assertTrue(h.getBlockState(stone).is(Blocks.STONE) && h.getBlockState(lava).is(Blocks.LAVA), "Stone dug or lava plugged");
            h.assertTrue(count(c, Items.COBBLESTONE) == 0, "Stone brought from outside the arena: " + c.getStoredItemsSummary() + ", he at " + h.relativePos(c.blockPosition()).toShortString());
            h.assertTrue(count(c, Items.DIRT) == 3, "Plugs spent on stone: " + c.getStoredItemsSummary());
            h.assertTrue(c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Hurt: " + c.getHealth());
        });
    }

    /**
     * A lava fall runs down a notch in the wall face, next to iron at mid-fall height. A falling cell has lava under it and does not
     * spread sideways, so it is no inflow: a plug there would give the fall above a floor, and it would spill into the cave.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500)
    public static void aLavaFallBesideTheOreIsNoInflowAndIsNotPlugged(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 12, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 20, 2, 8, 26, 9, 24, Blocks.STONE);
        // The notch: open from the floor pit (y 1) to the top, on the wall face x 20, z 17; open to the cave at x 19.
        box(h, 20, 1, 17, 20, 9, 17, Blocks.AIR);
        h.setBlock(new BlockPos(21, 9, 17), Blocks.LAVA); // a spring in the wall: lava runs out of the face and falls down it
        BlockPos ore = new BlockPos(20, 4, 16), fall = new BlockPos(20, 4, 17);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, 2, 16);
        bindAt(h, c, 12, 2, 12);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        boolean[] spilled = {false}, burnt = {false}, plugged = {false}; long[] done = {-1};
        h.onEachTick(() -> {
            for (int x = 10; x <= 19; x++) for (int y = 2; y <= 9; y++) for (int z = 12; z <= 22; z++)
                spilled[0] |= h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, z))).is(FluidTags.LAVA);
            burnt[0] |= c.isOnFire() || c.isInLava();
            plugged[0] |= h.getTick() > 400 && !h.getBlockState(fall).is(Blocks.AIR) && !h.getBlockState(fall).is(Blocks.LAVA);
            if (done[0] < 0 && h.getTick() > 410 && idle(c)) done[0] = h.getTick();
        });
        h.runAfterDelay(400, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(done[0] > 0 && h.getTick() > done[0] + SETTLE && idle(c), "Still working: " + report(c));
            h.assertTrue(!h.getBlockState(ore).is(Blocks.IRON_ORE), "Ore not mined: " + report(c));
            h.assertTrue(!plugged[0] && count(c, Items.COBBLESTONE) == 3, "A plug was put into the fall: " + h.getBlockState(fall) + " " + report(c));
            h.assertTrue(!spilled[0] && !burnt[0], "Lava spilled into the cave=" + spilled[0] + " burnt=" + burnt[0] + " " + report(c));
        });
    }

    /**
     * A closed lava fall in the rock (a spring on top, its foot on the stone floor); his tunnel to iron runs past it. Lava at the
     * foot stands on stone, so it would be an inflow, but a plug there only lifts the spill: the fall above gets a floor and spreads
     * into the open cell beside it. A fall is not plugged: the ore is left, the report says «лавопад».
     */
    @GameTest(template = "large_search", timeoutTicks = 2600)
    public static void aLavaFallFootBesideTheTunnelIsNotOpenedAndNamed(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 12, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        // The rock fills the arena east of x 17 from wall to wall: the only way to the ore is a tunnel from the cave in the west.
        box(h, 18, 2, 0, 31, 11, 31, Blocks.STONE);
        box(h, 0, 2, 0, 17, 11, 0, Blocks.BEDROCK); box(h, 0, 2, 31, 17, 11, 31, Blocks.BEDROCK); box(h, 0, 2, 0, 0, 11, 31, Blocks.BEDROCK);
        box(h, 24, 2, 17, 24, 9, 17, Blocks.AIR);
        h.setBlock(new BlockPos(24, 10, 17), Blocks.LAVA);
        BlockPos ore = new BlockPos(27, 2, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, 2, 16);
        bindAt(h, c, 12, 2, 12);
        // Following, he strolls round Oleg for the 400 ticks before the order and may stand beyond the 16 blocks of the search from the ore.
        c.setFollowing(false);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        boolean[] inTunnel = {false}, burnt = {false}, fixture = {false}; long[] done = {-1};
        h.onEachTick(() -> {
            boolean now = false;
            for (int x = 18; x <= 26; x++) for (int y = 2; y <= 3; y++) now |= h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, 16))).is(FluidTags.LAVA);
            if (h.getTick() == 390) fixture[0] = !now;
            inTunnel[0] |= now;
            burnt[0] |= c.isOnFire() || c.isInLava();
            if (done[0] < 0 && h.getTick() > 410 && (idle(c) || !c.isAlive())) done[0] = h.getTick();
        });
        h.runAfterDelay(400, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(fixture[0], "Fixture: lava in the tunnel row before the order");
            h.assertTrue(done[0] > 0 && h.getTick() > done[0] + SETTLE, "Still working: " + report(c));
            h.assertTrue(!inTunnel[0] && !burnt[0] && c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Lava came into his tunnel=" + inTunnel[0] + " burnt=" + burnt[0] + " " + report(c));
            // Either he went round the fall by a tunnel of his own (the ore taken, dry), or he left the ore and said why.
            h.assertTrue(!h.getBlockState(ore).is(Blocks.IRON_ORE) || c.getLastTaskResult().contains("лавопад") && count(c, Items.COBBLESTONE) == 3,
                    "The ore stands and the fall is not named, or a plug was spent: " + report(c));
        });
    }

    /**
     * The iron lies on the cave floor beside the foot of a lava fall (a stone lip closes the foot from the cave). Mined, and the foot
     * plugged, the fall would spill over the plug into the cave and burn him: the ore is left, the report says «лавопад».
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1800)
    public static void anOreBesideTheFootOfALavaFallIsNotTakenAndNamed(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 12, 31, Blocks.AIR);
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 31, 1, 31, Blocks.STONE);
        box(h, 20, 2, 8, 26, 9, 24, Blocks.STONE);
        box(h, 20, 2, 17, 20, 9, 17, Blocks.AIR);
        h.setBlock(new BlockPos(19, 2, 17), Blocks.STONE);
        h.setBlock(new BlockPos(21, 9, 17), Blocks.LAVA);
        BlockPos ore = new BlockPos(20, 2, 16);
        h.setBlock(ore, Blocks.IRON_ORE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 14, 2, 16);
        bindAt(h, c, 12, 2, 12);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        boolean[] spilled = {false}, burnt = {false}, fixture = {false}; long[] done = {-1};
        h.onEachTick(() -> {
            boolean now = false;
            for (int x = 10; x <= 20; x++) for (int y = 2; y <= 9; y++) for (int z = 12; z <= 22; z++)
                now |= !(x == 20 && z == 17) && h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, z))).is(FluidTags.LAVA);
            if (h.getTick() == 390) fixture[0] = !now;
            spilled[0] |= now;
            burnt[0] |= c.isOnFire() || c.isInLava();
            if (done[0] < 0 && h.getTick() > 410 && (idle(c) || !c.isAlive())) done[0] = h.getTick();
        });
        h.runAfterDelay(400, () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
        h.succeedWhen(() -> {
            h.assertTrue(fixture[0], "Fixture: lava in the cave before the order");
            h.assertTrue(done[0] > 0 && h.getTick() > done[0] + 200, "Still working: " + report(c));
            h.assertTrue(!spilled[0] && !burnt[0] && c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Lava spilled into the cave=" + spilled[0] + " burnt=" + burnt[0] + " " + report(c));
            h.assertTrue(h.getBlockState(ore).is(Blocks.IRON_ORE) && count(c, Items.COBBLESTONE) == 3, "Ore dug or a plug spent: " + report(c));
            h.assertTrue(c.getLastTaskResult().contains("лавопад"), "The fall is not named: " + report(c));
        });
    }

    // ---- Arena and measures ----

    /**
     * Bedrock floor and ceiling, air between; a stone wall x 20..26, y 1..6 across the arena at z 10..22. Oleg stands at x 16 on the
     * west side, the ore is in the wall at x 20 or deeper. The lava is set by the test and has time to stop before the order.
     */
    private static CompanionEntity wallArena(GameTestHelper h) {
        box(h, 0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        box(h, 0, 7, 0, 31, 7, 31, Blocks.BEDROCK);
        // Walled in: with no stone to take here he would go for it to the stone other tests lay past their own arenas, and pick up what lies on the way.
        box(h, 0, 1, 0, 31, 6, 0, Blocks.BEDROCK); box(h, 0, 1, 31, 31, 6, 31, Blocks.BEDROCK);
        box(h, 0, 1, 0, 0, 6, 31, Blocks.BEDROCK); box(h, 31, 1, 0, 31, 6, 31, Blocks.BEDROCK);
        box(h, 20, 1, 10, 26, 6, 22, Blocks.STONE);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(13, 1, 13));
        owner.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        return c;
    }

    /** Binds an owner to a companion already standing in a world the test built itself. */
    private static void bindAt(GameTestHelper h, CompanionEntity c, int x, int y, int z) {
        ServerPlayer owner = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(x, y, z));
        owner.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(16);
        c.setLifestyle(false, false);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
    }

    private static void order(GameTestHelper h, CompanionEntity c) { order(h, c, "minecraft:iron_ore"); }
    private static void order(GameTestHelper h, CompanionEntity c, String ore) {
        h.runAfterDelay(SETTLE, () -> h.assertTrue(c.startWork("mine_ore", ore, 1).success(), "Ore order not accepted"));
    }

    /** What the world and he did each tick: the tick the ore cell opened, the tick the lava cell became solid, lava in the opened cell, burns. */
    private static final class Watch { long opened, plugged; boolean lavaInside, burnt; }

    private static Watch watch(GameTestHelper h, CompanionEntity c, BlockPos ore, BlockPos lava) {
        Watch watch = new Watch();
        h.onEachTick(() -> {
            if (watch.opened == 0 && !h.getBlockState(ore).is(Blocks.IRON_ORE) && h.getTick() > SETTLE) watch.opened = h.getTick();
            if (watch.plugged == 0 && h.getBlockState(lava).is(Blocks.COBBLESTONE)) watch.plugged = h.getTick();
            for (int x = 19; x <= 27; x++) for (int y = 1; y <= 2; y++) for (int z = 15; z <= 17; z++) {
                BlockPos cell = new BlockPos(x, y, z);
                watch.lavaInside |= !cell.equals(lava) && h.getLevel().getFluidState(h.absolutePos(cell)).is(FluidTags.LAVA);
            }
            watch.burnt |= c.isOnFire() || c.isInLava();
        });
        return watch;
    }

    private static void noLava(GameTestHelper h, Watch watch, CompanionEntity c) {
        h.assertTrue(!watch.lavaInside, "Lava ran into the opened cells");
        h.assertTrue(!watch.burnt && c.isAlive() && c.getHealth() >= c.getMaxHealth(), "Burnt: " + c.getHealth());
    }

    private static void box(GameTestHelper h, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(new BlockPos(x, y, z), block);
    }
    private static boolean idle(CompanionEntity c) { return !c.isGatheringTask() && !c.isWorking(); }
    private static String report(CompanionEntity c) { return c.getLastTaskResult() + " | " + c.getDeedsForAi() + " | bag: " + c.getStoredItemsSummary(); }
    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }
}
