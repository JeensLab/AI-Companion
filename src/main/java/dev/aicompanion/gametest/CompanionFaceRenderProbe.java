package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.CompanionExpression;
import dev.aicompanion.client.CompanionVoiceAudio;
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
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import java.util.concurrent.CompletableFuture;

/** Opt-in offline render QA only; the entire gametest package is excluded from the release JAR. */
@EventBusSubscriber(modid=AICompanionMod.MOD_ID, value=Dist.CLIENT)
public final class CompanionFaceRenderProbe {
    private static boolean started, opened;
    private static volatile long soundStarted;
    private static final boolean[] SPEECH = {false,true,false,true,true,false,false,true,false,false,true,false};
    @SubscribeEvent public static void soundStarted(PlayStreamingSourceEvent event) {
        if (Boolean.getBoolean("aicompanion.faceProbe") && event.getSound().getClass().getName().endsWith("CompanionVoiceAudio$Voice"))
            soundStarted = System.nanoTime();
    }
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
        if (!opened && mc.level!=null && mc.player!=null && CompanionVoiceAudio.audible(CompanionVoiceAudio.speaker())) {
            opened=true; mc.options.guiScale().set(2); mc.resizeDisplay(); mc.setScreen(new Faces(mc));
        }
    }
    private static final class Faces extends Screen {
        private final CompanionEntity speaker;
        private final EntityDataAccessor<Integer> accessor;
        private final String[] labels={"Спокойное","Ухмылка","Задумчивое","Боевое","Обеспокоенное","Радостное","Удивлённое","Недовольное"};
        private final boolean[] checked=new boolean[12];
        private CompletableFuture<Void> playback;
        private int phase;
        private long phaseStarted, deadline=System.nanoTime()+45_000_000_000L;
        private String screenshot;
        @SuppressWarnings("unchecked")
        Faces(Minecraft mc) throws Exception {
            super(Component.literal("Oleg face render QA"));
            var field=CompanionEntity.class.getDeclaredField("DATA_EXPRESSION");field.setAccessible(true);
            accessor=(EntityDataAccessor<Integer>)field.get(null);
            speaker=CompanionVoiceAudio.speaker();
        }
        @Override public boolean isPauseScreen() { return false; }
        private void play(int seconds,boolean pauses) {
            soundStarted=0;
            playback=CompanionVoiceAudio.play(wav(seconds,pauses),speaker.getUUID(),minecraft.level);
            check(CompanionVoiceAudio.mouthOpen(speaker)==0,"Mouth opens before playback");
        }
        @Override public void tick() {
            check(System.nanoTime()<deadline,"Face/audio probe timed out, phase "+phase);
            double elapsed=soundStarted==0 ? -1 : (System.nanoTime()-soundStarted)/1_000_000_000.0;
            float mouth=CompanionVoiceAudio.mouthOpen(speaker);
            switch(phase) {
                case 0 -> { play(6,true); phase=1; }
                case 1 -> {
                    if(elapsed>=0 && elapsed<6) {
                        int segment=(int)(elapsed*2);
                        double inside=elapsed-segment*.5;
                        if(!checked[segment] && inside>.22 && inside<.4) {
                            check(SPEECH[segment] ? mouth>.3F : mouth<.08F,"Audio cursor/pauses drift at "+elapsed+", mouth="+mouth);
                            checked[segment]=true;
                            if(segment==0) screenshot="oleg-faces-closed.png";
                            if(segment==3) screenshot="oleg-faces-speaking.png";
                        }
                    }
                    if(playback.isDone()) {
                        playback.join(); for(boolean sample:checked)check(sample,"Missed an audio/silence window");
                        check(mouth==0,"Mouth remains open at natural end"); phase=2;
                    }
                }
                case 2 -> { play(3,false); phase=3; }
                case 3 -> {
                    if(elapsed>.35) {check(mouth>.3F,"Second reply does not animate");minecraft.getSoundManager().pause();phaseStarted=System.nanoTime();phase=4;}
                }
                case 4 -> {
                    if(System.nanoTime()-phaseStarted>350_000_000L) {check(mouth==0,"Paused sound keeps mouth open");minecraft.getSoundManager().resume();phaseStarted=System.nanoTime();phase=5;}
                }
                case 5 -> {
                    if(System.nanoTime()-phaseStarted>350_000_000L) {
                        check(mouth>.3F,"Resume does not restore mouth");playback.cancel(false);
                        check(CompanionVoiceAudio.mouthOpen(speaker)==0,"Cancellation keeps mouth open");phase=6;
                    }
                }
                case 6 -> {
                    playback=CompanionVoiceAudio.play(new byte[]{1,2,3},speaker.getUUID(),minecraft.level);phase=7;
                }
                case 7 -> {
                    if(playback.isDone()) {check(playback.isCompletedExceptionally()&&mouth==0,"Bad audio does not close mouth");play(3,false);phase=8;}
                }
                case 8 -> {
                    if(elapsed>.35) {
                        check(mouth>.3F,"Reply after error does not animate");minecraft.getSoundManager().stop(null,net.minecraft.sounds.SoundSource.VOICE);phaseStarted=System.nanoTime();phase=9;
                    }
                }
                case 9 -> {
                    if(System.nanoTime()-phaseStarted>250_000_000L) {check(mouth==0,"Stopped channel keeps mouth open");play(3,false);phase=10;}
                }
                case 10 -> {
                    if(elapsed>.35) {
                        check(mouth>.3F,"Reply before world change does not animate");
                        minecraft.level.disconnect();minecraft.disconnect(new TitleScreen());CompanionVoiceAudio.tick();
                        check(CompanionVoiceAudio.mouthOpen(speaker)==0 && playback.isDone(),"World change keeps voice alive");
                        AICompanionMod.LOGGER.info("FACE_AUDIO_PROBE_PASSED: eight expressions, local WAV, twelve cursor windows including stream refill, natural end, sequential replies, pause/resume, cancellation, invalid WAV, stopped channel, world change");
                        minecraft.stop();
                    }
                }
            }
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            g.fill(0,0,width,height,0xFF17231F);
            g.drawCenteredString(font,"Олег · восемь выражений · локальный звук · рот "+String.format(java.util.Locale.ROOT,"%.2f",CompanionVoiceAudio.mouthOpen(speaker)),width/2,12,0xECF1EB);
            int w=width/4,h=(height-36)/2;
            for(int i=0;i<CompanionExpression.values().length;i++) {
                int left=(i%4)*w,top=36+(i/4)*h;
                g.fill(left+8,top+4,left+w-8,top+h-4,0xFF24352C);
                speaker.getEntityData().set(accessor,i);
                InventoryScreen.renderEntityInInventoryFollowsAngle(g,left+10,top+10,left+w-10,top+h-28,
                        (int)((h-45)/2.0),0,.15F,0,speaker);
                g.drawCenteredString(font,labels[i],left+w/2,top+h-20,0xECF1EB);
            }
            if(screenshot!=null) {
                Screenshot.grab(minecraft.gameDirectory,screenshot,minecraft.getMainRenderTarget(),
                        message->AICompanionMod.LOGGER.info("Face probe screenshot: {}",message.getString()));
                screenshot=null;
            }
        }
    }
    private static void check(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
    private static byte[] wav(int seconds,boolean pauses) {
        int rate=24000;byte[] bytes=new byte[seconds*rate*2];
        for(int frame=0;frame<seconds*rate;frame++) {
            short value=(short)((!pauses || SPEECH[(frame/(rate/2))%SPEECH.length]) ? Math.sin(frame*2*Math.PI*220/rate)*14000 : 0);
            bytes[frame*2]=(byte)value;bytes[frame*2+1]=(byte)(value>>8);
        }
        try {
            var out=new java.io.ByteArrayOutputStream();
            try(var audio=new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(bytes),new javax.sound.sampled.AudioFormat(rate,16,1,true,false),seconds*rate)) {
                javax.sound.sampled.AudioSystem.write(audio,javax.sound.sampled.AudioFileFormat.Type.WAVE,out);
            }
            return out.toByteArray();
        } catch(Exception error) { throw new IllegalStateException(error); }
    }
}
