package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's two refusals of 20.11, said as the player says them and run the way the game runs them: a shovel asked for
 * with the wood for it is made and handed over — also when the bench he remembers is out of reach — and six sand blocks put
 * down in a row are all dug, also the one someone stands on. Only a block that truly cannot be come at refuses.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionReachRefusalTests {
    /** «Сделай лопату мне» with the wood for it in the bag and no bench anywhere about: made and tossed to the player. */
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void shovelForMeWithWoodAndNoBenchIsMadeAndHandedOver(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        // Two logs are a bench and the shovel that needs one: 8 planks, 4 into the bench, 2 into sticks, 1 into the shovel.
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        var player = owner(h, companion);
        player.teleportTo(companion.getX() + 3, companion.getY(), companion.getZ() + 1);
        var said = new MarathonChat(h, companion).say("сделай лопату мне", woodenShovel());
        h.succeedWhen(() -> {
            h.assertTrue(said.done(), "The order is still running");
            assertMadeAndHandedOver(h, said, companion, player, "wood in the bag, no bench about");
        });
    }

    /**
     * The same order with the wood in the bag and a bench he remembers six blocks off, shut in bedrock: no way to that bench
     * is no reason to refuse a craft the bag can do at a bench of its own.
     */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void shovelForMeWithARememberedBenchOutOfReachIsStillMade(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos bench = new BlockPos(22, 1, 16);
        h.setBlock(bench, Blocks.CRAFTING_TABLE);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) h.setBlock(bench.relative(side), Blocks.BEDROCK);
        var player = owner(h, companion);
        player.teleportTo(companion.getX() + 3, companion.getY(), companion.getZ() + 1);
        var said = new MarathonChat(h, companion).say("сделай лопату мне", woodenShovel());
        h.succeedWhen(() -> {
            h.assertTrue(said.done(), "The order is still running");
            assertMadeAndHandedOver(h, said, companion, player, "a remembered bench six blocks off, shut in bedrock");
        });
    }

    /** «Сделай лопату мне» for a stone one with wood but no stone about: said as the stone that is missing, not as a way. */
    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void stoneShovelWithoutStoneIsSaidAsStoneNotAsAPath(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 3));
        var player = owner(h, companion);
        player.teleportTo(companion.getX() + 3, companion.getY(), companion.getZ() + 1);
        var said = new MarathonChat(h, companion).say("сделай лопату мне",
                "[{'action':'craft_item','item':'minecraft:stone_shovel','count':1}]");
        h.succeedWhen(() -> {
            h.assertTrue(said.done(), "The order is still running");
            String spoken = CompanionDialogue.summarize(said.plan(), said.report());
            h.assertTrue(!spoken.contains("безопасного пути"),
                    "A missing stone was said as a lost way: " + spoken + " || " + said.report());
            h.assertTrue(spoken.toLowerCase(java.util.Locale.ROOT).contains("камн"),
                    "The refusal does not name the stone: " + spoken + " || " + said.report());
        });
    }

    /**
     * Six sand blocks put down in a row, with the wood for the shovel the dig needs in the bag: the bench he makes for that
     * shovel can land on the row, and the block under it (or under him) is dug like a player digs it. All six go.
     */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void sixSandBlocksWithHimOnTheLastAreAllDug(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos[] row = sandRow(h);
        var player = owner(h, companion);
        player.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() + 4);
        BlockPos on = h.absolutePos(row[5].above());
        companion.teleportTo(on.getX() + .5, on.getY(), on.getZ() + .5);
        var said = new MarathonChat(h, companion).say("накопай песок");
        h.succeedWhen(() -> assertAllSixDug(h, said, companion, row, "he stood on the last"));
    }

    /** The same six blocks with the player standing on the last: the block under a body comes down with it, one step. */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void sixSandBlocksWithThePlayerOnTheLastAreAllDug(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos[] row = sandRow(h);
        var player = owner(h, companion);
        BlockPos on = h.absolutePos(row[5].above());
        player.teleportTo(on.getX() + .5, on.getY(), on.getZ() + .5);
        var said = new MarathonChat(h, companion).say("накопай песок");
        h.succeedWhen(() -> assertAllSixDug(h, said, companion, row, "the player stood on the last"));
    }

    /** Sand shut in bedrock on every side: no stand cell, no way — the order refuses instead of claiming six. */
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void sandShutInBedrockStillRefusesHonestly(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        companion.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        BlockPos sand = new BlockPos(21, 1, 20);
        h.setBlock(sand, Blocks.SAND);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) h.setBlock(sand.relative(side), Blocks.BEDROCK);
        var player = owner(h, companion);
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() + 3);
        var said = new MarathonChat(h, companion).say("накопай песок");
        h.succeedWhen(() -> {
            h.assertTrue(said.done(), "The order is still running");
            h.assertTrue(h.getBlockState(sand).is(Blocks.SAND), "Sand shut in bedrock was dug: " + said.report());
            String spoken = CompanionDialogue.summarize(said.plan(), said.report());
            h.assertTrue(spoken.toLowerCase(java.util.Locale.ROOT).contains("песк"),
                    "The refusal does not say what happened to the sand: " + spoken + " || " + said.report());
        });
    }

    private static String woodenShovel() {
        return "[{'action':'craft_item','item':'minecraft:wooden_shovel','count':1}]";
    }

    private static BlockPos[] sandRow(GameTestHelper h) {
        BlockPos[] row = new BlockPos[6];
        for (int i = 0; i < row.length; i++) { row[i] = new BlockPos(18 + i, 1, 20); h.setBlock(row[i], Blocks.SAND); }
        return row;
    }

    private static void assertMadeAndHandedOver(GameTestHelper h, MarathonChat.Said said, CompanionEntity companion,
                                                net.minecraft.server.level.ServerPlayer player, String what) {
        String report = said.report();
        String spoken = CompanionDialogue.summarize(said.plan(), report);
        h.assertTrue(!spoken.contains("безопасного пути"), "A craft with the materials for it refused as a lost way (" + what + "): " + spoken);
        h.assertTrue(count(companion, Items.WOODEN_SHOVEL) == 0, "He kept the shovel he made for the player (" + what + "): " + report);
        boolean atThePlayer = !h.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(6),
                item -> item.getItem().is(Items.WOODEN_SHOVEL)).isEmpty()
                || player.getInventory().contains(new ItemStack(Items.WOODEN_SHOVEL));
        h.assertTrue(atThePlayer, "The shovel never reached the player (" + what + "): " + report);
    }

    private static void assertAllSixDug(GameTestHelper h, MarathonChat.Said said, CompanionEntity companion,
                                        BlockPos[] row, String what) {
        h.assertTrue(said.done(), "The sand order is still running");
        int left = 0; StringBuilder where = new StringBuilder();
        for (BlockPos at : row) if (h.getBlockState(at).is(Blocks.SAND)) { left++; where.append(h.absolutePos(at)).append(' '); }
        String spoken = CompanionDialogue.summarize(said.plan(), said.report());
        h.assertTrue(left == 0, left + " of the six sand blocks are still standing (" + what + "): " + spoken + " || left at " + where
                + " || at " + companion.blockPosition() + " bag " + companion.getStoredItemsSummary());
        h.assertTrue(count(companion, Items.SAND) + onGround(h, Items.SAND) == row.length,
                "Dug blocks and what came of them do not match (" + what + "): " + spoken + " || bag " + companion.getStoredItemsSummary());
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int y = 1; y <= 12; y++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }

    private static net.minecraft.server.level.ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static int count(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) total += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (companion.getItemBySlot(slot).is(item)) total += companion.getItemBySlot(slot).getCount();
        return total;
    }

    private static int onGround(GameTestHelper h, Item item) {
        int total = 0;
        for (ItemEntity entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(3), found -> found.getItem().is(item)))
            total += entity.getItem().getCount();
        return total;
    }
}
