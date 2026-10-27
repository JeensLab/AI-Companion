package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game log 23.10 (session 05cdd3a7, 23:39–23:40): «сделай каменный меч» — the stone lay at the bottom of a ravine. He walked
 * down its ledges (drops of up to three blocks), mined four stones right there and then «нет обратного прохода к месту начала
 * добычи»: the work stopped with the stone at hand, the self-rescue dug a little soil and gave up — «не смог безопасно выбраться».
 * the player: «нужно чтобы он в любой ситуации понимал как до меня добраться». A player mines what he came for and climbs out by
 * digging steps up the wall.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionRavineTests {
    private static final int TOP = 14; // surface layer

    /** The sword is made with the stone from the ravine floor, and he does not stop for want of a way back. */
    @GameTest(template = "tall_tree", timeoutTicks = 3600)
    public static void stoneAtTheRavineBottomIsMinedForTheSword(GameTestHelper h) {
        ravine(h);
        CompanionEntity companion = spawn(h, 16, TOP + 1, 2);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STICK, 4)); companion.setItem(1, new ItemStack(Items.CRAFTING_TABLE, 1));
        ServerPlayer player = player(h, companion);
        h.assertTrue(CompanionEvents.executeAction(player, "craft_item", "minecraft:stone_sword", 1).success(), "Craft not accepted");
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking(), "Still working: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.STONE_SWORD) == 1, companion.getLastTaskResult());
            // And then back up to the player, like after any work.
            h.assertTrue(companion.getY() >= h.absolutePos(new BlockPos(0, TOP + 1, 0)).getY() - .01 && companion.distanceToSqr(player) < 8 * 8,
                    "Sword made, still down at " + h.relativePos(companion.blockPosition()));
        });
    }

    /** Down in the ravine with nothing to build with, he digs steps up its wall and comes to the player on the edge. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void fromTheRavineBottomHeDigsStepsUpToThePlayer(GameTestHelper h) {
        ravine(h);
        CompanionEntity companion = spawn(h, 16, 3, 16);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.setItem(0, new ItemStack(Items.STONE_SHOVEL));
        companion.setFollowing(true);
        boolean[] steps = {false};
        h.onEachTick(() -> steps[0] |= companion.getLastTaskResult().contains("ступеньки"));
        h.succeedWhen(() -> {
            h.assertTrue(steps[0], "He did not cut steps up the wall");
            h.assertTrue(companion.getY() >= h.absolutePos(new BlockPos(0, TOP + 1, 0)).getY() - .01, "Still down in the ravine at " + h.relativePos(companion.blockPosition()));
            h.assertTrue(companion.distanceToSqr(player(h, companion)) < 8 * 8, "Up, but not with the player");
        });
    }

    /**
     * Stone below, soil above it, grass on top (y=TOP), bedrock border. A ravine three wide along x 15..17, z 8..28, its floor
     * at y=2: from the north end ledges three blocks apart lead down (one way: a mob jumps one block), its walls are sheer.
     * The player stands on the east edge.
     */
    private static void ravine(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= TOP; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == TOP ? Blocks.GRASS_BLOCK : y >= TOP - 3 ? Blocks.DIRT : Blocks.STONE);
        }
        for (int x = 15; x <= 17; x++) for (int z = 8; z <= 28; z++) for (int y = 2; y <= TOP; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        // The way down at the north end: ledges three blocks apart (z 4..7), the last one a block above the ravine floor.
        for (int x = 15; x <= 17; x++) for (int step = 0; step < 4; step++)
            for (int y = TOP - 2 - 3 * step; y <= TOP; y++) h.setBlock(new BlockPos(x, y, 4 + step), Blocks.AIR);
        // Spare stone on the surface would be no test: only the ravine walls and floor are exposed stone.
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int y, int z) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, y, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(20, TOP + 1, 16));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(24);
        return companion;
    }

    private static ServerPlayer player(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
