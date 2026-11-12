package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionVillageBuildings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.aicompanion.gametest.CompanionHouseTests.*;

/**
 * What the review of the village buildings found: the words that mean «дострой», the plain house under the names a model gives it, the
 * unfinished building after a reload (no coal and no path lights taken from the way), a round that comes back empty, the species of a
 * log cabin that is no tree, and what is not his to break on a site he chooses himself.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildReviewTests {
    private static final String PLAIN = "деревенский дом";

    @GameTest(template = "empty")
    public static void theResumeWordsOnlyContinueAndAModelsPlainHouseNamesAreTheHouse(GameTestHelper h) {
        for (String said : new String[]{"дострой", "достраивай", "Олег, дострой", "дострой дом", "дострой домик", "дострой кузницу", "достраивай храм", "дострой таёжный дом", "дострой пустынный дом",
                "продолжи стройку", "продолжи строительство", "продолжи строить дом", "продолжай стройку", "продолжай строительство дома", "продолжи строить"}) {
            var action = CompanionWorkCommands.parse(said, "Олег");
            h.assertTrue(action != null && "build_house".equals(action.type()) && "resume".equals(action.item()), "«" + said + "» is not «resume»: " + (action == null ? null : action.item()));
        }
        for (String other : new String[]{"продолжай", "продолжи", "продолжи копать", "дострой замок", "дострой кузницу из золота"})
            h.assertTrue(CompanionWorkCommands.parse(other, "Олег") == null, "«" + other + "» was taken as a building order");
        h.assertTrue("кузница".equals(CompanionWorkCommands.parse("построй кузницу", "Олег").item()), "«построй кузницу» stopped naming the kind");
        // The plain house under the names a model gives it: an empty item, as the item of build_house always was ignored before the catalogue.
        for (String generic : new String[]{"дом", "house", "домик", "маленький дом", "дом 5×5", "small house", "Small House", "деревянный дом", "дом 5x5"})
            h.assertTrue(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"build_house\",\"item\":\"" + generic + "\"}]")).getFirst().item().isEmpty(), "«" + generic + "» is not the plain house");
        String like = CompanionVillageBuildings.refusal("дом дракона"), nothing = CompanionVillageBuildings.refusal("замок");
        h.assertTrue(like.contains(PLAIN) && like.split(",").length <= 3 && !like.contains("кузница") && !like.contains("Умею"), "No houses like a house: " + like);
        h.assertTrue(nothing.contains("5×5") && nothing.contains("кузница") && nothing.contains("храм") && nothing.contains("дом,"), "The kinds, houses included, are not named: " + nothing);
        for (String spaced : new String[]{"кузница равнин", "кузница\tравнин", "Кузница  равнин", " кузница равнин "})
            h.assertTrue(CompanionVillageBuildings.find(spaced) != null, "«" + spaced.replace('\t', '_') + "» names no building");
        String prompt = OpenRouterClient.buildSystemPrompt(OpenRouterClient.buildPersona("Весёлый напарник.", "Гоша", Map.of()), true, "лучший друг");
        h.assertTrue(prompt.contains("item=resume") && !prompt.contains("повторный build_house достраивает сохранённый дом"), "The prompt still says a repeated build_house goes on with any saved house");
        h.succeed();
    }

    /**
     * A building left unfinished and the world loaded again, a dark night, coal and sticks in the bag, no order running: the path lights
     * are made and put down as ever, and the coal does not turn into torches again and again for a reservation nobody keeps.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 450)
    public static void anUnfinishedBuildingAfterAReloadDoesNotSwallowTheCoalOrTheLights(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 7; y++) h.setBlock(new BlockPos(x, y, z), y == 6 || (x == 0 || x == 31 || z == 0 || z == 31) && y < 5 ? Blocks.BEDROCK : Blocks.AIR);
        }
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 15, 1, 15);
        var player = h.makeMockServerPlayerInLevel(); player.teleportTo(oleg.getX() + 2, oleg.getY(), oleg.getZ() + 2);
        oleg.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", oleg.getUUID());
        CompanionEvents.getOwnedCompanion(player); oleg.setSearchRadius(24);
        oleg.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        CompanionEntity reloaded = reloadedWithUnfinished(h, oleg, h.absolutePos(new BlockPos(24, 1, 24)), PLAIN);
        reloaded.setItem(0, new ItemStack(Items.COAL, 2)); reloaded.setItem(1, new ItemStack(Items.STICK, 2));
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 350, "Observe standing in the dark");
            int torches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, 3, 30))) if (h.getBlockState(pos).is(Blocks.TORCH)) torches++;
            h.assertTrue(torches == 1, "Path lights: " + torches + " (the unfinished building held every torch back)");
            h.assertTrue(count(reloaded, Items.COAL) == 1 && count(reloaded, Items.STICK) == 1 && count(reloaded, Items.TORCH) == 3, "Not one four-torch recipe and a light: " + reloaded.getStoredItemsSummary());
        });
    }

    /** The companion as the save of the world holds him when a building was left unfinished: reloaded with that saved build in his data. */
    public static CompanionEntity reloadedWithUnfinished(GameTestHelper h, CompanionEntity companion, BlockPos base, String building) {
        CompoundTag data = new CompoundTag(); companion.saveWithoutId(data);
        CompoundTag build = new CompoundTag();
        build.putLong("Base", base.asLong()); build.putString("Building", building); build.putInt("Rotation", 0); build.putInt("Total", 200);
        build.putString("Dimension", h.getLevel().dimension().location().toString()); build.putLongArray("Placed", new long[0]);
        data.put("TemplateBuild", build);
        companion.discard();
        CompanionEntity reloaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        reloaded.load(data); h.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    /** «Дострой» never starts another building; a new order for another one says it drops the unfinished one, and for the same one it goes on. */
    @GameTest(template = "tall_tree", timeoutTicks = 200)
    public static void anotherBuildingOrderSaysItDropsTheUnfinishedOneAndTheSameOneGoesOn(GameTestHelper h) {
        CompanionEntity companion = reloadedWithUnfinished(h, groundedSetup(h), h.absolutePos(new BlockPos(20, GROUND, 20)), PLAIN);
        BlockPos base = templateBase(companion);
        h.assertTrue(companion.startWork("build_house", "resume", 1).success(), "Resume rejected");
        h.assertTrue(PLAIN.equals(saved(companion).getCompound("TemplateBuild").getString("Building")) && base.equals(templateBase(companion)), "«дострой» left the unfinished building");
        companion.stopGathering();
        var same = companion.startWork("build_house", PLAIN, 1);
        h.assertTrue(same.success() && !same.message().contains("Бросаю") && base.equals(templateBase(companion)), "The same building was not gone on with: " + same.message());
        companion.stopGathering();
        var other = companion.startWork("build_house", "кузница", 1);
        h.assertTrue(other.success() && other.message().contains("Бросаю недостроенную «" + PLAIN + "» на " + base.toShortString() + " и начинаю «кузница"), "The dropped building was not said: " + other.message());
        h.assertTrue(!saved(companion).getCompound("TemplateBuild").contains("Base"), "The new building kept the old site");
        companion.stopGathering();
        h.succeed();
    }

    /**
     * No stone to be found: the round comes back empty, and he builds all the rest, names what is missing with the counts (the stone with
     * the glass, the torches and the bed) and, once the player has put it in the bag, «дострой» finishes the house.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void aStoneRoundWithNoStoneIsNamedWithTheRestAndGivingItFinishesTheHouse(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        Map<Item, Integer> reference = placeReference(h, LOG_HOUSE_TEMPLATE);
        Map<Item, Integer> bag = new LinkedHashMap<>(reference);
        bag.keySet().removeAll(UNGETTABLE); bag.remove(Items.COBBLESTONE);
        fill(companion, bag);
        tools(companion); spareDirt(companion);
        playerAway(h);
        long[] stopped = {-1};
        h.onEachTick(() -> {
            if (stopped[0] < 0 && h.getTick() > 40 && !companion.isWorking() && !companion.isGatheringTask()) {
                stopped[0] = h.getTick();
                String said = companion.getLastTaskResult();
                h.assertTrue(!companion.didLastTaskSucceed(), said);
                h.assertTrue(said.contains(reference.get(Items.COBBLESTONE) + " × " + new ItemStack(Items.COBBLESTONE).getHoverName().getString()), "The stone was not named with its count: " + said);
                for (Item item : UNGETTABLE) h.assertTrue(said.contains(reference.get(item) + " × " + new ItemStack(item).getHoverName().getString()), "Not named with its count: " + item + " in " + said);
                // The roof is above the layer of stone: the missing stone held nothing back.
                h.assertTrue(countBlocks(h, Blocks.OAK_STAIRS) == 2 * reference.get(Items.OAK_STAIRS) && countBlocks(h, Blocks.OAK_PLANKS) == 2 * reference.get(Items.OAK_PLANKS), "The rest of the house was not built");
                int slot = companion.getContainerSize() - 9;
                companion.setItem(slot++, new ItemStack(Items.COBBLESTONE, reference.get(Items.COBBLESTONE)));
                for (Item item : UNGETTABLE) companion.setItem(slot++, new ItemStack(item, reference.get(item)));
                h.assertTrue(companion.startWork("build_house", "resume", 1).success(), "Resume rejected");
            }
        });
        h.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] >= 0 && h.getTick() - stopped[0] > 20, "Not stopped and given yet");
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            assertLooksLikeReference(h, templateBase(companion));
        });
    }

    /** The only «oak» there is is the wall of a log cabin, and spruce trees grow: the oak house is made of spruce, the cabin stays. */
    @GameTest(template = "tall_tree", timeoutTicks = 3000)
    public static void aLogCabinWallIsNoTreeThatGrowsSoAnOakHouseIsMadeOfTheSpruceThatDoes(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        Map<Item, Integer> bag = new LinkedHashMap<>(placeReference(h, LOG_HOUSE_TEMPLATE));
        bag.keySet().removeIf(CompanionHouseTests::wooden);
        fill(companion, bag);
        tools(companion); noTrees(h); forest(h, "spruce"); playerAway(h);
        // A cabin: a ring of logs two high with planks on it and no leaves anywhere.
        for (int x = 9; x <= 11; x++) for (int z = 3; z <= 5; z++) {
            if (x != 10 || z != 4) for (int y = GROUND + 1; y <= GROUND + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
            h.setBlock(new BlockPos(x, GROUND + 3, z), Blocks.OAK_PLANKS);
        }
        h.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            var woods = saved(companion).getCompound("TemplateBuild").getCompound("Woods");
            h.assertTrue("spruce".equals(woods.getString("oak")), "The oak house is not made of spruce: " + woods);
            h.assertTrue(countBlocks(h, Blocks.OAK_LOG) >= 16, "A log of the cabin was cut");
        });
    }

    /** Stone is no plant: a site he picks himself, with no ghost to say where, is not one that needs stone dug out. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aSiteHeChoosesHimselfIsNotOneThatNeedsStoneDugOut(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        for (int x = 1; x < 32; x += 3) for (int z = 1; z < 32; z += 3) {
            if (Math.abs(x - 16) <= 1 && Math.abs(z - 16) <= 1 || Math.abs(x - 11) <= 1 && Math.abs(z - 11) <= 1) continue;
            h.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.STONE);
        }
        tools(companion);
        int stone = countBlocks(h, Blocks.STONE);
        h.assertTrue(companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("площадк"), companion.getLastTaskResult());
            h.assertTrue(countBlocks(h, Blocks.STONE) == stone, "Stone was dug out for a site he picked");
        });
    }
}
