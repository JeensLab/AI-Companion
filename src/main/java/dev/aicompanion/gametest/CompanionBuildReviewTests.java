package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.client.OpenRouterClient;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionVillageBuildings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
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
            h.assertTrue(action != null && "build_house".equals(action.type()) && action.item().startsWith("resume"), "«" + said + "» is not «resume»: " + (action == null ? null : action.item()));
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
     * are made and put down as ever; the torches the house itself takes (counted from its template, exactly) are never touched, and every
     * torch there is came out of a recipe of the coal that is gone (no torch from nowhere, none made for the reserve alone).
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
        var template = h.getLevel().getServer().getStructureManager().get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1")).orElseThrow();
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        int reserved = template.filterBlocks(BlockPos.ZERO, settings, Blocks.TORCH).size() + template.filterBlocks(BlockPos.ZERO, settings, Blocks.WALL_TORCH).size();
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 350, "Observe standing in the dark");
            int torches = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(30, 3, 30))) if (h.getBlockState(pos).is(Blocks.TORCH)) torches++;
            h.assertTrue(torches >= 1, "Path lights: " + torches + " (the unfinished building held every torch back)");
            h.assertTrue(count(reloaded, Items.TORCH) >= reserved, "The house's " + reserved + " torches were spent: " + reloaded.getStoredItemsSummary());
            h.assertTrue(count(reloaded, Items.TORCH) + torches == 4 * (2 - count(reloaded, Items.COAL)), "Torches that came from no recipe: " + torches + " set down, " + reloaded.getStoredItemsSummary());
        });
    }

    /** The companion as the save of the world holds him when a building was left unfinished: reloaded with that saved build in his data. */
    public static CompanionEntity reloadedWithUnfinished(GameTestHelper h, CompanionEntity companion, BlockPos base, String building) {
        CompoundTag data = new CompoundTag(); companion.saveWithoutId(data);
        CompoundTag build = new CompoundTag();
        build.putLong("Base", base.asLong()); build.putString("Building", building); build.putInt("Rotation", 0); build.putInt("Total", 200);
        build.putString("Dimension", h.getLevel().dimension().location().toString()); build.putLongArray("Placed", new long[0]);
        // The species were chosen long ago: a reload does not choose them again.
        build.put("Woods", new CompoundTag());
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

    // ---- second review: what is set aside for a building, a bag that is full, the floor of a site he picks

    /** The one the player is in the arena as: the mock player of this arena only (the other tests of a batch have theirs in the same level). */
    static ServerPlayer playerOf(GameTestHelper h) {
        for (var player : h.getLevel().players()) if (h.getBounds().inflate(1).contains(player.position())) return (ServerPlayer) player;
        throw new IllegalStateException("No player in the arena");
    }

    /**
     * The torches of the house are its material from the order until the building is finished or dropped: waiting for the player to set the
     * house down for a minute of night (up to two) he does not light the path with them, and once the place is confirmed they are all in the bag.
     */
    @GameTest(template = "tall_tree", batch = "house_night", timeoutTicks = 900)
    public static void torchesOfAnOrderWaitingForItsPlaceAreNotSpentOnPathLightsAtNight(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        ServerPlayer player = playerOf(h);
        CompanionBuildPlacement.clientReady(player);
        Map<Item, Integer> reference = placeReference(h, LOG_HOUSE_TEMPLATE);
        fill(companion, reference);
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(16, GROUND + 1, 12), new BlockPos(28, GROUND + 12, 24))) h.setBlock(rel, Blocks.AIR);
        h.assertTrue(h.getLevel().getDayTime() % 24000 > 13000, "The premise: it is night");
        h.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Order rejected");
        h.runAfterDelay(400, () -> {
            h.assertTrue(LOG_HOUSE.equals(CompanionBuildPlacement.pendingName(player)), "The order does not wait for the place");
            h.assertTrue(count(companion, Items.TORCH) == reference.get(Items.TORCH) && countBlocks(h, Blocks.TORCH) == 0, "Waiting for the place he put " + countBlocks(h, Blocks.TORCH) + " of the house's torches down as path lights");
            String refused = CompanionBuildPlacement.confirm(player, LOG_HOUSE, 0, h.absolutePos(new BlockPos(18, GROUND, 14)));
            h.assertTrue(refused == null, "Confirmation refused: " + refused);
            h.assertTrue(count(companion, Items.TORCH) == reference.get(Items.TORCH), "Not all of the house's torches are in the bag when the place is confirmed");
            companion.stopGathering();
            h.succeed();
        });
    }

    /**
     * A building left unfinished (stopped, failed, a world loaded again) with no order running: the torches the player gave for it are still its
     * material, to the exact count of the cells not yet built; one torch more lights the way as ever.
     */
    @GameTest(template = "tall_tree", batch = "house_night", timeoutTicks = 800)
    public static void torchesGivenToAnUnfinishedBuildingAreNotSpentOnPathLightsAtNight(GameTestHelper h) {
        CompanionEntity oleg = groundedSetup(h);
        Map<Item, Integer> reference = placeReference(h, LOG_HOUSE_TEMPLATE);
        int needed = reference.get(Items.TORCH);
        CompanionEntity reloaded = reloadedWithUnfinished(h, oleg, h.absolutePos(new BlockPos(24, GROUND, 24)), LOG_HOUSE);
        reloaded.setItem(0, new ItemStack(Items.TORCH, needed));
        h.assertTrue(h.getLevel().getDayTime() % 24000 > 13000, "The premise: it is night");
        h.runAfterDelay(360, () -> {
            h.assertTrue(count(reloaded, Items.TORCH) == needed && countBlocks(h, Blocks.TORCH) == 0, "The house's " + needed + " torches: " + count(reloaded, Items.TORCH) + " in the bag, " + countBlocks(h, Blocks.TORCH) + " put down as path lights");
            reloaded.setItem(1, new ItemStack(Items.TORCH, 1));
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 400, "Observe standing in the dark");
            h.assertTrue(countBlocks(h, Blocks.TORCH) == 1 && count(reloaded, Items.TORCH) == needed, "The torch above the house's own did not light the way: " + count(reloaded, Items.TORCH) + " in the bag, " + countBlocks(h, Blocks.TORCH) + " put down");
        });
    }

    /**
     * A stone round with a full bag: the bag is what he says, not that stone cannot be got, and the order is saved as it stands.
     * The bag holds everything the log house takes but the stone; once he goes for the stone no slot stays free (what he takes out of
     * a slot is filled again at once).
     */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void aFullBagInAStoneRoundEndsTheOrderWithTheBagAndNotWithNoStone(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        Map<Item, Integer> bag = new LinkedHashMap<>(placeReference(h, LOG_HOUSE_TEMPLATE));
        // The reference is only the count: no wall of it is left for the stone round to take for a quarry.
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) h.setBlock(rel, Blocks.AIR);
        bag.remove(Items.COBBLESTONE);
        fill(companion, bag);
        tools(companion); spareDirt(companion); noTrees(h); quarry(h); playerAway(h);
        boolean[] full = {false};
        h.onEachTick(() -> {
            full[0] |= companion.getLastTaskResult().startsWith("Не хватает камня");
            if (full[0]) for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).isEmpty()) companion.setItem(slot, new ItemStack(Items.IRON_SWORD));
        });
        h.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            done(h, companion);
            String said = companion.getLastTaskResult();
            h.assertTrue(!companion.didLastTaskSucceed() && said.toLowerCase().contains("нет места"), "The bag was not named: " + said);
            h.assertTrue(!said.contains("не добуду") && !said.contains("Булыжник"), "A full bag was told as stone that cannot be got: " + said);
            h.assertTrue(saved(companion).getCompound("TemplateBuild").contains("Base"), "The unfinished building was not kept");
        });
    }

    /** The floor of a site he picks himself is sunk into the ground: stone there is no more his to dig out than stone above it. */
    @GameTest(template = "tall_tree", timeoutTicks = 600)
    public static void aSiteHeChoosesHimselfIsNotOneWhoseFloorNeedsStoneDugOut(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        // A lattice of stone in the layer the floor goes into: no site of the arena is free of it, and none needs a block cleared above the ground.
        for (int x = 1; x < 32; x += 3) for (int z = 1; z < 32; z += 3) h.setBlock(new BlockPos(x, GROUND, z), Blocks.STONE);
        tools(companion);
        int stone = countBlocks(h, Blocks.STONE);
        h.assertTrue(companion.startWork("build_house", VILLAGE_HOUSE, 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("площадк"), companion.getLastTaskResult());
            h.assertTrue(countBlocks(h, Blocks.STONE) == stone, "Stone was dug out of the floor of a site he picked");
        });
    }

    /**
     * The species of a building is chosen again as long as no block of wood stands, not only while nothing stands: the stone foundation goes
     * first. The only oak that grows is shut in bedrock (the round for it comes back empty), the bag holds the spruce of everything wooden
     * the log house takes: the oak house is made of the spruce he carries.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 12000)
    public static void theSpeciesIsChosenAgainWhileOnlyStoneStands(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        Map<Item, Integer> bag = new LinkedHashMap<>();
        for (var entry : placeReference(h, LOG_HOUSE_TEMPLATE).entrySet()) {
            Item item = entry.getKey();
            if (wooden(item)) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath().replace("oak", "spruce")));
            bag.merge(item, entry.getValue(), Integer::sum);
        }
        // The reference is only the count: it is no oak planks of the arena, and no log of it a tree.
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(8, 8, 8))) h.setBlock(rel, Blocks.AIR);
        noTrees(h);
        // An oak tree in a cage of bedrock: it counts as a tree that grows, and cannot be got at.
        for (BlockPos rel : BlockPos.betweenClosed(new BlockPos(24, GROUND + 1, 24), new BlockPos(28, GROUND + 7, 28))) h.setBlock(rel, Blocks.BEDROCK);
        for (int y = GROUND + 1; y <= GROUND + 4; y++) h.setBlock(new BlockPos(26, y, 26), Blocks.OAK_LOG);
        for (int x = 25; x <= 27; x++) for (int z = 25; z <= 27; z++) for (int y = GROUND + 4; y <= GROUND + 5; y++)
            if (x != 26 || z != 26 || y > GROUND + 4) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1));
        fill(companion, bag);
        tools(companion); spareDirt(companion); playerAway(h);
        h.assertTrue(companion.startWork("build_house", LOG_HOUSE, 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(countBlocks(h, Blocks.SPRUCE_PLANKS) > 0 && countBlocks(h, Blocks.OAK_PLANKS) == 0, "The house is not made of the spruce: " + countBlocks(h, Blocks.SPRUCE_PLANKS) + " spruce, " + countBlocks(h, Blocks.OAK_PLANKS) + " oak planks");
        });
    }

    /**
     * A drop lies on the ground beside the column while he climbs it (what clearing dropped, a sapling, scaffold dirt): the pickup does not
     * start while he is on the column, so he is not walked off it; the building is finished in one order, the drop is in the bag afterwards and
     * no block of the scaffolding is left.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void aDropOnTheGroundDoesNotPullHimOffTheColumnWhileHeClimbsIt(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        Map<Item, Integer> bag = placeReference(h, net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1"));
        fill(companion, bag);
        companion.setItem(companion.getContainerSize() - 1, new ItemStack(Items.DIRT, 24));
        int[] dropped = {0};
        boolean[] armed = {true};
        h.onEachTick(() -> {
            int dirt = countBlocks(h, Blocks.DIRT);
            if (dirt == 0) armed[0] = true;
            // Mid-climb: the status says so and the column stands two blocks high.
            else if (armed[0] && dirt >= 2 && dropped[0] < 3 && companion.getLastTaskResult().startsWith("Поднимаюсь на леса")) {
                armed[0] = false; dropped[0]++;
                var item = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), companion.getX() + 2.5, companion.getY() + .2, companion.getZ() + .5, new ItemStack(Items.OAK_SAPLING));
                item.setPickUpDelay(10);
                h.getLevel().addFreshEntity(item);
                companion.claimDrops(java.util.List.of(item));
            }
        });
        h.assertTrue(companion.startWork("build_house", "деревенский дом", 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(dropped[0] > 0, "The premise: a column was put up and a drop lay beside it");
            h.assertTrue(companion.didLastTaskSucceed() && !companion.getLastTaskResult().contains("Соскользнул"), companion.getLastTaskResult());
            h.assertTrue(count(companion, Items.OAK_SAPLING) == dropped[0], "The drops are not in the bag: " + count(companion, Items.OAK_SAPLING) + " of " + dropped[0]);
            h.assertTrue(countBlocks(h, Blocks.DIRT) == 0 && count(companion, Items.DIRT) == 24, "Scaffolding dirt: " + count(companion, Items.DIRT) + " in the bag, " + countBlocks(h, Blocks.DIRT) + " in the world");
        });
    }

    /** Found below the column during the climb (pushed, fallen, knocked off): not the end of the order; the column comes down and the building is finished. */
    @GameTest(template = "tall_tree", timeoutTicks = 9000)
    public static void beingKnockedOffTheColumnIsNotTheEndOfTheOrder(GameTestHelper h) {
        CompanionEntity companion = groundedSetup(h);
        var reference = net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
        fill(companion, placeReference(h, reference));
        companion.setItem(companion.getContainerSize() - 1, new ItemStack(Items.DIRT, 24));
        int[] knocked = {0};
        boolean[] armed = {true};
        h.onEachTick(() -> {
            int dirt = countBlocks(h, Blocks.DIRT);
            if (dirt == 0) armed[0] = true;
            else if (armed[0] && dirt >= 2 && knocked[0] < 1 && companion.getLastTaskResult().startsWith("Поднимаюсь на леса")) {
                armed[0] = false; knocked[0]++;
                companion.teleportTo(companion.getX() + 3, companion.getY() - 2.5, companion.getZ());
            }
        });
        h.assertTrue(companion.startWork("build_house", "деревенский дом", 1).success(), "Template house work rejected");
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(knocked[0] == 1, "The premise: he was knocked off a column of two blocks");
            h.assertTrue(companion.didLastTaskSucceed() && !companion.getLastTaskResult().contains("Соскользнул"), companion.getLastTaskResult());
            h.assertTrue(countBlocks(h, Blocks.DIRT) == 0 && count(companion, Items.DIRT) == 24, "Scaffolding dirt: " + count(companion, Items.DIRT) + " in the bag, " + countBlocks(h, Blocks.DIRT) + " in the world");
        });
    }
}
