package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.*;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Game 4f48bbab: three pigs, an empty working bag, blocked wood, and a dark worksite. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionFoodLightTests {
    private static long dayClock, nightClock;
    @BeforeBatch(batch="lighting")
    public static void daylight(ServerLevel level) { dayClock=level.getDayTime(); level.setDayTime(1000); }
    @AfterBatch(batch="lighting")
    public static void endDaylight(ServerLevel level) { level.setDayTime(dayClock); CompanionFixtureCleanup.afterAtomicTests(level); }
    @BeforeBatch(batch="lighting_night")
    public static void nighttime(ServerLevel level) { nightClock=level.getDayTime(); level.setDayTime(18000); }
    @AfterBatch(batch="lighting_night")
    public static void endNighttime(ServerLevel level) { level.setDayTime(nightClock); CompanionFixtureCleanup.afterAtomicTests(level); }

    @GameTest(template="empty")
    public static void foodRequestDoesNotStopAtTheModelsThreeAnimals(GameTestHelper h) {
        var model = new CompanionReply("", List.of(new PlannedAction("hunt_animals",0,3,"any"),new PlannedAction("smelt_items",0,0,"minecraft:meat")));
        var food = CompanionPlanRequirements.apply("пожарь нам еды",model).actions();
        h.assertTrue(food.getFirst().count()==8 && food.getFirst().item().equals("food"),"Still just three: "+food);
        var exact = CompanionPlanRequirements.apply("добудь три животных и пожарь",model).actions();
        h.assertTrue(exact.getFirst().count()==3 && exact.getFirst().item().equals("any"),"Explicit three lost: "+exact);
        h.succeed();
    }

    @GameTest(template="tall_tree",timeoutTicks=6000)
    public static void foodRunUsesMixedGameAndCooksEveryKind(GameTestHelper h) {
        var f=fixture(h,false,8,16);
        f.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        f.oleg.setItem(0,new ItemStack(Items.COAL,8));
        f.oleg.setItem(1,new ItemStack(Items.OAK_LOG,4));
        furnace(h,f,new BlockPos(10,1,20));
        for(int i=0;i<3;i++) stationary(h.spawn(EntityType.PIG,13+i,1,16));
        for(int i=0;i<3;i++) stationary(h.spawn(EntityType.COW,18+i,1,16));
        for(int i=0;i<2;i++) stationary(h.spawn(EntityType.SHEEP,23+i,1,16));
        var plan=CompanionPlanRequirements.apply("пожарь нам еды",new CompanionReply("",List.of(new PlannedAction("hunt_animals",0,3,"any"),new PlannedAction("smelt_items",0,0,"minecraft:meat")))).actions();
        var run=CompanionPlanRunner.run(plan,new CompanionServerPlanPort(h.getLevel().getServer(),f.player.getUUID(),"food-light-mixed",()->false));
        h.succeedWhen(()->{
            h.assertTrue(run.isDone(),"Still cooking mixed meat");
            h.assertTrue(count(f,Items.COOKED_PORKCHOP)>0 && count(f,Items.COOKED_BEEF)>0 && count(f,Items.COOKED_MUTTON)>0,"Some kinds not cooked: "+f.oleg.getStoredItemsSummary());
            h.assertTrue(count(f,Items.PORKCHOP)+count(f,Items.BEEF)+count(f,Items.MUTTON)==0,"Raw meat left");
            h.assertTrue(h.getLevel().getEntitiesOfClass(Animal.class,new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(1,1,1)).getCenter(),h.absolutePos(new BlockPos(31,5,31)).getCenter())).stream().noneMatch(Animal::isAlive),"Stopped with game remaining");
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=3000)
    public static void foodRunWithOnlyOneAnimalStillCooksItsRealCatch(GameTestHelper h) {
        var f=fixture(h,false,8,16);
        f.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        f.oleg.setItem(0,new ItemStack(Items.COAL,2));
        furnace(h,f,new BlockPos(10,1,20));
        stationary(h.spawn(EntityType.PIG,14,1,16));
        var run=CompanionPlanRunner.run(List.of(new PlannedAction("hunt_animals",0,8,"food"),new PlannedAction("smelt_items",0,0,"minecraft:meat")),
                new CompanionServerPlanPort(h.getLevel().getServer(),f.player.getUUID(),"food-light-small-catch",()->false));
        h.succeedWhen(()->{h.assertTrue(run.isDone() && count(f,Items.COOKED_PORKCHOP)>0,"A small catch did not reach the furnace: "+f.oleg.getLastTaskResult());});
    }

    @GameTest(template="tall_tree",batch="lighting",timeoutTicks=450)
    public static void darkCaveCraftsATorchAndDoesNotSpamInPlace(GameTestHelper h) {
        var f=fixture(h,true,15,15); freeze(f);
        f.oleg.setItem(0,new ItemStack(Items.COAL,2)); f.oleg.setItem(1,new ItemStack(Items.STICK,2));
        h.succeedWhen(()->{
            h.assertTrue(h.getTick()>350,"Observe standing in darkness");
            h.assertTrue(torches(h).size()==1,"Standing torch spam or no light: "+torches(h));
            h.assertTrue(count(f,Items.COAL)==1 && count(f,Items.STICK)==1 && count(f,Items.TORCH)==3,"Not a real four-torch recipe: "+f.oleg.getStoredItemsSummary());
        });
    }

    @GameTest(template="tall_tree",batch="lighting",timeoutTicks=450)
    public static void daylightDoesNotConsumeLightingSupplies(GameTestHelper h) {
        var f=fixture(h,false,15,15); freeze(f);
        f.oleg.setItem(0,new ItemStack(Items.COAL,2)); f.oleg.setItem(1,new ItemStack(Items.STICK,2));
        h.succeedWhen(()->{
            h.assertTrue(h.getTick()>300,"Observe daylight");
            h.assertTrue(torches(h).isEmpty() && count(f,Items.COAL)==2 && count(f,Items.STICK)==2,"Used lighting supplies in daylight");
        });
    }

    @GameTest(template="tall_tree",batch="lighting",timeoutTicks=450)
    public static void existingLightPreventsAnotherTorch(GameTestHelper h) {
        var f=fixture(h,true,15,15); freeze(f);
        h.setBlock(new BlockPos(15,0,15),Blocks.GLOWSTONE);
        f.oleg.setItem(0,new ItemStack(Items.TORCH,16));
        h.succeedWhen(()->{h.assertTrue(h.getTick()>300,"Observe existing light"); h.assertTrue(torches(h).isEmpty() && count(f,Items.TORCH)==16,"Added a torch to a lit spot");});
    }

    @GameTest(template="tall_tree",batch="lighting",timeoutTicks=450)
    public static void askedToStayHeDoesNotSpendTorches(GameTestHelper h) {
        var f=fixture(h,true,15,15); f.oleg.setFollowing(false);
        f.oleg.setItem(0,new ItemStack(Items.TORCH,16));
        h.succeedWhen(()->{h.assertTrue(h.getTick()>300,"Observe wait mode"); h.assertTrue(torches(h).isEmpty() && count(f,Items.TORCH)==16,"Wait mode spent torches");});
    }

    @GameTest(template="tall_tree",batch="lighting",timeoutTicks=1800)
    public static void aMonsterBehindASolidWallDoesNotCancelUsefulWork(GameTestHelper h) {
        var f=fixture(h,true,12,16);
        for(int z=1;z<31;z++) for(int y=1;y<6;y++) h.setBlock(new BlockPos(18,y,z),Blocks.BEDROCK);
        var zombie=h.spawn(EntityType.ZOMBIE,21,1,16); zombie.setNoAi(true); zombie.setInvulnerable(true);
        f.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        f.oleg.setItem(0,new ItemStack(Items.OAK_LOG,16)); f.oleg.setItem(1,new ItemStack(Items.COBBLESTONE,32));
        for(int x=7;x<11;x++) for(int y=1;y<=2;y++) h.setBlock(new BlockPos(x,y,16),Blocks.COAL_ORE);
        f.oleg.autonomy().setEnabled(true);
        h.succeedWhen(()->{
            h.assertTrue(count(f,Items.COAL)>0,"Hidden monster prevented real coal mining: "+f.oleg.getLastTaskResult());
            h.assertTrue(f.oleg.autonomy().said().stream().noneMatch(s->s.contains("монстр")),"Warned about a blocked monster");
            f.oleg.autonomy().setEnabled(false); f.oleg.setFollowing(false);
        });
    }

    @GameTest(template="tall_tree",batch="lighting",timeoutTicks=750)
    public static void walkingLightsDarkSectionsWithSpacing(GameTestHelper h) {
        var f=fixture(h,true,5,15);
        f.oleg.setItem(0,new ItemStack(Items.TORCH,16));
        BlockPos start = f.oleg.blockPosition();
        double[] farthest = {0};
        h.onEachTick(()->farthest[0]=Math.max(farthest[0],start.distSqr(f.oleg.blockPosition())));
        h.runAfterDelay(100,()->{var at=h.absolutePos(new BlockPos(26,1,15)); f.player.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);});
        h.succeedWhen(()->{
            h.assertTrue(h.getTick()>500 && farthest[0]>=15*15,"Did not walk the path: "+farthest[0]+" / "+f.oleg.getLastTaskResult());
            var placed=torches(h);
            h.assertTrue(placed.size()>=2 && placed.size()<=4,"Path not lit or spammed: "+placed);
            for(int i=0;i<placed.size();i++) for(int j=i+1;j<placed.size();j++) h.assertTrue(placed.get(i).distSqr(placed.get(j))>=36,"Torches too close: "+placed);
            h.assertTrue(count(f,Items.TORCH)+placed.size()==16,"Torches duplicated or lost");
        });
    }

    @GameTest(template="tall_tree",batch="lighting_night",timeoutTicks=450)
    public static void aDarkOutdoorNightAlsoGetsATorch(GameTestHelper h) {
        var f=fixture(h,false,15,15); freeze(f);
        f.oleg.setItem(0,new ItemStack(Items.COAL,1)); f.oleg.setItem(1,new ItemStack(Items.STICK,1));
        h.succeedWhen(()->{h.assertTrue(h.getTick()>300 && torches(h).size()==1,"No torch outdoors at night"); h.assertTrue(count(f,Items.COAL)==0 && count(f,Items.TORCH)==3,"Night lighting was free");});
    }

    @GameTest(template="tall_tree",timeoutTicks=6000)
    public static void failedWoodIsFollowedByHuntingAndCooking(GameTestHelper h) {
        var f=fixture(h,false,12,16);
        f.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        f.oleg.setItem(0,new ItemStack(Items.STONE_PICKAXE)); f.oleg.setItem(1,new ItemStack(Items.COBBLESTONE,32)); f.oleg.setItem(2,new ItemStack(Items.COAL,8));
        furnace(h,f,new BlockPos(10,1,20));
        for(int i=0;i<6;i++) {
            if(i<3) stationary(h.spawn(EntityType.PIG,17+i,1,16)); else stationary(h.spawn(EntityType.COW,17+i,1,16));
        }
        f.oleg.autonomy().setEnabled(true);
        h.succeedWhen(()->{
            h.assertTrue(count(f,Items.COOKED_PORKCHOP)+count(f,Items.COOKED_BEEF)>=6,"No fallback food: "+f.oleg.getLastTaskResult());
            h.assertTrue(f.oleg.autonomy().said().stream().anyMatch(s->s.contains("Доступного дерева")),"No actual failed wood attempt");
            f.oleg.autonomy().setEnabled(false); f.oleg.setFollowing(false);
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=7000)
    public static void autonomousCoalIronSmeltingAndIronPickAreReal(GameTestHelper h) {
        var f=fixture(h,false,12,16);
        f.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        f.oleg.setItem(0,new ItemStack(Items.OAK_LOG,16)); f.oleg.setItem(1,new ItemStack(Items.COBBLESTONE,32));
        furnace(h,f,new BlockPos(10,1,20));
        h.setBlock(new BlockPos(10,1,19),Blocks.CRAFTING_TABLE); f.oleg.rememberPlaced(h.absolutePos(new BlockPos(10,1,19)));
        for(int x=18;x<22;x++) for(int y=1;y<=2;y++) h.setBlock(new BlockPos(x,y,16),Blocks.COAL_ORE);
        for(int x=18;x<22;x++) h.setBlock(new BlockPos(x,1,21),Blocks.IRON_ORE);
        f.oleg.autonomy().setEnabled(true);
        h.succeedWhen(()->{
            h.assertTrue(count(f,Items.IRON_PICKAXE)==1 && count(f,Items.IRON_INGOT)>=1,"No coal-to-iron chain: "+f.oleg.getLastTaskResult()+" "+f.oleg.getStoredItemsSummary());
            h.assertTrue(count(f,Items.COAL)>0,"No physically mined coal");
            f.oleg.autonomy().setEnabled(false); f.oleg.setFollowing(false);
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=1200)
    public static void workingLogsComeFromHisOwnChest(GameTestHelper h) {
        var f=fixture(h,false,12,16);
        BlockPos own=new BlockPos(14,1,18), players=new BlockPos(14,1,20);
        h.setBlock(own,Blocks.CHEST); h.setBlock(players,Blocks.CHEST);
        var chest=(ChestBlockEntity)h.getBlockEntity(own); chest.setItem(0,new ItemStack(Items.OAK_LOG,32));
        var untouched=(ChestBlockEntity)h.getBlockEntity(players); untouched.setItem(0,new ItemStack(Items.DIAMOND,3));
        f.oleg.rememberPlaced(h.absolutePos(own)); f.oleg.autonomy().setEnabled(true);
        h.succeedWhen(()->{
            h.assertTrue(count(f,Items.OAK_LOG)>=8,"Own stored logs not used: "+f.oleg.getLastTaskResult());
            h.assertTrue(chest.getItem(0).getCount()+count(f,Items.OAK_LOG)==32,"Logs duplicated/lost");
            h.assertTrue(untouched.getItem(0).getCount()==3,"Player chest changed");
            f.oleg.autonomy().setEnabled(false); f.oleg.setFollowing(false);
        });
    }

    private record Fixture(CompanionEntity oleg, ServerPlayer player) {}
    private static Fixture fixture(GameTestHelper h,boolean roof,int x,int z) {
        for(int xx=0;xx<32;xx++) for(int zz=0;zz<32;zz++) {
            h.setBlock(new BlockPos(xx,0,zz),Blocks.STONE);
            for(int y=1;y<=7;y++) h.setBlock(new BlockPos(xx,y,zz), y==6&&roof || (xx==0||xx==31||zz==0||zz==31)&&y<5 ? Blocks.BEDROCK:Blocks.AIR);
        }
        var oleg=h.spawn(AICompanionMod.COMPANION.get(),x,1,z);
        var player=h.makeMockServerPlayerInLevel(); player.teleportTo(oleg.getX()+2,oleg.getY(),oleg.getZ()+2);
        oleg.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion",oleg.getUUID());
        CompanionEvents.getOwnedCompanion(player); oleg.setSearchRadius(24);
        return new Fixture(oleg,player);
    }
    private static void freeze(Fixture f) { f.oleg.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); }
    private static void stationary(Animal animal) { animal.setNoAi(true); }
    private static int count(Fixture f,net.minecraft.world.item.Item item) { return MarathonArena.count(f.oleg,item); }
    private static void furnace(GameTestHelper h,Fixture f,BlockPos pos) { h.setBlock(pos,Blocks.FURNACE); f.oleg.rememberPlaced(h.absolutePos(pos)); }
    private static List<BlockPos> torches(GameTestHelper h) {
        var found=new java.util.ArrayList<BlockPos>();
        for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(1,1,1),new BlockPos(30,3,30))) if(h.getBlockState(pos).is(Blocks.TORCH)) found.add(pos.immutable());
        return found;
    }
}
