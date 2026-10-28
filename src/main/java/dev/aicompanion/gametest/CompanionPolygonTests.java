package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The polygon: arenas that measure how well Oleg gets places, before any change of the pathfinding. Every variant (0 as built,
 * 1 with the start shifted, 2 mirrored) prints one line, e.g.
 * {@code POLYGON arena=mine variant=0 reached=true ticks=212 straight=90 nodes=1830 ms=4.2 maxTickMs=2.1}
 * where straight is what a walk on the straight line would take (calibrated on the flat control), nodes the cells the path
 * searches expanded and ms the time they took, maxTickMs the most the searches took in one tick. The search radius is 16, so one
 * search of the game reaches 32 blocks: the tightest in the tests. A variant red on the code as it was before the pathfinding
 * work is optional (required = false); the ticket that makes it green makes it required. Arenas are built by code, so a mirror is
 * a change of x. Read in .scratch/pathfinding/polygon.md.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPolygonTests {
    private static final int SIZE = 32, HEIGHT = 24;
    /** What the flat control measured: a walk on level ground, blocks per tick. */
    private static final double BLOCKS_PER_TICK = 0.19;
    /** The surface of the mine: the top layer, grass. */
    private static final int SURFACE = 19;

    @GameTest(template = "tall_tree", timeoutTicks = 700)
    public static void flatControl(GameTestHelper h) {
        Grid g = new Grid(h, false);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 1, 31, Blocks.STONE);
        CompanionEntity c = spawn(g, 4, 2, 4, 24, 2, 4);
        come(h, "flat", 0, 600, c, 20);
    }

    @GameTest(template = "tall_tree", timeoutTicks = 2500)
    public static void mineReturnV0(GameTestHelper h) { mine(h, 0, false, 36); }
    @GameTest(template = "tall_tree", timeoutTicks = 2500)
    public static void mineReturnV1(GameTestHelper h) { mine(h, 1, false, 30); }
    @GameTest(template = "tall_tree", timeoutTicks = 2500)
    public static void mineReturnV2(GameTestHelper h) { mine(h, 2, true, 36); }

    @GameTest(template = "tall_tree", timeoutTicks = 3700)
    public static void caveOreV0(GameTestHelper h) { cave(h, 0, false, 4, 4); }
    @GameTest(template = "tall_tree", timeoutTicks = 3700)
    public static void caveOreV1(GameTestHelper h) { cave(h, 1, false, 9, 4); }
    @GameTest(template = "tall_tree", timeoutTicks = 3700)
    public static void caveOreV2(GameTestHelper h) { cave(h, 2, true, 4, 4); }

    @GameTest(template = "tall_tree", timeoutTicks = 2500, required = false)
    public static void cliffTrailV0(GameTestHelper h) { cliff(h, 0, false, 6, 6); }
    @GameTest(template = "tall_tree", timeoutTicks = 2500, required = false)
    public static void cliffTrailV1(GameTestHelper h) { cliff(h, 1, false, 22, 3); }
    @GameTest(template = "tall_tree", timeoutTicks = 2500, required = false)
    public static void cliffTrailV2(GameTestHelper h) { cliff(h, 2, true, 6, 6); }

    /**
     * A winding mine: a corridor three high cut into the stone, going down a block for every two it goes on, back and forth
     * over a 9 x 13 square: 37 cells, 17 blocks deep. He starts down the corridor at cell {@code startCell}, the player waits on
     * the grass by its mouth.
     */
    private static void mine(GameTestHelper h, int variant, boolean mirror, int startCell) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, SURFACE - 1, 31, Blocks.STONE);
        g.box(0, SURFACE, 0, 31, SURFACE, 31, Blocks.GRASS_BLOCK);
        List<int[]> cells = new ArrayList<>();
        for (int x = 4; x <= 12; x++) cells.add(new int[]{x, 6});
        for (int z = 7; z <= 12; z++) cells.add(new int[]{12, z});
        for (int x = 11; x >= 4; x--) cells.add(new int[]{x, 12});
        for (int z = 13; z <= 18; z++) cells.add(new int[]{4, z});
        for (int x = 5; x <= 12; x++) cells.add(new int[]{x, 18});
        for (int i = 0; i < cells.size(); i++) {
            int floor = Math.max(2, SURFACE - i / 2), x = cells.get(i)[0], z = cells.get(i)[1];
            g.box(x, floor, z, x, floor, z, i < 2 ? Blocks.GRASS_BLOCK : Blocks.STONE);
            g.box(x, floor + 1, z, x, floor + 3, z, Blocks.AIR);
        }
        int[] at = cells.get(startCell);
        int floor = Math.max(2, SURFACE - startCell / 2);
        CompanionEntity c = spawn(g, at[0], floor + 1, at[1], 4, SURFACE + 1, 4);
        come(h, "mine", variant, 2400, c, Math.sqrt(sq(at[0] - 4) + sq(floor - SURFACE) + sq(at[1] - 4)));
    }

    /**
     * Solid stone with a one-wide cave two high: from the start room east along z=4, south along x=14, west along z=14 to a dead
     * end, where iron ore sits in the end wall (ten blocks from the start by the straight line: inside the search of the order). He is ordered to mine one iron ore.
     */
    private static void cave(GameTestHelper h, int variant, boolean mirror, int startX, int startZ) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 7, 31, Blocks.STONE);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 0, 0, 0, 7, 31, Blocks.BEDROCK); g.box(31, 0, 0, 31, 7, 31, Blocks.BEDROCK);
        g.box(0, 0, 0, 31, 7, 0, Blocks.BEDROCK); g.box(0, 0, 31, 31, 7, 31, Blocks.BEDROCK);
        g.box(3, 2, 3, 5, 3, 5, Blocks.AIR);
        g.box(6, 2, 4, 14, 3, 4, Blocks.AIR);
        g.box(14, 2, 5, 14, 3, 14, Blocks.AIR);
        g.box(7, 2, 14, 13, 3, 14, Blocks.AIR);
        g.box(6, 2, 14, 6, 2, 14, Blocks.IRON_ORE);
        CompanionEntity c = spawn(g, startX, 2, startZ, 3, 2, 3);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        double straight = Math.max(0, Math.sqrt(sq(6 - startX) + sq(14 - startZ)) - 3);
        measure(h, "cave", variant, 3600, c, straight, () -> count(c, Items.RAW_IRON) >= 1,
                () -> h.assertTrue(c.startWork("mine_ore", "minecraft:iron_ore", 1).success(), "Ore order not accepted"));
    }

    /**
     * A plateau thirteen blocks over the lowland, a sheer face between them, and one trail down along the foot of the plateau
     * (one block lower at each step to the west). He stands on the plateau, the player on the lowland: «иди ко мне».
     */
    private static void cliff(GameTestHelper h, int variant, boolean mirror, int startX, int startZ) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 1, 31, Blocks.STONE);
        g.box(0, 2, 0, 31, 14, 16, Blocks.STONE);
        for (int x = 16; x <= 28; x++) g.box(x, 2, 17, x, x - 15, 17, Blocks.STONE);
        CompanionEntity c = spawn(g, startX, 15, startZ, 8, 2, 24);
        come(h, "cliff", variant, 2400, c, Math.sqrt(sq(8 - startX) + sq(24 - startZ) + sq(13)));
    }

    private static void come(GameTestHelper h, String arena, int variant, int limit, CompanionEntity c, double straight) {
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        measure(h, arena, variant, limit, c, Math.max(0, straight - 4), () -> c.distanceToSqr(player) <= 16,
                () -> h.assertTrue(c.startWork("come", "", 1).success(), "Come order not accepted"));
    }

    /** Gives the order, then every tick looks whether he is there; one line at the end, reached or not. */
    private static void measure(GameTestHelper h, String arena, int variant, int limit, CompanionEntity c, double straight,
                                BooleanSupplier reached, Runnable order) {
        var nav = (CompanionNavigation) c.getNavigation();
        nav.resetMaxTick();
        var before = nav.meter();
        order.run();
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) return;
            long ticks = h.getTick();
            boolean ok = reached.getAsBoolean();
            if (!ok && ticks < limit && (ticks < 40 || c.isWorking())) return;
            done[0] = true;
            var spent = nav.meter().since(before);
            AICompanionMod.LOGGER.info(String.format(Locale.ROOT, "POLYGON arena=%s variant=%d reached=%b ticks=%d straight=%d nodes=%d ms=%.1f maxTickMs=%.1f",
                    arena, variant, ok, ticks, Math.round(straight / BLOCKS_PER_TICK), spent.nodes(), spent.ms(), nav.meter().maxTickMs()));
            if (ok) h.succeed();
            else h.fail("Not there after " + ticks + " ticks at " + h.relativePos(c.blockPosition()).toShortString() + ": " + c.getLastTaskResult());
        });
    }

    private static CompanionEntity spawn(Grid g, int x, int y, int z, int playerX, int playerY, int playerZ) {
        GameTestHelper h = g.h();
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), g.at(x, y, z).getX(), y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(g.at(playerX, playerY, playerZ));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static int sq(int v) { return v * v; }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        return count;
    }

    /** The arena's coordinates, mirrored across x when asked. */
    private record Grid(GameTestHelper h, boolean mirror) {
        BlockPos at(int x, int y, int z) { return new BlockPos(mirror ? SIZE - 1 - x : x, y, z); }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(at(x, y, z), block);
        }
    }
}
