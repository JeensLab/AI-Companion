package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in offline render QA only; the entire gametest package is excluded from the release JAR. */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID, value=Dist.CLIENT)
public final class CompanionFaceRenderProbe {
    private static boolean started, opened;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("aicompanion.faceProbe")) return;
        Minecraft mc=Minecraft.getInstance();
        if (!started && mc.screen instanceof TitleScreen) {
            started=true;
            mc.createWorldOpenFlows().createFreshLevel("OlegFaces-"+System.currentTimeMillis(),
                    new LevelSettings("Offline face QA",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L,false,false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
        }
        if (!opened && mc.level!=null && mc.player!=null) {opened=true;mc.setScreen(new Faces(mc));}
    }
    private static final class Faces extends Screen {
        private final CompanionEntity[] entities=new CompanionEntity[6];
        private final String[] labels={"Calm","Smirk","Focused","Combat","Worried","Pleased"};
        private int frames;
        @SuppressWarnings("unchecked")
        Faces(Minecraft mc) throws Exception {
            super(Component.literal("Oleg face render QA"));
            var field=CompanionEntity.class.getDeclaredField("DATA_EXPRESSION");field.setAccessible(true);
            var accessor=(EntityDataAccessor<Integer>)field.get(null);
            for(int i=0;i<6;i++) {entities[i]=AICompanionMod.COMPANION.get().create(mc.level);entities[i].getEntityData().set(accessor,i);}
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            g.fill(0,0,width,height,0xFF17231F);
            g.drawCenteredString(font,"Oleg: actual Minecraft renderer / original UV / six expressions",width/2,12,0xECF1EB);
            int w=width/3,h=(height-36)/2;
            for(int i=0;i<6;i++) {
                int left=(i%3)*w,top=36+(i/3)*h;
                g.fill(left+8,top+4,left+w-8,top+h-4,0xFF24352C);
                InventoryScreen.renderEntityInInventoryFollowsAngle(g,left+10,top+10,left+w-10,top+h-28,
                        (int)((h-45)/2.0),0,.15F,0,entities[i]);
                g.drawCenteredString(font,labels[i],left+w/2,top+h-20,0xECF1EB);
            }
            if (++frames==60) Screenshot.grab(minecraft.gameDirectory,"oleg-expressions-client.png",minecraft.getMainRenderTarget(),
                    message->AICompanionMod.LOGGER.info("Face probe screenshot: {}",message.getString()));
        }
    }
}
