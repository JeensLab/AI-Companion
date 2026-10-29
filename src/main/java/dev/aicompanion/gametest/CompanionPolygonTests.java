package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
 * a change of x. The baseline numbers are in the pull request.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionPolygonTests {
    private static final int SIZE = 32, HEIGHT = 24;
    /** What the flat control measured: a walk on level ground, blocks per tick. */
    private static final double BLOCKS_PER_TICK = 0.19;
    /** The surface of the mine: the top layer, grass. */
    private static final int SURFACE = 19;

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 700)
    public static void flatControl(GameTestHelper h) {
        Grid g = new Grid(h, false);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 1, 31, Blocks.STONE);
        CompanionEntity c = spawn(g, 4, 2, 4, 24, 2, 4);
        come(h, "flat", 0, 600, c, 20);
    }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void mineReturnV0(GameTestHelper h) { mine(h, 0, false, 36); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void mineReturnV1(GameTestHelper h) { mine(h, 1, false, 30); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void mineReturnV2(GameTestHelper h) { mine(h, 2, true, 36); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 3700)
    public static void caveOreV0(GameTestHelper h) { cave(h, 0, false, 4, 4); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 3700)
    public static void caveOreV1(GameTestHelper h) { cave(h, 1, false, 9, 4); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 3700)
    public static void caveOreV2(GameTestHelper h) { cave(h, 2, true, 4, 4); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void cliffTrailV0(GameTestHelper h) { cliff(h, 0, false, 6, 6); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void cliffTrailV1(GameTestHelper h) { cliff(h, 1, false, 22, 3); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void cliffTrailV2(GameTestHelper h) { cliff(h, 2, true, 6, 6); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void forestV0(GameTestHelper h) { forest(h, 0, false, 15); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void forestV1(GameTestHelper h) { forest(h, 1, false, 27); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void forestV2(GameTestHelper h) { forest(h, 2, true, 15); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void riverBankV0(GameTestHelper h) { river(h, 0, false, 25); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void riverBankV1(GameTestHelper h) { river(h, 1, false, 8); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void riverBankV2(GameTestHelper h) { river(h, 2, true, 25); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void villageV0(GameTestHelper h) { village(h, 0, false, 14); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void villageV1(GameTestHelper h) { village(h, 1, false, 28); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void villageV2(GameTestHelper h) { village(h, 2, true, 14); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void towerV0(GameTestHelper h) { tower(h, 0, false, 16); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void towerV1(GameTestHelper h) { tower(h, 1, false, 26); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void towerV2(GameTestHelper h) { tower(h, 2, true, 16); }

    @GameTest(batch = "polygon", template = "marathon_far", timeoutTicks = 3100)
    public static void farHillsV0(GameTestHelper h) { farHills(h, 0, false, 56); }
    @GameTest(batch = "polygon", template = "marathon_far", timeoutTicks = 3100)
    public static void farHillsV1(GameTestHelper h) { farHills(h, 1, false, 44); }
    @GameTest(batch = "polygon", template = "marathon_far", timeoutTicks = 3100)
    public static void farHillsV2(GameTestHelper h) { farHills(h, 2, true, 56); }

    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void longMineV0(GameTestHelper h) { longMine(h, 0, false, 58); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void longMineV1(GameTestHelper h) { longMine(h, 1, false, 52); }
    @GameTest(batch = "polygon", template = "tall_tree", timeoutTicks = 2500)
    public static void longMineV2(GameTestHelper h) { longMine(h, 2, true, 58); }

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
        carveAndCome(h, g, "mine", variant, cells, 2, startCell);
    }

    /** Cuts the corridor (one block down for every {@code run} cells) and sends him from {@code startCell} to the player at the mouth. */
    private static void carveAndCome(GameTestHelper h, Grid g, String arena, int variant, List<int[]> cells, int run, int startCell) {
        for (int i = 0; i < cells.size(); i++) {
            int floor = Math.max(2, SURFACE - i / run), x = cells.get(i)[0], z = cells.get(i)[1];
            g.box(x, floor, z, x, floor, z, floor == SURFACE ? Blocks.GRASS_BLOCK : Blocks.STONE);
            g.box(x, floor + 1, z, x, floor + 3, z, Blocks.AIR);
        }
        int[] at = cells.get(startCell);
        int floor = Math.max(2, SURFACE - startCell / run);
        CompanionEntity c = spawn(g, at[0], floor + 1, at[1], 4, SURFACE + 1, 4);
        come(h, arena, variant, 2400, c, Math.sqrt(sq(at[0] - 4) + sq(floor - SURFACE) + sq(at[1] - 4)));
    }

    /**
     * A long mine: the same stone, but the corridor winds back and forth over four rows and goes down a block for every three
     * cells: 59 cells, 17 blocks deep, so the way out is longer than one search (32 blocks). He starts at {@code startCell}, «иди ко мне».
     */
    private static void longMine(GameTestHelper h, int variant, boolean mirror, int startCell) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, SURFACE - 1, 31, Blocks.STONE);
        g.box(0, SURFACE, 0, 31, SURFACE, 31, Blocks.GRASS_BLOCK);
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            int z = 6 + 2 * row;
            for (int k = 0; k < 14; k++) cells.add(new int[]{row % 2 == 0 ? 4 + k : 17 - k, z});
            if (row < 3) cells.add(new int[]{row % 2 == 0 ? 17 : 4, z + 1});
        }
        carveAndCome(h, g, "longmine", variant, cells, 3, startCell);
    }

    /**
     * A forest with no gaps: trunks every second block, a crown of leaves over all of it, and bushes (leaves from the ground up)
     * in two of every five passages between trunks, so the way across winds (40 cells for a straight 24). The player waits on the
     * far side: «иди ко мне».
     */
    private static void forest(GameTestHelper h, int variant, boolean mirror, int startZ) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 1, 31, Blocks.GRASS_BLOCK);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            boolean trunk = x % 2 == 0 && z % 2 == 0;
            boolean bush = x % 2 != z % 2 && (x * 7 + z * 11) % 5 < 2;
            if (trunk) g.box(x, 2, z, x, 6, z, Blocks.OAK_LOG);
            for (int y = bush ? 2 : trunk ? 7 : 5; y <= 7; y++) g.set(x, y, z, leaves);
        }
        CompanionEntity c = spawn(g, 3, 2, startZ, 27, 2, 15);
        come(h, "forest", variant, 2400, c, Math.sqrt(sq(24) + sq(startZ - 15)));
    }

    /**
     * A river six wide and three deep across the arena, flush with the bank he starts on. The far bank is a wall three blocks over
     * the water except at one stretch (z 24 to 27) where it rises as steps. The player stands on the high bank: «иди ко мне».
     */
    private static void river(GameTestHelper h, int variant, boolean mirror, int startZ) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 4, 31, Blocks.STONE);
        g.box(18, 5, 0, 31, 7, 31, Blocks.STONE);
        g.box(12, 2, 0, 17, 4, 31, Blocks.WATER);
        g.box(12, 2, 0, 17, 4, 0, Blocks.BEDROCK);
        g.box(12, 2, 31, 17, 4, 31, Blocks.BEDROCK);
        for (int step = 0; step < 4; step++) g.box(18 + step, 5 + step, 24, 18 + step, 7, 27, Blocks.AIR);
        CompanionEntity c = spawn(g, 4, 5, startZ, 27, 8, 25);
        come(h, "river", variant, 2400, c, Math.sqrt(sq(23) + sq(startZ - 25) + 9));
    }

    /**
     * A village: a gate-less fence along x=8 with one gap (z 14 and 15), and behind it a long house across the whole arena
     * (fences close its two ends) whose only ways through are a wooden door in the west wall and one in the east wall, both at
     * z=20. The player waits behind the house: «иди ко мне».
     */
    private static void village(GameTestHelper h, int variant, boolean mirror, int startZ) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 1, 31, Blocks.STONE);
        g.box(8, 2, 0, 8, 2, 31, Blocks.OAK_FENCE);
        g.box(8, 2, 14, 8, 2, 15, Blocks.AIR);
        g.box(16, 2, 2, 22, 5, 29, Blocks.OAK_PLANKS);
        g.box(17, 2, 3, 21, 4, 28, Blocks.AIR);
        g.box(16, 2, 0, 22, 2, 1, Blocks.OAK_FENCE);
        g.box(16, 2, 30, 22, 2, 31, Blocks.OAK_FENCE);
        for (int x : new int[]{16, 22}) {
            BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, g.face(Direction.EAST));
            g.box(x, 2, 20, x, 3, 20, Blocks.AIR);
            g.set(x, 2, 20, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            g.set(x, 3, 20, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        CompanionEntity c = spawn(g, 3, 2, startZ, 27, 2, 20);
        come(h, "village", variant, 2400, c, Math.sqrt(sq(24) + sq(startZ - 20)));
    }

    /**
     * The player on a stone tower eleven blocks high, up only by a ladder on its west side; he starts on the ground, twelve
     * blocks off: «иди ко мне».
     */
    private static void tower(GameTestHelper h, int variant, boolean mirror, int startZ) {
        Grid g = new Grid(h, mirror);
        g.box(0, 0, 0, 31, HEIGHT - 1, 31, Blocks.AIR);
        g.box(0, 0, 0, 31, 0, 31, Blocks.BEDROCK);
        g.box(0, 1, 0, 31, 1, 31, Blocks.STONE);
        g.box(15, 2, 15, 17, 12, 17, Blocks.STONE);
        for (int y = 2; y <= 12; y++) g.set(14, y, 16, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, g.face(Direction.WEST)));
        CompanionEntity c = spawn(g, 4, 2, startZ, 16, 13, 16);
        come(h, "tower", variant, 2400, c, Math.sqrt(sq(12) + sq(startZ - 16) + sq(11)));
    }

    /**
     * Rolling ground (steps of at most one block, hills two to three blocks high) over a 32-wide strip, the player 150 blocks
     * away along z: «иди ко мне». Far past one search, so the route goes by the travel segments. The template is 112 wide
     * and 256 long; the strip stands at x 40 to 71.
     */
    private static void farHills(GameTestHelper h, int variant, boolean mirror, int startX) {
        Grid g = new Grid(h, mirror, 112);
        for (int x = 40; x < 72; x++) for (int z = 0; z < 172; z++) {
            int top = hill(x, z);
            g.box(x, 1, z, x, top - 1, z, Blocks.STONE);
            g.box(x, top, z, x, top, z, Blocks.GRASS_BLOCK);
        }
        // Beyond the strip the template floor is lower: a side step must not drop him where he cannot climb back.
        g.box(39, 1, 0, 39, 14, 171, Blocks.BARRIER); g.box(72, 1, 0, 72, 14, 171, Blocks.BARRIER);
        CompanionEntity c = spawn(g, startX, hill(startX, 10) + 1, 10, 56, hill(56, 160) + 1, 160);
        come(h, "farhills", variant, 3000, c, Math.sqrt(sq(startX - 56) + sq(150)));
    }

    private static int hill(int x, int z) {
        return 5 + (int) Math.round(2 * Math.sin(z / 9.0) + 1.5 * Math.sin(x / 5.0 + z / 13.0));
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
    private record Grid(GameTestHelper h, boolean mirror, int width) {
        Grid(GameTestHelper h, boolean mirror) { this(h, mirror, SIZE); }

        BlockPos at(int x, int y, int z) { return new BlockPos(mirror ? width - 1 - x : x, y, z); }

        void set(int x, int y, int z, BlockState state) { h.setBlock(at(x, y, z), state); }

        /** A direction of the arena as built (east is towards larger x before the mirror). */
        Direction face(Direction d) { return mirror && d.getAxis() == Direction.Axis.X ? d.getOpposite() : d; }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) h.setBlock(at(x, y, z), block);
        }
    }
}
