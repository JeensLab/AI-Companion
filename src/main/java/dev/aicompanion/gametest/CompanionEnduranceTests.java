package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

/** Long server-tick sessions, using the existing player/chat/plan simulator. No cloud calls or player saves. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionEnduranceTests {
    private static final int MINUTE = 20 * 60;

    @BeforeBatch(batch="endurance")
    public static void sprint(net.minecraft.server.level.ServerLevel level) {
        level.getServer().tickRateManager().requestGameToSprint(130*MINUTE);
    }

    // The sprint ends here, between batches: left running, it ended inside a later batch and the parked-smelt furnace test there failed.
    @AfterBatch(batch="endurance")
    public static void cleanup(net.minecraft.server.level.ServerLevel level) {
        level.getServer().tickRateManager().stopSprinting();
        CompanionFixtureCleanup.afterAtomicTests(level);
    }

    /** Reproduces e003a157: a new batch must not turn the previous descent into its new surface. */
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void successiveBatchesKeepTheOriginalSurface(GameTestHelper h) throws Exception {
        var a=yard(h,false); a.oleg.setNoAi(true); a.oleg.autonomy().setEnabled(true);
        BlockPos surface=a.oleg.autonomy().workArea();
        a.oleg.setPos(a.oleg.getX(),a.oleg.getY()-6,a.oleg.getZ());
        set(a.oleg.autonomy(),"starting",true);
        a.oleg.startWork("mine_ore","coal",8);
        Object work=field(a.oleg,"work");
        h.assertTrue(surface.equals(field(work,"oreExit")),"Next vein forgot original exit: "+field(work,"oreExit")+" vs "+surface);
        a.oleg.startWork("gather_stone","",32);
        h.assertTrue(surface.equals(field(work,"workOrigin")),"Next stone batch adopted the pit floor as its surface");
        h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void quarryCannotDeepenBelowTheWorkplaceLimit(GameTestHelper h) throws Exception {
        var a=yard(h,false); a.oleg.setNoAi(true); a.oleg.autonomy().setEnabled(true);
        BlockPos surface=a.oleg.autonomy().workArea();
        set(a.oleg.autonomy(),"starting",true); a.oleg.startWork("gather_stone","",32);
        Object work=field(a.oleg,"work");
        // A quarry planned at the lower end of a previous batch, not an arbitrary unsafe swing.
        BlockPos tooDeep=surface.below(16).east();
        h.getLevel().setBlock(tooDeep,Blocks.STONE.defaultBlockState(),2);
        h.getLevel().setBlock(tooDeep.north(),Blocks.AIR.defaultBlockState(),2);
        var method=field(work,"stoneMining").getClass().getDeclaredMethod("safeToMine",net.minecraft.server.level.ServerLevel.class,BlockPos.class,boolean.class);
        method.setAccessible(true);
        h.assertTrue(!(boolean)method.invoke(field(work,"stoneMining"),h.getLevel(),tooDeep,true),"Autonomous quarry permits another descent below the fixed depth limit");
        h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=1200)
    public static void reloadKeepsFailureBackoff(GameTestHelper h) {
        var a=yard(h,false); a.oleg.autonomy().setEnabled(true);
        boolean[] loaded={false}; int[] before={0};
        a.script.waitUntil("wood actually fails in an empty yard",()->a.oleg.autonomy().said().stream().anyMatch(s->s.contains("Доступного дерева")),600)
                .then("reload a failed deed",()->{var tag=new CompoundTag();a.oleg.addAdditionalSaveData(tag);before[0]=tag.getCompound("AutonomyBackoff").getInt("WOOD");a.reload(()->loaded[0]=true);},()->loaded[0],20)
                .pause(400)
                .check("failure is not retried immediately after loading",()->{
                    a.expect(a.oleg.autonomy().said().stream().noneMatch(s->s.contains("Доступного дерева")),"Reload discarded five-minute backoff");
                    var tag=new CompoundTag();a.oleg.addAdditionalSaveData(tag);int remaining=tag.getCompound("AutonomyBackoff").getInt("WOOD");
                    a.expect(remaining>0&&remaining<=before[0]-380,"Wood backoff did not count down across reload: "+before[0]+" -> "+remaining);
                }).order("Стой");
        finish(a,"endurance-backoff",()->"");
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=123*MINUTE)
    public static void twoHoursForestToolsStorageAndReturningPlayer(GameTestHelper h) {
        var a=yard(h,true); seed(a);
        ItemStack worn=new ItemStack(Items.WOODEN_PICKAXE); worn.setDamageValue(worn.getMaxDamage()-2);
        a.oleg.setItem(2,ItemStack.EMPTY);
        a.oleg.setItem(1,ItemStack.EMPTY);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND,worn);
        var w=new EnduranceWatch(a,"forest-120");
        int[] rescuesBeforeReturn={0};
        boolean[] wasFar={false};
        a.script.order("Будь самостоятельным")
                .check("owner leaves",()->leave(a))
                .pause(40*MINUTE)
                .check("real stock exists before the player uses it",()->{
                    a.expect(w.total(Items.COBBLESTONE)>=128,"No stone stock by minute 40");
                    w.takeStored(Items.COBBLESTONE,256); w.takeStored(Items.OAK_LOG,64);
                })
                .pause(40*MINUTE)
                .check("use more stored resources",()->w.takeStored(Items.COBBLESTONE,256))
                .pause(40*MINUTE)
                .order("Стой")
                .pause(100)
                .check("owner returns and calls from the original surface",()->{wasFar[0]=a.toPlayer()>150;rescuesBeforeReturn[0]=a.watch.everyRescue().size();callAtSurface(a);w.returnSnapshot();})
                .ask("Иди ко мне",null,2*MINUTE)
                .check("long work has real results",()->{
                    a.expect(w.successes>=12,"Too few successful deeds: "+w.successes);
                    a.expect(a.oleg.planProgress("gather_stone")>=256,"No sustained physical stone harvest");
                    a.expect(a.oleg.planProgress("gather_wood")>=48,"No sustained physical wood harvest");
                    a.expect(worn.isEmpty(),"Original worn pick never broke");
                    a.expect(w.toolsSeen.contains("minecraft:stone_pickaxe"),"Broken pick not replaced/upgraded during the session");
                    a.expect(w.total(Items.COBBLESTONE)>=dev.aicompanion.entity.CompanionAutonomy.STONE_RESERVE,"Stone reserve was not replenished after the player's withdrawals");
                    a.expect(wasFar[0],"Owner never left for the long session");
                    a.expect(a.toPlayer()<5&&a.oleg.getY()>=a.oleg.autonomy().workArea().getY()-.5,"Did not physically return to the surface");
                    a.expect(a.watch.everyRescue().size()==rescuesBeforeReturn[0],"Had to dig a new escape instead of using the return path");
                    w.verify();
                });
        finish(a,"endurance-forest-120",w::summary);
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=93*MINUTE)
    public static void ninetyMinutesCaveFoodOreLightAndChangingPassage(GameTestHelper h) {
        var a=yard(h,true); seed(a);
        // Low roof and a two-block-wide passage, a shallow vein and a visible slope.
        for(int x=33;x<=65;x++)for(int z=52;z<=63;z++)h.setBlock(a.cell(x,15,z),Blocks.STONE);
        for(int x=34;x<=43;x++)for(int z=56;z<=58;z++)for(int y=10;y<=12;y++)h.setBlock(a.cell(x,y,z),Blocks.AIR);
        for(int x=34;x<=41;x++)h.setBlock(a.cell(x,10,59),Blocks.COAL_ORE);
        for(int x=42;x<=45;x++)for(int y=12;y<=13;y++)h.setBlock(a.cell(x,y,54),Blocks.IRON_ORE);
        for(int x=43;x<=46;x++)for(int z=56;z<=58;z++)for(int y=10+(x-43);y<=14;y++)h.setBlock(a.cell(x,y,z),Blocks.AIR);
        h.setBlock(a.cell(47,12,50),Blocks.CRAFTING_TABLE);a.oleg.rememberPlaced(h.absolutePos(a.cell(47,12,50)));
        h.setBlock(a.cell(49,12,50),Blocks.FURNACE);a.oleg.rememberPlaced(h.absolutePos(a.cell(49,12,50)));
        a.oleg.setItem(3,new ItemStack(Items.IRON_SWORD));a.oleg.setItem(4,new ItemStack(Items.COAL,2));
        for(int i=0;i<6;i++){var pig=h.spawn(EntityType.PIG,a.spot(52+i*.5,12,46));pig.setNoAi(true);}
        var w=new EnduranceWatch(a,"cave-90");
        int[] rescuesBeforeReturn={0};
        a.script.order("Будь самостоятельным").check("owner leaves",()->leave(a))
                .pause(20*MINUTE)
                .check("world changes: block one approach, keep the other open",()->{
                    for(int y=12;y<=13;y++)h.setBlock(a.cell(46,y,57),Blocks.BEDROCK);
                    a.expect(w.total(Items.COOKED_PORKCHOP)>0,"Hunted food never cooked");
                    a.expect(w.toolsSeen.contains("minecraft:iron_pickaxe"),"Shallow iron did not become a pickaxe");
                })
                .pause(25*MINUTE)
                .check("open changed route and use stock",()->{
                    for(int y=12;y<=13;y++)h.setBlock(a.cell(46,y,57),Blocks.AIR);
                    w.takeStored(Items.COBBLESTONE,128);
                })
                .pause(45*MINUTE).order("Стой").pause(100)
                .check("owner calls from the surface",()->{rescuesBeforeReturn[0]=a.watch.everyRescue().size();callAtSurface(a);})
                .ask("Иди ко мне",null,2*MINUTE)
                .check("cave stayed safe and productive",()->{
                    a.expect(a.toPlayer()<5&&a.oleg.getY()>=a.oleg.autonomy().workArea().getY()-.5,"Did not physically return from cave");
                    a.expect(a.watch.everyRescue().size()==rescuesBeforeReturn[0],"Return required a new escape instead of the existing path");
                    a.expect(a.oleg.planProgress("gather_stone")>=128,"No useful stone in cave session");
                    a.expect(w.peakCoal>2,"No real coal gain before fuel and lighting consumed it");
                    a.expect(w.torches()>0,"Dark cave never lit");
                    a.expect(w.minimumY>=a.oleg.autonomy().workArea().getY()-14,"Successive batches exceeded the stone depth limit: "+w.minimumY);
                    w.verify();
                });
        finish(a,"endurance-cave-90",w::summary);
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=103*MINUTE)
    public static void hundredMinutesFullBagOrdersCancellationAndReloads(GameTestHelper h) {
        var a=yard(h,true); seed(a);
        // A nearly full bag with unique valuables: storage must make room without deleting anything.
        for(int slot=3;slot<24;slot++){
            var valuable=new ItemStack(Items.EMERALD);valuable.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("keepsake-"+slot));
            a.oleg.setItem(slot,valuable);
        }
        var w=new EnduranceWatch(a,"reload-100");
        int[] rescuesBeforeReturn={0};
        a.script.order("Будь самостоятельным").check("owner leaves",()->leave(a)).pause(20*MINUTE);
        for(int i=0;i<4;i++){
            int cycle=i;
            boolean[] loaded={false};
            a.script.then("save/load entity during session "+i,()->w.reload(()->loaded[0]=true),()->loaded[0],40)
                    .pause(10*MINUTE)
                    .check("returning owner gives an order "+i,()->a.putPlayer(48,48))
                    .ask("Добудь 24 земли","[{\"action\":\"gather_dirt\",\"count\":24}]",3*MINUTE)
                    .order("Стой").pause(200)
                    .check("cancellation holds, valuables and unique companion survive "+i,()->{
                        a.expect(!a.oleg.hasActiveWorkOrder()&&a.oleg.autonomy().held(),"Stop ignored in cycle "+cycle);
                        a.expect(w.total(Items.EMERALD)==21,"Valuables lost or duplicated in cycle "+cycle);
                        w.unique();
                    }).order("Будь самостоятельным").check("owner leaves again",()->leave(a))
                    .then("session reaches minute "+(40+20*cycle),()->{},()->h.getTick()>=(40+20*cycle)*MINUTE,10*MINUTE);
        }
        a.script.order("Стой").pause(100)
                .check("owner calls home after final reload",()->{rescuesBeforeReturn[0]=a.watch.everyRescue().size();callAtSurface(a);})
                .ask("Иди ко мне",null,2*MINUTE).check("session persisted physical items and memory",()->{
            a.expect(a.toPlayer()<5&&a.oleg.getY()>=a.oleg.autonomy().workArea().getY()-.5,"Did not physically return after reloads");
            a.expect(a.watch.everyRescue().size()==rescuesBeforeReturn[0],"Return needed new escape blocks");
            a.expect(w.reloads==4,"Reloads missing");
            a.expect(w.total(Items.EMERALD)==21,"Valuables not conserved");
            a.expect(w.harvestedSoil>=96,"Player dirt orders made no real progress");
            a.expect(w.successes>=10,"Little real work across reloads");w.verify();
        });
        finish(a,"endurance-reload-100",w::summary);
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=2400)
    public static void diskReloadDuringWorkReclaimsDropsAndExplainsInterruption(GameTestHelper h) {
        var a=yard(h,true);seed(a);var w=new EnduranceWatch(a,"interrupted-disk-reload");boolean[] loaded={false};
        a.script.ask("Добудь 64 камня",null,2*MINUTE)
                .check("start another unfinished job and an owned physical drop",()->{
                    CompanionEvents.executeAction(a.player,"gather_stone","",64);
                    var drop=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),a.oleg.getX()+1,a.oleg.getY()+.2,a.oleg.getZ(),new ItemStack(Items.EMERALD,3));
                    drop.getPersistentData().putUUID("aicompanion:harvester",a.oleg.getUUID());drop.setPickUpDelay(20);h.getLevel().addFreshEntity(drop);
                })
                .then("disk reload of active work",()->{a.expect(a.oleg.hasActiveWorkOrder(),"No interrupted work");w.reload(()->loaded[0]=true);},()->loaded[0],40)
                .waitUntil("saved harvest physically reclaimed",()->a.count(Items.EMERALD)==3,400)
                .check("no old work or duplicate entities",()->{
                    a.expect(!a.oleg.hasActiveWorkOrder(),"Old unsaved plan resumed as if successful");
                    a.expect(a.oleg.getDeedsForAi().contains("прервалась"),"Pickup erased interruption from factual memory");
                    a.expect(w.total(Items.EMERALD)==3,"Drop lost or duplicated");w.unique();
                });
        finish(a,"endurance-interrupted-reload",w::summary);
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void aQueuedStoneStairUnderfootNeverStartsASwing(GameTestHelper h) throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        a.oleg.startWork("gather_stone","",32);Object work=field(a.oleg,"work");
        BlockPos under=a.oleg.blockPosition().below();h.getLevel().setBlock(under,Blocks.STONE.defaultBlockState(),2);
        set(work,"target",under);set(work,"stoneCandidates",new ArrayList<>(List.of(under)));
        ((java.util.Deque<BlockPos>)field(work,"quarrySoil")).add(under);
        var mine=field(work,"stoneMining").getClass().getDeclaredMethod("mine",net.minecraft.server.level.ServerLevel.class);mine.setAccessible(true);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue((int)field(field(work,"breaking"),"ticks")==0,"Queued stair under own feet was swung at");
        h.assertTrue(h.getLevel().getBlockState(under).is(Blocks.STONE),"Own support destroyed");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void dirtApproachNeverEndsAboveAnInvisibleBuriedBlock(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);BlockPos buried=h.absolutePos(a.cell(52,11,48));
        h.getLevel().setBlock(buried.above(),Blocks.BEDROCK.defaultBlockState(),2);
        var selector=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");selector.setAccessible(true);
        var goals=(net.minecraft.world.entity.ai.goal.GoalSelector)selector.get(a.oleg);
        Object dirt=goals.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal).filter(g->g.getClass().getSimpleName().equals("CompanionDigGoal")).findFirst().orElseThrow();
        var approach=dirt.getClass().getDeclaredMethod("findDirtApproachPath",BlockPos.class);approach.setAccessible(true);
        h.assertTrue(approach.invoke(dirt,buried)==null,"Dirt route leads above an enclosed block without a visible/collectable face");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=100)
    public static void aBenchBelowALedgeIsNotBrokenFromAnUnsafePickupSpot(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        for(int x=47;x<=50;x++)for(int z=47;z<=49;z++)for(int y=10;y<=14;y++)h.setBlock(a.cell(x,y,z),Blocks.AIR);
        BlockPos bench=h.absolutePos(a.cell(48,10,48));h.getLevel().setBlock(bench,Blocks.CRAFTING_TABLE.defaultBlockState(),2);
        a.oleg.startWork("gather_stone","",32);Object work=field(a.oleg,"work");
        set(work,"packTarget",bench);set(work,"packResult",CompanionActionResult.ok("Основное дело закончено."));
        var phase=work.getClass().getDeclaredField("phase");phase.setAccessible(true);
        for(Object value:phase.getType().getEnumConstants())if(value.toString().equals("PACK"))phase.set(work,value);
        Object handOver=field(work,"handOver");var pack=handOver.getClass().getDeclaredMethod("packBench",net.minecraft.server.level.ServerLevel.class);pack.setAccessible(true);
        h.startSequence().thenExecuteFor(40,()->{try{pack.invoke(handOver,h.getLevel());}catch(Exception e){throw new IllegalStateException(e);}})
                .thenExecute(()->{
                    h.assertTrue(h.getLevel().getBlockState(bench).is(Blocks.CRAFTING_TABLE),"Bench was broken two levels below before a safe pickup approach");
                    try{set(work,"packTarget",null);pack.invoke(handOver,h.getLevel());}catch(Exception e){throw new IllegalStateException(e);}
                    h.assertTrue(!a.oleg.getLastTaskResult().contains("верстак забрал"),"Claims a bench that was never picked up");
                }).thenSucceed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void aNewQuarryKeepsThePreviousBatchesReturnStair(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);
        for(int x=43;x<=55;x++)for(int z=45;z<=51;z++)for(int y=9;y<=20;y++)h.setBlock(a.cell(x,y,z),Blocks.BEDROCK);
        for(int x=46;x<=51;x++){
            int feet=12+x-46;
            for(int y=feet;y<=feet+2;y++)h.setBlock(a.cell(x,y,48),Blocks.AIR);
            h.setBlock(a.cell(x,feet-1,48),Blocks.STONE);
        }
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        a.oleg.autonomy().setEnabled(true);set(a.oleg.autonomy(),"workArea",h.absolutePos(a.cell(51,17,48)));set(a.oleg.autonomy(),"starting",true);
        a.oleg.startWork("gather_stone","",32);Object work=field(a.oleg,"work");BlockPos stair=h.absolutePos(a.cell(47,12,48));
        set(work,"target",stair);set(work,"stoneCandidates",new ArrayList<>(List.of(stair)));((java.util.Deque<BlockPos>)field(work,"quarrySoil")).add(stair);
        var mine=field(work,"stoneMining").getClass().getDeclaredMethod("mine",net.minecraft.server.level.ServerLevel.class);mine.setAccessible(true);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue((int)field(field(work,"breaking"),"ticks")==0,"New quarry started destroying the existing return stair");
        h.assertTrue(h.getLevel().getBlockState(stair).is(Blocks.STONE),"Return stair gone");
        // The final step needs the same protection even when he is only one block below the surface.
        var upper=h.absolutePos(a.cell(50,16,48));a.oleg.setPos(upper.getX()+.5,upper.getY(),upper.getZ()+.5);a.oleg.setOnGround(true);
        a.oleg.startWork("gather_stone","",32);stair=h.absolutePos(a.cell(51,16,48));
        set(work,"target",stair);set(work,"stoneCandidates",new ArrayList<>(List.of(stair)));((java.util.Deque<BlockPos>)field(work,"quarrySoil")).add(stair);
        mine.invoke(field(work,"stoneMining"),h.getLevel());h.assertTrue((int)field(field(work,"breaking"),"ticks")==0,"Final return step one below the surface was swung at");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void miningKeepsFloorAndWallTorchSupports(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.startWork("gather_stone","",32);Object work=field(a.oleg,"work");
        BlockPos support=h.absolutePos(a.cell(50,12,50));h.getLevel().setBlock(support,Blocks.STONE.defaultBlockState(),2);
        var safe=field(work,"stoneMining").getClass().getDeclaredMethod("safeToMine",net.minecraft.server.level.ServerLevel.class,BlockPos.class,boolean.class);safe.setAccessible(true);
        h.getLevel().setBlock(support.above(),Blocks.TORCH.defaultBlockState(),2);
        h.assertTrue(!(boolean)safe.invoke(field(work,"stoneMining"),h.getLevel(),support,true),"Floor torch support can be mined");
        h.getLevel().setBlock(support.above(),Blocks.AIR.defaultBlockState(),2);
        h.getLevel().setBlock(support.east(),Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING,net.minecraft.core.Direction.EAST),2);
        h.assertTrue(!(boolean)safe.invoke(field(work,"stoneMining"),h.getLevel(),support,true),"Wall torch support can be mined");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void returnSearchTriesAnotherSideAndReachesTheActualExitHeight(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);
        for(int x=43;x<=54;x++)for(int z=45;z<=52;z++)for(int y=9;y<=20;y++)h.setBlock(a.cell(x,y,z),Blocks.BEDROCK);
        for(int[] cell:new int[][]{{46,12,48},{46,12,49},{47,13,49},{47,13,48},{48,14,48},{49,15,48}}){
            h.setBlock(a.cell(cell[0],cell[1],cell[2]),Blocks.AIR);h.setBlock(a.cell(cell[0],cell[1]+1,cell[2]),Blocks.AIR);
        }
        for(int[] cell:new int[][]{{46,14,49},{47,15,48},{48,16,48}})h.setBlock(a.cell(cell[0],cell[1],cell[2]),Blocks.AIR);
        BlockPos exit=h.absolutePos(a.cell(49,15,48));var route=a.oleg.getNavigation().createPath(exit,0);
        h.assertTrue(route!=null&&route.canReach(),"Fixture has no real alternative return route");
        var type=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var back=type.getDeclaredMethod("canWalkBackUp",CompanionEntity.class,BlockPos.class,int.class);back.setAccessible(true);
        h.assertTrue((boolean)back.invoke(null,a.oleg,a.oleg.blockPosition(),exit.getY()),"Blocked jump marked a cell seen before trying its open side");
        h.assertTrue(!(boolean)back.invoke(null,a.oleg,exit,exit.getY()+1),"An enclosed cell one below the exit counts as reaching the surface");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void returnSearchNeverFallsThroughAnObstructedColumn(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);
        for(int x=44;x<=57;x++)for(int z=46;z<=51;z++)for(int y=8;y<=22;y++)h.setBlock(a.cell(x,y,z),Blocks.BEDROCK);
        for(int[] cell:new int[][]{{46,14,48},{47,11,48},{47,11,49}}){h.setBlock(a.cell(cell[0],cell[1],cell[2]),Blocks.AIR);h.setBlock(a.cell(cell[0],cell[1]+1,cell[2]),Blocks.AIR);}
        h.setBlock(a.cell(47,14,48),Blocks.AIR); // the start and landing are empty, the middle and head envelope are solid
        h.setBlock(a.cell(47,13,49),Blocks.AIR);
        for(int x=48;x<=54;x++){int feet=12+x-48;for(int y=feet;y<=feet+2;y++)h.setBlock(a.cell(x,y,49),Blocks.AIR);}
        BlockPos from=h.absolutePos(a.cell(46,14,48)),exit=h.absolutePos(a.cell(54,18,49));
        a.oleg.setPos(from.getX()+.5,from.getY(),from.getZ()+.5);a.oleg.setOnGround(true);
        var actual=a.oleg.getNavigation().createPath(exit,0);h.assertTrue(actual==null||!actual.canReach(),"Obstructed-column fixture has an unintended real route");
        var type=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var back=type.getDeclaredMethod("canWalkBackUp",CompanionEntity.class,BlockPos.class,int.class);back.setAccessible(true);
        h.assertTrue(!(boolean)back.invoke(null,a.oleg,from,exit.getY()),"Safety search falls through solid blocks between the start and landing");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=1200)
    public static void stopThenComeKeepsTheLastPhysicalDropOnThePickupList(GameTestHelper h)throws Exception {
        var a=yard(h,false);seed(a);a.oleg.autonomy().setEnabled(true);
        var player=h.absolutePos(a.cell(46,12,42));a.player.teleportTo(player.getX()+.5,player.getY(),player.getZ()+.5);
        var drop=CompanionEntity.class.getDeclaredMethod("dropHarvest",BlockPos.class,List.class);drop.setAccessible(true);
        drop.invoke(a.oleg,h.absolutePos(a.cell(49,12,48)),List.of(new ItemStack(Items.COBBLESTONE)));
        a.script.order("Стой").ask("Иди ко мне",null,800)
                .check("the last already-created drop is picked up before leaving",()->{
                    a.expect(a.count(Items.COBBLESTONE)==33,"Stop/come forgot a real harvest drop: "+a.oleg.getStoredItemsSummary());
                    a.expect(a.oleg.autonomy().held()&&!a.oleg.autonomy().running(),"Pickup restarted autonomous work");
                    a.watch.assertNothingLeft();a.watch.assertNoTeleport();
                });
        finish(a,"endurance-stop-come-pickup",a.watch::summary);
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=600)
    public static void aBushPickupWalksCloserToTheLastLeafInsteadOfGivingUp(GameTestHelper h){
        var a=yard(h,false);a.oleg.setFollowing(false);
        var at=h.absolutePos(a.cell(47,12,48));a.oleg.setPos(at.getX()+.8,at.getY(),at.getZ()+.5);
        h.setBlock(a.cell(42,11,48),Blocks.AIR);
        var leaves=Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1);
        for(int x=41;x<=43;x++)for(int z=47;z<=49;z++)for(int y=12;y<=13;y++)h.setBlock(a.cell(x,y,z),leaves);
        var pos=h.absolutePos(a.cell(42,11,48));var log=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5,new ItemStack(Items.OAK_LOG));
        log.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);h.getLevel().addFreshEntity(log);a.oleg.claimDrops(List.of(log));
        h.succeedWhen(()->{h.assertTrue(a.count(Items.OAK_LOG)==1,"The last required leaf just outside reach stopped pickup: "+a.oleg.watchDetail());h.assertTrue(!log.isAlive(),"Log duplicated");});
    }

    static MarathonArena yard(GameTestHelper h,boolean resources) {
        for(int x=20;x<=76;x++)for(int z=20;z<=72;z++){
            for(int y=39;y>=0;y--)h.setBlock(new BlockPos(x+MarathonArena.M,y,z+MarathonArena.M),
                    y==0?Blocks.BEDROCK:y<9?Blocks.STONE:y<12?Blocks.DIRT:Blocks.AIR);
        }
        if(resources){
            for(int x=31;x<=64;x+=3)for(int z=32;z<=41;z+=3){
                for(int y=12;y<=16;y++)h.setBlock(new BlockPos(x+MarathonArena.M,y,z+MarathonArena.M),Blocks.OAK_LOG);
                h.setBlock(new BlockPos(x+MarathonArena.M,17,z+MarathonArena.M),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1));
            }
            for(int x=30;x<=66;x++)for(int z=58;z<=65;z++)for(int y=12;y<=14;y++)h.setBlock(new BlockPos(x+MarathonArena.M,y,z+MarathonArena.M),Blocks.STONE);
        }
        var box=new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(52,0,52)).getCenter(),h.absolutePos(new BlockPos(109,40,105)).getCenter());
        for(var item:h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box))item.discard();
        return new MarathonArena(h,96,40,80,46,48,48,48,24);
    }
    private static void seed(MarathonArena a){a.oleg.setItem(0,new ItemStack(Items.OAK_LOG,16));a.oleg.setItem(1,new ItemStack(Items.COBBLESTONE,32));a.oleg.setItem(2,new ItemStack(Items.STONE_PICKAXE));}
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void automaticWorkbenchNeverClosesTheOnlyReturnPassage(GameTestHelper h)throws Exception {
        var a=returnStair(h);a.oleg.setItem(0,new ItemStack(Items.CRAFTING_TABLE));
        var place=CompanionEntity.class.getDeclaredMethod("placeBlockNearResult",String.class,BlockPos.class);place.setAccessible(true);
        place.invoke(a.oleg,"minecraft:crafting_table",a.oleg.blockPosition());
        h.assertTrue(returnable(a),"Automatic bench closed the only safe return passage");h.succeed();
    }

    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=200)
    public static void soilAndPackedBenchesKeepTheOnlyReturnStep(GameTestHelper h)throws Exception {
        var a=returnStair(h);BlockPos step=h.absolutePos(a.cell(47,12,48));h.getLevel().setBlock(step,Blocks.DIRT.defaultBlockState(),2);
        var gathering=field(a.oleg,"gathering");var mine=gathering.getClass().getDeclaredMethod("mineDirt",BlockPos.class);mine.setAccessible(true);mine.invoke(gathering,step);
        h.assertTrue(h.getLevel().getBlockState(step).is(Blocks.DIRT)&&returnable(a),"Soil gathering destroyed the only return step");
        h.getLevel().setBlock(step,Blocks.CRAFTING_TABLE.defaultBlockState(),2);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
        Object work=field(a.oleg,"work");set(work,"packTarget",step);set(work,"packResult",CompanionActionResult.ok("Готово."));
        Object handOver=field(work,"handOver");var pack=handOver.getClass().getDeclaredMethod("packBench",net.minecraft.server.level.ServerLevel.class);pack.setAccessible(true);
        h.startSequence().thenExecuteFor(100,()->{try{pack.invoke(handOver,h.getLevel());}catch(Exception e){throw new IllegalStateException(e);}})
                .thenExecute(()->h.assertTrue(h.getLevel().getBlockState(step).is(Blocks.CRAFTING_TABLE)&&returnable(a),"Packing a travelling bench removed the only return step")).thenSucceed();
    }
    private static MarathonArena returnStair(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);
        for(int x=43;x<=55;x++)for(int z=45;z<=51;z++)for(int y=9;y<=23;y++)h.setBlock(a.cell(x,y,z),Blocks.STONE);
        for(int x=46;x<=51;x++){int feet=12+x-46;for(int y=feet;y<=feet+2;y++)h.setBlock(a.cell(x,y,48),Blocks.AIR);}
        a.oleg.autonomy().setEnabled(true);set(a.oleg.autonomy(),"workArea",h.absolutePos(a.cell(51,17,48)));set(a.oleg.autonomy(),"starting",true);
        h.assertTrue(returnable(a),"Fixture has no safe return before the action");return a;
    }
    private static boolean returnable(MarathonArena a){
        try{var t=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var m=t.getDeclaredMethod("canWalkBackUp",CompanionEntity.class,BlockPos.class,int.class);m.setAccessible(true);return (boolean)m.invoke(null,a.oleg,a.oleg.blockPosition(),a.oleg.autonomy().workArea().getY());}catch(Exception e){throw new IllegalStateException(e);}
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void autonomousMiningStopsWhenNoReturnCanBeProven(GameTestHelper h)throws Exception {
        var a=returnStair(h);h.setBlock(a.cell(47,13,48),Blocks.STONE);h.setBlock(a.cell(47,14,48),Blocks.STONE);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));a.oleg.startWork("gather_stone","",32);
        Object work=field(a.oleg,"work");BlockPos target=h.absolutePos(a.cell(45,13,48));
        set(work,"workOrigin",a.oleg.autonomy().workArea());
        set(work,"target",target);set(work,"stoneCandidates",new ArrayList<>(List.of(target)));
        var mine=field(work,"stoneMining").getClass().getDeclaredMethod("mine",net.minecraft.server.level.ServerLevel.class);mine.setAccessible(true);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue((int)field(field(work,"breaking"),"ticks")==0,"Autonomous mining started a swing with no proven return");
        h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.STONE),"Continued digging without a safe exit");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void quarryPickupNeverDescendsIntoAnOldShaft(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);
        for(int y=8;y<=10;y++)h.setBlock(a.cell(47,y,48),Blocks.AIR);
        BlockPos plug=h.absolutePos(a.cell(47,11,48));a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SHOVEL));
        a.oleg.autonomy().setEnabled(true);set(a.oleg.autonomy(),"starting",true);a.oleg.startWork("gather_stone","",32);
        Object work=field(a.oleg,"work");set(work,"target",plug);set(work,"stoneCandidates",new ArrayList<BlockPos>());((java.util.Deque<BlockPos>)field(work,"quarrySoil")).add(plug);
        var mine=field(work,"stoneMining").getClass().getDeclaredMethod("mine",net.minecraft.server.level.ServerLevel.class);mine.setAccessible(true);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue((int)field(field(work,"breaking"),"ticks")==0,"Surface quarry started opening a plug whose pickup falls into an old shaft");
        h.assertTrue(h.getLevel().getBlockState(plug).is(Blocks.DIRT),"Unsafe shaft plug was mined");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void resumingAfterAnOrderKeepsTheWorkplaceSurface(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.autonomy().setEnabled(true);BlockPos original=a.oleg.autonomy().workArea();
        var order=a.oleg.autonomy().getClass().getDeclaredMethod("playerOrder",String.class);order.setAccessible(true);order.invoke(a.oleg.autonomy(),"wait");
        a.oleg.setPos(a.oleg.getX(),a.oleg.getY()-6,a.oleg.getZ());a.oleg.autonomy().setEnabled(true);
        h.assertTrue(original.equals(a.oleg.autonomy().workArea()),"Resuming after an order adopted the pit floor as a new workplace");
        order.invoke(a.oleg.autonomy(),"wait");a.oleg.setPos(a.oleg.getX()+80,original.getY(),a.oleg.getZ());a.oleg.autonomy().setEnabled(true);
        h.assertTrue(a.oleg.blockPosition().equals(a.oleg.autonomy().workArea()),"A genuinely relocated workplace was not adopted");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=600)
    public static void theLastRejectedDropIsRetriedFromANewPickupOrigin(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setFollowing(false);
        var drop=CompanionEntity.class.getDeclaredMethod("dropHarvest",BlockPos.class,List.class);drop.setAccessible(true);drop.invoke(a.oleg,h.absolutePos(a.cell(49,12,48)),List.of(new ItemStack(Items.COBBLESTONE)));
        var item=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,a.oleg.getBoundingBox().inflate(10)).getFirst();item.setNoPickUpDelay();
        Object loot=field(a.oleg,"loot");var reject=loot.getClass().getDeclaredMethod("giveUpFromHere",net.minecraft.server.level.ServerLevel.class,java.util.UUID.class);reject.setAccessible(true);reject.invoke(loot,h.getLevel(),item.getUUID());
        a.oleg.setPos(a.oleg.getX()+5,a.oleg.getY(),a.oleg.getZ());var pending=loot.getClass().getDeclaredMethod("hasPending");pending.setAccessible(true);
        h.assertTrue((boolean)pending.invoke(loot),"The last rejected drop prevents its own reconsideration after a real change of pickup origin");
        a.oleg.setNoAi(false);h.startSequence().thenWaitUntil(()->h.assertTrue(a.oleg.planProgress("gather_stone")==1&&!item.isAlive(),"Drop was not physically recovered from the new side")).thenSucceed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void aSwingLosingItsCornerViewDoesNotRestartTheSameStone(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));a.oleg.startWork("gather_stone","",32);
        BlockPos target=h.absolutePos(a.cell(47,13,49));h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),2);
        Object work=field(a.oleg,"work");set(work,"target",target);set(work,"stoneCandidates",new ArrayList<>(List.of(target)));
        var mine=field(work,"stoneMining").getClass().getDeclaredMethod("mine",net.minecraft.server.level.ServerLevel.class);mine.setAccessible(true);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue((int)field(field(work,"breaking"),"ticks")>0,"Fixture did not begin a real diagonal swing");
        h.setBlock(a.cell(47,13,48),Blocks.STONE);h.setBlock(a.cell(46,13,49),Blocks.STONE);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue(((java.util.Set<?>)field(work,"rejected")).contains(target),"Lost corner view merely reset the swing and leaves the same target eligible forever");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void anExplicitDirtOrderKeepsThePausedWorkplacesReturnStep(GameTestHelper h)throws Exception {
        var a=returnStair(h);set(a.oleg.autonomy(),"starting",false);
        var order=a.oleg.autonomy().getClass().getDeclaredMethod("playerOrder",String.class);order.setAccessible(true);order.invoke(a.oleg.autonomy(),"gather_dirt");
        var gather=CompanionEntity.class.getDeclaredMethod("startGatheringDirtForWork",int.class);gather.setAccessible(true);gather.invoke(a.oleg,1);
        BlockPos step=h.absolutePos(a.cell(47,12,48));h.getLevel().setBlock(step,Blocks.DIRT.defaultBlockState(),2);
        h.assertTrue(!a.oleg.autonomy().running()&&a.oleg.autonomy().enabled(),"Fixture is not a player's interruption of the enabled workplace");
        var gathering=field(a.oleg,"gathering");var mine=gathering.getClass().getDeclaredMethod("mineDirt",BlockPos.class);mine.setAccessible(true);mine.invoke(gathering,step);
        h.assertTrue(h.getLevel().getBlockState(step).is(Blocks.DIRT)&&returnable(a),"Temporary explicit soil order destroyed the saved workplace's exit");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void firstWorkbenchCanBeMadeBelowAnUnreachableOldWorkplace(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setItem(0,new ItemStack(Items.OAK_LOG,14));
        a.oleg.autonomy().setEnabled(true);set(a.oleg.autonomy(),"workArea",a.oleg.blockPosition().above(8));
        var result=a.oleg.craftItemResult("minecraft:wooden_pickaxe",1);
        h.assertTrue(result.success(),"No initial bench/pick below an old unreachable workplace: "+result.message());
        h.assertTrue(carriedTool(a,Items.WOODEN_PICKAXE)==1,"Recipe did not produce the initial pickaxe");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void firstWorkbenchBelowTheOldWorkplaceKeepsThePartialClimb(GameTestHelper h)throws Exception {
        var a=returnStair(h);set(a.oleg.autonomy(),"workArea",h.absolutePos(a.cell(51,20,48)));
        h.setBlock(a.cell(46,12,47),Blocks.AIR);h.setBlock(a.cell(46,13,47),Blocks.AIR);
        a.oleg.setItem(0,new ItemStack(Items.OAK_LOG,14));var result=a.oleg.craftItemResult("minecraft:wooden_pickaxe",1);
        h.assertTrue(result.success(),"Safe bench pocket refused because only the final old height is unreachable: "+result.message());
        var t=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var back=t.getDeclaredMethod("canWalkBackUp",CompanionEntity.class,BlockPos.class,int.class);back.setAccessible(true);
        h.assertTrue((boolean)back.invoke(null,a.oleg,a.oleg.blockPosition(),h.absolutePos(a.cell(51,17,48)).getY()),"Placement destroyed the existing partial climb");h.succeed();
    }
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=4*MINUTE)
    public static void requestedStoneToolsBootstrapFromLogsBelowTheOldWorkplace(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setItem(0,new ItemStack(Items.OAK_LOG,14));a.oleg.autonomy().setEnabled(true);set(a.oleg.autonomy(),"workArea",a.oleg.blockPosition().above(8));
        for(int x=50;x<=54;x++)for(int z=46;z<=50;z++)for(int y=12;y<=14;y++)h.setBlock(a.cell(x,y,z),Blocks.STONE);
        a.script.ask("Сделай каменную кирку, топор и меч","[{\"action\":\"craft_item\",\"item\":\"minecraft:stone_pickaxe\",\"count\":1},{\"action\":\"craft_item\",\"item\":\"minecraft:stone_axe\",\"count\":1},{\"action\":\"craft_item\",\"item\":\"minecraft:stone_sword\",\"count\":1}]",3*MINUTE)
                .order("Стой").check("all requested tools really made",()->{
                    a.expect(carriedTool(a,Items.STONE_PICKAXE)==1,"Requested stone pickaxe absent");a.expect(carriedTool(a,Items.STONE_AXE)==1,"Requested stone axe absent");a.expect(carriedTool(a,Items.STONE_SWORD)==1,"Requested stone sword absent");
                    a.expect(a.oleg.planProgress("gather_stone")>=8,"Requested tools bypassed real stone collection");a.watch.assertNothingLeft();
                });finish(a,"endurance-tools-below-old-camp",()->"");
    }
    private static int carriedTool(MarathonArena a,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<a.oleg.getContainerSize();i++)if(a.oleg.getItem(i).is(item))n+=a.oleg.getItem(i).getCount();for(var slot:EquipmentSlot.values())if(a.oleg.getItemBySlot(slot).is(item))n+=a.oleg.getItemBySlot(slot).getCount();return n;}
    @GameTest(template="marathon_valley",batch="endurance",timeoutTicks=40)
    public static void aQueuedStoneStepLosingItsCornerViewChoosesAnotherDescent(GameTestHelper h)throws Exception {
        var a=yard(h,false);a.oleg.setNoAi(true);a.oleg.setOnGround(true);a.oleg.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));a.oleg.startWork("gather_stone","",32);
        BlockPos target=h.absolutePos(a.cell(47,13,49));h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),2);
        Object work=field(a.oleg,"work");set(work,"target",target);set(work,"stoneCandidates",new ArrayList<>(List.of(target)));((java.util.Deque<BlockPos>)field(work,"quarrySoil")).add(target);
        var mine=field(work,"stoneMining").getClass().getDeclaredMethod("mine",net.minecraft.server.level.ServerLevel.class);mine.setAccessible(true);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue((int)field(field(work,"breaking"),"ticks")>0,"Fixture did not begin a real queued diagonal swing");
        h.setBlock(a.cell(47,13,48),Blocks.STONE);h.setBlock(a.cell(46,13,49),Blocks.STONE);mine.invoke(field(work,"stoneMining"),h.getLevel());
        h.assertTrue(((java.util.Set<?>)field(work,"rejected")).contains(target),"Queued step losing its view remains eligible for repeated swings");
        h.assertTrue(((java.util.Deque<?>)field(work,"quarrySoil")).isEmpty(),"Failed staircase queue was retained");
        h.assertTrue(a.oleg.hasActiveWorkOrder(),"A single unstable step aborted the entire batch instead of a bounded replan");h.succeed();
    }
    private static void callAtSurface(MarathonArena a){
        // A fresh autonomy order can legitimately remember a lower workplace. Call from the arena's original surface,
        // inside a broad patch: ordinary COME may finish within three blocks while still one stair below an edge.
        BlockPos center=a.oleg.autonomy().workArea().atY(a.h.absolutePos(a.cell(46,12,48)).getY());
        try{var type=Class.forName("dev.aicompanion.entity.CompanionMiningNavigation");var stand=type.getDeclaredMethod("standable",CompanionEntity.class,BlockPos.class);stand.setAccessible(true);
            for(int radius=0;radius<=24;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;BlockPos pos=center.offset(dx,0,dz);
                boolean broad=true;for(int x=-3;x<=3&&broad;x++)for(int z=-3;z<=3;z++)if(!(boolean)stand.invoke(null,a.oleg,pos.offset(x,0,z))){broad=false;break;}
                if(broad){a.player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return;}
            }
        }catch(Exception e){throw new IllegalStateException(e);}
        a.h.fail("No surface cell remains from which the owner can call");
    }
    private static void leave(MarathonArena a){a.player.setNoGravity(true);a.player.teleportTo(a.player.getX()+192,a.player.getY(),a.player.getZ());}
    private static void finish(MarathonArena a,String name,java.util.function.Supplier<String> extra){
        a.script.check("release test work chunks",()->{a.oleg.setFollowing(false);a.oleg.setLifestyle(false,false);CompanionChunks.release(a.oleg.getUUID());});
        a.script.run(name,extra);
    }
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
}
