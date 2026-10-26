package dev.aicompanion.gametest;

import com.mojang.authlib.GameProfile;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Development only (excluded from release JAR). Each stage is a fresh JVM and normal dedicated world save. */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID)
public final class CompanionGoalRestartProbe {
    private static final String STAGE=System.getProperty("aicompanion.goalRestartStage","disabled");
    private static final UUID PLAYER=UUID.fromString("fb255b15-ddd3-4c0a-b3ee-a030b5b880c6");
    private static final Path MANIFEST=Path.of("goal-probe.properties");
    private static ServerPlayer player;
    private static CompanionEntity companion;
    private static final Properties manifest=new Properties();
    private static int ticks;
    private static boolean finished, initialized;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(STAGE.equals("disabled"))return;
        try {
            if(Files.exists(MANIFEST))try(var in=Files.newInputStream(MANIFEST)){manifest.load(in);}
            check(STAGE.equals("prepare") ? manifest.isEmpty() : !manifest.isEmpty(),"Fresh/previous world manifest mismatch");
            MinecraftServer server=event.getServer();var level=server.overworld();
            level.setChunkForced(0,0,true);level.setChunkForced(1,0,true);level.setChunkForced(0,1,true);level.setChunkForced(1,1,true);
            var cookie=CommonListenerCookie.createInitial(new GameProfile(PLAYER,"goal-restart-probe"),false);
            player=new ServerPlayer(server,level,cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection,player,cookie);
            server.tickRateManager().setTickRate(200);
        }catch(Exception ex){fail(event.getServer(),ex);}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(STAGE.equals("disabled")||finished||player==null)return;
        var server=event.getServer();
        try {
            ticks++;
            if(!initialized) {
                companion=CompanionEvents.getOwnedCompanion(player);
                if(companion==null){check(ticks<200,"Canonical companion did not load from entity region");return;}
                if(STAGE.equals("prepare"))setup(server);
                else {
                    check(companion.getUUID().toString().equals(manifest.getProperty("companion")),"Companion UUID changed across process");
                    check(companion.goals().id().toString().equals(manifest.getProperty("goal")),"Goal UUID changed across process");
                    check(companion.goals().state()!=CompanionGoals.State.UNSUPPORTED,"Saved goal unsupported");
                    if(STAGE.equals("crafted"))check(manifest.getProperty("stage").equals("prepare"),"Preparation stage missing");
                    if(STAGE.equals("issued"))check(manifest.getProperty("stage").equals("crafted") && companion.goals().countPicks()==2,"Crafted items did not survive disk");
                    if(STAGE.equals("delivered"))check(manifest.getProperty("stage").equals("issued") && companion.goals().operation().toString().equals(manifest.getProperty("operation")),"Issued operation did not survive disk");
                    if(STAGE.equals("verify")) {
                        check(manifest.getProperty("stage").equals("delivered") && companion.goals().state()==CompanionGoals.State.COMPLETED,"Completed goal was not saved");
                        check(player.getInventory().countItem(Items.STONE_PICKAXE)==1,"Player pickup not persisted by PlayerList");
                        player.getInventory().clearContent(); // Simulate later legitimate loss, after the confirmed delivery.
                    }
                }
                initialized=true;
            }
            if(STAGE.equals("prepare") && ticks>25 && companion.hasActiveWorkOrder() && companion.goals().countPicks()==0)pass(server);
            if(STAGE.equals("crafted") && companion.goals().countPicks()==2 && companion.goals().operation()==null)pass(server);
            if(STAGE.equals("issued") && companion.goals().operation()!=null) {
                check(player.getInventory().countItem(Items.STONE_PICKAXE)==0,"Toss already picked up before issued save");
                check(server.overworld().getEntity(companion.goals().giftEntity()) instanceof ItemEntity,"Physical issued entity missing");pass(server);
            }
            if(STAGE.equals("delivered")) {
                if(server.overworld().getEntity(companion.goals().giftEntity()) instanceof ItemEntity item && !item.hasPickUpDelay()) {
                    player.teleportTo(item.getX(),item.getY(),item.getZ());item.playerTouch(player);
                }
                if(companion.goals().state()==CompanionGoals.State.COMPLETED) {
                    check(companion.goals().countPicks()==1 && player.getInventory().countItem(Items.STONE_PICKAXE)==1,"Wrong final recipients");pass(server);
                }
            }
            if(STAGE.equals("verify") && ticks>160) {
                check(companion.goals().state()==CompanionGoals.State.COMPLETED && companion.goals().countPicks()==1 && player.getInventory().countItem(Items.STONE_PICKAXE)==0,"Later loss restarted order");
                check(companion.goals().operation().toString().equals(manifest.getProperty("operation")),"Duplicate operation");pass(server);
            }
            check(ticks<4000,"Stage timeout: "+companion.goals().status()+" / "+companion.getLastTaskResult());
        }catch(Exception ex){fail(server,ex);}
    }
    private static void setup(MinecraftServer server) {
        var level=server.overworld();
        for(int x=0;x<32;x++)for(int z=0;z<32;z++) {
            level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=81;y<=88;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(11.5,81,11.5);companion.teleportTo(16.5,81,16.5);companion.setLifestyle(false,false);companion.setSearchRadius(16);
        for(int y=81;y<=85;y++)level.setBlockAndUpdate(new BlockPos(18,y,18),Blocks.OAK_LOG.defaultBlockState());
        for(int z=14;z<25;z++)level.setBlockAndUpdate(new BlockPos(21,81,z),Blocks.STONE.defaultBlockState());
        check(CompanionEvents.executeAction(player,"goal_pickaxes","",1).success(),"Goal not accepted");
    }
    private static void pass(MinecraftServer server)throws Exception {
        manifest.setProperty("stage",STAGE);manifest.setProperty("companion",companion.getUUID().toString());manifest.setProperty("goal",companion.goals().id().toString());
        manifest.setProperty("state",companion.goals().state().name());manifest.setProperty("ownPicks",Integer.toString(companion.goals().countPicks()));
        manifest.setProperty("playerPicks",Integer.toString(player.getInventory().countItem(Items.STONE_PICKAXE)));
        if(companion.goals().operation()!=null)manifest.setProperty("operation",companion.goals().operation().toString());
        try(var out=Files.newOutputStream(MANIFEST)){manifest.store(out,"Dedicated process persistence probe");}
        System.out.println("GOAL_RESTART_PASS stage="+STAGE+" companion="+companion.getUUID()+" goal="+companion.goals().id()+" own="+companion.goals().countPicks()+" player="+player.getInventory().countItem(Items.STONE_PICKAXE));
        finished=true;server.halt(false); // Normal stopServer saves players and entity/chunk regions after this tick.
    }
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void fail(MinecraftServer server,Exception ex){finished=true;System.out.println("GOAL_RESTART_FAIL stage="+STAGE+" "+ex);ex.printStackTrace();server.halt(false);}
}
