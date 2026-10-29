package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Player report: «в джунглях Олег пошёл копать камень, запутался в лианах, отходил, и всё равно шёл в лианы». A body in a vine is
 * slowed, cut short in its jump and lifted up the wall at the first touch of it (LivingEntity.onClimbable). The companion's own
 * search climbs vines on purpose (PR #36); this guards that a stone behind vines with no way round is reached without looping.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionVineTests {
    private static final int GROUND = 5; // top layer of the arena floor
    private static final int WALL_Z = 10;

    /**
     * A row of trunks between him and the stone cliff, an opening seven wide in it, and a strand of vines on the trunk at its
     * west end, straight in front of him. A player steps one cell aside; the walking search went through the vines, being a
     * little shorter.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void stoneBehindVinesWithNoWayRoundIsMinedWithoutLoopingInThem(GameTestHelper h) {
        ground(h);
        cliff(h);
        trunks(h, 16, 16);
        CompanionEntity companion = spawn(h);
        Watch watch = new Watch(h, companion);
        h.onEachTick(watch::tick);
        companion.startWork("gather_stone", "", 3);
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 3, companion.getLastTaskResult());
            h.assertTrue(watch.entries <= 2 && watch.inVine <= 60 && watch.rescues == 0, "Tangled in the vines: " + watch);
        });
    }

    /** Ticks and entries in a cell with a vine, and whether a self-rescue ever started. */
    private static final class Watch {
        final GameTestHelper h; final CompanionEntity companion;
        int inVine, entries, rescues; boolean wasInVine, wasRescuing;
        Watch(GameTestHelper h, CompanionEntity companion) { this.h = h; this.companion = companion; }
        void tick() {
            boolean vine = companion.onClimbable(), rescuing = companion.getActivityForAi().startsWith("пытается выбраться");
            if (vine) inVine++;
            if (vine && !wasInVine) entries++;
            if (rescuing && !wasRescuing) rescues++;
            wasInVine = vine; wasRescuing = rescuing;
        }
        @Override public String toString() {
            return inVine + " ticks in " + entries + " entries, " + rescues + " rescues, at " + h.relativePos(companion.blockPosition()).toShortString()
                    + " " + companion.getLastTaskResult();
        }
    }

    /**
     * A row of jungle trunks across the arena with an opening from {@code from} to {@code to}, and a strand of vines in the
     * opening's west end cell, on the trunk beside it (on both trunks, if the opening is one wide), as a jungle trunk carries them.
     */
    private static void trunks(GameTestHelper h, int from, int to) {
        for (int x = 1; x <= 30; x++) {
            if (x >= from && x <= to) continue;
            for (int y = GROUND + 1; y <= GROUND + 4; y++) h.setBlock(new BlockPos(x, y, WALL_Z), Blocks.JUNGLE_LOG);
        }
        for (int y = GROUND + 1; y <= GROUND + 4; y++) vine(h, new BlockPos(from, y, WALL_Z));
    }

    /** A vine on each side of the cell where a solid block holds it. */
    private static void vine(GameTestHelper h, BlockPos pos) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos near = pos.relative(side);
            if (h.getBlockState(near).isCollisionShapeFullBlock(h.getLevel(), h.absolutePos(near))) vine = vine.setValue(VineBlock.PROPERTY_BY_DIRECTION.get(side), true);
        }
        h.assertTrue(!vine.equals(Blocks.VINE.defaultBlockState()), "Nothing holds the vine at " + pos);
        h.setBlock(pos, vine);
    }

    /** Bare stone, the only stone in the arena: a cliff three high across the north end. */
    private static void cliff(GameTestHelper h) {
        for (int x = 6; x <= 25; x++) for (int z = 2; z <= 4; z++) for (int y = GROUND + 1; y <= GROUND + 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            for (int y = 0; y <= GROUND; y++) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            if (border) for (int y = GROUND + 1; y <= GROUND + 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, GROUND + 1, 14);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(16, GROUND + 1, 17));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        // The least radius there is: the stone of the next arena of the grid stays out of his reach.
        companion.setSearchRadius(16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        return companion;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
