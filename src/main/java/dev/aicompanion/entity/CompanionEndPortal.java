package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

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
 */
final class CompanionEndPortal {
    /** How far a frame is looked for: on foot from where the player brought him, not across a stronghold. */
    private static final int NEAR = 32, NEAR_Y = 16;
    /** A ring is five by five: the frames of the one he fills are no farther than this from the nearest. */
    private static final int RING = 4;
    private static final int PACE = 12;
    private static final double REACH = 2.8D;

    private final CompanionWork w;
    /** The frames of the ring, with an eye or without. */
    private List<BlockPos> ring = List.of();
    private BlockPos frame;
    private int put;
    private long nextAt;

    /** Made with the work, before its owner is set: the owner is read when used. */
    CompanionEndPortal(CompanionWork w) { this.w = w; }

    /** null when the work may start, else the answer: why not, or that the portal is open already. */
    CompanionActionResult begin() {
        CompanionEntity c = w.owner;
        ring = List.of(); frame = null; put = 0; nextAt = 0;
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу без загруженного мира.");
        ring = ringNear(level, c.blockPosition());
        if (ring.isEmpty()) return CompanionActionResult.failed("Не вижу рядом рамки портала Края: подведи меня к ней.");
        CompanionActionResult answer = open(level) ? CompanionActionResult.ok("Портал Края уже открыт.")
                // A ring a player set wrong (a frame turned outward, one missing): full of eyes and no portal, no success of his.
                : empty(level).isEmpty() ? CompanionActionResult.failed("Во всех рамках уже есть очи, а портал не открылся: рамка собрана не так.")
                : w.items.countCarried(Items.ENDER_EYE) == 0 ? CompanionActionResult.failed("Нет ока Края: дай мне или скажи сделать.") : null;
        // No work of his begins: the ring is not kept, or the next portal built for the Nether would be taken for this one (active()).
        if (answer != null) ring = List.of();
        return answer;
    }

    /** The work in hand is his (CompanionWork tells it from the Nether portal's build by this). */
    boolean active() { return !ring.isEmpty(); }

    void cancel() { ring = List.of(); frame = null; }

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

    void tick(ServerLevel level) {
        CompanionEntity c = w.owner;
        List<BlockPos> left = empty(level);
        if (left.isEmpty() || w.items.countCarried(Items.ENDER_EYE) == 0) { finish(level, left.size()); return; }
        // The last eye opens the portal under whoever stands in the ring: a player there is not sent to the End by his hand.
        if (left.size() == 1 && level.players().stream().anyMatch(player -> !outside(player.blockPosition()) && Math.abs(player.getBlockY() - left.getFirst().getY()) <= 2)) {
            w.finish(false, (put > 0 ? "Вставил " + eyes(put) + ", осталось" : "Осталось") + " последнее око. Выйди из кольца портала и скажи ещё раз: портал откроется прямо под тобой.");
            return;
        }
        if (frame == null || !left.contains(frame)) frame = left.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(c.blockPosition()))).orElseThrow();
        if (outside(c.blockPosition()) && c.placement.reaches(frame, Blocks.END_PORTAL_FRAME)) { put(level); return; }
        // In the air no way is searched (as to a bench); in water he goes on.
        if (!c.onGround() && !c.isInWater()) return;
        if (!frame.equals(w.heading)) { w.heading = frame; w.resetApproach(); }
        c.movementIntent(frame);
        if (w.approach(level, frame, REACH, this::outside)) { c.workStatus("Иду к рамке портала Края."); return; }
        w.finish(false, "Не смог подойти к рамке портала Края на " + frame.toShortString() + "." + (put > 0 ? " Вставил " + eyes(put) + ", пустых рамок ещё " + left.size() + "." : ""));
    }

    /** An eye of his into the frame, as EnderEyeItem.useOn puts a player's: the frame's state, the sound, and the portal when the ring is whole. */
    private void put(ServerLevel level) {
        CompanionEntity c = w.owner;
        c.getLookControl().setLookAt(frame.getX() + .5, frame.getY() + .8, frame.getZ() + .5);
        if (level.getGameTime() < nextAt) return;
        nextAt = level.getGameTime() + PACE;
        if (!c.getMainHandItem().is(Items.ENDER_EYE)) c.equipForWork("minecraft:ender_eye");
        ItemStack held = c.getMainHandItem();
        if (!held.is(Items.ENDER_EYE)) { w.finish(false, "Не могу взять око в руку: в сумке нет места." + (put > 0 ? " Вставил " + eyes(put) + "." : "")); return; }
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
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "end_portal_eye", "entity", c.getUUID(), "frame", frame, "put", put, "opened", whole != null);
        c.workStatus("Вставляю очи в рамки портала Края: " + put + ".");
        frame = null;
    }

    private void finish(ServerLevel level, int left) {
        CompanionEntity c = w.owner;
        boolean opened = open(level);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "end_portal_done", "entity", c.getUUID(), "put", put, "empty", left, "opened", opened);
        String did = put > 0 ? "Вставил " + eyes(put) : "Очей не вставил";
        if (opened) w.finish(true, did + ". Портал Края открыт.");
        else if (left > 0) w.finish(false, did + ", не хватает ещё " + left + " — дай мне или скажи сделать.");
        else w.finish(false, did + ", но портал не открылся: рамка собрана не так.");
    }

    private static String eyes(int count) {
        int tens = count % 100, ones = count % 10;
        return count + (tens >= 11 && tens <= 14 ? " очей" : ones == 1 ? " око" : ones >= 2 && ones <= 4 ? " ока" : " очей");
    }
}
