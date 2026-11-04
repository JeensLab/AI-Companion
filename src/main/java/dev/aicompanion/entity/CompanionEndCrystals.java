package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The fight with the dragon as a partner takes his part of it: the dragon is hurt by nobody but a player (EnderDragon.hurt), so Oleg
 * shoots the crystals on the pillars that heal it. A reflex like the bow in a fight, no step of a plan and no model: while a dragon
 * lives near him, a crystal the game put there (it shows its base; one the player set down does not) gets his arrows. Following the
 * player he walks to where an arrow gets to it; told to stay or to guard, he shoots only from where he is. An order of the player
 * comes first.
 */
public final class CompanionEndCrystals {
    /** A dragon this near is the fight on: it never leaves the island, whose pillars stand some 42 blocks round its middle. */
    private static final double FIGHT_RANGE = 192;
    /** How far round him crystals are looked for: from the island's edge, the pillars on its far side. */
    private static final double SEEK = 160;
    /** How often the flight to the crystal is worked out again: it takes a few thousand steps of an arrow. */
    private static final int FLIGHT_EVERY = 10;
    /**
     * A crystal blows up with power 6, which hurts out to twice that (ExplosionDamageCalculator): at eight blocks some 20 health. So
     * nobody is to be nearer than this when one is shot, a player, Oleg, the player's pets; four blocks of it for what a player climbing
     * beside a pillar covers while a lob is in the air (one to four seconds).
     */
    private static final double BLAST = 16;
    /**
     * Where he shoots a crystal from when not from where he stands: this far from its pillar's middle on his side, one after the other.
     * Too near a tall wide pillar no lob gets over its edge (the pitch stops at 85°), too far the arrow does not reach.
     */
    private static final double[] STANDS = {22, 32, 14};
    /** What a crystal may cost before he leaves it to the player: arrows, and ticks of this reflex spent on it. */
    private static final int ARROWS_PER_CRYSTAL = 8, TICKS_PER_CRYSTAL = 20 * 40;
    /** The dragon is looked for once a second: the lookup goes over every entity of the world. */
    private static final int FIGHT_CHECK_EVERY = 20;
    private static final int LINES_KEPT = 16;

    final CompanionEntity c;
    private EndCrystal crystal;
    /** The stand he walks to for this crystal, its number in STANDS, and where it is once worked out. */
    private int standIndex;
    private BlockPos stand;
    private CompanionArrowFlight.Flight flight;
    private int flightAt, scannedAt, fightCheckedAt = -FIGHT_CHECK_EVERY, worked, spent;
    private boolean fight, shooting, chosenTold, toldStart, toldDone, toldCaged, toldLeft, toldNoBow;
    /** Crystals that went while he was shooting at them: only then are the crystals «his» to report as shot down. */
    private int broke;
    /** Crystals no arrow of his got to from any stand, or that cost too much: the player's now. */
    private final Set<UUID> left = new HashSet<>();
    /** Not following, the crystals no arrow gets to from where he stands, tried again once he is somewhere else. */
    private final Set<UUID> notFromHere = new HashSet<>();
    private BlockPos tried;
    private final List<String> said = new ArrayList<>();

    CompanionEndCrystals(CompanionEntity c) { this.c = c; }

    /** The lines this reflex said, last ones kept. */
    public static List<String> said(CompanionEntity c) { return List.copyOf(c.endCrystals.said); }

    /** He is at a crystal now: the bow is his weapon, whatever blade the player chose (CompanionEquipment.selectWeaponForCombat). */
    boolean shooting() { return shooting; }

