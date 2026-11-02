package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * After the player through a portal, on foot and never by teleport: when he leaves this world by a Nether or an End portal, a companion
 * who follows him walks to that portal and steps in, and the game takes him over as any mob. A player's order in hand goes first, «стой»
 * holds; what he says is sent to the player, who is no longer near enough to hear him. Not saved: a world closed on the way leaves him
 * where he stood, for /companion summon. The portal he came by is no part of it: the game sets him beside the player's way out.
 * «Зайди в портал» sends him the same way into the nearest Nether portal, with the player staying where he is.
 */
final class CompanionPortals {
    /** The player's last cell here, and how far from it the portal he took may be: the End portal takes him a little above its plane. */
    private static final int PORTAL_SEEN_WITHIN = 3;
    /** Farther off he stays; the walk is given this long, then he stays and says so. */
    private static final int WALKS_FROM = 64, PATIENCE_TICKS = 20 * 60, RETRY_TICKS = 20;
    /** From this close to the place before the portal he walks straight in, past the end of any route: a portal is no place a route stands on. */
    private static final double STEP_IN_FROM = 2.5, BEFORE_THE_PLANE = 1.5;

    private final CompanionEntity c;
    private BlockPos playerSeen, portal, entry;
    private long giveUpAt, retryAt;
    /** Sent by the player's order: the walk goes on with the player still here. */
    private boolean ordered;

    CompanionPortals(CompanionEntity c) { this.c = c; }

    boolean walking() { return portal != null; }

    /** Any later order of the player ends the walk. */
    void stop() { portal = null; ordered = false; }

