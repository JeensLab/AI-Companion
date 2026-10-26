package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game log 23.10 (session aa07e605, 22:39): «сделай сундук» — the trees left were 70–90 blocks off over hills, the pathfinder
 * builds no route that long in one search, and in 15 seconds every tree was «unreachable» without a single step («как будто у
 * него маленькая зона поиска»). Here a long wall stands between him and the goal; the way round it through the far gap is longer
 * than one search of the pathfinder may walk. A player walks there in legs and goes round the wall; so does Oleg.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionTravelTests {
    @GameTest(template = "large_search", timeoutTicks = 3000)
    public static void treesBeyondTheLongWallAreReached(GameTestHelper h) {
        CompanionEntity companion = arena(h);
        for (int y = 1; y <= 4; y++) { h.setBlock(new BlockPos(60, y, 118), Blocks.OAK_LOG); h.setBlock(new BlockPos(68, y, 118), Blocks.OAK_LOG); }
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        companion.startGatheringLogs(4);
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Still gathering: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.OAK_LOG) >= 4, companion.getLastTaskResult());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 3000)
    public static void stoneBeyondTheLongWallIsReached(GameTestHelper h) {
        CompanionEntity companion = arena(h);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(63, 1, 117), new BlockPos(64, 2, 118))) h.setBlock(pos, Blocks.STONE);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 4);
        java.util.List<String> seen = new java.util.ArrayList<>();
        h.onEachTick(() -> { String now = companion.getLastTaskResult(); if (seen.isEmpty() || !seen.getLast().endsWith(now)) seen.add(h.getTick() + "@" + h.relativePos(companion.blockPosition()).toShortString() + ":" + now); });
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking(), "Still mining: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed() && count(companion, Items.COBBLESTONE) >= 4, String.join(" / ", seen));
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 3000)
    public static void cowsBeyondTheLongWallAreHunted(GameTestHelper h) {
        CompanionEntity companion = arena(h);
        for (int i = 0; i < 2; i++) h.spawn(EntityType.COW, 62 + i * 4, 1, 117).setNoAi(true);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        h.assertTrue(CompanionEvents.executeAction(player, "hunt_animals", "minecraft:cow", 1).success(), "Hunt not accepted");
        long id = companion.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = companion.workResult(id);
            h.assertTrue(result != null && !companion.isWorking(), "Still hunting: " + companion.getLastTaskResult());
            h.assertTrue(result.success() && count(companion, Items.BEEF) >= 1, result.message());
        });
    }

    /** The same session: «Не успел: сложить вещи в сундук, сложить вещи в сундук, …» — one store step per tool, said four times. */
    @GameTest(template = "empty")
    public static void stepsNotDoneAreSaidOnce(GameTestHelper h) {
        var steps = java.util.List.of(new dev.aicompanion.client.PlannedAction("store_items", 0, 1, "minecraft:stone_pickaxe"),
                new dev.aicompanion.client.PlannedAction("store_items", 0, 1, "minecraft:stone_axe"),
                new dev.aicompanion.client.PlannedAction("store_items", 0, 1, "minecraft:stone_sword"),
                new dev.aicompanion.client.PlannedAction("follow", 0, 1, ""), new dev.aicompanion.client.PlannedAction("follow", 0, 1, ""));
        String said = dev.aicompanion.client.CompanionDialogue.describeAll(steps);
        h.assertTrue(said.indexOf("сундук") == said.lastIndexOf("сундук") && said.indexOf("идти за тобой") == said.lastIndexOf("идти за тобой")
                && said.split("«").length == 4, "Said: " + said);
        h.succeed();
    }

    /**
     * Bedrock floor and border; a wall three high across the field at z=90 from x=30 to the east border, so the only way north
     * is the gap at the west end. He starts at (64, 64), the goal is about (64, 118): 54 blocks straight, about 90 round the wall,
     * more than one search of the pathfinder walks (search radius 60, so his search stays inside the arena).
     */
    private static CompanionEntity arena(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            boolean border = x == 0 || x == 127 || z == 0 || z == 127;
            boolean wall = z == 90 && x >= 30;
            if (border || wall) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 64, 1, 64);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(62, 1, 62));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(60);
        return companion;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