    /**
     * A crystal to go for now: the fight is on, no order of the player runs, he has a bow and arrows, and a crystal he can try stands.
     * Not following the player (told to stay or to guard), only while an arrow gets to it from where he is.
     */
    boolean wanted() {
        if (!(c.level() instanceof ServerLevel level) || c.inventoryViewers != 0 || !fightOn(level) || c.hasActiveWorkOrder()) return false;
        // The crystals are looked over with the flight, not every tick: the box is some four hundred chunks wide, and a flight that
        // fails flies all its pitches. So also with no crystal at hand (guarding a walking player, none to be hit from here).
        if (c.tickCount - scannedAt < FLIGHT_EVERY) return crystal != null && crystal.isAlive() && (c.following || flight != null);
        scannedAt = c.tickCount;
        if (crystal != null && !crystal.isAlive()) { if (spent > 0) broke++; crystal = null; }
        if (c.following || !c.blockPosition().equals(tried)) { notFromHere.clear(); tried = c.blockPosition(); }
        List<EndCrystal> standing = level.getEntitiesOfClass(EndCrystal.class, c.getBoundingBox().inflate(SEEK), CompanionEndCrystals::pillars);
        List<EndCrystal> caged = standing.stream().filter(one -> caged(level, one)).toList();
        EndCrystal next = standing.stream().filter(one -> !caged.contains(one) && !left.contains(one.getUUID()) && !notFromHere.contains(one.getUUID()))
                .min(Comparator.comparingDouble(c::distanceToSqr)).orElse(null);
        if (next == null) {
            // Some only out of reach from where he was told to stay: nothing to report yet.
            if (standing.stream().anyMatch(one -> notFromHere.contains(one.getUUID()))) return false;
            long unreached = standing.stream().filter(one -> left.contains(one.getUUID())).count();
            if (broke > 0 && unreached == 0 && !toldLeft && !toldDone) { toldDone = true; say("Кристаллы на колоннах сбил."); }
            if (unreached > 0 && !toldLeft) { toldLeft = true; say(count((int) unreached) + " стрелой не достать — " + (unreached == 1 ? "он" : "они") + " на тебе."); }
            if (!caged.isEmpty() && !toldCaged) { toldCaged = true; say(cagedLine(caged.size())); }
            return false;
        }
        if (!c.archery.armed()) {
            if (!toldNoBow) { toldNoBow = true; say("Нечем сбивать кристаллы: нужны лук и стрелы."); }
            return false;
        }
        if (next != crystal) {
            crystal = next; standIndex = 0; stand = null; flight = null; flightAt = c.tickCount - FLIGHT_EVERY; worked = spent = 0;
            chosenTold = false;
        }
        if (c.tickCount - flightAt >= FLIGHT_EVERY) {
            flight = CompanionArrowFlight.toward(level, new Vec3(c.getX(), c.getEyeY() - .1, c.getZ()), crystal.getBoundingBox());
            flightAt = c.tickCount;
        }
        if (c.following || flight != null) return true;
        // Told to stay or to guard: this one is out of reach from here, the next scan tries the next.
        notFromHere.add(crystal.getUUID());
        crystal = null;
        return false;
    }

    private boolean fightOn(ServerLevel level) {
        if (c.tickCount - fightCheckedAt < FIGHT_CHECK_EVERY) return fight;
        fightCheckedAt = c.tickCount;
        boolean on = false;
        for (EnderDragon dragon : level.getDragons()) on |= dragon.distanceToSqr(c) <= FIGHT_RANGE * FIGHT_RANGE;
        // A fight over: the next one is told about again from the start.
        if (!on) { toldStart = toldDone = toldCaged = toldLeft = toldNoBow = false; broke = 0; left.clear(); notFromHere.clear(); crystal = null; }
        return fight = on;
    }

    /** One of the pillars': the game sets them with their base shown; a player's own (EndCrystalItem) has none. */
    private static boolean pillars(EndCrystal crystal) {
        return crystal.isAlive() && crystal.showsBottom() && !crystal.isInvulnerable();
    }

