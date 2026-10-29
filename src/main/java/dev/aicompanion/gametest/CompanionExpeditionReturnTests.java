package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionChunkTicket;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Leaving the diamond mine by his own staircase: when the stone pickaxes run out, after a reload in the middle of a tunnel,
 * and the chunk ticket when he is removed without dying. Arena as in the expedition tests: origin at Y -60, Oleg picks NORTH at
 * (24,22,24), stand k is (24,22-k,24-k), the tunnel feet at relative height 2.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionExpeditionReturnTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2;
    private static final int RING_FROM = 21, NO_RING = 99, SURFACE = GROUND + 1, NEAR = 6, SCAN_EVERY = 5;
    private static final int WORN_USES_LEFT = 45, TUNNEL_CELLS = 3, RELOAD_TIMEOUT = 12000, TUNNEL_HEAD = DIAMOND_FEET + 1;
    private static final String CLIMBED = "поднялся за припасами";

    private record Rig(CompanionEntity c, ServerPlayer owner) { }

    /** One worn stone pickaxe: when it breaks he walks up his own stairs, digs nothing new on the way, and says he came up for supplies. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void climbsHisOwnStairsWhenPickaxesRunOut(GameTestHelper h) {
        Rig r = rig(h, NO_RING, standard(16));
        CompanionEntity c = r.c();
        ItemStack worn = new ItemStack(Items.STONE_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - WORN_USES_LEFT);
        c.setItemSlot(EquipmentSlot.MAINHAND, worn);
        c.setItem(0, ItemStack.EMPTY);
        long id = start(h, c, 1);
        AtomicReference<Set<BlockPos>> airWhenGone = new AtomicReference<>();
        h.onEachTick(() -> {
            CompanionActionResult result = c.workResult(id);
            if (result != null && !result.message().contains(CLIMBED)) h.fail("Ended without climbing out: " + result + " | at " + rel(h, c.blockPosition()).toShortString());
            if (airWhenGone.get() == null) {
                if (count(c, Items.STONE_PICKAXE) == 0) airWhenGone.set(airCells(h));
            } else if (h.getTick() % SCAN_EVERY == 0) {
                Set<BlockPos> added = airCells(h);
                added.removeAll(airWhenGone.get());
                h.assertTrue(added.isEmpty(), "Dug " + added.size() + " new cells after the pickaxe was gone, first " + first(added) + " | " + report(c));
            }
        });
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.message().contains(CLIMBED), "Result does not say he came up for supplies: " + result);
            BlockPos feet = rel(h, c.blockPosition());
            h.assertTrue(feet.getY() >= SURFACE && Math.abs(feet.getX() - CENTER) <= 3 && Math.abs(feet.getZ() - CENTER) <= 3, "Not on the surface at the entrance: " + feet.toShortString());
            h.assertTrue(count(c, Items.STONE_PICKAXE) == 0, "A stone pickaxe is left, the run did not exhaust it: " + report(c));
            h.assertTrue(count(c, Items.IRON_PICKAXE) == 1 && damage(c, Items.IRON_PICKAXE) <= 2, "Iron pickaxe used on stone, damage " + damage(c, Items.IRON_PICKAXE));
            h.assertTrue(tallestAirColumn(h) <= 4, "A vertical shaft instead of the staircase: " + tallestAirColumn(h));
        });
    }

    /**
     * Reloaded in the middle of a tunnel: he keeps his mine record, goes to the owner by his stairs without opening a new way up,
     * and an order to dig again takes him down the same stairs to continue the tunnel.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = RELOAD_TIMEOUT)
    public static void afterReloadMidTunnelWalksOutByHisStairsAndResumes(GameTestHelper h) {
        Rig r = rig(h, NO_RING, standard(16, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE)));
        AtomicReference<CompanionEntity> current = new AtomicReference<>(r.c());
        start(h, r.c(), 16);
        int[] phase = {0};
        AtomicReference<Set<BlockPos>> atReload = new AtomicReference<>();
        AtomicReference<BlockPos> entrance = new AtomicReference<>();
        h.runAtTickTime(RELOAD_TIMEOUT - 5, () -> h.fail("Stuck in phase " + phase[0] + " at " + (current.get() == null ? "reload" : rel(h, current.get().blockPosition()).toShortString())
                + " | " + (current.get() == null ? "" : report(current.get()))));
        h.onEachTick(() -> {
            CompanionEntity c = current.get();
            if (c == null || h.getTick() % SCAN_EVERY != 0) return;
            BlockPos feet = rel(h, c.blockPosition());
            if (phase[0] == 0) {
                int advanced = c.mineSite().tunnels().stream().mapToInt(CompanionMineSite.Tunnel::length).sum();
                if (feet.getY() != DIAMOND_FEET || advanced < TUNNEL_CELLS) return;
                atReload.set(airCells(h));
                entrance.set(c.mineSite().entrance());
                phase[0] = 1;
                current.set(null);
                reload(h, c, replacement -> { current.set(replacement); phase[0] = 2; });
                return;
            }
            h.assertTrue(c.mineSite().exists() && c.mineSite().entrance().equals(entrance.get()), "Mine record lost or moved after the reload: " + c.mineSite().entrance());
            Set<BlockPos> added = airCells(h);
            added.removeAll(atReload.get());
            if (phase[0] == 2) {
                h.assertTrue(added.isEmpty(), "Opened a new way up after the reload: " + added.size() + " cells, first " + first(added) + " | " + report(c));
                if (feet.getY() < SURFACE || Math.abs(feet.getX() - CENTER) > NEAR || Math.abs(feet.getZ() - CENTER) > NEAR) return;
                start(h, c, 16);
                phase[0] = 3;
                return;
            }
            for (BlockPos cell : added) h.assertTrue(cell.getY() >= DIAMOND_FEET && cell.getY() <= TUNNEL_HEAD, "New cell outside the tunnel after resuming: " + cell.toShortString() + " | " + report(c));
            if (feet.getY() == DIAMOND_FEET && !added.isEmpty()) h.succeed();
        });
    }

    /** Removed from the world in the middle of the dig, neither dead nor told to stop: the chunk is let go all the same. */
    @GameTest(template = "marathon_cave", timeoutTicks = 600)
    public static void releasesTheChunkWhenRemovedWithoutDeath(GameTestHelper h) {
        CompanionEntity c = rig(h, RING_FROM, standard(16)).c();
        UUID id = c.getUUID();
        start(h, c, 1);
        h.runAfterDelay(100, () -> {
            h.assertTrue(c.isWorking() && CompanionChunkTicket.held(id), "Not digging with a ticket after 100 ticks: " + report(c));
            c.discard();
        });
        h.runAfterDelay(110, () -> {
            boolean kept = CompanionChunkTicket.held(id);
            CompanionChunkTicket.release(id);
            h.assertTrue(!kept, "Chunk ticket kept after the companion was removed");
            h.succeed();
        });
    }

    /** Saved, removed and loaded a tick later as a new entity with the same UUID, as the game does when the world is left and entered. */
    private static void reload(GameTestHelper h, CompanionEntity old, Consumer<CompanionEntity> after) {
        CompoundTag saved = new CompoundTag();
        old.saveWithoutId(saved);
        old.discard();
        CompanionChunkTicket.release(old.getUUID());
        h.runAfterDelay(1, () -> {
            CompanionEntity replacement = AICompanionMod.COMPANION.get().create(h.getLevel());
            replacement.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(replacement), "Reloaded companion rejected");
            after.accept(replacement);
        });
    }

    /** Relative positions of the air cells inside the mountain. */
    private static Set<BlockPos> airCells(GameTestHelper h) {
        Set<BlockPos> air = new HashSet<>();
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) for (int y = 1; y <= GROUND; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (h.getBlockState(pos).isAir()) air.add(pos);
        }
        return air;
    }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    private static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    private static String first(Set<BlockPos> cells) { return cells.isEmpty() ? "none" : cells.iterator().next().toShortString(); }

    private static int tallestAirColumn(GameTestHelper h) {
        int tallest = 0;
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) {
            int run = 0;
            for (int y = 1; y < GROUND; y++) {
                run = h.getBlockState(new BlockPos(x, y, z)).isAir() ? run + 1 : 0;
                tallest = Math.max(tallest, run);
            }
        }
        return tallest;
    }

    /** Stone pickaxe in hand and in the bag, iron pickaxe, the given torches and bread, then the extras. */
    private static ItemStack[] standard(int torches, ItemStack... extra) {
        List<ItemStack> bag = new ArrayList<>(List.of(new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8)));
        if (torches > 0) bag.add(new ItemStack(Items.TORCH, torches));
        bag.addAll(List.of(extra));
        return bag.toArray(new ItemStack[0]);
    }

    private static Rig rig(GameTestHelper h, int ringFrom, ItemStack... bag) {
        mountain(h, ringFrom);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        ServerPlayer owner = bind(h, companion);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        for (int slot = 0; slot < bag.length; slot++) companion.setItem(slot, bag[slot]);
        return new Rig(companion, owner);
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

    private static void mountain(GameTestHelper h, int ringFrom) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            int ring = Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER));
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : y == DIAMOND_FEET - 1 && ring >= ringFrom ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    private static ServerPlayer bind(GameTestHelper h, CompanionEntity companion) {
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        return owner;
    }

    private static String report(CompanionEntity companion) { return companion.getDeedsForAi() + " | " + companion.getLastTaskResult() + " | bag: " + companion.getStoredItemsSummary(); }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }

    private static int damage(CompanionEntity companion, Item item) {
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) return companion.getItem(slot).getDamageValue();
        return companion.getMainHandItem().is(item) ? companion.getMainHandItem().getDamageValue() : -1;
    }
}
