package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGoalTests {
    @GameTest(template="tall_tree",timeoutTicks=2200)
    public static void emptyBagGoalMinesCraftsAndPhysicallyDelivers(GameTestHelper h) {
        var f = fixture(h); resources(h); f.start(); f.collect = true;
        h.succeedWhen(() -> { f.done();
            h.assertTrue(h.getBlockState(new BlockPos(18,1,18)).isAir(), "No physical wood harvest");
            h.assertTrue(f.c.goals().resultCount() >= 3, "No structured craft/toss/pickup results");
            h.assertTrue(f.c.goals().attempts() <= 3, "Unexpected extra work");
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=1000)
    public static void existingPickAndMaterialsOnlyCraftMissingShare(GameTestHelper h) {
        var f=fixture(h); f.c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        f.c.setItem(0,new ItemStack(Items.COBBLESTONE,9)); f.c.setItem(1,new ItemStack(Items.STICK,6));
        h.setBlock(new BlockPos(17,1,16),Blocks.CRAFTING_TABLE); f.start(); f.collect=true;
        h.succeedWhen(()->{ f.done();
            h.assertTrue(count(f.c,Items.COBBLESTONE)==6 && count(f.c,Items.STICK)==4,"Not exactly one real recipe: "+f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=300)
    public static void existingRecipientPickNeedsNoCraftOrDelivery(GameTestHelper h) {
        var f=fixture(h); f.c.setItem(0,new ItemStack(Items.STONE_PICKAXE));
        f.p.getInventory().setItem(0,new ItemStack(Items.STONE_PICKAXE)); f.start();
        h.succeedWhen(()->{ f.done(); h.assertTrue(f.c.goals().attempts()==0 && f.c.goals().operation()==null,"Made or issued an unnecessary pick"); });
    }

    @GameTest(template="tall_tree",timeoutTicks=2500)
    public static void reloadDuringPreparationReconcilesAndFinishes(GameTestHelper h) {
        var f=fixture(h); resources(h); f.start(); f.collect=true;
        boolean[] loaded={false};
        h.onEachTick(()->{if(!loaded[0] && h.getTick()>50 && f.c.hasActiveWorkOrder() && f.c.goals().countPicks()==0){loaded[0]=true; f.reload();}});
        h.succeedWhen(()->{f.done();h.assertTrue(loaded[0],"No preparation reload");});
    }

    @GameTest(template="tall_tree",timeoutTicks=1300)
    public static void reloadAfterCraftBeforeHandoverDoesNotCraftAgain(GameTestHelper h) {
        var f=fixture(h); materials(f,h); f.start(); f.collect=true; boolean[] loaded={false};
        h.onEachTick(()->{if(!loaded[0] && f.c.goals().countPicks()==2 && f.c.goals().operation()==null){
            loaded[0]=true;var tag=new CompoundTag();f.c.addAdditionalSaveData(tag);
            var facts=tag.getCompound(CompanionGoals.KEY).getList("Results",net.minecraft.nbt.Tag.TAG_COMPOUND);
            h.assertTrue(!facts.isEmpty() && facts.getCompound(0).getString("Action").equals("craft_item")
                    && facts.getCompound(0).getLong("WorkId")>0 && facts.getCompound(0).getInt("Count")==2
                    && facts.getCompound(0).getUUID("Recipient").equals(f.c.getUUID()),"Committed craft evidence missing or wrong recipient");
            int confirmed=f.c.goals().resultCount();f.reload();h.assertTrue(f.c.goals().resultCount()==confirmed,"Confirmed skill result lost at reload");
        }});
        h.succeedWhen(()->{ f.done(); h.assertTrue(loaded[0] && count(f.c,Items.COBBLESTONE)==0,"Craft reload missing or recipe duplicated"); });
    }

    @GameTest(template="tall_tree",timeoutTicks=1300)
    public static void reloadOfUncollectedTossKeepsSameOperationAndItem(GameTestHelper h) {
        var f=fixture(h); picks(f); f.start(); boolean[] loaded={false}; UUID[] op={null}, item={null};
        h.onEachTick(()->{if(!loaded[0] && f.c.goals().operation()!=null){
            loaded[0]=true; op[0]=f.c.goals().operation(); item[0]=f.c.goals().giftEntity();
            h.assertTrue(f.p.getInventory().countItem(Items.STONE_PICKAXE)==0,"Toss teleported into inventory"); f.reload();
            h.assertTrue(f.c.goals().state()!=CompanionGoals.State.COMPLETED,"Toss alone completed the goal"); f.collect=true;
        }});
        h.succeedWhen(()->{ f.done(); h.assertTrue(loaded[0] && op[0].equals(f.c.goals().operation()) && item[0].equals(f.c.goals().giftEntity()),"Reissued delivery"); });
    }

    @GameTest(template="tall_tree",timeoutTicks=800)
    public static void completedReloadAndLaterPlayerLossNeverReissues(GameTestHelper h) {
        var f=fixture(h); picks(f); f.start(); f.collect=true; long[] loaded={-1}; UUID[] op={null};
        h.onEachTick(()->{if(loaded[0]<0 && f.c.goals().state()==CompanionGoals.State.COMPLETED){f.done();op[0]=f.c.goals().operation();f.reload(); f.p.getInventory().clearContent(); loaded[0]=h.getTick();}});
        h.succeedWhen(()->{
            h.assertTrue(loaded[0]>0 && h.getTick()>loaded[0]+120,"Observe completed reload");
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.COMPLETED && f.c.goals().countPicks()==1,"Completed order restarted");
            h.assertTrue(op[0].equals(f.c.goals().operation()) && f.p.getInventory().countItem(Items.STONE_PICKAXE)==0 && f.ground()==0,"Reissued lost player pick");
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=1200)
    public static void duplicateNaturalRequestsKeepOneGoal(GameTestHelper h) {
        var f=fixture(h); materials(f,h); f.start(); UUID id=f.c.goals().id();
        for(String phrase:new String[]{"Олег, сделай нам обоим каменные кирки и отдай мне мою", "Себе и мне скрафти каменные кирки", "Изготовь кирки из камня для нас обоих"}) {
            String command=CompanionLocalCommands.route(phrase,"Олег");
            h.assertTrue("companion goal pickaxes".equals(command),"Natural request missed: "+phrase);
            var action=CompanionLocalCommands.action(command); h.assertTrue(action.type().equals("goal_pickaxes"),"Wrong goal action");
            h.assertTrue(CompanionEvents.executeAction(f.p,action.type(),"",1).success() && id.equals(f.c.goals().id()),"Duplicate goal created");
        }
        h.assertTrue(CompanionLocalCommands.route("Не делай нам каменные кирки","Олег")==null,"Negation started a goal");
        f.collect=true; h.succeedWhen(f::done);
    }

    @GameTest(template="tall_tree",timeoutTicks=350)
    public static void savedCancellationNeverRestarts(GameTestHelper h) {
        var f=fixture(h); materials(f,h); f.start();
        int[] picks={-1},stone={-1};
        h.runAfterDelay(10,()->{f.order("stop");h.assertTrue(f.c.goals().state()==CompanionGoals.State.PAUSED_PLAYER,"Stop did not only pause the order: "+f.c.goals().status());f.order("goal_cancel");picks[0]=f.c.goals().countPicks();stone[0]=count(f.c,Items.COBBLESTONE);f.reload();});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>200,"Observe cancellation");
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.CANCELLED && f.c.goals().countPicks()==picks[0] && f.ground()==0,"Cancelled goal worked after load: "+f.c.goals().status());
            h.assertTrue(count(f.c,Items.COBBLESTONE)==stone[0],"Cancelled order consumed recipe");
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=1200)
    public static void savedStayPersistsUntilExplicitResume(GameTestHelper h) {
        var f=fixture(h); materials(f,h); f.start();
        int[] picks={-1};h.runAfterDelay(10,()->{f.order("wait");picks[0]=f.c.goals().countPicks();f.reload();});
        h.runAfterDelay(200,()->{
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.PAUSED_PLAYER && !f.c.isFollowing() && f.c.goals().countPicks()==picks[0] && f.ground()==0,"Saved stay was lifted: "+f.c.goals().status()+" following="+f.c.isFollowing());
            h.assertTrue(CompanionLocalCommands.action(CompanionLocalCommands.route("Олег, продолжай работу","Олег")).type().equals("goal_resume"),"Resume route");
            f.order("goal_resume");f.collect=true;
        });h.succeedWhen(f::done);
    }

    @GameTest(template="tall_tree",timeoutTicks=1400)
    public static void followAndComePauseGoalAcrossReload(GameTestHelper h) {
        var f=fixture(h); picks(f); f.start(); f.order("follow"); f.reload();
        double[] closest={Double.MAX_VALUE};h.onEachTick(()->{if(h.getTick()>=80 && h.getTick()<250)closest[0]=Math.min(closest[0],f.c.distanceTo(f.p));});
        h.runAfterDelay(80,()->{
            h.assertTrue(f.c.isFollowing() && f.c.goals().state()==CompanionGoals.State.PAUSED_PLAYER && f.c.goals().countPicks()==2,"Follow interrupted by goal");
            f.order("come");
        });
        h.runAfterDelay(250,()->{ h.assertTrue(f.c.goals().state()==CompanionGoals.State.PAUSED_PLAYER && closest[0]<3.2 && !f.c.isWorking() && f.c.didLastTaskSucceed(),"Come did not reach the player: closest="+closest[0]+" / "+f.c.getLastTaskResult()); f.order("goal_resume");f.collect=true; });
        h.succeedWhen(f::done);
    }

    @GameTest(template="tall_tree",timeoutTicks=1600)
    public static void temporaryCombatThenSafeResumeFinishesRealItems(GameTestHelper h) {
        var f=fixture(h); materials(f,h); f.start();
        var enemy=h.spawn(EntityType.ZOMBIE,25,1,25); enemy.setNoAi(true); enemy.setInvulnerable(true);
        h.runAfterDelay(15,()->f.c.setTarget(enemy));
        h.runAfterDelay(60,()->{h.assertTrue(f.c.goals().state()==CompanionGoals.State.PAUSED_COMBAT,"Fight did not pause goal");f.reload();enemy.discard();f.c.setTarget(null);f.collect=true;});
        h.succeedWhen(f::done);
    }

    @GameTest(template="tall_tree",timeoutTicks=2400)
    public static void brokenMiningToolUsesRealReplacementMaterials(GameTestHelper h) {
        var f=fixture(h); resources(h); f.c.setItem(0,new ItemStack(Items.OAK_LOG,5));
        var worn=new ItemStack(Items.WOODEN_PICKAXE);worn.setDamageValue(worn.getMaxDamage()-1);f.c.setItemSlot(EquipmentSlot.MAINHAND,worn);
        f.start();f.collect=true; h.succeedWhen(()->{f.done();h.assertTrue(!f.c.getMainHandItem().is(Items.WOODEN_PICKAXE)||f.c.getMainHandItem().getDamageValue()<worn.getMaxDamage()-1,"Broken tool still used");});
    }

    @GameTest(template="tall_tree",timeoutTicks=650)
    public static void fullBagBlocksWithReasonAndBoundedAttempts(GameTestHelper h) {
        var f=fixture(h); for(int i=0;i<f.c.getContainerSize();i++) f.c.setItem(i,new ItemStack(Items.BEDROCK,64));
        f.start();h.succeedWhen(()->{
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.BLOCKED,"Full bag goal still active: "+f.c.goals().status());
            h.assertTrue(f.c.goals().reason().contains("места") && f.c.goals().attempts()==1 && f.c.goals().countPicks()==0 && f.ground()==0,"Full bag refused without factual reason");
            f.reload(); h.assertTrue(f.c.goals().state()==CompanionGoals.State.BLOCKED && f.c.goals().attempts()==1,"Blocked state reset");
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=2800)
    public static void unreachableDeliveryNeverLosesOrDuplicatesPicks(GameTestHelper h) {
        var f=fixture(h); picks(f);
        for(int x=0;x<32;x++) for(int y=1;y<8;y++) h.setBlock(new BlockPos(x,y,20),Blocks.BEDROCK);
        var at=h.absolutePos(new BlockPos(16,1,25));f.p.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);f.start();
        h.succeedWhen(()->{
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.BLOCKED,"No bounded route refusal: "+f.c.getLastTaskResult());
            h.assertTrue(f.c.goals().countPicks()==2 && f.p.getInventory().countItem(Items.STONE_PICKAXE)==0 && f.ground()==0 && !f.c.goals().reason().isBlank(),"Unreachable delivery lost picks");
        });
    }

    @GameTest(template="tall_tree",timeoutTicks=1900)
    public static void unavailableSourceStopsAndRemembersFailureBudget(GameTestHelper h) {
        var f=fixture(h); f.start(); int[] tries={0};
        h.onEachTick(()->{if(f.c.goals().state()==CompanionGoals.State.BLOCKED && tries[0]<3){
            tries[0]++;f.reload();var response=CompanionEvents.executeAction(f.p,"goal_resume","",1);
            if(tries[0]==3)h.assertTrue(!response.success(),"Failure limit bypassed");
        }});
        h.succeedWhen(()->{h.assertTrue(tries[0]==3 && f.c.goals().failures()==3 && f.c.goals().state()==CompanionGoals.State.BLOCKED,"Failure budget not reached");
            h.assertTrue(f.c.goals().countPicks()==0 && f.ground()==0 && f.p.getInventory().countItem(Items.STONE_PICKAXE)==0,"Unavailable source conjured items");});
    }

    @GameTest(template="tall_tree",timeoutTicks=300)
    public static void oldWorldAndUnsupportedGoalAreSafe(GameTestHelper h) {
        var f=fixture(h); f.c.setItem(0,new ItemStack(Items.DIAMOND,7));CompoundTag saved=new CompoundTag();f.c.addAdditionalSaveData(saved);
        saved.remove(CompanionGoals.KEY);f.c.readAdditionalSaveData(saved);
        h.assertTrue(f.c.goals().state()==null && count(f.c,Items.DIAMOND)==7,"Old world changed items or started goal");
        CompoundTag bad=new CompoundTag();bad.putInt("Schema",999); saved.put(CompanionGoals.KEY,bad);f.c.readAdditionalSaveData(saved);
        h.runAfterDelay(180,()->{h.assertTrue(f.c.goals().state()==CompanionGoals.State.UNSUPPORTED && !f.c.goals().reason().isBlank() && count(f.c,Items.DIAMOND)==7 && f.c.goals().countPicks()==0,"Unsupported record ran work");h.succeed();});
    }

    @GameTest(template="tall_tree",timeoutTicks=400)
    public static void damagedDeliveryRecordCannotManufactureAReplacement(GameTestHelper h) {
        var f=fixture(h);picks(f);f.start();boolean[] damaged={false};
        h.onEachTick(()->{if(!damaged[0] && f.c.goals().operation()!=null){
            damaged[0]=true;var saved=new CompoundTag();f.c.addAdditionalSaveData(saved);
            saved.getCompound(CompanionGoals.KEY).putString("Operation","not-a-uuid"); f.c.readAdditionalSaveData(saved);
        }});
        h.succeedWhen(()->{h.assertTrue(damaged[0] && h.getTick()>150,"Wait for damaged delivery");
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.UNSUPPORTED && f.c.goals().countPicks()==1 && f.ground()==1 && f.p.getInventory().countItem(Items.STONE_PICKAXE)==0,"Damaged operation duplicated gift");});
    }

    @GameTest(template="tall_tree",timeoutTicks=700)
    public static void fullRecipientInventoryWaitsForTheSamePhysicalItem(GameTestHelper h) {
        var f=fixture(h);picks(f);for(int i=0;i<f.p.getInventory().getContainerSize();i++)f.p.getInventory().setItem(i,new ItemStack(Items.BEDROCK,64));
        f.start();f.collect=true;UUID[] op={null};
        h.runAfterDelay(150,()->{
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.ACTIVE && f.c.goals().countPicks()==1 && f.ground()==1 && f.p.getInventory().countItem(Items.STONE_PICKAXE)==0,"Uncollected toss: state="+f.c.goals().state()+" own="+f.c.goals().countPicks()+" ground="+f.ground()+" player="+f.p.getInventory().countItem(Items.STONE_PICKAXE)+" creative="+f.p.isCreative());
            op[0]=f.c.goals().operation();f.reload();f.p.getInventory().clearContent();
        });h.succeedWhen(()->{f.done();h.assertTrue(op[0]!=null && op[0].equals(f.c.goals().operation()),"Reissued after player made room");});
    }

    @GameTest(template="tall_tree",timeoutTicks=700)
    public static void comeInterruptedByLogoutKeepsPlayerBehaviorUntilResume(GameTestHelper h) {
        var f=fixture(h);picks(f);f.start();
        var at=h.absolutePos(new BlockPos(28,1,28));f.p.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
        f.order("come");f.reload();
        double[] closest={Double.MAX_VALUE};long[] work={0};h.onEachTick(()->{
            closest[0]=Math.min(closest[0],f.c.distanceTo(f.p));if(f.c.workPhase().startsWith("COME/"))work[0]=f.c.activeWorkId();
        });
        h.succeedWhen(()->{h.assertTrue(h.getTick()>220 && closest[0]<3.2 && work[0]>0 && f.c.workResult(work[0])!=null
                    && f.c.workResult(work[0]).status()==CompanionActionResult.Status.SUCCESS,"Come behavior was lost at logout: "+closest[0]+" / "+f.c.getLastTaskResult());
            h.assertTrue(f.c.goals().state()==CompanionGoals.State.PAUSED_PLAYER && f.c.goals().countPicks()==2 && f.ground()==0,"Goal resumed over player order");});
    }

    @GameTest(template="tall_tree",timeoutTicks=2200)
    public static void foodRunSwitchesToReachableSheepWhenAnotherCowIsSealedAway(GameTestHelper h) {
        var f=fixture(h);f.c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        var cow=h.spawn(EntityType.COW,14,1,16);cow.setNoAi(true);
        var sheep=h.spawn(EntityType.SHEEP,12,1,16);sheep.setNoAi(true);
        var sealed=h.spawn(EntityType.COW,22,1,16);sealed.setNoAi(true);
        for(int x=20;x<=24;x++)for(int z=14;z<=18;z++)for(int y=1;y<=5;y++)
            if(x==20||x==24||z==14||z==18||y==5)h.setBlock(new BlockPos(x,y,z),Blocks.BEDROCK);
        h.assertTrue(CompanionEvents.executeAction(f.p,"hunt_animals","food",2).success(),"Food run refused");
        h.succeedWhen(()->{
            h.assertTrue(!f.c.isWorking() && f.c.didLastTaskSucceed() && !cow.isAlive() && !sheep.isAlive(),"Reachable sheep skipped after cow: "+f.c.getLastTaskResult());
            h.assertTrue(count(f.c,Items.BEEF)>0 && count(f.c,Items.MUTTON)>0 && sealed.isAlive(),"Did not physically collect both reachable kinds or broke sealed cow out");
        });
    }

    private static Fixture fixture(GameTestHelper h) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)h.setBlock(new BlockPos(x,0,z),Blocks.BEDROCK);
        var c=h.spawn(AICompanionMod.COMPANION.get(),16,1,16);var p=survivalPlayer(h);
        p.teleportTo(c.getX()-5,c.getY(),c.getZ()-5);c.setOwnerId(p.getUUID());c.setSearchRadius(16);c.setLifestyle(false,false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion",c.getUUID());CompanionEvents.getOwnedCompanion(p);
        var f=new Fixture(h,c,p);h.onEachTick(f::pickup);return f;
    }
    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        // Vanilla's GameTest mock overrides isCreative() to true; with a full bag it deletes a pickup.
        // This scenario needs the actual survival inventory rules.
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"test-mock-player"),false);
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setInvulnerable(true);
        return p;
    }
    private static void materials(Fixture f,GameTestHelper h){f.c.setItem(0,new ItemStack(Items.COBBLESTONE,6));f.c.setItem(1,new ItemStack(Items.STICK,4));h.setBlock(new BlockPos(17,1,16),Blocks.CRAFTING_TABLE);}
    private static void picks(Fixture f){f.c.setItem(0,new ItemStack(Items.STONE_PICKAXE));f.c.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));}
    private static void resources(GameTestHelper h){for(int y=1;y<=5;y++)h.setBlock(new BlockPos(18,y,18),Blocks.OAK_LOG);for(int z=14;z<25;z++)h.setBlock(new BlockPos(21,1,z),Blocks.STONE);}
    private static int count(CompanionEntity c,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();for(var s:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND})if(c.getItemBySlot(s).is(item))n+=c.getItemBySlot(s).getCount();return n;}
    private static final class Fixture {
        final GameTestHelper h; CompanionEntity c; final ServerPlayer p; boolean collect;
        Fixture(GameTestHelper h,CompanionEntity c,ServerPlayer p){this.h=h;this.c=c;this.p=p;}
        void start(){h.assertTrue(CompanionEvents.executeAction(p,"goal_pickaxes","",1).status()==CompanionActionResult.Status.ACCEPTED,"Goal not accepted");}
        void order(String action){h.assertTrue(CompanionEvents.executeAction(p,action,"",1).success(),"Order failed: "+action);}
        int ground(){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)),net.minecraft.world.phys.Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(32,20,32)))),e->e.getItem().is(Items.STONE_PICKAXE)).stream().mapToInt(e->e.getItem().getCount()).sum();}
        void pickup(){if(collect)for(var item:h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(5),e->e.getItem().is(Items.STONE_PICKAXE))){if(!item.hasPickUpDelay()) {p.teleportTo(item.getX(),item.getY(),item.getZ());item.playerTouch(p);}}}
        void reload(){var saved=new CompoundTag();c.saveWithoutId(saved);UUID uuid=c.getUUID(),goal=c.goals().id();c.discard();c=AICompanionMod.COMPANION.get().create(h.getLevel());c.load(saved);h.assertTrue(h.getLevel().addFreshEntity(c)&&c.getUUID().equals(uuid),"Reload duplicated canonical companion");h.assertTrue(java.util.Objects.equals(goal,c.goals().id()),"Goal identity lost");}
        void done(){h.assertTrue(c.goals().state()==CompanionGoals.State.COMPLETED,"Goal incomplete: "+c.goals().status()+" / "+c.getLastTaskResult());h.assertTrue(c.goals().countPicks()==1 && p.getInventory().countItem(Items.STONE_PICKAXE)==1 && ground()==0,"Wrong items or recipients: "+c.getStoredItemsSummary()+" player="+p.getInventory().countItem(Items.STONE_PICKAXE)+" ground="+ground());}
    }
}