    /** Iron bars round it, as the game guards two of the ten (SpikeFeature): from its base up three blocks, two to each side. */
    private static boolean caged(ServerLevel level, EndCrystal crystal) {
        BlockPos at = crystal.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 2, 2))) if (level.getBlockState(pos).is(Blocks.IRON_BARS)) return true;
        return false;
    }

    private static String cagedLine(int count) {
        if (count == 1) return "Кристалл в клетке — его только ты: залезть и разбить.";
        return count(count) + " в клетках — их только ты: залезть и разбить.";
    }

    /** «Кристалл», «Два кристалла», «5 кристаллов»: the count spelled as the lines say it. */
    private static String count(int count) {
        return switch (count) {
            case 1 -> "Один кристалл";
            case 2 -> "Два кристалла";
            case 3 -> "Три кристалла";
            case 4 -> "Четыре кристалла";
            default -> count + " кристаллов";
        };
    }

    void tick() {
        if (crystal == null || !crystal.isAlive()) return;
        shooting = true;
        if (!chosenTold) { chosenTold = true; event("end_crystal_chosen", ""); }
        if (!toldStart) { toldStart = true; say("Сбиваю кристаллы."); }
        // Counted here, not by the clock: a fight with an enderman or an order of the player in between costs this crystal nothing.
        if (spent >= ARROWS_PER_CRYSTAL) { giveUp("arrows"); return; }
        if (++worked > TICKS_PER_CRYSTAL) { giveUp("time"); return; }
        if (flight == null) { if (c.following) walkToStand(); return; }
        c.travel().reset();
        c.getNavigation().stop();
        if (nearBlast()) return;
        if (c.archery.shootStill(crystal, flight)) { spent++; event("end_crystal_shot", ""); }
    }

    /**
     * Toward the next stand by the single route; the flight is tried again on the way. A stand reached with no flight, or none to be
     * got to: the next one; none left: the crystal is the player's.
     */
    private void walkToStand() {
        if (stand == null) {
            if (standIndex >= STANDS.length) { giveUp("stands"); return; }
            Vec3 middle = crystal.position(), from = c.position();
            double dx = from.x - middle.x, dz = from.z - middle.z, flat = Math.max(Math.sqrt(dx * dx + dz * dz), 1e-6);
            int x = (int) Math.floor(middle.x + dx / flat * STANDS[standIndex]), z = (int) Math.floor(middle.z + dz / flat * STANDS[standIndex]);
            stand = new BlockPos(x, c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        if (c.work.route.legs(stand, 1.5) != CompanionTravel.Step.WALKING) {
            c.travel().reset();
            stand = null;
            standIndex++;
        }
    }

    /** Someone the blast would hurt is near the crystal: a player, Oleg himself or another, a pet of his player. */
    private boolean nearBlast() {
        return !c.level().getEntitiesOfClass(LivingEntity.class, crystal.getBoundingBox().inflate(BLAST), one -> one.isAlive() && !one.isSpectator()
                && (one instanceof Player || one instanceof CompanionEntity || one instanceof OwnableEntity pet && c.ownerId != null
                && c.ownerId.equals(pet.getOwnerUUID())) && one.distanceTo(crystal) < BLAST).isEmpty();
    }

    private void giveUp(String why) {
        event("end_crystal_left", why);
        left.add(crystal.getUUID());
        crystal = null;
        stand = null;
        flight = null;
        shooting = false;
        c.archery.lowerBow();
        c.travel().reset();
        c.getNavigation().stop();
    }

    /** The journal's line on the crystal at hand: where it is and what it cost so far, for a report of a fight gone wrong. */
    private void event(String name, String why) {
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, "crystal", crystal.blockPosition().toShortString(), "from",
                c.blockPosition().toShortString(), "arrows", spent, "ticks", worked, "stand", standIndex, "reason", why);
    }

    private void say(String line) {
        said.add(line);
        if (said.size() > LINES_KEPT) said.removeFirst();
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(Component.literal(c.getName().getString() + ": " + line));
    }

    void stop() {
        shooting = false;
        c.archery.lowerBow();
        c.travel().reset();
        c.getNavigation().stop();
    }

    /** Below the fight (a foe at his side comes first), above following the player. */
    static final class CrystalGoal extends Goal {
        final CompanionEntity c;
        CrystalGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return c.endCrystals.wanted(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { c.endCrystals.tick(); }
        @Override public void stop() { c.endCrystals.stop(); }
    }
}
