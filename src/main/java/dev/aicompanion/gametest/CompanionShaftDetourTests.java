package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * Where the staircase meets a hollow, a liquid pocket or gravel, it goes on instead of refusing: the floor is laid under the next
 * step, another turn is taken, and a relaunch after a refusal does not repeat the move that failed. Arena as in the other mine tests:
 * origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), diamond ore under the tunnel floor far from the middle.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionShaftDetourTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, RING_FROM = 12, TIMEOUT = 12000, UNKILLABLE = 1024;
    private static final String FLOOR_REFUSAL = "неустойчив";

    /** A 7x7x5 empty dungeon under the stairs: the steps that fall into it have no floor, he lays one block after another down to the dungeon's bottom. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void goesDownThroughAnEmptyDungeonUnderTheStairsLayingTheFloor(GameTestHelper h) {
        mountain(h);
        carve(h, 21, 27, 7, 11, 9, 15);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond after the dungeon: " + report(c));
            h.assertTrue(!result.message().contains(FLOOR_REFUSAL), "Refused for the floor: " + report(c));
            for (int k = 12; k <= 14; k++) h.assertTrue(!h.getBlockState(under(k)).isAir(), "No floor under stand " + k + " at " + under(k).toShortString() + " | " + report(c));
        });
    }

    /** A cave 29 wide and ten high: the way down is nothing but air under every step for nine steps, and no turn offers any ground. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void crossesAWideCaveLayingTheFloorUnderEachStep(GameTestHelper h) {
        mountain(h);
        carve(h, 10, 38, 4, 13, 4, 22);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond after the cave: " + report(c));
            h.assertTrue(!result.message().contains(FLOOR_REFUSAL), "Refused for the floor: " + report(c));
            h.assertTrue(!h.getBlockState(under(15)).isAir(), "No floor under stand 15 | " + report(c));
        });
    }

    /** Lava sits where the next step's floor and both turns' floors would be, hidden in the rock: he opens the step, lays a block over the lava and walks on without burning. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void laysABlockOverLavaUnderTheStairInsteadOfRefusing(GameTestHelper h) {
        mountain(h);
        BlockPos straight = under(12), west = new BlockPos(22, 9, 13), east = new BlockPos(26, 9, 13);
        for (BlockPos lava : new BlockPos[]{straight, west, east}) h.setBlock(lava, Blocks.LAVA);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.onEachTick(() -> h.assertTrue(!c.isOnFire() && c.getRemainingFireTicks() <= 0 && !c.isInLava(), "Burning at " + c.blockPosition().toShortString() + " | " + report(c)));
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond past the lava: " + report(c));
            var state = h.getBlockState(straight);
            h.assertTrue(!state.isAir() && state.getFluidState().isEmpty(), "Lava under the stair not covered: " + state + " | " + report(c));
            h.assertTrue(result.success(), "Ended without success: " + report(c));
        });
    }

    /** Gravel over the head cell of the next step and over the first step along either turn: the turn is taken anyway and the next one after it. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void goesAroundGravelOverTheNextCell(GameTestHelper h) {
        mountain(h);
        for (BlockPos gravel : new BlockPos[]{new BlockPos(24, 13, 12), new BlockPos(22, 13, 13), new BlockPos(26, 13, 13)}) h.setBlock(gravel, Blocks.GRAVEL);
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(count(c, Items.DIAMOND) >= 1, "No diamond past the gravel: " + report(c));
            h.assertTrue(result.success(), "Ended without success: " + report(c));
        });
    }

    /**
     * Straight on is closed by gravel, the first turn (west, the left on a tie) leads into a pocket with two zombies and is plugged, so the
     * first launch ends. The relaunch must not take west again: east is free.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 2 * TIMEOUT)
    public static void takesAnotherMoveWhenRelaunchedAfterARefusal(GameTestHelper h) {
        mountain(h);
        h.setBlock(new BlockPos(24, 13, 12), Blocks.GRAVEL);
        carve(h, 20, 22, 10, 12, 12, 14);
        for (int x : new int[]{21, 22}) {
            Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(x, 10, 13));
            zombie.setNoAi(true);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(UNKILLABLE);
            zombie.setHealth(UNKILLABLE);
            zombie.setPersistenceRequired();
        }
        CompanionEntity c = rig(h);
        long first = start(h, c, 1);
        boolean[] relaunched = {false};
        long[] second = {0};
        h.succeedWhen(() -> {
            if (!relaunched[0]) {
                CompanionActionResult refused = ended(h, c, first);
                h.assertTrue(!refused.success() && count(c, Items.DIAMOND) == 0, "SETUP: the first launch should end in a refusal at the pocket: stands "
                        + c.mineSite().stands().subList(Math.max(0, c.mineSite().stands().size() - 3), c.mineSite().stands().size()) + ", plug " + c.mineSite().plug() + " | " + report(c));
                relaunched[0] = true;
                second[0] = start(h, c, 1);
                h.fail("Relaunched, waiting for the second end");
            }
            CompanionActionResult result = ended(h, c, second[0]);
            h.assertTrue(count(c, Items.DIAMOND) >= 1 && result.success(), "The relaunch repeated the move that failed: " + report(c));
        });
    }

    /** The refused moves and forbidden cells survive a save, an old save without them loads as before, the oldest are dropped past the cap, and clear forgets them. */
    @GameTest(template = "marathon_cave")
    public static void mineSiteKeepsRefusedMovesAndForbiddenCellsOverASave(GameTestHelper h) {
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER)), stand = entrance.below(3), cell = entrance.offset(3, -5, 2);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        site.refuse(stand, Direction.NORTH);
        site.forbid(cell);
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = loadedFrom(tag);
        h.assertTrue(loaded.refused(stand, Direction.NORTH) && loaded.forbidden(cell), "A refusal or a forbidden cell was lost over the save");
        h.assertTrue(!loaded.refused(stand, Direction.EAST) && !loaded.refused(stand.below(), Direction.NORTH) && !loaded.forbidden(cell.above()), "Another move counts as refused");
        h.assertTrue(loaded.avoid().forbidden().contains(cell) && loaded.avoid().refused().test(stand, Direction.NORTH), "avoid() does not show the memory");
        for (int i = 0; i < 200; i++) loaded.refuse(entrance.offset(i, 0, 0), Direction.WEST);
        h.assertTrue(!loaded.refused(stand, Direction.NORTH) && loaded.refused(entrance.offset(199, 0, 0), Direction.WEST), "The oldest refusal is not the one dropped past the cap");
        CompanionMineSite plain = new CompanionMineSite();
        plain.start(entrance, Direction.NORTH);
        CompoundTag old = new CompoundTag();
        plain.save(old);
        h.assertTrue(!old.getCompound(CompanionMineSite.KEY).contains("Refused") && !old.getCompound(CompanionMineSite.KEY).contains("Forbidden"), "An empty memory is saved");
        CompanionMineSite fromOld = loadedFrom(old);
        h.assertTrue(fromOld.exists() && !fromOld.refused(stand, Direction.NORTH) && !fromOld.forbidden(cell), "A save without the keys loads wrong");
        site.clear();
        h.assertTrue(!site.refused(stand, Direction.NORTH) && !site.forbidden(cell), "clear() keeps the memory");
        h.succeed();
    }

    /** A stand whose floor is gone is where the recorded staircase ends, as one whose column is closed. */
    @GameTest(template = "marathon_cave")
    public static void mineSiteIsCutBackAtAStandWithoutAFloor(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stands = CompanionShaftPlan.stands(entrance, Direction.NORTH, 10);
        for (BlockPos stand : stands) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stands.subList(1, stands.size())) site.reached(stand);
        h.assertTrue(site.verify(level) == 10, "A staircase with floors was cut to " + site.stands().size());
        level.setBlock(stands.get(6).below(), Blocks.AIR.defaultBlockState(), 2 | 16);
        int kept = site.verify(level);
        h.assertTrue(kept == 5 && site.foot().equals(stands.get(5)), "Cut at the missing floor of stand 6 left " + kept);
        h.succeed();
    }

    /** A turn that needs nothing placed beats one that needs a floor, which beats one whose next step is blocked; a refused or forbidden side is out. */
    @GameTest(template = "marathon_cave")
    public static void turnsAreWeighedByWhatTheyNeedPlaced(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        BlockPos stand = h.absolutePos(new BlockPos(CENTER, 12, CENTER)), westLanding = stand.west(), eastLanding = stand.east();
        Set<BlockPos> own = Set.of();
        CompanionShaftPlan.Avoid none = CompanionShaftPlan.Avoid.NONE;
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, stand, Direction.NORTH, own, none) == Direction.WEST, "Two free sides: the left on a tie");
        level.setBlock(westLanding.below(), Blocks.AIR.defaultBlockState(), 2 | 16);
        h.assertTrue(CompanionShaftPlan.turnCost(level, stand, Direction.WEST, own, none) == 1 && CompanionShaftPlan.turnCost(level, stand, Direction.EAST, own, none) == 0, "Costs of a floor to lay and a free side");
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, stand, Direction.NORTH, own, none) == Direction.EAST, "A free side did not beat a side that needs a floor");
        level.setBlock(eastLanding.east().above(2), Blocks.GRAVEL.defaultBlockState(), 2 | 16);
        h.assertTrue(CompanionShaftPlan.turnCost(level, stand, Direction.EAST, own, none) >= 100, "A side with a blocked next step is not a last resort");
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, stand, Direction.NORTH, own, none) == Direction.WEST, "A side that needs a floor did not beat a blocked next step");
        var refusesWest = new CompanionShaftPlan.Avoid(Set.of(), (at, d) -> d == Direction.WEST);
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, stand, Direction.NORTH, own, refusesWest) == Direction.EAST, "The last resort was not taken when the other side is refused");
        var noWay = new CompanionShaftPlan.Avoid(Set.of(eastLanding), (at, d) -> d == Direction.WEST);
        h.assertTrue(CompanionShaftPlan.chooseTurn(level, stand, Direction.NORTH, own, noWay) == null, "A refused side and a forbidden landing still gave a turn");
        h.succeed();
    }

    private static CompanionMineSite loadedFrom(CompoundTag tag) {
        CompanionMineSite site = new CompanionMineSite();
        site.load(tag);
        return site;
    }

    private static BlockPos under(int step) { return new BlockPos(CENTER, GROUND - step, CENTER - step); }

    private static void carve(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; deepslate diamond ore in a ring under the tunnel floor. */
    private static void mountain(GameTestHelper h) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            int ring = Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER));
            Block block = y == 0 || edge ? Blocks.BEDROCK
                    : y == DIAMOND_FEET - 1 && ring >= RING_FROM ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static CompanionEntity rig(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 16),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64)};
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        return companion;
    }

    private static long start(GameTestHelper h, CompanionEntity c, int count) {
        h.assertTrue(c.startWork("dig_for_diamonds", "", count).success(), "Diamond mine not accepted: " + c.getLastTaskResult());
        return c.activeWorkId();
    }

    private static CompanionActionResult ended(GameTestHelper h, CompanionEntity c, long id) {
        h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult());
        CompanionActionResult result = c.workResult(id);
        h.assertTrue(result != null, "No result for the order: " + report(c));
        return result;
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
