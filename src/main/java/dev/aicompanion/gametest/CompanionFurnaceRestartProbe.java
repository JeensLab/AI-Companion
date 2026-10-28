package dev.aicompanion.gametest;

import com.mojang.authlib.GameProfile;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkLink;
import dev.aicompanion.client.PlannedAction;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Development only (excluded from release JAR). Stage {@code prepare}: a long smelt is handed over to its furnace and the world is saved.
 * Stage {@code verify}: a fresh JVM reads that world; the batch must be an orphan, collected by the companion himself.
 */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID)
public final class CompanionFurnaceRestartProbe {
    private static final String STAGE=System.getProperty("aicompanion.furnaceRestartStage","disabled");
    private static final UUID PLAYER=UUID.fromString("5d1b7a3e-0c4f-4e0a-9b63-1f3a2c7d9e58");
    private static final Path MANIFEST=Path.of("furnace-probe.properties");
    private static final BlockPos FURNACE=new BlockPos(18,81,18);
    private static final int BEEF=6;
    private static ServerPlayer player;
    private static CompanionEntity companion;
    private static CompanionParkLink link;
    private static final Properties manifest=new Properties();
    private static int ticks, calm;
    private static boolean finished, initialized;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(STAGE.equals("disabled"))return;
        try {
            if(Files.exists(MANIFEST))try(var in=Files.newInputStream(MANIFEST)){manifest.load(in);}
            check(STAGE.equals("prepare") ? manifest.isEmpty() : !manifest.isEmpty(),"Fresh/previous world manifest mismatch");
            MinecraftServer server=event.getServer();var level=server.overworld();
            level.setChunkForced(0,0,true);level.setChunkForced(1,0,true);level.setChunkForced(0,1,true);level.setChunkForced(1,1,true);
            var cookie=CommonListenerCookie.createInitial(new GameProfile(PLAYER,"furnace-restart-probe"),false);
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
                    check(manifest.getProperty("stage").equals("prepare"),"Preparation stage missing");
                    var batches=companion.batches().summaries();
                    check(batches.size()==1 && batches.getFirst().orphan(),"The saved batch did not come back as an orphan: "+batches);
                    check(Long.toString(batches.getFirst().id()).equals(manifest.getProperty("batch")),"Batch id changed across process");
                    check(batches.getFirst().input().equals("minecraft:beef") && batches.getFirst().output().equals("minecraft:cooked_beef"),"Recipe not resolved again: "+batches);
                    check(!container(server).isEmpty(),"Furnace lost its content across the restart");
                    check(count(Items.COOKED_BEEF)==0,"Bag already holds the product");
                }
                initialized=true;
            }
            if(STAGE.equals("prepare") && ticks>25) {
                if(!companion.batches().summaries().isEmpty() && !container(server).isEmpty()) {
                    check(!companion.batches().summaries().getFirst().orphan(),"Parked batch is an orphan before the save");
                    // Let the furnace burn a while so that the saved cooking progress is not the very start.
                    if(++calm>60)pass(server);
                }
            }
            if(STAGE.equals("verify") && companion.batches().summaries().isEmpty() && count(Items.COOKED_BEEF)==BEEF && !companion.autonomy().said().isEmpty()) {
                check(container(server).isEmpty(),"Furnace is not empty after the collection");
                pass(server);
            }
            check(ticks<(STAGE.equals("prepare") ? 4000 : 9000),"Stage timeout: "+companion.batches().summaries()+" "+companion.getStoredItemsSummary()+" / "+companion.getLastTaskResult());
        }catch(Exception ex){fail(server,ex);}
    }
    private static void setup(MinecraftServer server) {
        var level=server.overworld();
        for(int x=0;x<32;x++)for(int z=0;z<32;z++) {
            level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=81;y<=88;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(11.5,81,11.5);companion.teleportTo(16.5,81,16.5);companion.setLifestyle(false,false);companion.setSearchRadius(16);
        level.setBlockAndUpdate(FURNACE,Blocks.FURNACE.defaultBlockState());
        companion.setItem(0,new ItemStack(Items.BEEF,BEEF));companion.setItem(1,new ItemStack(Items.COAL,2));
        link=new CompanionParkLink();link.bind(server,player.getUUID());
        link.submit(List.of(new PlannedAction("smelt_items",0,BEEF,"minecraft:beef")),"furnace-restart-probe",()->false);
    }
    private static Container container(MinecraftServer server){return (Container)server.overworld().getBlockEntity(FURNACE);}
    private static int count(net.minecraft.world.item.Item item) {
        int count=0;
        for(int slot=0;slot<companion.getContainerSize();slot++)if(companion.getItem(slot).is(item))count+=companion.getItem(slot).getCount();
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND})if(companion.getItemBySlot(slot).is(item))count+=companion.getItemBySlot(slot).getCount();
        return count;
    }
    private static void pass(MinecraftServer server)throws Exception {
        manifest.setProperty("stage",STAGE);manifest.setProperty("companion",companion.getUUID().toString());
        if(STAGE.equals("prepare"))manifest.setProperty("batch",Long.toString(companion.batches().summaries().getFirst().id()));
        try(var out=Files.newOutputStream(MANIFEST)){manifest.store(out,"Dedicated process persistence probe");}
        System.out.println("FURNACE_RESTART_PASS stage="+STAGE+" companion="+companion.getUUID()+" batches="+companion.batches().summaries().size()+" cooked="+count(Items.COOKED_BEEF));
        finished=true;
        if(link!=null)link.close();
        server.halt(false); // Normal stopServer saves players and entity/chunk regions after this tick.
    }
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static void fail(MinecraftServer server,Exception ex){finished=true;System.out.println("FURNACE_RESTART_FAIL stage="+STAGE+" "+ex);ex.printStackTrace();server.halt(false);}
}
