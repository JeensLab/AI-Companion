package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Judge's own scene, written from the owner's report and not from the PR's rows: different kinds of block hanging in the air
 * with nothing under them, 3 to 25 blocks across and 3 to 20 up (gold block, wool, planks, gold ore, glass, a barrel), each
 * asked for by name, each found, mined, the column taken down and the soil returned to the bag. One kind per row so a failure
 * names the kind and the distance, not only the scene.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionWayJudgeSceneTests {
    private static final int SIDE = 48, TOP = 32;
    private static final int GROUND = 5;
    private static final int FEET = GROUND + 1;
    private static final int HOME_X = 20, HOME_Z = 16;

    /** The owner's nearest: a gold block 3 across and 3 up, nothing under it. */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void goldBlockThreeAcrossThreeUpIsTaken(GameTestHelper h) {
        taken(h, Blocks.GOLD_BLOCK, Items.GOLD_BLOCK, "minecraft:gold_block", "добудь золотой блок", 3, 0, 3, true, plainPickaxe());
    }

    /** Wool 8 across and 8 up. */
    @GameTest(template = "way_wide", timeoutTicks = 20000)
    public static void woolEightAcrossEightUpIsTaken(GameTestHelper h) {
        taken(h, Blocks.WHITE_WOOL, Items.WHITE_WOOL, "minecraft:white_wool", "добудь белую шерсть", 8, 1, 8, true, plainPickaxe());
    }

    /** Planks 14 across and 14 up: the middle of the owner's scene. */
    @GameTest(template = "way_wide", timeoutTicks = 24000)
    public static void planksFourteenAcrossFourteenUpAreTaken(GameTestHelper h) {
        taken(h, Blocks.OAK_PLANKS, Items.OAK_PLANKS, "minecraft:oak_planks", "добудь дубовые доски", 14, -2, 14, true, plainPickaxe());
    }

    /** Gold ore 6 across and 3 up, asked for as its drop: raw gold, not the ore block. */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void goldOreSixAcrossThreeUpGivesRawGold(GameTestHelper h) {
        // An ore's drop is not in the block table (a wooden pickaxe cannot take an ore), so the way's search does not see it:
        // the mine's own chain takes the ore. The order is what must end with raw gold in the bag.
        taken(h, Blocks.GOLD_ORE, Items.RAW_GOLD, "minecraft:raw_gold", "добудь золото", 6, 2, 3, false, plainPickaxe());
    }

    /**
     * A plain pickaxe and a Silk Touch one in the same bag, a gold block three above: the block is taken, not swapped between
     * hands until the order times out.
     */
    @GameTest(template = "way_wide", timeoutTicks = 16000)
    public static void goldBlockThreeUpWithAPlainAndASilkPickaxeIsTaken(GameTestHelper h) {
        taken(h, Blocks.GOLD_BLOCK, Items.GOLD_BLOCK, "minecraft:gold_block", "добудь золотой блок", 3, 0, 3, true,
                plainPickaxe(), silkPickaxe(h));
    }

    /** The far corner of the owner's own numbers: a barrel 25 across and 20 up, nothing under it. */
    @GameTest(template = "way_wide", timeoutTicks = 40000)
    public static void barrelTwentyFiveAcrossTwentyUpIsTaken(GameTestHelper h) {
        taken(h, Blocks.BARREL, Items.BARREL, "minecraft:barrel", "добудь бочку", 25, 0, 20, true, plainPickaxe());
    }

    /** Glass 20 across and 16 up, the only pickaxe a Silk Touch one (glass gives nothing to a plain one). */
    @GameTest(template = "way_wide", timeoutTicks = 32000)
    public static void glassTwentyAcrossSixteenUpIsTakenWithASilkTool(GameTestHelper h) {
        taken(h, Blocks.GLASS, Items.GLASS, "minecraft:glass", "добудь стекло", 20, 0, 16, true, silkPickaxe(h));
    }

    /** A block seen but 33 up, past the way's height: the refusal names the distance, not a bare «подойти не смог». */
    @GameTest(template = "way_wide", timeoutTicks = 400)
    public static void aSeenBlockTwentyOneUpIsRefusedWithItsDistance(GameTestHelper h) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + 12, FEET + 33, HOME_Z);
        h.setBlock(target, Blocks.PUMPKIN);
        CompanionEntity c = spawn(h);
        c.setItem(0, new ItemStack(Items.DIRT, 24));
        var plan = c.obtainPlan("minecraft:pumpkin", 1);
        h.assertTrue(plan.refused(), "The pumpkin 33 up is not refused: " + plan);
        h.assertTrue(plan.refusal().contains("33 блок") && plan.refusal().contains("вверх"),
                "The refusal does not name the distance: " + plan.refusal());
        var future = sayModel(h, c, "добудь тыкву", obtainPlan("minecraft:pumpkin"));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            h.assertBlockPresent(Blocks.PUMPKIN, target);
        });
    }

    // ---- helpers ----

    /** A block floating {@code dx}, {@code dz} to the side and {@code up} above his feet, nothing under it: found and taken. */
    private static void taken(GameTestHelper h, Block block, Item item, String id, String phrase, int dx, int dz, int up,
                              boolean searchSees, ItemStack... tools) {
        flat(h, Blocks.GRASS_BLOCK);
        BlockPos target = new BlockPos(HOME_X + dx, FEET + up, HOME_Z + dz);
        h.setBlock(target, block);
        CompanionEntity c = spawn(h);
        for (int slot = 0; slot < tools.length; slot++) c.setItem(slot, tools[slot]);
        c.setItem(tools.length, new ItemStack(Items.DIRT, 24));
        int[] before = snapshot(h);
        int[] most = {0};
        h.onEachTick(() -> most[0] = Math.max(most[0], c.getTemporarySupportCount()));
        if (searchSees) {
            var seen = c.obtainAvailable(id);
            h.assertTrue(seen.some(), "The search does not see «" + id + "» floating " + up + " up and " + dx + " across: "
                    + seen + ", feet " + c.blockPosition() + ", radius " + c.getSearchRadius());
        }
        var future = sayModel(h, c, phrase, obtainPlan(id));
        h.succeedWhen(() -> {
            h.assertTrue(future.isDone() && !future.isCompletedExceptionally(), "Still running: " + c.getLastTaskResult());
            String report = future.join().report();
            h.assertTrue(count(c, item) >= 1, "«" + phrase + "» (" + up + " up, " + dx + " across) is not in the bag: " + report);
            h.assertBlockPresent(Blocks.AIR, target);
            assertRestored(h, before, target);
            // A block three above him is worked from the ground under it: no column is raised for it.
            h.assertTrue(up < 4 || most[0] > 0, "No column was raised for " + up + " up: " + report);
            h.assertTrue(c.getTemporarySupportCount() == 0, "The column is still standing: " + c.getTemporarySupportCount());
            h.assertTrue(count(c, Items.DIRT) + looseDirt(h) >= 24, "The soil of the column was lost: "
                    + count(c, Items.DIRT) + " in the bag, " + looseDirt(h) + " loose, " + report);
            h.assertTrue(dirtAboveGround(h) == 0, "A support was left in the air: " + dirtAboveGround(h));
            h.assertTrue(!report.contains("Рядом нечего копать") && !report.contains("Рядом не вижу")
                    && !report.contains("Полторы минуты") && !report.contains("не выполнено"), "A wrong end: " + report);
        });
    }

    private static ItemStack plainPickaxe() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    private static ItemStack silkPickaxe(GameTestHelper h) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        EnchantmentHelper.updateEnchantments(stack, mutable ->
                mutable.set(h.getLevel().registryAccess().holderOrThrow(Enchantments.SILK_TOUCH), 1));
        return stack;
    }

    private static String obtainPlan(String item) {
        return "[{\"action\":\"obtain_item\",\"item\":\"" + item + "\",\"count\":1}]";
    }

    private static CompletableFuture<CompanionPlanRunner.Outcome> sayModel(GameTestHelper h, CompanionEntity c, String phrase, String json) {
        var reply = CompanionPlanRequirements.apply(phrase, new CompanionReply("", CompanionPlanCodec.parsePlan(JsonParser.parseString(json))));
        return CompanionPlanRunner.run(reply.actions(),
                new CompanionServerPlanPort(h.getLevel().getServer(), c.getOwnerId(), "gametest-way-judge-model", () -> false));
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), HOME_X, FEET, HOME_Z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(HOME_X - 2, FEET, HOME_Z));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        owner.setInvulnerable(true);
        c.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        c.setSearchRadius(32);
        c.setLifestyle(false, false);
        return c;
    }

    private static void flat(GameTestHelper h, Block floor) {
        for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) for (int y = 0; y < TOP; y++) {
            boolean border = x == 0 || x == SIDE - 1 || z == 0 || z == SIDE - 1;
            if (y == 0 || border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
            else if (y == GROUND) h.setBlock(new BlockPos(x, y, z), floor);
            else if (y < GROUND) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
            else h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static int[] snapshot(GameTestHelper h) {
        int[] cells = new int[SIDE * TOP * SIDE];
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++)
            cells[at++] = Block.getId(h.getBlockState(new BlockPos(x, y, z)));
        return cells;
    }

    private static void assertRestored(GameTestHelper h, int[] before, BlockPos... mined) {
        Set<BlockPos> allowed = new HashSet<>(List.of(mined));
        int at = 0;
        for (int y = 0; y < TOP; y++) for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            int now = Block.getId(h.getBlockState(pos));
            int was = before[at++];
            if (now == was) continue;
            h.assertTrue(allowed.contains(pos) && h.getBlockState(pos).isAir(),
                    "A cell changed at " + pos + ": " + BuiltInRegistries.BLOCK.getKey(h.getBlockState(pos).getBlock()) + ", allowed " + allowed);
        }
    }

    private static int dirtAboveGround(GameTestHelper h) {
        int count = 0;
        for (int x = 1; x < SIDE - 1; x++) for (int z = 1; z < SIDE - 1; z++) for (int y = GROUND + 1; y < TOP - 1; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) count++;
        return count;
    }

    private static int looseDirt(GameTestHelper h) {
        int count = 0;
        var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(1, 1, 1)).getCenter(),
                h.absolutePos(new BlockPos(SIDE - 2, TOP - 2, SIDE - 2)).getCenter());
        for (net.minecraft.world.entity.item.ItemEntity entity : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box))
            if (entity.getItem().is(Items.DIRT)) count += entity.getItem().getCount();
        return count;
    }

    private static int count(CompanionEntity c, Item item) {
        int count = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) count += c.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (c.getItemBySlot(slot).is(item)) count += c.getItemBySlot(slot).getCount();
        return count;
    }
}
