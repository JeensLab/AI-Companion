package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionChunkTicket;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The expedition of the diamond mine under hazards and bookkeeping: lava and water beside the way, the last pickaxe, torches,
 * a bag under load, the chunk ticket on every end, and the ore against the diamonds. Arena as in the acceptance test: origin at
 * Y -60, Oleg picks NORTH at (24,22,24), stand k is (24,22-k,24-k), the tunnel feet at relative height 2.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionExpeditionTests {
    private static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2;
    private static final int RING_FROM = 21, NO_RING = 99, LAVA_STEP = 8, JUNK_SLOTS = 17, FIRST_JUNK = 6;
    private static final BlockPos WATER = new BlockPos(25, 4, 3);
    private static final String NO_PICKAXES = "кирок не осталось", STALL = "Полторы минуты";

    private record Rig(CompanionEntity c, ServerPlayer owner) { }

    /** A lava source open only from above, beside the column he opens next and in sight from his stand: it is plugged before that column is opened. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void sealsSeenLavaBesideTheStairsBeforeOpeningTheNextColumn(GameTestHelper h) {
        Rig r = rig(h, RING_FROM, standard(16));
        CompanionEntity c = r.c();
        BlockPos stand = stand(LAVA_STEP), lava = stand.offset(-1, -1, -1);
        for (BlockPos air : List.of(stand.west(), stand.north(), stand.offset(-1, 0, -1))) h.setBlock(air, Blocks.AIR);
        h.setBlock(lava, Blocks.LAVA);
        List<BlockPos> guarded = List.of(stand(LAVA_STEP + 1), stand(LAVA_STEP + 1).above(2));
        long id = start(h, c, 1);
        h.onEachTick(() -> {
            h.assertTrue(!c.isOnFire() && c.getRemainingFireTicks() <= 0 && !c.isInLava(), "Burning at " + c.blockPosition().toShortString() + " | " + report(c));
            if (h.getBlockState(lava).is(Blocks.LAVA))
                for (BlockPos cell : guarded) h.assertTrue(!h.getBlockState(cell).isAir(), "Opened " + cell.toShortString() + " beside unsealed lava at " + lava.toShortString());
        });
        h.succeedWhen(() -> {
            ended(h, c, id);
            var state = h.getBlockState(lava);
            h.assertTrue(!state.is(Blocks.LAVA) && !state.isAir() && state.getFluidState().isEmpty(), "Lava source not replaced by a block: " + state + " | " + report(c));
        });
    }

    /** Water inside the rock above a tunnel cell: the cell is never opened under it, or the water is plugged first; no water in the mine. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void keepsWaterAboveTheTunnelOutOfTheMine(GameTestHelper h) {
        Rig r = rig(h, NO_RING, standard(16));
        CompanionEntity c = r.c();
        for (int[] ore : new int[][]{{22, 4}, {22, 3}, {26, 4}, {26, 5}}) h.setBlock(new BlockPos(ore[0], DIAMOND_FEET - 1, ore[1]), Blocks.DEEPSLATE_DIAMOND_ORE);
        h.setBlock(WATER, Blocks.WATER);
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            ended(h, c, id);
            for (BlockPos pos : blocks(h, Blocks.WATER)) h.assertTrue(pos.equals(WATER), "Water in the mine at " + pos.toShortString() + " | " + report(c));
            if (h.getBlockState(WATER).is(Blocks.WATER))
                for (var side : Direction.values())
                    h.assertTrue(!h.getBlockState(WATER.relative(side)).isAir(), "Water source opened to the air at " + WATER.relative(side).toShortString());
        });
    }

    /** One stone pickaxe with three uses left: when it breaks the step ends saying he is out of pickaxes; the iron one never digs stone. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void endsOutOfPickaxesAndKeepsTheIronOneForOre(GameTestHelper h) {
        Rig r = lastPickaxe(h);
        CompanionEntity c = r.c();
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(result.message().contains(NO_PICKAXES), "Result does not say the pickaxes are gone: " + result);
            h.assertTrue(count(c, Items.STONE_PICKAXE) == 0, "A stone pickaxe is left: " + report(c));
            h.assertTrue(count(c, Items.IRON_PICKAXE) == 1 && damage(c, Items.IRON_PICKAXE) <= 2, "Iron pickaxe used on stone, damage " + damage(c, Items.IRON_PICKAXE));
        });
    }

    /** No torches, but coal and sticks: he crafts some and lays them; the ones in the world and in the bag come from the coal he used. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void craftsTorchesFromCoalAndSticksAndLaysThem(GameTestHelper h) {
        Rig r = rig(h, RING_FROM, standard(0, new ItemStack(Items.COAL, 2), new ItemStack(Items.STICK, 2)));
        CompanionEntity c = r.c();
        long id = start(h, c, 1);
        h.succeedWhen(() -> {
            ended(h, c, id);
            int crafted = 4 * (2 - count(c, Items.COAL)), total = torches(h) + count(c, Items.TORCH) + ground(h, Items.TORCH);
            h.assertTrue(torches(h) >= 1, "No torch laid: " + report(c));
            h.assertTrue(total <= crafted && 2 - count(c, Items.STICK) == 2 - count(c, Items.COAL), "Torches " + total + " from " + crafted + " crafted; sticks left " + count(c, Items.STICK));
        });
    }

    /** A full-depth dig through lots of rock with a nearly full bag: never the stall watchdog, never an overfull stack, the valuables stay. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void longDigWithFullBagNeverStallsAndKeepsValuables(GameTestHelper h) {
        Item[] junk = {Items.POPPY, Items.DANDELION, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP,
                Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY, Items.APPLE, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.SUGAR, Items.PAPER};
        Rig r = rig(h, RING_FROM, standard(16, new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.IRON_INGOT, 8)));
        CompanionEntity c = r.c();
        for (int i = 0; i < JUNK_SLOTS; i++) c.setItem(FIRST_JUNK + i, new ItemStack(junk[i]));
        long id = start(h, c, 1);
        h.onEachTick(() -> {
            for (int slot = 0; slot < c.getContainerSize(); slot++)
                h.assertTrue(c.getItem(slot).getCount() <= c.getItem(slot).getMaxStackSize(), "Overfull stack in slot " + slot + ": " + c.getItem(slot));
            h.assertTrue(count(c, Items.DIAMOND) >= 3 && count(c, Items.IRON_INGOT) >= 8, "Valuables lost: " + report(c));
        });
        h.succeedWhen(() -> {
            CompanionActionResult result = ended(h, c, id);
            h.assertTrue(!result.message().contains(STALL), "Ended by the stall watchdog: " + result);
        });
    }

    /** The chunk is held while the step runs and let go of when it ends by its own result. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void holdsTheChunkWhileRunningAndReleasesItAtTheResult(GameTestHelper h) {
        CompanionEntity c = lastPickaxe(h).c();
        long id = start(h, c, 1);
        h.assertTrue(CompanionChunkTicket.held(c.getUUID()), "No chunk ticket right after the order was accepted");
        h.onEachTick(() -> h.assertTrue(c.isWorking() == CompanionChunkTicket.held(c.getUUID()), "Working " + c.isWorking() + " but ticket " + CompanionChunkTicket.held(c.getUUID())));
        h.succeedWhen(() -> {
            ended(h, c, id);
            h.assertTrue(!CompanionChunkTicket.held(c.getUUID()), "Chunk ticket still held after the result");
        });
    }

    /** «Стой» in the middle of the dig lets the chunk go. */
    @GameTest(template = "marathon_cave", timeoutTicks = 600)
    public static void releasesTheChunkWhenToldToStop(GameTestHelper h) {
        Rig r = rig(h, RING_FROM, standard(16));
        CompanionEntity c = r.c();
        start(h, c, 1);
        h.runAfterDelay(100, () -> {
            h.assertTrue(c.isWorking() && CompanionChunkTicket.held(c.getUUID()), "Not digging with a ticket after 100 ticks: " + report(c));
            CompanionEvents.executeAction(r.owner(), "stop", "", 1);
        });
        h.runAfterDelay(110, () -> {
            h.assertTrue(!c.isWorking(), "Still working after stop: " + report(c));
            h.assertTrue(!CompanionChunkTicket.held(c.getUUID()), "Chunk ticket kept after stop");
            h.succeed();
        });
    }

    /** Death in the middle of the dig lets the chunk go. */
    @GameTest(template = "marathon_cave", timeoutTicks = 600)
    public static void releasesTheChunkWhenHeDies(GameTestHelper h) {
        CompanionEntity c = rig(h, RING_FROM, standard(16)).c();
        start(h, c, 1);
        h.runAfterDelay(100, () -> {
            h.assertTrue(c.isWorking() && CompanionChunkTicket.held(c.getUUID()), "Not digging with a ticket after 100 ticks: " + report(c));
            c.kill();
        });
        h.runAfterDelay(110, () -> {
            h.assertTrue(!CompanionChunkTicket.held(c.getUUID()), "Chunk ticket kept after death");
            h.succeed();
        });
    }

    /** The diamonds are the ore he removed, and the torches come only from the coal he used: nothing appears from nothing. */
    @GameTest(template = "marathon_cave", timeoutTicks = 9000)
    public static void diamondsEqualRemovedOreAndNothingAppearsFromNothing(GameTestHelper h) {
        Rig r = rig(h, RING_FROM, standard(4, new ItemStack(Items.COAL, 1), new ItemStack(Items.STICK, 1)));
        CompanionEntity c = r.c();
        int oreBefore = blocks(h, Blocks.DEEPSLATE_DIAMOND_ORE).size();
        long id = start(h, c, 2);
        h.succeedWhen(() -> {
            ended(h, c, id);
            int removed = oreBefore - blocks(h, Blocks.DEEPSLATE_DIAMOND_ORE).size(), diamonds = count(c, Items.DIAMOND) + ground(h, Items.DIAMOND);
            h.assertTrue(removed >= 1 && diamonds == removed, "Diamonds " + diamonds + " but ore removed " + removed + " | " + report(c));
            int torches = torches(h) + count(c, Items.TORCH) + ground(h, Items.TORCH), allowed = 4 + 4 * (1 - count(c, Items.COAL));
            h.assertTrue(torches <= allowed, "Torches " + torches + " from 4 given and " + (allowed - 4) + " crafted");
            h.assertTrue(count(c, Items.IRON_PICKAXE) == 1, "Iron pickaxes: " + count(c, Items.IRON_PICKAXE));
        });
    }

    private static Rig lastPickaxe(GameTestHelper h) {
        Rig r = rig(h, RING_FROM, standard(16));
        ItemStack worn = new ItemStack(Items.STONE_PICKAXE);
        worn.setDamageValue(worn.getMaxDamage() - 3);
        r.c().setItemSlot(EquipmentSlot.MAINHAND, worn);
        r.c().setItem(0, ItemStack.EMPTY);
        return r;
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

    private static BlockPos stand(int k) { return new BlockPos(CENTER, GROUND + 1 - k, CENTER - k); }

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

    /** Relative positions of every block of the kind in the arena (water and lava count with their flowing states). */
    private static List<BlockPos> blocks(GameTestHelper h, Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = 1; x < SIZE - 1; x++) for (int z = 1; z < SIZE - 1; z++) for (int y = 1; y <= GROUND; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = h.getBlockState(pos);
            if (state.is(block) || block == Blocks.WATER && state.getFluidState().is(FluidTags.WATER)) found.add(pos);
        }
        return found;
    }

    private static int torches(GameTestHelper h) { return blocks(h, Blocks.TORCH).size() + blocks(h, Blocks.WALL_TORCH).size(); }

    private static int ground(GameTestHelper h, Item item) {
        int total = 0;
        for (ItemEntity entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.encapsulatingFullBlocks(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(SIZE, GROUND + 15, SIZE)))))
            if (entity.getItem().is(item)) total += entity.getItem().getCount();
        return total;
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
