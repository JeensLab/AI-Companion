package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * open_end_portal («открой портал Края», «вставь очи»): the empty frames of the End portal near him get eyes of ender of his
 * own, one by one, as a player puts them in (EnderEyeItem.useOn); with the last the game's own pattern opens the portal. He
 * walks to the ring and round it on foot and digs no wall for it; he stands outside the ring, never on a frame or in it: a
 * stronghold's frames hang over lava, and the portal that opens in the ring would take him to the End. Short of eyes he puts
 * in what he has and says how many are missing; the next order goes on with the empty frames. Nothing of it is saved: the
 * frames keep their eyes.
 * <p>
 * A spawner by the ring (a stronghold's stands on the top step before it) is broken first, with his best pickaxe or by hand: as
 * long as a player is near it lets out silverfish, and a hit one wakes the infested blocks round it, the pool's border among them
 * (CompanionCombat.fairTarget). Lava out on the floor all the same, he stands on no cell with lava in it or beside it.
 */
final class CompanionEndPortal {
    /** How far a frame is looked for: on foot from where the player brought him, not across a stronghold. */
    private static final int NEAR = 32, NEAR_Y = 16;
    /** A ring is five by five: the frames of the one he fills are no farther than this from the nearest. */
    private static final int RING = 4;
    private static final int PACE = 12;
    private static final double REACH = 2.8D;
    /** How far from the ring a spawner is broken first: a stronghold's is two blocks off the frames. */
    private static final int SPAWNER_NEAR = 10, SPAWNER_NEAR_Y = 5;

    private final CompanionWork w;
    /** The frames of the ring, with an eye or without. */
    private List<BlockPos> ring = List.of();
    private BlockPos frame;
    private int put;
    private long nextAt;
    /** The spawner to break before the first eye; null once broken or left. */
    private BlockPos spawner;
    private boolean broke, missed;
    /** A cell to stand on by the frame he goes to was refused for lava in it or beside it; so it was by a frame he could not come to. */
    private boolean wet, byLava;
    /** Frames he could not come to: the others are filled before he says so. */
    private final List<BlockPos> unreached = new ArrayList<>();

    /** Made with the work, before its owner is set: the owner is read when used. */
    CompanionEndPortal(CompanionWork w) { this.w = w; }

    /** null when the work may start, else the answer: why not, or that the portal is open already. */
    CompanionActionResult begin() {
        CompanionEntity c = w.owner;
        ring = List.of(); frame = null; put = 0; nextAt = 0; spawner = null; broke = false; missed = false; wet = false; byLava = false;
        unreached.clear();
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу без загруженного мира.");
        ring = ringNear(level, c.blockPosition());
        if (ring.isEmpty()) return CompanionActionResult.failed("Не вижу рядом рамки портала Края: подведи меня к ней.");
        CompanionActionResult answer = open(level) ? CompanionActionResult.ok("Портал Края уже открыт.")
                // A ring a player set wrong (a frame turned outward, one missing): full of eyes and no portal, no success of his.
                : empty(level).isEmpty() ? CompanionActionResult.failed("Во всех рамках уже есть очи, а портал не открылся: рамка собрана не так.")
                : w.items.countCarried(Items.ENDER_EYE) == 0 ? CompanionActionResult.failed("Нет ока Края: дай мне или скажи сделать.") : null;
        // No work of his begins: the ring is not kept, or the next portal built for the Nether would be taken for this one (active()).
        if (answer != null) ring = List.of();
        else spawner = spawnerNear(level);
        return answer;
    }

    /** The work in hand is his (CompanionWork tells it from the Nether portal's build by this). */
    boolean active() { return !ring.isEmpty(); }

    void cancel() { ring = List.of(); frame = null; spawner = null; }

    String jobName() { return "очи в портал Края"; }

    /** An End portal's block near him: «зайди в портал» is answered about the End then (CompanionPortals). */
    static boolean openNear(ServerLevel level, BlockPos from, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-radius, -radius, -radius), from.offset(radius, radius, radius)))
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.END_PORTAL)) return true;
        return false;
    }

    /** The frames of the ring whose frame is nearest to him: those at its height within a ring's width of it. */
    private static List<BlockPos> ringNear(ServerLevel level, BlockPos from) {
        List<BlockPos> frames = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-NEAR, -NEAR_Y, -NEAR), from.offset(NEAR, NEAR_Y, NEAR)))
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) frames.add(pos.immutable());
        BlockPos nearest = frames.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(from))).orElse(null);
        if (nearest == null) return List.of();
        frames.removeIf(pos -> pos.getY() != nearest.getY() || Math.abs(pos.getX() - nearest.getX()) > RING || Math.abs(pos.getZ() - nearest.getZ()) > RING);
        return frames;
    }

    /** The spawner nearest to the ring within reach of it, if any. */
    private BlockPos spawnerNear(ServerLevel level) {
        int y = ring.getFirst().getY();
        Vec3 middle = new Vec3((minX() + maxX()) / 2D + .5, y, (minZ() + maxZ()) / 2D + .5);
        BlockPos nearest = null;
        for (BlockPos pos : BlockPos.betweenClosed(minX() - SPAWNER_NEAR, y - SPAWNER_NEAR_Y, minZ() - SPAWNER_NEAR, maxX() + SPAWNER_NEAR, y + SPAWNER_NEAR_Y, maxZ() + SPAWNER_NEAR))
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.SPAWNER) && (nearest == null || pos.getCenter().distanceToSqr(middle) < nearest.getCenter().distanceToSqr(middle))) nearest = pos.immutable();
        return nearest;
    }

    private List<BlockPos> empty(ServerLevel level) {
        return ring.stream().filter(pos -> { BlockState state = level.getBlockState(pos); return state.is(Blocks.END_PORTAL_FRAME) && !state.getValue(EndPortalFrameBlock.HAS_EYE); }).toList();
    }

    private boolean open(ServerLevel level) {
        int y = ring.getFirst().getY();
        for (BlockPos pos : BlockPos.betweenClosed(minX(), y, minZ(), maxX(), y, maxZ())) if (level.getBlockState(pos).is(Blocks.END_PORTAL)) return true;
        return false;
    }

    private int minX() { return ring.stream().mapToInt(BlockPos::getX).min().orElse(0); }
    private int maxX() { return ring.stream().mapToInt(BlockPos::getX).max().orElse(0); }
    private int minZ() { return ring.stream().mapToInt(BlockPos::getZ).min().orElse(0); }
    private int maxZ() { return ring.stream().mapToInt(BlockPos::getZ).max().orElse(0); }

    /** Not over the ring nor under it: no frame's top, no corner by the lava, no cell where the portal opens. */
    private boolean outside(BlockPos feet) {
        return feet.getX() < minX() || feet.getX() > maxX() || feet.getZ() < minZ() || feet.getZ() > maxZ();
    }

    /** No lava in the cell nor beside it: lava let out of the pool's border runs over the floor by the ring. */
    private static boolean dry(ServerLevel level, BlockPos feet) {
        if (CompanionLava.at(level, feet)) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) if (CompanionLava.at(level, feet.relative(side))) return false;
        return true;
    }

    /** Where he may stand: outside the ring and dry; a cell refused for lava is remembered for what he says. */
    private boolean stands(ServerLevel level, BlockPos feet) {
        if (!outside(feet)) return false;
        if (dry(level, feet)) return true;
        wet = true;
        return false;
    }

    void tick(ServerLevel level) {
        CompanionEntity c = w.owner;
        if (spawner != null && !level.getBlockState(spawner).is(Blocks.SPAWNER)) spawner = null;
        if (spawner != null) { breakSpawner(level); return; }
        List<BlockPos> left = empty(level);
        if (left.isEmpty() || w.items.countCarried(Items.ENDER_EYE) == 0) { finish(level, left.size()); return; }
        // The last eye opens the portal under whoever stands in the ring: a player there is not sent to the End by his hand.
        if (left.size() == 1 && level.players().stream().anyMatch(player -> !outside(player.blockPosition()) && Math.abs(player.getBlockY() - left.getFirst().getY()) <= 2)) {
            end(false, (put > 0 ? "Вставил " + eyes(put) + ", осталось" : "Осталось") + " последнее око. Выйди из кольца портала и скажи ещё раз: портал откроется прямо под тобой.");
            return;
        }
        List<BlockPos> open = left.stream().filter(pos -> !unreached.contains(pos)).toList();
        if (open.isEmpty()) {
            String did = put > 0 ? " Вставил " + eyes(put) + ", пустых рамок ещё " + left.size() + "." : "";
            end(false, (byLava ? "У рамки лава — не подойти." : "Не смог подойти к рамке портала Края на " + unreached.getFirst().toShortString() + ".") + did);
            return;
        }
        if (frame == null || !open.contains(frame)) frame = open.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(c.blockPosition()))).orElseThrow();
        if (outside(c.blockPosition()) && dry(level, c.blockPosition()) && c.placement.reaches(frame, Blocks.END_PORTAL_FRAME)) { put(level); return; }
        // In the air no way is searched (as to a bench); in water he goes on.
        if (!c.onGround() && !c.isInWater()) return;
        if (!frame.equals(w.heading)) { w.heading = frame; w.resetApproach(); wet = false; }
        c.movementIntent(frame);
        if (w.approach(level, frame, REACH, feet -> stands(level, feet))) { c.workStatus("Иду к рамке портала Края."); return; }
        // Not come to: the frames left are tried before he says so.
        unreached.add(frame);
        byLava |= wet;
        frame = null;
    }

    /** The spawner, broken as he breaks a block (CompanionBlockBreaking): the swings, the cracks, a player's time; what it drops is experience. */
    private void breakSpawner(ServerLevel level) {
        CompanionEntity c = w.owner;
        BlockPos feet = c.blockPosition();
        if (outside(feet) && dry(level, feet) && c.placement.reaches(spawner, Blocks.SPAWNER)) {
            BlockState state = level.getBlockState(spawner);
            c.takeToolForBlock(state);
            c.workStatus("Ломаю спаунер.");
            if (!w.breaking.tick(level, spawner)) return;
            ItemStack tool = c.getMainHandItem();
            int experience = state.getExpDrop(level, spawner, null, c, tool);
            if (level.destroyBlock(spawner, false, c, 512)) {
                CompanionTools.wearOnBreak(tool, level, state, spawner, c);
                ExperienceOrb.award(level, Vec3.atCenterOf(spawner), experience);
                broke = true;
                dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "end_portal_spawner", "entity", c.getUUID(), "spawner", spawner, "broken", true);
            }
            c.putToolAway();
            w.breaking.reset();
            spawner = null;
            return;
        }
        if (!c.onGround() && !c.isInWater()) return;
        if (!spawner.equals(w.heading)) { w.heading = spawner; w.resetApproach(); }
        c.movementIntent(spawner);
        if (w.approach(level, spawner, REACH, cell -> stands(level, cell))) { c.workStatus("Иду к спаунеру."); return; }
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "end_portal_spawner", "entity", c.getUUID(), "spawner", spawner, "broken", false);
        missed = true;
        spawner = null;
        w.breaking.reset();
    }

    /** What he did of the spawner goes first in what he says at the end. */
    private void end(boolean done, String text) {
        w.finish(done, (broke ? "Сломал спаунер. " : missed ? "К спаунеру не подойти — оставил. " : "") + text);
    }

    /** An eye of his into the frame, as EnderEyeItem.useOn puts a player's: the frame's state, the sound, and the portal when the ring is whole. */
    private void put(ServerLevel level) {
        CompanionEntity c = w.owner;
        c.getLookControl().setLookAt(frame.getX() + .5, frame.getY() + .8, frame.getZ() + .5);
        if (level.getGameTime() < nextAt) return;
        nextAt = level.getGameTime() + PACE;
        if (!c.getMainHandItem().is(Items.ENDER_EYE)) c.equipForWork("minecraft:ender_eye");
        ItemStack held = c.getMainHandItem();
        if (!held.is(Items.ENDER_EYE)) { end(false, "Не могу взять око в руку: в сумке нет места." + (put > 0 ? " Вставил " + eyes(put) + "." : "")); return; }
        BlockState state = level.getBlockState(frame), filled = state.setValue(EndPortalFrameBlock.HAS_EYE, true);
        ItemStack shown = held.copyWithCount(1);
        Block.pushEntitiesUp(state, filled, level, frame);
        level.setBlock(frame, filled, 2);
        level.updateNeighbourForOutputSignal(frame, Blocks.END_PORTAL_FRAME);
        held.shrink(1);
        level.levelEvent(1503, frame, 0);
        var whole = EndPortalFrameBlock.getOrCreatePortalShape().find(level, frame);
        if (whole != null) {
            BlockPos corner = whole.getFrontTopLeft().offset(-3, 0, -3);
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) level.setBlock(corner.offset(x, 0, z), Blocks.END_PORTAL.defaultBlockState(), 2);
            level.globalLevelEvent(1038, corner.offset(1, 0, 1), 0);
        }
        c.animateAction(1, frame, shown);
        c.setChanged();
        c.refreshInventorySummary();
        put++;
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "end_portal_eye", "entity", c.getUUID(), "frame", frame, "at", c.blockPosition(), "put", put, "opened", whole != null);
        c.workStatus("Вставляю очи в рамки портала Края: " + put + ".");
        frame = null;
    }

    private void finish(ServerLevel level, int left) {
        CompanionEntity c = w.owner;
        boolean opened = open(level);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "end_portal_done", "entity", c.getUUID(), "put", put, "empty", left, "opened", opened);
        String did = put > 0 ? "Вставил " + eyes(put) : "Очей не вставил";
        if (opened) end(true, did + ". Портал Края открыт.");
        else if (left > 0) end(false, did + ", не хватает ещё " + left + " — дай мне или скажи сделать.");
        else end(false, did + ", но портал не открылся: рамка собрана не так.");
    }

    private static String eyes(int count) {
        int tens = count % 100, ones = count % 10;
        return count + (tens >= 11 && tens <= 14 ? " очей" : ones == 1 ? " око" : ones >= 2 && ones <= 4 ? " ока" : " очей");
    }
}
