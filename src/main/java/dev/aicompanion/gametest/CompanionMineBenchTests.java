package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A stone pickaxe made on the stairs: the passage is one cell wide and a stone lies on every step, so the bench goes on a step, the
 * stone must not count as an obstacle, and the bench comes up again by hand before he digs on. Arena as in the other mine tests:
 * origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineBenchTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, RING_FROM = 12, TIMEOUT = 2400, STAIRS = 8, SETTLED = 40, STICKS = 8, TORCH_WINDOW = 700;

    private CompanionMineBenchTests() { }

    /** No stone pickaxe is left, the bag holds cobblestone, sticks and a bench: a pickaxe is made on the spot, the steps are free again and the bench is back in the bag, with no climb. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void makesAPickaxeOnAOneWideStairWithStonesOnEveryStep(GameTestHelper h) {
        probe(h, false, false);
    }

    /** The same while the autonomy protects his way back: a bench he takes up at once must not be refused for the way it shuts. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void makesAPickaxeOnTheStairWhileTheWayBackIsProtected(GameTestHelper h) {
        probe(h, true, false);
    }

    /** A mine started again under the ground has no way up to climb for supplies: the same pickaxe is made and the order does not end in a shortage. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void makesAPickaxeInABuriedMineInsteadOfEndingInAShortage(GameTestHelper h) {
        probe(h, false, true);
    }

    /** Two sticks are the next stone pickaxe: topping up the torches (which also take a stick each) must leave them alone, underground there is no wood. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void topsUpTorchesWithoutEatingTheSticksOfTheNextPickaxe(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carve(h, c);
        BlockPos at = stairs.get(STAIRS);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(5, new ItemStack(Items.STICK, 2));
        c.setItem(7, new ItemStack(Items.COAL, 8));
        start(h, c);
        int torches = count(c, Items.TORCH);
        h.runAfterDelay(TORCH_WINDOW, () -> {
            h.assertTrue(count(c, Items.STICK) == 2 && count(c, Items.TORCH) <= torches, "Torches were made of the pickaxe's sticks: sticks " + count(c, Items.STICK) + ", torches " + torches + " -> " + count(c, Items.TORCH) + " | " + report(c));
            h.succeed();
        });
    }

    /** Items lie in the only free cell and do not matter; a mob standing there does. */
    @GameTest(template = "marathon_cave", timeoutTicks = 200)
    public static void aBenchGoesOverAnItemButNotOverAMob(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        List<BlockPos> stairs = carve(h, c);
        BlockPos at = stairs.get(STAIRS), behind = stairs.get(STAIRS - 1);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        Cow cow = h.spawn(EntityType.COW, rel(h, behind));
        cow.setNoAi(true);
        cow.setPersistenceRequired();
        CompanionActionResult blocked = c.craftItemResult("minecraft:stone_pickaxe", 1);
        h.assertTrue(!blocked.success() && count(c, Items.STONE_PICKAXE) == 0 && tables(h) == 0, "A bench was set down over a cow: " + blocked.message() + " | " + report(c));
        cow.discard();
        CompanionActionResult made = c.craftItemResult("minecraft:stone_pickaxe", 1);
        h.assertTrue(made.success() && count(c, Items.STONE_PICKAXE) == 1 && tables(h) == 1, "No bench over the stone lying on the step: " + made.message() + " | " + report(c));
        h.succeed();
    }

    /** A bench left standing on the stairs (a taking cut short by a quit) is in the record: the next order takes it up by hand before anything else and goes on. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void takesUpABenchLeftOnTheStairsWhenTheOrderBegins(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        c.setItem(6, ItemStack.EMPTY);
        List<BlockPos> stairs = carve(h, c);
        BlockPos at = stairs.get(STAIRS), left = stairs.get(STAIRS - 1);
        h.getLevel().setBlock(left, Blocks.CRAFTING_TABLE.defaultBlockState(), 2 | 16);
        c.mineSite().temporary(left);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        long id = start(h, c);
        h.succeedWhen(() -> {
            h.assertTrue(count(c, Items.CRAFTING_TABLE) == 1 && tables(h) == 0 && c.mineSite().temporary().isEmpty(), "The bench was not taken up: " + tables(h) + " on the stairs, " + c.mineSite().temporary() + " | " + report(c));
            h.assertTrue(c.mineSite().stands().size() > STAIRS || c.workResult(id) != null, "He did not go on digging after the bench | " + report(c));
        });
    }

    /** A bench that cannot be taken up (the bag has no room for it) does not hold the order: the effort is bounded and the digging goes on, the bench stays in the record. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void givesUpOnABenchItCannotTakeAndDigsOn(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        for (int slot = 0; slot < c.getContainerSize(); slot++) c.setItem(slot, new ItemStack(Items.DIRT, 64));
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        // The rig's torches back over the dirt: with none and nothing to make one of he does not dig on in the dark but climbs.
        c.setItem(1, new ItemStack(Items.TORCH, 16));
        List<BlockPos> stairs = carve(h, c);
        BlockPos at = stairs.get(STAIRS), left = stairs.get(STAIRS - 1);
        h.getLevel().setBlock(left, Blocks.CRAFTING_TABLE.defaultBlockState(), 2 | 16);
        c.mineSite().temporary(left);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        start(h, c);
        h.succeedWhen(() -> {
            h.assertTrue(c.mineSite().stands().size() > STAIRS, "The bench he cannot take holds the order: " + c.mineSite().stands().size() + " stands | " + report(c));
            h.assertTrue(tables(h) == 1 && c.mineSite().temporary().contains(left), "The bench left standing is not in the record: " + c.mineSite().temporary());
        });
    }

    /** The remembered bench is a cell in the ground closed on every side, four steps away: there is no walk to it, and with logs in the bag he makes a new one instead of saying there is no wood. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void makesANewBenchWhenTheKnownOneIsWalledOff(GameTestHelper h) {
        mountain(h);
        CompanionEntity c = rig(h);
        c.setItem(6, new ItemStack(Items.OAK_LOG, 5));
        BlockPos bench = new BlockPos(CENTER + 4, GROUND - 1, CENTER);
        for (Direction side : Direction.values()) h.setBlock(bench.relative(side), Blocks.BEDROCK);
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        c.rememberPlaced(h.absolutePos(bench));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        CompanionActionResult order = CompanionEvents.executeAction(player, "craft_item", "minecraft:stone_pickaxe", 1);
        h.assertTrue(order.success(), "Craft order not accepted: " + order.message());
        h.succeedWhen(() -> {
            h.assertTrue(!c.isWorking(), "Still working | " + report(c));
            h.assertTrue(c.didLastTaskSucceed() && count(c, Items.STONE_PICKAXE) == 1, "No pickaxe from a new bench | " + report(c));
        });
    }

    private static long start(GameTestHelper h, CompanionEntity c) {
        h.assertTrue(c.startWork("dig_for_diamonds", "", 1).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        return c.activeWorkId();
    }

    private static void probe(GameTestHelper h, boolean protectsReturn, boolean buried) {
        mountain(h);
        CompanionEntity c = rig(h);
        // The way back is protected from the surface, where the autonomy remembers its workplace.
        if (protectsReturn) c.autonomy().setEnabled(true);
        List<BlockPos> stairs = carve(h, c);
        BlockPos at = stairs.get(STAIRS);
        c.teleportTo(at.getX() + .5, at.getY(), at.getZ() + .5);
        if (buried) {
            // No spare iron pickaxe to dig the rock with instead: the stone one is the only way on.
            c.setItem(0, ItemStack.EMPTY);
            c.mineSite().restart(at, Direction.NORTH);
            c.mineSite().buried(true);
        }
        long id = start(h, c);
        double[] highest = {at.getY()};
        h.onEachTick(() -> {
            highest[0] = Math.max(highest[0], c.getY());
            boolean made = count(c, Items.STONE_PICKAXE) >= 1, benchBack = count(c, Items.CRAFTING_TABLE) == 1 && tables(h) == 0;
            if (!c.isWorking() && !c.isGatheringTask()) {
                CompanionActionResult result = c.workResult(id);
                h.assertTrue(made && benchBack, "The order ended without the pickaxe and the bench in the bag: " + (result == null ? "no result" : result.status() + " " + result.message()) + " | " + report(c));
            }
            if (!made || !benchBack) return;
            h.assertTrue(highest[0] <= at.getY() + .6, "He climbed to " + highest[0] + " from " + at.getY() + " | " + report(c));
            for (BlockPos stand : stairs.subList(1, stairs.size())) h.assertTrue(CompanionShaftPlan.columnOpen(h.getLevel(), stand), "A step is shut at " + rel(h, stand).toShortString() + " | " + report(c));
            h.succeed();
        });
    }

    /** The stairs down to {@link #STAIRS} dug and recorded, a stone lying on every step like those he tosses; the rock ahead is not dug. */
    private static List<BlockPos> carve(GameTestHelper h, CompanionEntity c) {
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, STAIRS);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        c.mineSite().start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) c.mineSite().reached(stand);
        for (BlockPos stand : stairs) {
            ItemEntity stone = new ItemEntity(level, stand.getX() + .5, stand.getY() + .35, stand.getZ() + .5, new ItemStack(Items.COBBLESTONE));
            stone.setThrower(c);
            stone.setDeltaMovement(0, 0, 0);
            stone.setPickUpDelay(SETTLED);
            level.addFreshEntity(stone);
        }
        return stairs;
    }

    private static int tables(GameTestHelper h) {
        int tables = 0;
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) for (int y = 1; y <= GROUND + 1; y++) if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.CRAFTING_TABLE)) tables++;
        return tables;
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; deepslate diamond ore in a ring under the tunnel floor. */
    private static void mountain(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : y == DIAMOND_FEET - 1 && Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER)) >= RING_FROM ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    /** No stone pickaxe anywhere, an iron one kept for ore; cobblestone, sticks and a bench in the bag. */
    private static CompanionEntity rig(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        ItemStack[] bag = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.STICK, STICKS), new ItemStack(Items.CRAFTING_TABLE)};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        return companion;
    }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    private static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
