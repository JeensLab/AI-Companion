package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import dev.aicompanion.entity.CompanionShaftPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A dead end on the staircase is not the end of the mine: he backs up his own stairs, plugs the way behind him and branches
 * off sideways, and the record of caves and counters that makes the choice survives a save. Arena as in the other mine tests:
 * origin at Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), diamond ore under the tunnel floor far from the middle.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineBacktrackTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2, RING_FROM = 12, TIMEOUT = 12000, DEAD_END = 12;

    private CompanionMineBacktrackTests() { }

    /** Bedrock closes the stairs ahead of stand 12 and both sides of it: he goes back to stand 11, plugs stand 12 and turns off there. */
    @GameTest(template = "marathon_cave", timeoutTicks = TIMEOUT)
    public static void backsUpAndBranchesFromADeadEnd(GameTestHelper h) {
        mountain(h);
        BlockPos dead = under(DEAD_END).above(), before = under(DEAD_END - 1).above();
        for (int y = -1; y <= 1; y++) h.setBlock(dead.north().above(y), Blocks.BEDROCK);
        for (int y = 0; y <= 2; y++) {
            h.setBlock(dead.west().above(y), Blocks.BEDROCK);
            h.setBlock(dead.east().above(y), Blocks.BEDROCK);
        }
        CompanionEntity c = rig(h);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.success() && count(c, Items.DIAMOND) >= 1, "The dead end stopped the mine: " + report(c));
            h.assertTrue(!h.getBlockState(dead.above()).isAir(), "No plug behind him at " + dead.above().toShortString() + " | " + report(c));
            h.assertTrue(c.mineSite().stands().contains(h.absolutePos(before.west())) || c.mineSite().stands().contains(h.absolutePos(before.east())), "No branch off stand 11: " + c.mineSite().stands().size() + " stands | " + report(c));
            h.assertTrue(c.mineSite().backtracks() == 1, "Backtracks " + c.mineSite().backtracks());
        });
    }

    /** Caves, counters and a restart survive a save; a save without them loads as before; a flat stand of a tunnel base is two cells and stays. */
    @GameTest(template = "marathon_cave")
    public static void mineSiteKeepsCavesAndCountersOverASaveAndAFlatStand(GameTestHelper h) {
        mountain(h);
        var level = h.getLevel();
        BlockPos entrance = h.absolutePos(new BlockPos(CENTER, GROUND + 1, CENTER));
        List<BlockPos> stairs = CompanionShaftPlan.stands(entrance, Direction.NORTH, 10);
        for (BlockPos stand : stairs) for (BlockPos cell : CompanionShaftPlan.column(stand)) level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2 | 16);
        CompanionMineSite site = new CompanionMineSite();
        site.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) site.reached(stand);
        BlockPos inside = stairs.get(4).offset(1, 0, 0), outside = stairs.get(4).offset(9, 0, 0);
        site.shun(stairs.get(4).offset(-1, 0, 0), stairs.get(4).offset(1, 0, 0));
        site.backtracked();
        site.backtracked();
        site.retreated();
        h.assertTrue(site.shunned(inside) && !site.shunned(outside) && site.firstShunned() == 3, "Shun box: first shunned stand " + site.firstShunned());
        h.assertTrue(site.avoid().shunned().test(inside) && !site.avoid().shunned().test(outside), "avoid() does not show the caves");
        CompoundTag tag = new CompoundTag();
        site.save(tag);
        CompanionMineSite loaded = loadedFrom(tag);
        h.assertTrue(loaded.shunned(inside) && !loaded.shunned(outside) && loaded.backtracks() == 2 && loaded.retreats() == 1 && loaded.restarts() == 0,
                "The caves or counters were lost over the save: " + loaded.backtracks() + "/" + loaded.retreats());
        for (int i = 0; i < 40; i++) loaded.shun(entrance.offset(i * 20, 0, 0), entrance.offset(i * 20 + 1, 1, 1));
        h.assertTrue(!loaded.shunned(inside) && loaded.shunned(entrance.offset(39 * 20, 0, 0)) && !loaded.shunned(entrance.offset(23 * 20, 0, 0)), "The oldest cave is not the one dropped past the cap");

        CompanionMineSite fresh = new CompanionMineSite();
        fresh.start(entrance, Direction.NORTH);
        CompoundTag plain = new CompoundTag();
        fresh.save(plain);
        CompoundTag record = plain.getCompound(CompanionMineSite.KEY);
        h.assertTrue(!record.contains("Caves") && !record.contains("Backtracks") && !record.contains("Retreats") && !record.contains("Restarts"), "An empty memory is saved");
        CompanionMineSite fromOld = loadedFrom(plain);
        h.assertTrue(fromOld.exists() && !fromOld.shunned(inside) && fromOld.firstShunned() == -1 && fromOld.backtracks() == 0 && fromOld.restarts() == 0, "A save without the keys loads wrong");

        BlockPos here = entrance.offset(0, -8, 3);
        site.restart(here, Direction.WEST);
        h.assertTrue(site.entrance().equals(here) && site.stands().isEmpty() && site.shunned(inside) && site.restarts() == 1 && site.backtracks() == 0 && site.stairDirection() == Direction.WEST,
                "restart() did not start over at the spot keeping the caves: restarts " + site.restarts());
        site.clear();
        h.assertTrue(!site.shunned(inside) && site.restarts() == 0, "clear() keeps the memory");

        CompanionMineSite flat = new CompanionMineSite();
        flat.start(entrance, Direction.NORTH);
        for (BlockPos stand : stairs.subList(1, stairs.size())) flat.reached(stand);
        flat.openTunnel(Direction.WEST);
        BlockPos foot = flat.foot();
        for (int k = 1; k <= 3; k++) {
            level.setBlock(foot.relative(Direction.WEST, k), Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(foot.relative(Direction.WEST, k).above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        h.assertTrue(flat.rebase(3, Direction.WEST) && flat.stands().size() == 13 && flat.tunnels().isEmpty() && flat.foot().equals(foot.relative(Direction.WEST, 3)), "rebase() put the base at " + flat.foot());
        h.assertTrue(!flat.rebase(600, Direction.WEST) && flat.stands().size() == 13, "rebase() went past the cap");
        h.assertTrue(flat.verify(level) == 13, "A flat stand of two open cells was cut: " + flat.stands().size());
        level.setBlock(foot.relative(Direction.WEST, 2).above(), Blocks.STONE.defaultBlockState(), 2 | 16);
        h.assertTrue(flat.verify(level) == 11, "A flat stand with a closed head was kept: " + flat.stands().size());
        h.succeed();
    }

    private static CompanionMineSite loadedFrom(CompoundTag tag) {
        CompanionMineSite site = new CompanionMineSite();
        site.load(tag);
        return site;
    }

    /** The floor cell under stand {@code step}. */
    private static BlockPos under(int step) { return new BlockPos(CENTER, GROUND - step, CENTER - step); }

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
