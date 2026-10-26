package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.*;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSpecificationTests {
    @GameTest(template = "empty")
    public static void preservesAllExplicitBaseRequests(GameTestHelper h) {
        var reply = CompanionPlanRequirements.apply("Можешь внутри нашего дома поставить сундук, печку и сложить все свои вещи в сундук?",
                new CompanionReply("ладно", List.of(new PlannedAction("place_block", 0, 1, "minecraft:chest"))));
        h.assertTrue(reply.actions().size() == 3, "Lost furnace or storage conjunct");
        h.assertTrue(reply.actions().stream().allMatch(s -> s.location().equals("inside_house")), "Lost interior constraint");
        h.assertTrue(reply.actions().get(1).item().equals("minecraft:furnace") && reply.actions().get(2).item().equals("all"), "Wrong prerequisites/storage order");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void questionsAndNegationsDoNotInventActions(GameTestHelper h) {
        var empty = new CompanionReply("хорошо", List.of());
        for (String phrase : List.of("Не надо поставить сундук внутри дома", "Почему ты хочешь поставить сундук?", "Где поставить печку"))
            h.assertTrue(CompanionPlanRequirements.apply(phrase, empty).actions().isEmpty(), "Invented order: " + phrase);
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void destinationReferencesAreNotExtraPlacementOrders(GameTestHelper h) {
        var empty = new CompanionReply("хорошо", List.of());
        var near = CompanionPlanRequirements.apply("Поставь сундук рядом с верстаком", empty);
        h.assertTrue(near.actions().size()==1 && near.actions().getFirst().item().equals("minecraft:chest"), "Destination became another order");
        h.assertTrue(CompanionPlanRequirements.apply("Как поставить печку?", empty).actions().isEmpty(), "Question became action");
        h.assertTrue(CompanionPlanRequirements.apply("Поставь сундук или печку", empty).actions().isEmpty(), "Alternative became two actions");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void locationProtocolRejectsUnknownDestinations(GameTestHelper h) {
        var plan = CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"place_block\",\"item\":\"minecraft:chest\",\"location\":\"inside_house\"}]"));
        h.assertTrue(plan.getFirst().location().equals("inside_house"), "Interior location lost");
        boolean rejected = false;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"place_block\",\"item\":\"minecraft:chest\",\"location\":\"anywhere\"}]")); }
        catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected, "Unknown destination accepted"); h.succeed();
    }
    @GameTest(template = "tall_tree", timeoutTicks = 400)
    public static void missingWorkbenchIsCraftedBeforePlacement(GameTestHelper h) {
        var c = setup(h); c.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        h.assertTrue(c.startWork("place_block", "minecraft:crafting_table", 1).success(), "Order rejected");
        h.succeedWhen(() -> { done(h,c); h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.CRAFTING_TABLE) == 1, "No real workbench"); });
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void furnaceIsCraftedAndPlacedFromRealStone(GameTestHelper h) {
        var c = setup(h); c.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        h.setBlock(new BlockPos(17,1,16), Blocks.CRAFTING_TABLE);
        c.startWork("place_block", "minecraft:furnace", 1);
        h.succeedWhen(() -> { done(h,c); h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            h.assertTrue(blocks(h, Blocks.FURNACE) == 1 && blocks(h, Blocks.CRAFTING_TABLE) == 1, "Furnace missing or bench duplicated"); });
    }
    @GameTest(template = "tall_tree")
    public static void unknownInteriorFailsWithoutOutsideSubstitution(GameTestHelper h) {
        var c = setup(h); c.setItem(0, new ItemStack(Items.CHEST));
        var result = c.startWork("place_block", "minecraft:chest", 1, "inside_house");
        h.assertTrue(!result.success() && result.status() == CompanionActionResult.Status.BLOCKED, "Missing house was not blocked");
        h.assertTrue(blocks(h, Blocks.CHEST) == 0 && c.getItem(0).is(Items.CHEST), "Placed outside or lost item"); h.succeed();
    }
    @GameTest(template = "tall_tree", timeoutTicks = 500)
    public static void chestGoesInsideSavedHouseThroughDoor(GameTestHelper h) {
        var c = setup(h);
        BlockPos base = new BlockPos(20,0,20);
        for (int x=0;x<5;x++) for (int z=0;z<5;z++) {
            h.setBlock(base.offset(x,0,z), Blocks.OAK_PLANKS); h.setBlock(base.offset(x,4,z), Blocks.OAK_SLAB);
            if (x==0 || x==4 || z==0 || z==4) for(int y=1;y<=3;y++) h.setBlock(base.offset(x,y,z), Blocks.OAK_PLANKS);
        }
        h.setBlock(base.offset(2,1,0), Blocks.AIR); h.setBlock(base.offset(2,2,0), Blocks.AIR);
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH);
        h.setBlock(base.offset(2,1,0), door); h.setBlock(base.offset(2,2,0), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        CompoundTag saved = new CompoundTag(); c.addAdditionalSaveData(saved);
        CompoundTag house = new CompoundTag(); house.putLong("Base", h.absolutePos(base).asLong()); house.putString("Wood", "oak");
        house.putString("Dimension", h.getLevel().dimension().location().toString()); saved.put("SmallHouse", house); c.readAdditionalSaveData(saved);
        c.setItem(0, new ItemStack(Items.CHEST)); c.startWork("place_block", "minecraft:chest", 1, "inside_house");
        h.succeedWhen(() -> { done(h,c); h.assertTrue(c.didLastTaskSucceed(), c.getLastTaskResult());
            int inside=0; for (BlockPos p:BlockPos.betweenClosed(base.offset(1,1,1),base.offset(3,1,3))) if(h.getBlockState(p).is(Blocks.CHEST)) inside++;
            h.assertTrue(inside==1 && blocks(h, Blocks.CHEST)==1, "Chest placed outside"); });
    }
    @GameTest(template = "tall_tree")
    public static void hiddenCreeperIsNotAnObservation(GameTestHelper h) {
        var c = setup(h); c.setNoAi(true);
        var creeper=h.spawn(EntityType.CREEPER,20,1,16); creeper.setNoAi(true);
        for(int y=1;y<=4;y++) for(int z=13;z<=19;z++) h.setBlock(new BlockPos(18,y,z), Blocks.STONE);
        h.assertTrue(!CompanionHostiles.observed(c,creeper,8), "Creeper seen through solid wall");
        for(int y=1;y<=4;y++) for(int z=13;z<=19;z++) h.setBlock(new BlockPos(18,y,z), Blocks.AIR);
        h.assertTrue(CompanionHostiles.observed(c,creeper,8), "Visible creeper not observed"); h.succeed();
    }
    private static CompanionEntity setup(GameTestHelper h) {
        for(int x=0;x<32;x++) for(int z=0;z<32;z++) h.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK);
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16);
        var p=h.makeMockServerPlayerInLevel(); p.teleportTo(c.getX()-4,c.getY(),c.getZ()-4);
        c.setOwnerId(p.getUUID()); p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        c.setSearchRadius(16); return c;
    }
    private static void done(GameTestHelper h, CompanionEntity c) { h.assertTrue(!c.isGatheringTask() && !c.isWorking(), "Still working: "+c.getLastTaskResult()); }
    private static int blocks(GameTestHelper h, net.minecraft.world.level.block.Block b) {
        int n=0; for(BlockPos p:BlockPos.betweenClosed(new BlockPos(0,1,0),new BlockPos(31,5,31))) if(h.getBlockState(p).is(b)) n++; return n;
    }
}
