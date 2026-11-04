package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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
 * player he walks to where an arrow gets to it; told to stay, he shoots only from where he stands. An order of the player comes first.
 */
public final class CompanionEndCrystals {
    /** A dragon this near is the fight on: it never leaves the island, whose pillars stand some 42 blocks round its middle. */
    private static final double FIGHT_RANGE = 192;
    /** How far round him crystals are looked for. */
    private static final double SEEK = 96;
    /** How often the flight to the crystal is worked out again: it takes a few thousand steps of an arrow. */
    private static final int FLIGHT_EVERY = 10;
    /** A crystal blows up like TNT (power 6): nobody this near it when it is shot, the player or Oleg. */
    private static final double BLAST = 8;
    /** The nearest he goes to a pillar's middle to shoot from: under a tall one only a steep lob gets over its edge. */
    private static final double NEAREST_STAND = 10;
    /** What a crystal may cost before he leaves it to the player: arrows, and ticks of walking and shooting. */
    private static final int ARROWS_PER_CRYSTAL = 8, TICKS_PER_CRYSTAL = 20 * 40;
    /** The dragon is looked for once a second: the lookup goes over every entity of the world. */
    private static final int FIGHT_CHECK_EVERY = 20;
    private static final int LINES_KEPT = 16;

    final CompanionEntity c;
    private EndCrystal crystal;
    /** Where he walks to shoot this crystal from, chosen once for it. */
    private BlockPos stand;
    private CompanionArrowFlight.Flight flight;
    private int flightAt, fightCheckedAt = -FIGHT_CHECK_EVERY, since, spent;
    private boolean fight, started, toldDone, toldCaged, toldNoBow;
    /** Crystals he could not get an arrow to from the nearest stand, or that cost too much: the player's now. */
    private final Set<UUID> left = new HashSet<>();
    private final List<String> said = new ArrayList<>();

    CompanionEndCrystals(CompanionEntity c) { this.c = c; }

    /** The lines this reflex said, last ones kept. */
    public static List<String> said(CompanionEntity c) { return List.copyOf(c.endCrystals.said); }

    /** A crystal to shoot now: the fight is on, no order of the player runs, he has a bow and arrows, and a crystal he can try stands. */
    boolean wanted() {
        if (!(c.level() instanceof ServerLevel level) || c.inventoryViewers != 0 || !fightOn(level) || c.hasActiveWorkOrder()) return false;
        List<EndCrystal> standing = level.getEntitiesOfClass(EndCrystal.class, c.getBoundingBox().inflate(SEEK), CompanionEndCrystals::pillars);
        List<EndCrystal> caged = standing.stream().filter(one -> caged(level, one)).toList();
        EndCrystal next = standing.stream().filter(one -> !caged.contains(one) && !left.contains(one.getUUID()))
                .min(Comparator.comparingDouble(c::distanceToSqr)).orElse(null);
        if (next == null) {
            if (started && !toldDone) { toldDone = true; say("Кристаллы на колоннах сбил."); }
            if (!caged.isEmpty() && !toldCaged) { toldCaged = true; say(cagedLine(caged.size())); }
            return false;
        }
        if (!c.archery.armed()) {
            if (!toldNoBow) { toldNoBow = true; say("Нечем сбивать кристаллы: нужны лук и стрелы."); }
            return false;
        }
        if (next != crystal) { crystal = next; stand = null; flight = null; since = c.tickCount; spent = 0; }
        return true;
    }

    private boolean fightOn(ServerLevel level) {
        if (c.tickCount - fightCheckedAt < FIGHT_CHECK_EVERY) return fight;
        fightCheckedAt = c.tickCount;
        boolean on = false;
        for (EnderDragon dragon : level.getDragons()) on |= dragon.distanceToSqr(c) <= FIGHT_RANGE * FIGHT_RANGE;
        // A fight over: the next one is told about again from the start.
        if (!on) { started = toldDone = toldCaged = toldNoBow = false; left.clear(); crystal = null; }
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
        String number = switch (count) { case 2 -> "Два"; case 3 -> "Три"; case 4 -> "Четыре"; default -> String.valueOf(count); };
        return number + (count <= 4 ? " кристалла" : " кристаллов") + " в клетках — их только ты: залезть и разбить.";
    }

    void tick() {
        if (crystal == null || !crystal.isAlive()) return;
        if (!started) { started = true; say("Сбиваю кристаллы."); }
        if (spent >= ARROWS_PER_CRYSTAL || c.tickCount - since > TICKS_PER_CRYSTAL) { giveUp(); return; }
        if (flight == null || c.tickCount - flightAt >= FLIGHT_EVERY) {
            flight = CompanionArrowFlight.toward(c.level(), new Vec3(c.getX(), c.getEyeY() - .1, c.getZ()), crystal.getBoundingBox());
            flightAt = c.tickCount;
        }
        if (flight == null) { if (c.following) walkToStand(); return; }
        c.travel().reset();
        c.getNavigation().stop();
        if (nearBlast()) return;
        if (c.archery.shootStill(crystal, flight)) spent++;
    }

    /**
     * Toward the pillar on his own side of it, by the single route, down to the nearest stand; the flight is tried again on the way.
     * There and still no flight, or no way there: it is the player's.
     */
    private void walkToStand() {
        if (stand == null) {
            Vec3 middle = crystal.position(), from = c.position();
            double dx = from.x - middle.x, dz = from.z - middle.z, flat = Math.max(Math.sqrt(dx * dx + dz * dz), 1e-6);
            int x = (int) Math.floor(middle.x + dx / flat * NEAREST_STAND), z = (int) Math.floor(middle.z + dz / flat * NEAREST_STAND);
            stand = new BlockPos(x, c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        if (c.work.route.legs(stand, 1.5) != CompanionTravel.Step.WALKING) giveUp();
    }

    private boolean nearBlast() {
        ServerPlayer owner = c.getOwnerPlayer();
        return c.distanceTo(crystal) < BLAST || owner != null && owner.level() == c.level() && owner.distanceTo(crystal) < BLAST;
    }

    private void giveUp() {
        left.add(crystal.getUUID());
        crystal = null;
        stand = null;
        flight = null;
        c.archery.lowerBow();
        c.travel().reset();
        c.getNavigation().stop();
    }

    private void say(String line) {
        said.add(line);
        if (said.size() > LINES_KEPT) said.removeFirst();
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(Component.literal(c.getName().getString() + ": " + line));
    }

    void stop() {
        c.archery.lowerBow();
        c.travel().reset();
        c.getNavigation().stop();
        flight = null;
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
