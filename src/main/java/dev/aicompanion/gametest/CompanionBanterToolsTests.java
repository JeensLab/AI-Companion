package dev.aicompanion.gametest;

import dev.aicompanion.*;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Regression of session a6f67a6d: silent banter, a wooden pickaxe in dirt, twelve swings at one rejected cell. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBanterToolsTests {
    @GameTest(template="empty")
    public static void ordinaryAnswersDoNotPostponeInitiativeForAnotherFiveMinutes(GameTestHelper h) {
        var c=new CompanionConversation();c.reset(0,0);
        c.playerMessage(3400,0);c.delivered("Ага, сейчас разберёмся.",4000,0);
        h.assertTrue(!c.ready(4899)&&c.ready(4900)&&c.nextOpportunity()==3600,"Ordinary answer reset the full interval");
        h.assertTrue(c.takeObservation(1,0,4900,true,false,2400),"Useful event after a conversation lost");
        c.initiativeDelivered("Дождь вовремя, бля.",5000,2400);
        c.playerMessage(5100,0);c.delivered("Зато пыль прибило.",5200,0);
        h.assertTrue(!c.ready(10999)&&c.ready(11000),"Normal conversation bypassed initiative cap");h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=30)
    public static void unavailableConversationDoesNotConsumeTheNextUsefulTopic(GameTestHelper h) {
        var c=flat(h,1);c.setNoAi(true);c.tickCount=300;mature(h,c);
        c.setConversationAvailable(false);seed(c,false);
        c.observeEvent("stone","Реально сломал камень.");
        h.assertTrue(c.socialSequence()==0,"Busy conversation received event");
        c.setConversationAvailable(true);seed(c,false);c.observeEvent("stone","Реально сломал камень.");
        h.assertTrue(c.socialSequence()==1,"Busy client consumed topic or cooldown");h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=30)
    public static void missedChanceDoesNotSilenceTheTopicForTwentyMinutes(GameTestHelper h) {
        var c=flat(h,1);c.setNoAi(true);c.tickCount=300;mature(h,c);
        seed(c,true);c.observeEvent("ore","Видна настоящая руда.");
        h.assertTrue(c.socialSequence()==0,"Missed chance emitted");
        seed(c,false);c.observeEvent("ore","Видна настоящая руда.");
        h.assertTrue(c.socialSequence()==1,"Missed chance permanently ate topic");h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=60)
    public static void workingNearThePlayerCanOfferAConversationWithoutStoppingWork(GameTestHelper h) throws Exception {
        var c=flat(h,1); c.setNoAi(true); c.tickCount=4000;mature(h,c);
        c.startWork("gather_stone","",2);
        Object social=field(c,"social");
        var tick=social.getClass().getDeclaredMethod("tick");tick.setAccessible(true);
        for(long seed=0;seed<10000&&c.socialSequence()==0;seed++){c.getRandom().setSeed(seed);tick.invoke(social);}
        h.assertTrue(c.socialSequence()==1&&c.socialObservation().contains("продолжаю работу")&&c.isWorking(),"No working conversation opportunity");h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=900)
    public static void coalMiningUpgradesWoodToStoneWithExistingSticks(GameTestHelper h) {
        var c=coalWall(h,Items.WOODEN_PICKAXE);
        c.setItem(0,new ItemStack(Items.COBBLESTONE,3));c.setItem(1,new ItemStack(Items.STICK,2));
        c.startWork("mine_ore","coal",1);
        h.succeedWhen(()->{
            done(h,c);h.assertTrue(c.didLastTaskSucceed()&&count(c,Items.COAL)>=1,"No coal: "+c.getLastTaskResult());
            h.assertTrue(c.getMainHandItem().is(Items.STONE_PICKAXE)&&c.getMainHandItem().getDamageValue()>0,"Stone upgrade not used");
            h.assertTrue(count(c,Items.WOODEN_PICKAXE)==1&&count(c,Items.STICK)==0,"Old tool lost or sticks free");
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=900)
    public static void availableIronIsUsedForAnOptionalPickaxeUpgrade(GameTestHelper h) {
        var c=coalWall(h,Items.STONE_PICKAXE);
        c.setItem(0,new ItemStack(Items.IRON_INGOT,3));c.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.STICK,2));
        c.startWork("mine_ore","coal",1);
        h.succeedWhen(()->{
            done(h,c);h.assertTrue(c.didLastTaskSucceed()&&c.getMainHandItem().is(Items.IRON_PICKAXE)&&c.getMainHandItem().getDamageValue()>0,"No iron upgrade");
            h.assertTrue(count(c,Items.STONE_PICKAXE)==1&&count(c,Items.IRON_INGOT)==0&&count(c,Items.STICK)==0,"Upgrade duplicated/lost resources");
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=900)
    public static void availableDiamondsUpgradeIronWithoutDiscardingIt(GameTestHelper h) {
        var c=coalWall(h,Items.IRON_PICKAXE);
        c.setItem(0,new ItemStack(Items.DIAMOND,3));c.setItem(1,new ItemStack(Items.STICK,2));
        c.startWork("mine_ore","coal",1);
        h.succeedWhen(()->{
            done(h,c);h.assertTrue(c.didLastTaskSucceed()&&c.getMainHandItem().is(Items.DIAMOND_PICKAXE)&&c.getMainHandItem().getDamageValue()>0,"No diamond upgrade");
            h.assertTrue(count(c,Items.IRON_PICKAXE)==1&&count(c,Items.DIAMOND)==0,"Old iron lost or diamonds free");
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=900)
    public static void insufficientIngredientsLeaveToolAndMaterialsIntact(GameTestHelper h) {
        var c=coalWall(h,Items.IRON_PICKAXE);
        c.setItem(0,new ItemStack(Items.DIAMOND,2));c.setItem(1,new ItemStack(Items.STICK,2));c.setItem(2,new ItemStack(Items.COBBLESTONE,8));
        c.startWork("mine_ore","coal",1);
        h.succeedWhen(()->{
            done(h,c);h.assertTrue(c.didLastTaskSucceed()&&c.getMainHandItem().is(Items.IRON_PICKAXE),"Downgraded or stopped");
            h.assertTrue(count(c,Items.DIAMOND)==2&&count(c,Items.STICK)==2,"Failed upgrade consumed ingredients");
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=2600)
    public static void oreTunnelMakesAndUsesAShovelForDirt(GameTestHelper h) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)for(int y=0;y<6;y++)
            h.setBlock(new BlockPos(x,y,z),y==0?Blocks.BEDROCK:y<4?Blocks.STONE:y==5?Blocks.GRASS_BLOCK:Blocks.DIRT);
        var c=spawn(h,6);bench(h,c,new BlockPos(14,6,16));
        h.setBlock(new BlockPos(16,3,23),Blocks.COAL_ORE);
        c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        c.setItem(0,new ItemStack(Items.COBBLESTONE,4));c.setItem(1,new ItemStack(Items.STICK,4));
        boolean[] used={false};h.onEachTick(()->{var hand=c.getMainHandItem();used[0]|=hand.is(Items.STONE_SHOVEL)&&hand.getDamageValue()>0;});
        c.startWork("mine_ore","coal",1);
        h.succeedWhen(()->{done(h,c);h.assertTrue(c.didLastTaskSucceed()&&count(c,Items.COAL)>0&&used[0],"No shovel in real dirt tunnel: "+c.getLastTaskResult());h.assertTrue(count(c,Items.WOODEN_PICKAXE)==1,"Original pickaxe lost");});
    }
    @GameTest(template="tall_tree",timeoutTicks=4000)
    public static void autonomousBuriedVeinUsesShovelAndClimbsBackWithoutRepeatedSwings(GameTestHelper h) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)for(int y=0;y<6;y++)
            h.setBlock(new BlockPos(x,y,z),y==0?Blocks.BEDROCK:y<4?Blocks.STONE:y==5?Blocks.GRASS_BLOCK:Blocks.DIRT);
        var c=spawn(h,6);bench(h,c,new BlockPos(14,6,16));
        for(int x=15;x<=17;x++)for(int z=22;z<=24;z++)h.setBlock(new BlockPos(x,3,z),Blocks.COAL_ORE);
        c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));
        c.setItem(0,new ItemStack(Items.OAK_LOG,16));c.setItem(1,new ItemStack(Items.COBBLESTONE,32));
        c.setItem(2,new ItemStack(Items.COOKED_BEEF,32));
        boolean[] used={false}, descended={false};
        h.onEachTick(()->{
            var hand=c.getMainHandItem();used[0]|=hand.is(Items.STONE_SHOVEL)&&hand.getDamageValue()>0;
            descended[0]|=c.getY()<h.absolutePos(new BlockPos(0,5,0)).getY();
        });
        c.autonomy().setEnabled(true);
        h.succeedWhen(()->{
            h.assertTrue(count(c,Items.COAL)>=8&&!c.isWorking()&&c.getY()>=h.absolutePos(new BlockPos(0,6,0)).getY()-.1,"Autonomous tunnel unfinished: "+c.getActivityForAi()+" "+c.getLastTaskResult());
            h.assertTrue(used[0]&&descended[0],"No actual descent or shovel use");
            c.autonomy().setEnabled(false);c.setFollowing(false);
        });
    }
    @GameTest(template="tall_tree",timeoutTicks=30)
    public static void dirtUsesTheOffhandShovelAndNeverTheWrongPickaxe(GameTestHelper h) {
        var c=flat(h,1);c.setNoAi(true);
        c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));c.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.IRON_SHOVEL));
        c.selectToolForBlock(Blocks.DIRT.defaultBlockState());
        h.assertTrue(c.getMainHandItem().is(Items.IRON_SHOVEL)&&count(c,Items.WOODEN_PICKAXE)==1,"Offhand shovel ignored");
        c.unequipItem("mainhand");
        // Remove the shovel as a player would, then a wrong tool must be put away for hand digging.
        for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(Items.IRON_SHOVEL))c.setItem(i,ItemStack.EMPTY);
        c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.WOODEN_PICKAXE));c.selectToolForBlock(Blocks.DIRT.defaultBlockState());
        h.assertTrue(c.getMainHandItem().isEmpty(),"Dirt still dug with pickaxe");h.succeed();
    }
    @GameTest(template="tall_tree",timeoutTicks=60)
    public static void sharedReturnStairIsRejectedBeforeAnySwingAcrossSeveralOreTargets(GameTestHelper h) throws Exception {
        for(int x=8;x<=24;x++)for(int z=8;z<=24;z++)for(int y=0;y<6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.BEDROCK);
        for(int x=16;x<=21;x++) {
            int feet=x-15;
            for(int y=feet;y<=Math.min(7,feet+2);y++)h.setBlock(new BlockPos(x,y,16),Blocks.AIR);
            if(x>16)h.setBlock(new BlockPos(x,feet-1,16),Blocks.STONE);
        }
        var c=spawn(h,1); c.setNoAi(true);c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        c.startWork("mine_ore","coal",4);set(c.autonomy(),"starting",true);
        Object work=field(c,"work");set(work,"oreExit",h.absolutePos(new BlockPos(21,6,16)));
        BlockPos stair=h.absolutePos(new BlockPos(17,1,16));
        Object oreMining=field(work,"oreMining");
        var safe=oreMining.getClass().getDeclaredMethod("safeOreSwing",net.minecraft.server.level.ServerLevel.class,BlockPos.class);safe.setAccessible(true);
        var blocked=oreMining.getClass().getDeclaredMethod("blockedOreCell",net.minecraft.server.level.ServerLevel.class,BlockPos.class);blocked.setAccessible(true);
        for(int ore=0;ore<12;ore++) {
            set(work,"target",h.absolutePos(new BlockPos(22,1,12+ore%8)));
            h.assertTrue(!(boolean)safe.invoke(oreMining,h.getLevel(),stair),"Essential return stair allowed");
        }
        Object breaking=field(work,"breaking");h.assertTrue((int)field(breaking,"ticks")==0&&field(breaking,"target")==null,"Unsafe swings began");
        h.assertTrue((boolean)blocked.invoke(oreMining,h.getLevel(),stair)&&h.getLevel().getBlockState(stair).is(Blocks.STONE),"Shared obstruction not remembered");
        h.getLevel().setBlockAndUpdate(stair,Blocks.AIR.defaultBlockState());
        h.assertTrue(!(boolean)blocked.invoke(oreMining,h.getLevel(),stair),"Changed world stayed blacklisted");h.succeed();
    }
    private static CompanionEntity coalWall(GameTestHelper h,Item tool) {
        var c=flat(h,1);bench(h,c,new BlockPos(14,1,16));
        for(int x=20;x<=22;x++)for(int z=14;z<=18;z++)for(int y=1;y<=3;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        h.setBlock(new BlockPos(20,1,16),Blocks.COAL_ORE);c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool));return c;
    }
    private static CompanionEntity flat(GameTestHelper h,int y) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)h.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK);return spawn(h,y);
    }
    private static CompanionEntity spawn(GameTestHelper h,int y) {
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,y,16);var p=h.makeMockServerPlayerInLevel();p.teleportTo(c.getX()-3,c.getY(),c.getZ()-3);
        c.setOwnerId(p.getUUID());p.getPersistentData().putUUID("aicompanion:canonical_companion",c.getUUID());CompanionEvents.getOwnedCompanion(p);
        c.setFollowing(false);c.setLifestyle(false,true);c.setSearchRadius(16);return c;
    }
    private static void bench(GameTestHelper h,CompanionEntity c,BlockPos pos){h.setBlock(pos,Blocks.CRAFTING_TABLE);c.rememberPlaced(h.absolutePos(pos));}
    private static void done(GameTestHelper h,CompanionEntity c){h.assertTrue(!c.isWorking()&&!c.isGatheringTask(),"Still working: "+c.getLastTaskResult());}
    private static int count(CompanionEntity c,Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();for(var s:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND})if(c.getItemBySlot(s).is(item))n+=c.getItemBySlot(s).getCount();return n;}
    private static void mature(GameTestHelper h,CompanionEntity c){var tag=new CompoundTag();c.addAdditionalSaveData(tag);var cadence=new CompoundTag();cadence.putLong("Next",h.getLevel().getGameTime());tag.put("SocialCadence",cadence);c.readAdditionalSaveData(tag);}
    private static void seed(CompanionEntity c,boolean miss){for(long s=0;s<100000;s++){c.getRandom().setSeed(s);if((c.getRandom().nextFloat()>.65F)==miss){c.getRandom().setSeed(s);return;}}throw new AssertionError("No seed");}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
}
