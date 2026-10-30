package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionDialogue;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionPlanRequirements;
import dev.aicompanion.client.CompanionPlanRunner;
import dev.aicompanion.client.CompanionReply;
import dev.aicompanion.client.CompanionServerPlanPort;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * «Сделай огород» and «собери урожай». Like a player with a hoe: farmland only within four blocks of water (vanilla hydration, no
 * water is ever made), tilled with a hoe that wears, sown from the bag; wheat seeds from the grass he tears out; a harvest takes the
 * ripe crops only, picks the drops up from the ground and sows the same farmland again. A world that cannot grow a crop is left as it
 * was, and the reason is said.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFarmTests {
    private static final int GROUND = 5;

    @net.minecraft.gametest.framework.AfterBatch(batch = "farm")
    public static void afterFarm(net.minecraft.server.level.ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** The ordinary field: eight wheat seeds and a hoe, a pond near by. Every tile tilled, sown, hydrated; the hoe worn; no water made. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aFieldBesideAPondIsTilledAndSown(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 8));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 8);
        int[] swings = {0};
        h.onEachTick(() -> { if (companion.swinging && companion.getMainHandItem().is(ItemTags.HOES)) swings[0]++; });
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            List<BlockPos> land = blocks(h, GROUND, Blocks.FARMLAND), wheat = blocks(h, GROUND + 1, Blocks.WHEAT);
            h.assertTrue(land.size() == 8 && wheat.size() == 8, "Farmland " + land.size() + ", wheat " + wheat.size() + ": " + result.message());
            for (BlockPos tile : land) {
                h.assertTrue(h.getBlockState(tile.above()).is(Blocks.WHEAT) && h.getBlockState(tile.above()).getValue(CropBlock.AGE) == 0, "No fresh wheat on " + tile);
                h.assertTrue(nearWater(h, tile), "Farmland nobody waters: " + tile);
            }
            h.assertTrue(count(companion, Items.WHEAT_SEEDS) == 0, "Seeds left: " + count(companion, Items.WHEAT_SEEDS));
            h.assertTrue(hoe(companion).getDamageValue() == 8, "The hoe took " + hoe(companion).getDamageValue() + " damage for eight tiles");
            h.assertTrue(swings[0] >= 8, "Only " + swings[0] + " ticks of swinging with the hoe for eight tiles");
            h.assertTrue(water(h) == 9, "The water is now " + water(h) + " blocks");
            h.assertTrue(lying(h).isEmpty(), "Items left lying: " + lying(h));
            h.assertTrue(result.message().contains("посадил") && result.message().contains("8") && result.message().contains("вскопал 8"), "Report: " + result.message());
            // The result carries its own facts: whose work it was, what was sown and how many.
            var facts = result.evidence();
            h.assertTrue(facts != null && facts.workId() == id && facts.action().equals("farm_crops") && facts.item().equals("minecraft:wheat_seeds") && facts.count() == 8, "Evidence: " + facts);
        });
    }

    /**
     * A 4x4 patch of ripe wheat, one carrot and two crops still growing; the middle of the patch is not reachable from its edge, so the
     * drops there are picked up from the farmland. The ripe ones are broken and sown again in the same place; the young are left alone.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void aHarvestTakesTheRipeCropsAndSowsTheSameFarmlandAgain(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        for (int x = 11; x <= 14; x++) for (int z = 11; z <= 14; z++) {
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            boolean young = z == 11 && x <= 12;
            Block crop = x == 13 && z == 13 ? Blocks.CARROTS : Blocks.WHEAT;
            h.setBlock(new BlockPos(x, GROUND + 1, z), crop.defaultBlockState().setValue(CropBlock.AGE, young ? 3 : 7));
        }
        CompanionEntity companion = spawn(h, 9, 9, 10, 10, 16);
        long id = order(h, companion, "harvest_crops", "any", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            int young = 0, again = 0;
            for (int x = 11; x <= 14; x++) for (int z = 11; z <= 14; z++) {
                BlockPos cell = new BlockPos(x, GROUND + 1, z);
                BlockState state = h.getBlockState(cell);
                h.assertTrue(h.getBlockState(cell.below()).is(Blocks.FARMLAND), "The farmland under " + cell + " is gone");
                if (z == 11 && x <= 12) { young++; h.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 3, "A young crop was touched: " + cell + " " + state); continue; }
                Block expected = x == 13 && z == 13 ? Blocks.CARROTS : Blocks.WHEAT;
                h.assertTrue(state.is(expected) && state.getValue(CropBlock.AGE) == 0, "Not sown again: " + cell + " " + state);
                again++;
            }
            h.assertTrue(young == 2 && again == 14, "Young " + young + ", sown again " + again);
            h.assertTrue(count(companion, Items.WHEAT) == 13, "Wheat in the bag: " + count(companion, Items.WHEAT) + " of 13");
            h.assertTrue(lying(h).isEmpty(), "Drops left lying on the field: " + lying(h));
            h.assertTrue(result.message().contains("с 14 грядок") && result.message().contains("заново посадил 14") && result.message().contains("Ещё не созрело: 2"), "Report: " + result.message());
        });
    }

    /** Nothing is ripe: that is said with the number still growing, and not a crop is touched. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void nothingRipeIsSaidAndTheYoungCropsAreLeft(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        for (int x = 12; x <= 13; x++) for (int z = 12; z <= 13; z++) {
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 6));
        }
        CompanionEntity companion = spawn(h, 10, 10, 10, 10, 16);
        long id = order(h, companion, "harvest_crops", "any", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("ещё растёт посадок: 4"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND + 1, Blocks.WHEAT).size() == 4 && count(companion, Items.WHEAT) == 0, "A young crop was taken");
            h.assertTrue(blocks(h, GROUND + 1, Blocks.WHEAT).stream().allMatch(cell -> h.getBlockState(cell).getValue(CropBlock.AGE) == 6), "A young crop was changed");
        });
    }

    /** No water within four blocks of any ground: not a block is tilled, no water is made, hoe and seeds untouched, and he says why. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void withoutWaterNothingIsTilledAndNoWaterIsMade(GameTestHelper h) {
        ground(h);
        // In the middle of the box: the search (sixteen blocks, and four more for the water) then stays within the five blocks between
        // the boxes, where no neighbouring test's pond can be seen.
        CompanionEntity companion = spawn(h, 15, 15, 16, 16, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 8));
        int grass = blocks(h, GROUND, Blocks.GRASS_BLOCK).size();
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 8);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.status() == CompanionActionResult.Status.BLOCKED, "Not an honest refusal: " + result);
            h.assertTrue(result.message().contains("Воды") && result.message().contains("Воду я не создаю"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).isEmpty() && blocks(h, GROUND + 1, Blocks.WHEAT).isEmpty(), "The world was changed");
            h.assertTrue(blocks(h, GROUND, Blocks.GRASS_BLOCK).size() == grass && water(h) == 0, "Grass " + blocks(h, GROUND, Blocks.GRASS_BLOCK).size() + " of " + grass + ", water " + water(h));
            h.assertTrue(count(companion, Items.WHEAT_SEEDS) == 8 && hoe(companion).getDamageValue() == 0, "Seeds or hoe were spent");
            String spoken = CompanionDialogue.summarize(List.of(new PlannedAction("farm_crops", 0, 8, "minecraft:wheat_seeds")), "План остановлен: " + result.message());
            h.assertTrue(spoken.contains("Воды") && !spoken.contains("не справился"), "Said aloud: " + spoken);
        });
    }

    /**
     * Wheat seeds come from the grass, the way a player gets them: the tufts are torn out, a seed falls from one in eight and is picked up
     * from the ground. Plenty of grass, so that the chance does not decide the test (the whole meadow holds hundreds of tufts).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void wheatSeedsComeFromTheGrassAndAreSownFromTheDrops(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        for (int x = 1; x < 31; x++) for (int z = 1; z < 31; z++) if (h.getBlockState(new BlockPos(x, GROUND, z)).is(Blocks.GRASS_BLOCK)) h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.SHORT_GRASS);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).size() == 2 && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 2, "Farmland " + blocks(h, GROUND, Blocks.FARMLAND).size()
                    + ", wheat " + blocks(h, GROUND + 1, Blocks.WHEAT).size() + ": " + result.message());
            h.assertTrue(result.message().contains("Траву рвал ради семян"), "The seeds did not come from the grass: " + result.message());
            h.assertTrue(blocks(h, GROUND + 1, Blocks.SHORT_GRASS).size() < 30 * 30 - 9, "No grass was torn out");
            h.assertTrue(lying(h).isEmpty(), "Seeds left lying: " + lying(h));
        });
    }

    /** No seeds and not a tuft of grass: the ground is not even tilled, and he says what is missing. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void withoutSeedsOrGrassNothingIsTilled(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 8);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.status() == CompanionActionResult.Status.BLOCKED && result.message().contains("нет семян пшеницы"), "Report: " + result);
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).isEmpty() && hoe(companion).getDamageValue() == 0, "The ground was tilled");
        });
    }

    /** Carrots are only the ones in the bag: none, none are planted and the ground stays as it was; three, three are. */
    @GameTest(template = "tall_tree", timeoutTicks = 400, batch = "farm")
    public static void carrotsAreNeverMadeUpForTheField(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE));
        long id = order(h, companion, "farm_crops", "minecraft:carrot", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("нет моркови"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).isEmpty(), "The ground was tilled for carrots he does not have");
        });
    }

    @GameTest(template = "tall_tree", timeoutTicks = 1200, batch = "farm")
    public static void fewerCarrotsThanTilesPlantsOnlyAsManyAsThereAre(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.CARROT, 3));
        long id = order(h, companion, "farm_crops", "minecraft:carrot", 8);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("Посадил морковь на 3 грядках из 8") && result.message().contains("Моркови хватило только на 3"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).size() == 3 && blocks(h, GROUND + 1, Blocks.CARROTS).size() == 3, "A bare tile was tilled: farmland "
                    + blocks(h, GROUND, Blocks.FARMLAND).size() + ", carrots " + blocks(h, GROUND + 1, Blocks.CARROTS).size());
            h.assertTrue(count(companion, Items.CARROT) == 0, "Carrots left over");
        });
    }

    /**
     * «Стоп» in the middle of the field: no crop without its farmland, no seed lost or doubled (those in the bag and those in the
     * ground make the eight), nothing lying about, and the work does not go on by itself.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aStoppedFieldLeavesNothingHalfDoneAndNoSeedLost(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 8));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 8);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        long[] stoppedAt = {-1};
        int[] sown = {0};
        h.onEachTick(() -> {
            if (stoppedAt[0] < 0 && blocks(h, GROUND + 1, Blocks.WHEAT).size() >= 3) {
                stoppedAt[0] = h.getTick(); CompanionEvents.executeAction(player, "stop", "", 1);
                sown[0] = blocks(h, GROUND + 1, Blocks.WHEAT).size();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(stoppedAt[0] >= 0 && h.getTick() >= stoppedAt[0] + 80, "Not stopped yet");
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.status() == CompanionActionResult.Status.CANCELLED, "Result after the stop: " + result);
            List<BlockPos> wheat = blocks(h, GROUND + 1, Blocks.WHEAT);
            h.assertTrue(wheat.size() >= sown[0] && wheat.size() <= sown[0] + 1, "The work went on after the stop: " + sown[0] + " then " + wheat.size());
            for (BlockPos cell : wheat) h.assertTrue(h.getBlockState(cell.below()).is(Blocks.FARMLAND), "A crop without farmland: " + cell);
            h.assertTrue(wheat.size() + count(companion, Items.WHEAT_SEEDS) == 8, "Seeds: " + wheat.size() + " sown + " + count(companion, Items.WHEAT_SEEDS) + " in the bag");
            h.assertTrue(lying(h).isEmpty(), "Items left lying: " + lying(h));
        });
    }

    /** No hoe, but wood: the bench, the planks, the sticks and a wooden hoe are made the usual way, and the hoe then wears like any. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void aHoeIsMadeFromLogsWhenThereIsNone(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.OAK_LOG, 3), new ItemStack(Items.WHEAT_SEEDS, 2));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).size() == 2 && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 2, "Farmland " + blocks(h, GROUND, Blocks.FARMLAND).size() + ", wheat "
                    + blocks(h, GROUND + 1, Blocks.WHEAT).size());
            h.assertTrue(hoe(companion).is(Items.WOODEN_HOE) && hoe(companion).getDamageValue() == 2, "The hoe: " + hoe(companion));
            h.assertTrue(count(companion, Items.OAK_LOG) < 3, "No wood was spent");
            h.assertTrue(blocks(h, GROUND + 1, Blocks.CRAFTING_TABLE).size() == 1 && count(companion, Items.CRAFTING_TABLE) == 0, "Not exactly one bench, set down by him for the hoe");
        });
    }

    /** The only hoe breaks on the first tile: the next is made from the logs in the bag, and the field goes on to the end. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void aHoeThatBreaksIsReplacedAndTheFieldGoesOn(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        ItemStack worn = new ItemStack(Items.WOODEN_HOE);
        worn.setDamageValue(worn.getMaxDamage() - 1);
        give(companion, worn, new ItemStack(Items.OAK_LOG, 3), new ItemStack(Items.WHEAT_SEEDS, 3));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 3);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).size() == 3 && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 3, "Farmland " + blocks(h, GROUND, Blocks.FARMLAND).size() + ", wheat "
                    + blocks(h, GROUND + 1, Blocks.WHEAT).size() + ": " + result.message());
            h.assertTrue(count(companion, Items.WOODEN_HOE) == 1 && hoe(companion).getDamageValue() == 2, "The hoes: " + count(companion, Items.WOODEN_HOE) + ", damage " + hoe(companion).getDamageValue());
        });
    }

    /** No hoe and no wood in the bag: he fells a tree near by for it first (the wood stage hands back to the field), then makes the hoe. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void aHoeIsMadeFromATreeNearbyWhenThereIsNoWoodAtAll(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(8, GROUND + y, 12), Blocks.OAK_LOG);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WHEAT_SEEDS, 2));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).size() == 2 && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 2, "Farmland " + blocks(h, GROUND, Blocks.FARMLAND).size());
            h.assertTrue(hoe(companion).is(Items.WOODEN_HOE) && hoe(companion).getDamageValue() == 2, "The hoe: " + hoe(companion));
            h.assertTrue(blocks(h, GROUND + 1, Blocks.OAK_LOG).size() < 4 || blocks(h, GROUND + 2, Blocks.OAK_LOG).size() < 1, "The tree was not felled");
        });
    }

    /** No hoe, no wood, no tree: nothing is tilled, the seeds stay in the bag, and it is said that the wood is missing. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void withoutAHoeOrWoodNothingIsTilledAndTheSeedsStay(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WHEAT_SEEDS, 2));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 2);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("дерев"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).isEmpty() && blocks(h, GROUND + 1, Blocks.WHEAT).isEmpty() && count(companion, Items.WHEAT_SEEDS) == 2, "The world or the seeds were changed");
        });
    }

    /**
     * The pond is a long way from him (twenty blocks): the field is walked to in legs, not given up. The search reaches over the
     * walls into the boxes of the tests round this one, whose ponds are about as near by the line, but not on foot.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void aFieldFarFromHimIsWalkedTo(GameTestHelper h) {
        ground(h); pond(h, 22, 22);
        CompanionEntity companion = spawn(h, 2, 3, 8, 8, 32);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 4));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 4);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success() && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 4, "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND + 1, Blocks.WHEAT).stream().allMatch(cell -> nearWater(h, cell.below())), "A tile away from the water");
        });
    }

    /** Only the crop asked for is reaped: the carrots are broken and sown again, the ripe wheat beside them stands. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void harvestOfOneCropLeavesTheOtherRipeCropsStanding(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        patch(h, 12, 12, 13, 12, Blocks.WHEAT, 7);
        patch(h, 12, 13, 13, 13, Blocks.CARROTS, 7);
        CompanionEntity companion = spawn(h, 9, 9, 10, 10, 16);
        long id = order(h, companion, "harvest_crops", "minecraft:carrot", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            for (int x = 12; x <= 13; x++) {
                BlockState wheat = h.getBlockState(new BlockPos(x, GROUND + 1, 12)), carrots = h.getBlockState(new BlockPos(x, GROUND + 1, 13));
                h.assertTrue(wheat.is(Blocks.WHEAT) && wheat.getValue(CropBlock.AGE) == 7, "The wheat was touched: " + wheat);
                h.assertTrue(carrots.is(Blocks.CARROTS) && carrots.getValue(CropBlock.AGE) == 0, "The carrots were not sown again: " + carrots);
            }
            h.assertTrue(count(companion, Items.WHEAT) == 0 && lying(h).isEmpty(), "Wheat taken or items lying: " + lying(h));
        });
    }

    /** A full bag cannot take the harvest: nothing is broken, so nothing is lost on the ground, and the reason is said. */
    @GameTest(template = "tall_tree", timeoutTicks = 600, batch = "farm")
    public static void aFullBagStopsTheHarvestBeforeAnythingIsLost(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        patch(h, 12, 12, 13, 13, Blocks.WHEAT, 7);
        CompanionEntity companion = spawn(h, 9, 9, 10, 10, 16);
        for (int slot = 0; slot < companion.getContainerSize(); slot++) companion.setItem(slot, new ItemStack(Items.DIRT, 64));
        long id = order(h, companion, "harvest_crops", "any", 64);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("нет места"), "Report: " + result.message());
            h.assertTrue(blocks(h, GROUND + 1, Blocks.WHEAT).stream().filter(cell -> h.getBlockState(cell).getValue(CropBlock.AGE) == 7).count() == 4, "A crop was broken without room for it");
            h.assertTrue(lying(h).isEmpty(), "Drops left lying: " + lying(h));
        });
    }

    /**
     * «Стоп» in the middle of the harvest: every crop broken has its wheat in the bag (none lost, none doubled), nothing is left
     * lying, and the work does not go on by itself.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aStoppedHarvestLosesNoCropAndLeavesNothingLying(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        patch(h, 11, 11, 14, 14, Blocks.WHEAT, 7);
        CompanionEntity companion = spawn(h, 9, 9, 10, 10, 16);
        long id = order(h, companion, "harvest_crops", "any", 64);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        long[] stoppedAt = {-1};
        h.onEachTick(() -> {
            if (stoppedAt[0] < 0 && ripe(h) <= 12) { stoppedAt[0] = h.getTick(); CompanionEvents.executeAction(player, "stop", "", 1); }
        });
        int[] ripeAtStop = {-1};
        h.succeedWhen(() -> {
            h.assertTrue(stoppedAt[0] >= 0 && h.getTick() >= stoppedAt[0] + 100, "Not stopped yet");
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.status() == CompanionActionResult.Status.CANCELLED, "Result after the stop: " + result);
            int ripe = ripe(h), broken = 16 - ripe;
            if (ripeAtStop[0] < 0) ripeAtStop[0] = ripe;
            h.assertTrue(ripe == ripeAtStop[0] && broken >= 4 && ripe >= 1, "The work went on after the stop or the stop came too late: ripe " + ripe);
            h.assertTrue(count(companion, Items.WHEAT) == broken, "Wheat in the bag " + count(companion, Items.WHEAT) + " for " + broken + " crops broken");
            h.assertTrue(lying(h).isEmpty(), "Items left lying: " + lying(h));
        });
    }

    /** Beetroot is sown from its seeds, a potato from a potato; both join the field, in other tiles than the first order took. */
    @GameTest(template = "tall_tree", timeoutTicks = 2400, batch = "farm")
    public static void potatoesAndBeetrootsAreSownFromTheirOwnSeedsToo(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.BEETROOT_SEEDS, 2), new ItemStack(Items.POTATO, 2));
        long[] id = {order(h, companion, "farm_crops", "minecraft:beetroot_seeds", 2)};
        int[] stage = {0};
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id[0]);
            if (stage[0] == 0) {
                h.assertTrue(result.success() && blocks(h, GROUND + 1, Blocks.BEETROOTS).size() == 2, "Beetroot: " + result.message());
                stage[0] = 1;
                id[0] = order(h, companion, "farm_crops", "minecraft:potato", 2);
                h.assertTrue(false, "Potatoes ordered");
            }
            h.assertTrue(result.success() && blocks(h, GROUND + 1, Blocks.POTATOES).size() == 2 && blocks(h, GROUND + 1, Blocks.BEETROOTS).size() == 2, "Potatoes: " + result.message());
            h.assertTrue(blocks(h, GROUND, Blocks.FARMLAND).size() == 4 && count(companion, Items.POTATO) == 0 && count(companion, Items.BEETROOT_SEEDS) == 0, "Farmland "
                    + blocks(h, GROUND, Blocks.FARMLAND).size() + ", potatoes left " + count(companion, Items.POTATO));
        });
    }

    /** A fall onto farmland tramples it for a pig; for him it never does, so the field he made stays whole. */
    @GameTest(template = "tall_tree", timeoutTicks = 300, batch = "farm")
    public static void heNeverTramplesTheFarmlandHeFallsOn(GameTestHelper h) {
        ground(h);
        BlockPos mine = new BlockPos(10, GROUND, 10), theirs = new BlockPos(14, GROUND, 14);
        for (BlockPos tile : List.of(mine, theirs)) h.setBlock(tile, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        CompanionEntity companion = spawn(h, 20, 20, 21, 21, 16);
        Vec3 above = h.absoluteVec(new Vec3(10.5, GROUND + 5, 10.5));
        companion.teleportTo(above.x, above.y, above.z);
        var pig = h.spawn(EntityType.PIG, 14, GROUND + 5, 14);
        h.succeedWhen(() -> {
            h.assertTrue(companion.onGround() && pig.onGround() && h.getTick() > 10, "Still falling");
            h.assertTrue(h.getBlockState(theirs).is(Blocks.DIRT), "The pig did not trample, so this proves nothing: " + h.getBlockState(theirs));
            h.assertTrue(h.getBlockState(mine).is(Blocks.FARMLAND), "He trampled the farmland: " + h.getBlockState(mine));
        });
    }

    /** The whole way: the codec, the plan runner waiting for the farm's own result, then a harvest of what has grown, sown again. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000, batch = "farm")
    public static void aFieldThenItsHarvestRunAsPlans(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 4));
        var farm = CompanionPlanRunner.run(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"farm_crops\",\"item\":\"minecraft:wheat\",\"count\":4}]")),
                new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-farm-plan", () -> false));
        var reap = new java.util.concurrent.CompletableFuture<CompanionPlanRunner.Outcome>();
        boolean[] grown = {false};
        h.succeedWhen(() -> {
            h.assertTrue(farm.isDone() && !farm.isCompletedExceptionally(), "The field plan is still running: " + companion.getLastTaskResult());
            if (!grown[0]) {
                // The game would take hours of growth; the test makes the wheat ripe and asks for the harvest as a second order.
                h.assertTrue(farm.join().report().contains("посадил") && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 4, "The field is not finished: " + farm.join().report());
                for (BlockPos cell : blocks(h, GROUND + 1, Blocks.WHEAT)) h.setBlock(cell, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
                CompanionPlanRunner.run(CompanionPlanRequirements.apply("собери урожай", new CompanionReply("", CompanionPlanCodec.parsePlan(
                        JsonParser.parseString("[{\"action\":\"harvest_crops\",\"count\":1}]")))).actions(),
                        new CompanionServerPlanPort(h.getLevel().getServer(), companion.getOwnerId(), "gametest-harvest-plan", () -> false)).whenComplete((outcome, error) -> reap.complete(outcome));
                grown[0] = true;
            }
            h.assertTrue(reap.isDone(), "The harvest plan is still running: " + companion.getLastTaskResult());
            var outcome = reap.join();
            h.assertTrue(outcome.report().contains("Собрал урожай с 4 грядок") && outcome.skipped().isEmpty(), "Harvest report: " + outcome.report());
            h.assertTrue(count(companion, Items.WHEAT) == 4 && blocks(h, GROUND + 1, Blocks.WHEAT).stream().allMatch(cell -> h.getBlockState(cell).getValue(CropBlock.AGE) == 0)
                    && blocks(h, GROUND + 1, Blocks.WHEAT).size() == 4, "Wheat " + count(companion, Items.WHEAT) + ", sown again " + blocks(h, GROUND + 1, Blocks.WHEAT).size());
        });
    }

    /** What the model may send is checked whole; «собери урожай» is all of it; the direct phrases work without any model. */
    @GameTest(template = "empty", batch = "farm")
    public static void farmOrdersAreCheckedAndRoutedWithoutTheModel(GameTestHelper h) {
        // The check for drops left lying looks at this test's own box: an item put there is seen, so "nothing lying" elsewhere means something.
        h.spawnItem(Items.DIRT, 1, 2, 1);
        h.assertTrue(lying(h).size() == 1, "The check for lying drops is blind: " + lying(h));
        var defaults = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"farm_crops\"},{\"action\":\"harvest_crops\"}]"));
        h.assertTrue(defaults.size() == 2 && defaults.get(0).item().equals("minecraft:wheat_seeds") && defaults.get(0).count() == 8
                && defaults.get(1).item().equals("any") && defaults.get(1).count() == 64, "Defaults: " + defaults);
        var carrots = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"farm_crops\",\"item\":\"minecraft:carrots\",\"count\":20}]"));
        h.assertTrue(carrots.size() == 2 && carrots.get(0).count() == 16 && carrots.get(1).count() == 4 && carrots.get(0).item().equals("minecraft:carrot"), "Twenty tiles: " + carrots);
        for (String bad : new String[]{"{\"action\":\"farm_crops\",\"item\":\"minecraft:diamond\"}", "{\"action\":\"farm_crops\",\"item\":\"minecraft:pumpkin_seeds\"}",
                "{\"action\":\"harvest_crops\",\"item\":\"minecraft:stone\"}", "{\"action\":\"farm_crops\",\"count\":65}"}) {
            boolean rejected = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[" + bad + "]")); } catch (IllegalStateException expected) { rejected = true; }
            h.assertTrue(rejected, "Accepted: " + bad);
        }
        var oneCrop = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"harvest_crops\",\"count\":1}]"));
        var all = CompanionPlanRequirements.apply("собери урожай", new CompanionReply("", oneCrop)).actions();
        h.assertTrue(all.size() == 1 && all.getFirst().count() == 64, "Only one crop harvested: " + all);
        var said = CompanionPlanRequirements.apply("собери 1 урожай", new CompanionReply("", oneCrop)).actions();
        h.assertTrue(said.getFirst().count() == 1, "A number from the player was overruled: " + said);
        h.assertTrue("companion work harvest_crops 64 any".equals(CompanionLocalCommands.route("Собери урожай", "Олег")), "Harvest phrase: " + CompanionLocalCommands.route("Собери урожай", "Олег"));
        h.assertTrue("companion work farm_crops 8 minecraft:wheat_seeds".equals(CompanionLocalCommands.route("Олег, сделай огород", "Олег")), "Field phrase: " + CompanionLocalCommands.route("Олег, сделай огород", "Олег"));
        h.assertTrue("companion work farm_crops 5 minecraft:carrot".equals(CompanionLocalCommands.route("посади 5 морковок", "Олег")), "Carrot phrase: " + CompanionLocalCommands.route("посади 5 морковок", "Олег"));
        h.assertTrue("companion work farm_crops 8 minecraft:beetroot_seeds".equals(CompanionLocalCommands.route("посади свёклу", "Олег")), "Beetroot phrase: " + CompanionLocalCommands.route("посади свёклу", "Олег"));
        h.assertTrue(CompanionLocalCommands.route("посади дерево", "Олег") == null, "A tree is not a crop");
        PlannedAction step = CompanionLocalCommands.action("companion work farm_crops 5 minecraft:carrot");
        h.assertTrue(step.type().equals("farm_crops") && step.count() == 5 && step.item().equals("minecraft:carrot"), "Step: " + step);
        String words = CompanionDialogue.describeAll(List.of(new PlannedAction("farm_crops", 0, 8, "minecraft:carrot"), new PlannedAction("harvest_crops", 0, 64, "any")));
        h.assertTrue(words.contains("морковь") && words.contains("грядок: 8") && words.contains("собрать урожай"), "Steps in words: " + words);
        // A request for a field is an order even in a fight (a question of small talk is not).
        h.assertTrue(!CompanionLocalCommands.hushed(true, "Можешь сделать огород у воды?") && CompanionLocalCommands.hushed(true, "Как тебе погода?"), "A farm request was hushed");
        h.succeed();
    }

    // ---- the world -----------------------------------------------------------------------------------------------------------

    /** Grass over dirt on bedrock, walled round: a field of its own for each test. */
    /** Review of PR #39: a flower bed beside the pond is the player's. No flower is torn out, no gap between them dug up. */
    @GameTest(template = "tall_tree", timeoutTicks = 1500, batch = "farm")
    public static void aFlowerBedBesideThePondIsLeftAlone(GameTestHelper h) {
        ground(h); pond(h, 16, 16);
        // Round the pond only the flower bed next to it and a strip of grass farther off are soil: the bed is the nearer of the two.
        for (int x = 11; x <= 21; x++) for (int z = 11; z <= 21; z++)
            if (x < 15 || x > 17 || z < 15 || z > 17) h.setBlock(new BlockPos(x, GROUND, z), x >= 20 || x >= 12 && x <= 14 && z >= 15 && z <= 17 ? Blocks.GRASS_BLOCK : Blocks.STONE);
        List<BlockPos> bed = new ArrayList<>();
        for (int x = 12; x <= 14; x++) for (int z = 15; z <= 17; z++) {
            BlockPos at = new BlockPos(x, GROUND + 1, z);
            h.setBlock(at, (x + z) % 2 == 0 ? Blocks.POPPY : Blocks.DANDELION);
            bed.add(at);
        }
        CompanionEntity companion = spawn(h, 12, 12, 10, 10, 16);
        give(companion, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 8));
        long id = order(h, companion, "farm_crops", "minecraft:wheat_seeds", 8);
        h.succeedWhen(() -> {
            CompanionActionResult result = result(h, companion, id);
            h.assertTrue(result.success(), result.message());
            List<BlockPos> land = blocks(h, GROUND, Blocks.FARMLAND);
            h.assertTrue(land.size() == 8, "Farmland " + land.size() + ": " + result.message());
            for (BlockPos flower : bed)
                h.assertTrue(h.getBlockState(flower).is(BlockTags.FLOWERS) && h.getBlockState(flower.below()).is(Blocks.GRASS_BLOCK), "The flower bed was touched at " + flower);
            for (BlockPos tile : land) for (BlockPos flower : bed)
                h.assertTrue(Math.abs(tile.getX() - flower.getX()) > 1 || Math.abs(tile.getZ() - flower.getZ()) > 1, "Dug up beside the flowers: " + tile);
        });
    }

    private static void ground(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= GROUND + 6; y++) {
            boolean border = x == 0 || x == 31 || z == 0 || z == 31;
            if (y <= GROUND) h.setBlock(new BlockPos(x, y, z), y == 0 || border ? Blocks.BEDROCK : y == GROUND ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            else if (border) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
    }

    /** Three by three, its surface level with the ground, in the ground's own layer: it holds still, nothing flows out of it. */
    private static void pond(GameTestHelper h, int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) h.setBlock(new BlockPos(x, GROUND, z), Blocks.WATER);
    }

    /** Watered farmland with a crop of the given age on every tile of the rectangle. */
    private static void patch(GameTestHelper h, int x0, int z0, int x1, int z1, Block crop, int age) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            h.setBlock(new BlockPos(x, GROUND, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, GROUND + 1, z), crop.defaultBlockState().setValue(CropBlock.AGE, age));
        }
    }

    /** Ripe wheat still standing. */
    private static int ripe(GameTestHelper h) {
        return (int) blocks(h, GROUND + 1, Blocks.WHEAT).stream().filter(cell -> h.getBlockState(cell).getValue(CropBlock.AGE) == 7).count();
    }

    private static CompanionEntity spawn(GameTestHelper h, int x, int z, int playerX, int playerZ, int radius) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), x, GROUND + 1, z);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(playerX, GROUND + 1, playerZ));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(radius);
        return companion;
    }

    private static void give(CompanionEntity companion, ItemStack... stacks) {
        for (int slot = 0; slot < stacks.length; slot++) companion.setItem(slot, stacks[slot]);
    }

    private static long order(GameTestHelper h, CompanionEntity companion, String action, String item, int count) {
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        CompanionActionResult accepted = CompanionEvents.executeAction(player, action, item, count);
        h.assertTrue(accepted.success(), action + " not accepted: " + accepted.message());
        return companion.activeWorkId();
    }

    private static CompanionActionResult result(GameTestHelper h, CompanionEntity companion, long id) {
        CompanionActionResult result = companion.workResult(id);
        h.assertTrue(result != null && !companion.isWorking(), "Still working: " + companion.getLastTaskResult());
        return result;
    }

    private static List<BlockPos> blocks(GameTestHelper h, int y, Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (h.getBlockState(pos).is(block)) found.add(pos);
        }
        return found;
    }

    private static int water(GameTestHelper h) {
        int count = 0;
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = GROUND - 1; y <= GROUND + 2; y++)
            if (h.getBlockState(new BlockPos(x, y, z)).getFluidState().is(FluidTags.WATER)) count++;
        return count;
    }

    /** Vanilla hydration, asked of the world: water at the farmland's level or one above, within four blocks sideways. */
    private static boolean nearWater(GameTestHelper h, BlockPos tile) {
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 1; dy++)
            if (h.getBlockState(tile.offset(dx, dy, dz)).getFluidState().is(FluidTags.WATER)) return true;
        return false;
    }

    /** Drops lying anywhere in this test's own box (the box of the test, wherever and however turned the game put it). */
    private static List<ItemEntity> lying(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds(), ItemEntity::isAlive);
    }

    private static ItemStack hoe(CompanionEntity companion) {
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(ItemTags.HOES)) return companion.getItem(slot);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(ItemTags.HOES)) return companion.getItemBySlot(slot);
        return ItemStack.EMPTY;
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
