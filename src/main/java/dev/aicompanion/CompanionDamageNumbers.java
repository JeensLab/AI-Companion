package dev.aicompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Idea 7ES2V (#105): what he lost or regained rises above him for a second, and «блок» when a shield stopped a whole hit.
 * Counted on the client from the health the server already sends him: nothing new crosses the network, and nothing runs
 * each tick, the arrival of his synced data is the event. The loss is what his health lost, after armour and resistance,
 * and no more than he had left.
 */
public final class CompanionDamageNumbers {
    public static final int LIFE_TICKS = 20;
    private static final int MOST = 8;
    private static final int SLOTS = 3;

    public enum Kind {
        HIT(0xFF5555), FIRE(0xFFAA00), MAGIC(0xC77DFF), EXPLOSION(0xFFFF55), BLOCK(0xC8C8C8), HEAL(0x55FF55);
        public final int color;
        Kind(int color) { this.color = color; }
    }

    /** One number: the slot keeps numbers that are up together side by side instead of on top of each other. */
    public record Pop(String text, Kind kind, int born, int slot) { }

    private final List<Pop> pops = new ArrayList<>();
    private float last = Float.NaN;

    /**
     * His health as it arrived. The first value, and any before his first tick (the data that comes with him into the
     * world), only set where counting starts. The kind of a loss is the game's last damage source.
     * ponytail: damage that went into absorption hearts is not seen, they are not synced for a mob; he rarely has any.
     */
    public void health(float health, @Nullable DamageSource source, int tick) {
        boolean start = Float.isNaN(last) || tick == 0;
        float change = health - last;
        last = health;
        if (start) return;
        float shown = Math.round(Math.abs(change) * 10) / 10F;
        if (shown == 0) return;
        if (change > 0) add("+" + amount(shown), Kind.HEAL, tick);
        else add(amount(shown), kind(source), tick);
    }

    /** A shield took the whole hit (the game's entity event 29): his health did not change, so it says nothing itself. */
    public void blocked(int tick) { add("блок", Kind.BLOCK, tick); }

    public List<Pop> live(int tick) {
        pops.removeIf(pop -> tick - pop.born() >= LIFE_TICKS);
        return Collections.unmodifiableList(pops);
    }

    private void add(String text, Kind kind, int tick) {
        live(tick);
        if (pops.size() >= MOST) pops.removeFirst();
        int slot = IntStream.range(0, SLOTS).filter(free -> pops.stream().noneMatch(pop -> pop.slot() == free))
                .findFirst().orElse(pops.size() % SLOTS);
        pops.add(new Pop(text, kind, tick, slot));
    }

    /** One decimal with a comma, none when whole: «5,3», «2». */
    private static String amount(float shown) {
        return shown == Math.floor(shown) ? String.valueOf((int) shown) : String.valueOf(shown).replace('.', ',');
    }

    /** The colour of a loss: fire and lava, blasts, poison and magic and wither; everything else is a plain hit. */
    static Kind kind(@Nullable DamageSource source) {
        if (source == null) return Kind.HIT;
        if (source.is(DamageTypeTags.IS_FIRE)) return Kind.FIRE;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Kind.EXPLOSION;
        if (source.is(Tags.DamageTypes.IS_MAGIC)) return Kind.MAGIC;
        return Kind.HIT;
    }
}
