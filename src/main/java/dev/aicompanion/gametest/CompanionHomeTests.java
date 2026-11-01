package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.CompanionWorkCommands;
import dev.aicompanion.entity.CompanionAutonomy;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Oleg goes to what he remembers instead of breeding chests and furnaces, and sets new ones down at home. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionHomeTests {
    private static final int STAND = 8, Z = 64;

    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void storeWalksToRememberedChestPastSearchRadius(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 40);
        h.setBlock(chest, Blocks.CHEST); c.rememberPlaced(h.absolutePos(chest));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success(), "Store order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(count((Container)h.getBlockEntity(chest), Items.COBBLESTONE) == 12 && blocks(h, Blocks.CHEST) == 1, "Not in the remembered chest: " + c.getLastTaskResult());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void takeWalksToRememberedChest(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 40);
        h.setBlock(chest, Blocks.CHEST); c.rememberPlaced(h.absolutePos(chest));
        ((Container)h.getBlockEntity(chest)).setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "take_items", "minecraft:iron_ingot", 5).success(), "Take order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed() && carried(c, Items.IRON_INGOT) == 5, c.getLastTaskResult());
        });
    }

    /** The structure's chunks are held loaded by the test server: the far furnace is out of the search here, not out of the world. */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void smeltWalksToRememberedFurnacePastSearchRadius(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos furnace = cell(STAND + 56);
        h.setBlock(furnace, Blocks.FURNACE); c.rememberPlaced(h.absolutePos(furnace));
        c.setItem(0, new ItemStack(Items.RAW_IRON, 2)); c.setItem(1, new ItemStack(Items.COAL, 2));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "smelt_items", "minecraft:raw_iron", 2).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed() && carried(c, Items.IRON_INGOT) == 2, c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 1, "A new furnace was set down");
        });
    }

    /** No home in the save at all: only the finished house. The new chest stands inside and the door stays free. */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void oldSaveFinishedHouseGetsTheNewChestInside(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos base = new BlockPos(STAND + 28, 0, Z - 2);
        house(h, c, base);
        c.setItem(0, new ItemStack(Items.CHEST)); c.setItem(1, new ItemStack(Items.COBBLESTONE, 12));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success(), "Store order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            int inside = 0; BlockPos at = null;
            for (BlockPos p : BlockPos.betweenClosed(base.offset(1, 1, 1), base.offset(3, 1, 3))) if (h.getBlockState(p).is(Blocks.CHEST)) { inside++; at = p.immutable(); }
            h.assertTrue(inside == 1 && blocks(h, Blocks.CHEST) == 1, "Chest not inside the house: " + where(h, Blocks.CHEST) + " base " + base + " he at " + h.relativePos(c.blockPosition()) + " " + c.getLastTaskResult());
            h.assertTrue(at.getZ() > base.getZ() + 1, "Chest blocks the door: " + at);
            h.assertTrue(count((Container)h.getBlockEntity(at), Items.COBBLESTONE) == 12, "Stone not stored");
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 20 * 40)
    public static void namedHomeSurvivesDeathAndReachesSummary(GameTestHelper h) {
        CompanionEntity c = setup(h);
        ServerPlayer player = owner(h, c);
        h.assertTrue(CompanionEvents.executeAction(player, "set_home", "", 1).success(), "Home not taken");
        String here = player.blockPosition().toShortString();
        h.assertTrue(c.getPlacesForAi().startsWith("Известные места") && c.getPlacesForAi().contains("дом на " + here), "No home in the summary: " + c.getPlacesForAi());
        c.kill();
        h.succeedWhen(() -> {
            CompanionEntity back = CompanionEvents.getOwnedCompanion(player);
            h.assertTrue(back != null && back.isAlive() && !back.getUUID().equals(c.getUUID()), "He did not come back");
            h.assertTrue(back.getPlacesForAi().contains("дом на " + here), "Forgot home: " + back.getPlacesForAi());
        });
    }

    /** A named home in another dimension is not home here: no walk, the chest stands where he is. */
    @GameTest(template = "large_search", timeoutTicks = 800)
    public static void homeOfAnotherDimensionIsNoHomeHere(GameTestHelper h) {
        CompanionEntity c = setup(h);
        CompoundTag saved = new CompoundTag(); c.addAdditionalSaveData(saved);
        CompoundTag home = new CompoundTag(); home.putString("Dimension", "minecraft:the_nether"); home.putLong("Pos", h.absolutePos(cell(STAND + 40)).asLong());
        saved.put("HomePlace", home); c.readAdditionalSaveData(saved);
        c.setItem(0, new ItemStack(Items.CHEST)); c.setItem(1, new ItemStack(Items.COBBLESTONE, 4));
        h.assertTrue(!c.getPlacesForAi().contains("дом на"), "A home of another dimension shown here: " + c.getPlacesForAi());
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success(), "Store order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed() && blocks(h, Blocks.CHEST) == 1, c.getLastTaskResult());
            h.assertTrue(c.distanceToSqr(h.absoluteVec(cell(STAND).getCenter())) < 8 * 8, "Walked off for a home that is not here");
        });
    }

    /** The way home is walled off: one chest where he stands, the reason said, and the next order uses that chest. */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void noRouteHomeSaysSoAndPlacesOneChest(GameTestHelper h) {
        CompanionEntity c = setup(h);
        for (int x = STAND - 4; x <= STAND + 4; x++) for (int z = Z - 4; z <= Z + 4; z++) for (int y = 1; y <= 6; y++)
            if (Math.abs(x - STAND) == 4 || Math.abs(z - Z) == 4) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        BlockPos home = cell(STAND + 50);
        owner(h, c).setPos(h.absoluteVec(home.getCenter()));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "set_home", "", 1).success(), "Home not taken");
        owner(h, c).teleportTo(c.getX() + 1, c.getY(), c.getZ());
        c.setItem(0, new ItemStack(Items.CHEST)); c.setItem(1, new ItemStack(Items.COBBLESTONE, 4));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success(), "Store order not accepted");
        boolean[] second = {false};
        String[] said = {""};
        h.onEachTick(() -> {
            if (second[0] || c.isWorking() || c.isGatheringTask()) return;
            said[0] = c.getLastTaskResult();
            c.setItem(1, new ItemStack(Items.COBBLESTONE, 5));
            second[0] = CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success();
        });
        h.succeedWhen(() -> {
            h.assertTrue(second[0], "Second store not started");
            done(h, c);
            h.assertTrue(said[0].contains("нет пути"), "The reason was not said: " + said[0]);
            h.assertTrue(blocks(h, Blocks.CHEST) == 1, "A second chest was set down: " + c.getLastTaskResult());
            h.assertTrue(carried(c, Items.COBBLESTONE) == 0, "Second batch not stored: " + c.getLastTaskResult());
        });
    }

    /** A chest the player set down himself is where he lives: a new furnace goes there, past the search round Oleg. */
    @GameTest(template = "large_search", timeoutTicks = 1800)
    public static void furnaceGoesWhereThePlayerSetHisChest(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 50);
        h.setBlock(chest, Blocks.CHEST); placedByPlayer(h, c, chest);
        c.setItem(0, new ItemStack(Items.FURNACE)); c.setItem(1, new ItemStack(Items.RAW_IRON, 1)); c.setItem(2, new ItemStack(Items.COAL, 1));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "smelt_items", "minecraft:raw_iron", 1).success(), "Smelt order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 1, "No single furnace");
            h.assertTrue(near(h, Blocks.FURNACE, chest, 6), "Furnace not at the player's chest: " + c.getLastTaskResult());
        });
    }

    /** A chest of his in a chunk Oleg has never searched is used without a visit first: it is in the memory of the world. */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void storeGoesToThePlayersOwnChest(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 40);
        h.setBlock(chest, Blocks.CHEST); placedByPlayer(h, c, chest);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success(), "Store order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed() && count((Container)h.getBlockEntity(chest), Items.COBBLESTONE) == 7, c.getLastTaskResult());
        });
    }

    /** The chests outweigh a bigger heap of plain blocks, and the plain heap is a home only once it is a building, never as a soil pillar. */
    @GameTest(template = "large_search", timeoutTicks = 100)
    public static void chestsMakeTheHomeOverPlainBlocks(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setNoAi(true);
        for (int y = 1; y <= 45; y++) placedByPlayer(h, c, new BlockPos(100, 1 + y % 3, 100 + y % 2), Blocks.DIRT);
        h.assertTrue(!c.getPlacesForAi().contains("дом на"), "A pile of soil became a home: " + c.getPlacesForAi());
        for (int i = 0; i < 39; i++) placedByPlayer(h, c, new BlockPos(100 + i % 8, 1 + i % 4, 110 + i % 3), Blocks.COBBLESTONE);
        h.assertTrue(!c.getPlacesForAi().contains("дом на"), "Too few blocks for a home: " + c.getPlacesForAi());
        placedByPlayer(h, c, new BlockPos(100, 3, 112), Blocks.COBBLESTONE);
        h.assertTrue(c.getPlacesForAi().contains("здесь строит игрок"), "A building is no home: " + c.getPlacesForAi());
        BlockPos chest = cell(60);
        h.setBlock(chest, Blocks.CHEST); placedByPlayer(h, c, chest);
        BlockPos second = cell(62);
        h.setBlock(second, Blocks.CHEST); placedByPlayer(h, c, second);
        h.assertTrue(c.getPlacesForAi().contains("дом на " + h.absolutePos(chest).toShortString()), "Two chests did not outweigh the heap: " + c.getPlacesForAi());
        h.setBlock(chest, Blocks.AIR); h.setBlock(second, Blocks.AIR);
        h.assertTrue(!c.getPlacesForAi().contains("дом на " + h.absolutePos(chest).toShortString()), "Gone chests still make a home: " + c.getPlacesForAi());
        h.succeed();
    }

    /** What the player says outranks what he builds. */
    @GameTest(template = "large_search", timeoutTicks = 100)
    public static void namedHomeOutranksThePlayersBuilding(GameTestHelper h) {
        CompanionEntity c = setup(h);
        c.setNoAi(true);
        BlockPos chest = cell(60);
        h.setBlock(chest, Blocks.CHEST); placedByPlayer(h, c, chest);
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "set_home", "", 1).success(), "Home not taken");
        h.assertTrue(c.getPlacesForAi().contains("назвал игрок") && !c.getPlacesForAi().contains("здесь строит игрок"), c.getPlacesForAi());
        h.succeed();
    }

    /** Far from a vein the iron chain does not turn back home: the furnace stands by the vein. */
    @GameTest(template = "large_search", timeoutTicks = 2400)
    public static void ironChainStaysAtTheVeinWithFarHome(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 90);
        h.setBlock(chest, Blocks.CHEST); placedByPlayer(h, c, chest);
        c.setItem(0, new ItemStack(Items.FURNACE)); c.setItem(1, new ItemStack(Items.RAW_IRON, 3)); c.setItem(2, new ItemStack(Items.COAL, 3));
        c.setItem(3, new ItemStack(Items.STICK, 2));
        h.setBlock(cell(STAND + 2), Blocks.CRAFTING_TABLE);
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "craft_item", "minecraft:iron_pickaxe", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed() && c.getMainHandItem().is(Items.IRON_PICKAXE), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 1 && near(h, Blocks.FURNACE, cell(STAND), 8), "He left the vein for home: " + where(h, Blocks.FURNACE) + " he at " + h.relativePos(c.blockPosition()) + " " + c.getLastTaskResult());
        });
    }

    /** A deed of his own: a home past the leash stays out of it, and the player's chest there is not his. */
    @GameTest(template = "large_search", timeoutTicks = 200)
    public static void seenHomeChestStaysThePlayers(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 4);
        h.setBlock(chest, Blocks.CHEST); placedByPlayer(h, c, chest);
        h.runAtTickTime(120, () -> {
            h.assertTrue(c.getPlacesForAi().contains("minecraft:chest на " + h.absolutePos(chest).toShortString()), "Home chest not noticed: " + c.getPlacesForAi());
            h.assertTrue(c.getPlacesForAi().contains("(видел)") && !c.getPlacesForAi().contains("(поставил)"), "A seen chest counts as his own: " + c.getPlacesForAi());
            h.succeed();
        });
    }

    /** «Стой» in the middle of the walk ends it: he does not go on to the chest, and the things stay with him. */
    @GameTest(template = "large_search", timeoutTicks = 400)
    public static void stopInTheMiddleOfTheWalkEndsIt(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos chest = cell(STAND + 60);
        h.setBlock(chest, Blocks.CHEST); c.rememberPlaced(h.absolutePos(chest));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 6));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "store_items", "stone", 1).success(), "Store order not accepted");
        double[] stoppedAt = {0};
        h.runAtTickTime(60, () -> {
            h.assertTrue(c.isWorking(), "Not on the way: " + c.getLastTaskResult());
            h.assertTrue(CompanionEvents.executeAction(owner(h, c), "stop", "", 1).success(), "Stop refused");
            c.setFollowing(false);
            stoppedAt[0] = c.getX();
        });
        h.runAtTickTime(160, () -> {
            done(h, c);
            h.assertTrue(Math.abs(c.getX() - stoppedAt[0]) < 3, "Walked on after the stop: " + (c.getX() - stoppedAt[0]));
            h.assertTrue(carried(c, Items.COBBLESTONE) == 6 && count((Container)h.getBlockEntity(chest), Items.COBBLESTONE) == 0, "Things moved: " + c.getLastTaskResult());
            h.succeed();
        });
    }

    /** The next step of a plan starts from home with the player far off: the block still stands by the player, not by the chest he left. */
    @GameTest(template = "large_search", timeoutTicks = 1600)
    public static void placeBlockForAPlayerFarOffIsWalkedInLegs(GameTestHelper h) {
        CompanionEntity c = setup(h);
        BlockPos away = cell(STAND + 70);
        owner(h, c).setPos(h.absoluteVec(away.getCenter()));
        c.setItem(0, new ItemStack(Items.CHEST));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "place_block", "minecraft:chest", 1).success(), "Order not accepted");
        h.succeedWhen(() -> {
            done(h, c);
            h.assertTrue(c.didLastTaskSucceed() && near(h, Blocks.CHEST, away, 5), "Not by the player: " + where(h, Blocks.CHEST) + " " + c.getLastTaskResult());
        });
    }

    /** A deed of his own that needs a new chest sets it down on the leash, however good a home is known farther on. */
    @GameTest(template = "large_search", batch = "home_deed", timeoutTicks = 20 * 60 * 3)
    public static void deedNeverLeavesLeashForHome(GameTestHelper h) {
        CompanionEntity c = setup(h);
        owner(h, c).setPos(h.absoluteVec(cell(STAND + 60).getCenter()));
        h.assertTrue(CompanionEvents.executeAction(owner(h, c), "set_home", "", 1).success(), "Home not taken");
        owner(h, c).teleportTo(c.getX() + 1, c.getY(), c.getZ());
        c.setItem(1, new ItemStack(Items.OAK_LOG, 4));
        Item[] junk = {Items.COBBLESTONE, Items.DIRT, Items.GRAVEL, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.FLINT, Items.SAND};
        for (int slot = 2; slot < 27 - CompanionAutonomy.BAG_FREE_SLOTS; slot++) c.setItem(slot, new ItemStack(junk[slot % junk.length], 64));
        var start = c.position();
        double[] farthest = {0};
        h.onEachTick(() -> farthest[0] = Math.max(farthest[0], c.position().distanceTo(start)));
        c.autonomy().setEnabled(true);
        h.succeedWhen(() -> {
            h.assertTrue(blocks(h, Blocks.CHEST) == 1 && near(h, Blocks.CHEST, cell(STAND), CompanionAutonomy.LEASH), "No chest of his on the leash: " + c.getLastTaskResult());
            h.assertTrue(farthest[0] <= CompanionAutonomy.LEASH + 4, "Left the leash: " + farthest[0]);
            c.autonomy().setEnabled(false); c.setFollowing(false);
        });
    }

    /** The phrases and the codec, with no provider. */
    @GameTest(template = "empty")
    public static void homeIsNamedInWordsAndBadStepsSpoilThePlan(GameTestHelper h) {
        for (String said : java.util.List.of("это наш дом", "Олег, здесь наш дом", "тут будет наш дом", "наш дом здесь", "вот мой дом", "запомни это место как дом"))
            h.assertTrue("companion work set_home 1 resources".equals(CompanionLocalCommands.route(said, "Олег")), "Not understood: " + said);
        for (String said : java.util.List.of("это наш дом?", "это не наш дом", "построй дом", "у нас есть дом"))
            h.assertTrue(CompanionWorkCommands.parse(said, "Олег") == null || !"set_home".equals(CompanionWorkCommands.parse(said, "Олег").type()), "Taken as naming: " + said);
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"set_home\"}]"));
        h.assertTrue(plan.size() == 1 && plan.getFirst().type().equals("set_home"), "Step lost");
        for (String bad : java.util.List.of("[{\"action\":\"set_home\",\"count\":2}]", "[{\"action\":\"set_home\",\"item\":\"minecraft:chest\"}]", "[{\"action\":\"set_home\",\"location\":\"inside_house\"}]")) {
            boolean refused = false;
            try { CompanionPlanCodec.parsePlan(JsonParser.parseString(bad)); } catch (RuntimeException expected) { refused = true; }
            h.assertTrue(refused, "Accepted: " + bad);
        }
        h.succeed();
    }

    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos pos) {
        BlockPos at = h.absolutePos(pos);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), at), Blocks.BEDROCK.defaultBlockState(), owner(h, c)));
    }
    private static void placedByPlayer(GameTestHelper h, CompanionEntity c, BlockPos pos, Block block) { h.setBlock(pos, block); placedByPlayer(h, c, pos); }

    /** The house of a save from before homes: its blocks, and a blueprint with every step placed. */
    private static void house(GameTestHelper h, CompanionEntity c, BlockPos base) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(base.offset(x, 0, z), Blocks.OAK_PLANKS); h.setBlock(base.offset(x, 4, z), Blocks.OAK_SLAB);
            if (x == 0 || x == 4 || z == 0 || z == 4) for (int y = 1; y <= 3; y++) h.setBlock(base.offset(x, y, z), Blocks.OAK_PLANKS);
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        h.setBlock(base.offset(2, 1, 0), door); h.setBlock(base.offset(2, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        h.setBlock(base.offset(2, 0, -1), Blocks.OAK_PLANKS);
        CompoundTag saved = new CompoundTag(); c.addAdditionalSaveData(saved);
        CompoundTag house = new CompoundTag(); house.putLong("Base", h.absolutePos(base).asLong()); house.putString("Wood", "oak");
        house.putString("Dimension", h.getLevel().dimension().location().toString());
        house.put("Placed", new LongArrayTag(new long[]{-1, -1, -1, -1, -1, -1, -1, -1}));
        saved.put("SmallHouse", house); c.readAdditionalSaveData(saved);
    }

    private static BlockPos cell(int x) { return new BlockPos(x, 1, Z); }
    private static boolean near(GameTestHelper h, Block block, BlockPos to, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(to.offset(-radius, 0, -radius), to.offset(radius, 3, radius))) if (h.getBlockState(pos).is(block)) return true;
        return false;
    }
    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 0; x < 128; x++) for (int z = Z - 12; z < Z + 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), STAND, 1, Z);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(c.getX() + 1, c.getY(), c.getZ());
        c.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        c.setSearchRadius(16);
        return c;
    }
    private static ServerPlayer owner(GameTestHelper h, CompanionEntity c) { return h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId()); }
    private static void done(GameTestHelper h, CompanionEntity c) { h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Work still active: " + c.getLastTaskResult()); }
    private static int blocks(GameTestHelper h, Block block) {
        int result = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 40), new BlockPos(127, 4, 88))) if (h.getBlockState(pos).is(block)) result++;
        return result;
    }
    private static java.util.List<BlockPos> where(GameTestHelper h, Block block) {
        var found = new java.util.ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 40), new BlockPos(127, 4, 88))) if (h.getBlockState(pos).is(block)) found.add(pos.immutable());
        return found;
    }
    private static int count(Container container, Item item) {
        int result = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(item)) result += container.getItem(slot).getCount();
        return result;
    }
    private static int carried(CompanionEntity c, Item item) {
        int result = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(item)) result += c.getItem(slot).getCount();
        return result;
    }
}
