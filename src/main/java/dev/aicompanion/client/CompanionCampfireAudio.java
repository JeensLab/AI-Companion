package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionCampfire;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The tune by the fire on this client (the owner's ruling, 13.11): the recordings come from the player's folder, which the game's
 * sound engine knows only through the dynamic resource pack ({@link CompanionMusicPack}), not as registered sound events. So the
 * server sends no sound packet: it syncs the sound id with his pose (CompanionEntity.campfireTune) and this plays that id from him
 * while he sits and plays, stopping when the tune changes, he stands up or the world goes. The «Jukebox/Note Blocks» volume and the
 * distance work as for any entity-bound sound.
 */
final class CompanionCampfireAudio {
    /** He is looked for only this close: the sound itself fades out at sixteen blocks. */
    private static final double LOOK_RANGE = 48;
    private static SoundInstance playing;
    private static String tune = "";
    private static CompanionEntity companion;
    private static int lookAgain;

    private CompanionCampfireAudio() { }

    /** Every client tick: the sound follows the pose and the tune the server synced. */
    static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) { forget(); return; }
        if (companion != null && (companion.isRemoved() || companion.level() != client.level)) companion = null;
        if (companion == null) {
            // No companion in sight yet: look again now and then, not on every tick.
            if (lookAgain-- > 0) return;
            lookAgain = 10;
            companion = client.level.getEntitiesOfClass(CompanionEntity.class, client.player.getBoundingBox().inflate(LOOK_RANGE),
                    entity -> client.player.getUUID().equals(entity.getOwnerId())).stream().findFirst().orElse(null);
            if (companion == null) return;
        }
        String wanted = companion.campfirePose() == CompanionCampfire.PLAYING ? companion.campfireTune() : "";
        if (playing != null && wanted.equals(tune)) return;
        stop();
        if (wanted.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(wanted);
        if (id == null) return;
        if (client.getSoundManager().getSoundEvent(id) == null) {
            // A recording added to the folder after the last resource reload: the sound engine has not read it yet.
            client.gui.getChat().addMessage(Component.literal("Новая запись ещё не загружена: введите /companion music."));
            return;
        }
        playing = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.RECORDS, 1F, 1F, companion,
                companion.getRandom().nextLong());
        client.getSoundManager().play(playing);
        tune = wanted;
    }

    /** The evening is over, the tune changed or the world went: the sound stops here. */
    static void stop() {
        if (playing != null) Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
        tune = "";
    }

    /** Leaving the world: nothing of the old one is left sounding. */
    static void forget() { stop(); companion = null; lookAgain = 0; }
}