    /** The player's order: the nearest Nether portal, whoever lit it. What he works on ends here, as on «иди за мной». */
    CompanionActionResult enter(ServerLevel level) {
        var found = level.getPoiManager().findClosest(type -> type.is(PoiTypes.NETHER_PORTAL),
                pos -> level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.NETHER_PORTAL), c.blockPosition(), WALKS_FROM, PoiManager.Occupancy.ANY);
        if (found.isEmpty()) return CompanionActionResult.failed("Рядом нет портала в Незер.");
        c.setFollowing(true);
        // Fresh from a crossing the game keeps him from the portal for as long as he stands in it; an order is no bounce.
        c.setPortalCooldown(0);
        walkTo(level, foot(level, found.get()));
        ordered = true;
        return CompanionActionResult.ok("Иду к порталу.");
    }

    private void walkTo(ServerLevel level, BlockPos taken) {
        portal = taken;
        entry = entry(level, taken);
        giveUpAt = level.getGameTime() + PATIENCE_TICKS;
        retryAt = 0;
    }

    void tick(ServerLevel level) {
        ServerPlayer owner = c.ownerId == null ? null : level.getServer().getPlayerList().getPlayer(c.ownerId);
        boolean playerHere = owner != null && owner.level() == level;
        if (owner == null || playerHere) {
            playerSeen = owner == null ? null : owner.blockPosition();
            if (owner == null || !ordered) stop();
        }
        if (portal != null) {
            // An empty world stops ticking its entities after fifteen seconds; his leased chunks do not count as a player there.
            level.resetEmptyTime();
            if (level.getGameTime() >= giveUpAt || !isPortal(level.getBlockState(portal))) giveUp(owner);
            return;
        }
        if (playerHere || owner == null || playerSeen == null) return;
        BlockPos taken = portalNear(level, playerSeen);
        playerSeen = null;
        // Dead, or sent by a command: there is no way to walk after him.
        if (taken == null || c.autonomy.staysAtWork() || !c.following && !c.protecting) return;
        if (c.busyForOrders()) { tell(owner, "Закончу начатое и останусь здесь. Позови через /companion summon."); return; }
        if (c.blockPosition().distSqr(taken) > WALKS_FROM * WALKS_FROM) { tell(owner, "Я слишком далеко от портала. Позови через /companion summon."); return; }
        // As on «иди за мной»: what he did here stays here, a long goal waits for «продолжай».
        c.goals().playerOrder("follow"); c.autonomy().playerOrder("follow"); CompanionAgenda.playerOrder(owner, c, "follow");
        walkTo(level, taken);
        tell(owner, "Иду за тобой через портал.");
    }

    private void giveUp(ServerPlayer owner) {
        tell(owner, ordered ? "Не смог дойти до портала." : "Не смог дойти до портала. Позови через /companion summon.");
        stop();
    }

    private void tell(ServerPlayer owner, String text) { owner.sendSystemMessage(Component.literal(c.getName().getString() + ": " + text)); }

    private static boolean isPortal(BlockState state) { return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL); }

    /** The nearest portal block round the place, at the foot of its column: the cell he can step into. */
    private static BlockPos portalNear(ServerLevel level, BlockPos seen) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(seen.offset(-PORTAL_SEEN_WITHIN, -PORTAL_SEEN_WITHIN, -PORTAL_SEEN_WITHIN), seen.offset(PORTAL_SEEN_WITHIN, PORTAL_SEEN_WITHIN, PORTAL_SEEN_WITHIN))) {
            if (!level.hasChunkAt(pos) || !isPortal(level.getBlockState(pos)) || pos.distSqr(seen) >= distance) continue;
            distance = pos.distSqr(seen);
            best = pos.immutable();
        }
        return best == null ? null : foot(level, best);
    }

    private static BlockPos foot(ServerLevel level, BlockPos pos) {
        BlockPos foot = pos;
        while (level.getBlockState(foot.below()).is(Blocks.NETHER_PORTAL)) foot = foot.below();
        return foot;
    }

    /** A Nether portal is entered from before or behind its plane, not past the end of its frame; an End portal from anywhere round its rim. */
    private BlockPos entry(ServerLevel level, BlockPos taken) {
        BlockState state = level.getBlockState(taken);
        if (!state.is(Blocks.NETHER_PORTAL)) return taken;
        Direction front = state.getValue(NetherPortalBlock.AXIS) == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
        BlockPos one = taken.relative(front), other = taken.relative(front.getOpposite());
        boolean oneFree = CompanionMiningNavigation.standable(c, one), otherFree = CompanionMiningNavigation.standable(c, other);
        if (oneFree != otherFree) return oneFree ? one : other;
        return c.blockPosition().distSqr(one) <= c.blockPosition().distSqr(other) ? one : other;
    }

    private void walk() {
        // The selector checks canContinueToUse every other tick only: on the tick between, a stopped walk still comes here.
        if (portal == null) return;
        Vec3 inside = Vec3.atBottomCenterOf(portal), before = Vec3.atBottomCenterOf(entry), here = c.position();
        double near = entry.equals(portal) ? STEP_IN_FROM : BEFORE_THE_PLANE;
        if (Math.hypot(before.x - here.x, before.z - here.z) <= near && Math.abs(before.y - here.y) <= STEP_IN_FROM) {
            c.getNavigation().stop();
            c.getMoveControl().setWantedPosition(inside.x, inside.y, inside.z, 1D);
            if (c.horizontalCollision && c.onGround()) c.getJumpControl().jump();
            return;
        }
        if (c.level().getGameTime() < retryAt || !c.onGround() && !c.isInWater()) return;
        if (c.work.route.follow(entry, 1.5, true) != CompanionTravel.Step.WALKING) {
            c.travel().reset();
            retryAt = c.level().getGameTime() + RETRY_TICKS;
        }
    }

    /** Above following, which has no player to go to here; a fight or a rescue comes first and the walk goes on after it. */
    static final class FollowThroughPortalGoal extends Goal {
        final CompanionEntity c;
        FollowThroughPortalGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.portals.walking() && c.inventoryViewers == 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { c.portals.walk(); }
        @Override public void stop() { c.travel().reset(); c.getNavigation().stop(); }
    }
}
