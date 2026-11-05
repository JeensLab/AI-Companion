package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The speedrunners' water bucket: falling to a landing that would hurt, with a water bucket in his bag, he pours it into the cell he
 * is about to land in, in the last ticks before he does (poured water starts to run five ticks later), and scoops it back the moment
 * he is down. The game's own bucket pours and scoops, sounds and all, and the water stops the fall as it does a player's
 * (LivingEntity.checkFallDamage looks for water before it counts the fall). Not in the Nether, where water boils away.
 */
final class CompanionWaterBucket {
    /** The least damage a landing must do him to be worth the bucket, unless it kills him. */
    private static final int WORTH = 4;
    /** How many ticks ahead his landing is looked for. */
    private static final int AHEAD = 2;
    /** How far he reaches to scoop the water back: a player's reach. */
    private static final double REACH = 4.5;
    /** He says what the bucket did at most this often (ticks): a dragon may fling him again and again (the owner, 05.11). */
    private static final int TALK = 1200;
    /** How long his water may stand before he gives it up (ticks): 30 seconds (the owner, 05.11). */
    private static final int FETCH = 600;
    /** Farther than this from his water he does not go back for it (blocks). */
    private static final int FAR = 48;
    /** How often the way to his water is looked for again (ticks), and how many looks without a way he takes. */
    private static final int AGAIN = 20, LOOKS = 3;
    static final String FETCHING = "Сбегаю за водой — ведро пустое.";
    static final String LEFT = "Воду не забрал, ведро осталось пустым.";
    static final List<String> SAVED = List.of("Фух, ведро выручило.", "Выучил спидранерскую фишку — крутая, да?",
            "Ведро воды — и ни царапины.", "Как у спидранеров, видал?", "Вот это я понимаю — приземление.");

    private final CompanionEntity c;
    /** Where his water stands, or null. */
    private BlockPos poured;
    /** He has come down since he poured it. */
    private boolean down;
    private long saidAt = Long.MIN_VALUE / 2;
    private long pouredAt;
    /** He has said he goes back for this water. */
    private boolean told;
    private int noWay;
    private long lookedAt;

    CompanionWaterBucket(CompanionEntity c) { this.c = c; }

    private record Landing(AABB box, float fall) {}

    /** Every server tick, whatever he does: no order or «стоп» holds a reflex back. */
    void tick() {
        if (!(c.level() instanceof ServerLevel level) || !c.isAlive()) return;
        if (poured != null) { settle(level); return; }
        if (c.onGround() || c.isInWater() || c.isPassenger() || c.isFallFlying() || c.getDeltaMovement().y >= 0
                || level.dimensionType().ultraWarm()) return;
        int slot = waterBucket();
        if (slot < 0) return;
        Landing landing = landing(level);
        if (landing == null) return;
        int damage = c.fallDamage(landing.fall);
        if (!worth(damage)) return;
        BlockPos cell = cellFor(level, landing.box, damage >= c.getHealth());
        if (cell != null) pour(level, slot, cell);
    }

    /** Two hearts and more, or his death, are worth the bucket; a step's knock is not (the owner, 05.11). */
    private boolean worth(int damage) { return damage >= WORTH || damage >= c.getHealth(); }

    private int waterBucket() {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.WATER_BUCKET)) return slot;
        return -1;
    }

    /**
     * Where he lands within the next ticks, moved as the game moves him through the air (gravity, the air's drag), with the fall he
     * will have by then; null if not so soon, or into a liquid.
     */
    private Landing landing(ServerLevel level) {
        Vec3 motion = c.getDeltaMovement();
        AABB box = c.getBoundingBox();
        float fall = c.fallDistance;
        for (int tick = 0; tick < AHEAD; tick++) {
            Vec3 moved = Entity.collideBoundingBox(c, motion, box, level, List.of());
            box = box.move(moved);
            if (moved.y < 0) fall -= (float) moved.y;
            if (level.containsAnyLiquid(box)) return null;
            if (moved.y > motion.y + 1.0E-7) return new Landing(box, fall);
            motion = new Vec3(motion.x * .91, (motion.y - c.getGravity()) * .98, motion.z * .91);
        }
        return null;
    }

    /**
     * The cell to pour into: the lowest about his body as he lands that takes water without harm, empty or a block that holds water (a
     * slab, a fence). Water anywhere about his body stops the fall (Entity.updateFluidHeightAndDoFluidPushing), so over ripe wheat it
     * goes about his chest. A block the water would wash away (a crop, a torch, grass) only if the fall would kill him (the owner,
     * 05.11); null if none.
     */
    private static BlockPos cellFor(ServerLevel level, AABB box, boolean deadly) {
        BlockPos washable = null;
        for (int y = Mth.floor(box.minY + 1.0E-3); y < box.maxY; y++) {
            BlockPos cell = BlockPos.containing(box.getCenter().x, y, box.getCenter().z);
            BlockState state = level.getBlockState(cell);
            if (state.isAir() || state.getBlock() instanceof LiquidBlockContainer holder && holder.canPlaceLiquid(null, level, cell, state, Fluids.WATER))
                return cell;
            if (washable == null && state.getFluidState().isEmpty() && state.canBeReplaced(Fluids.WATER)) washable = cell;
        }
        return deadly ? washable : null;
    }

    private void pour(ServerLevel level, int slot, BlockPos cell) {
        ItemStack bucket = c.getItem(slot);
        if (!((BucketItem) bucket.getItem()).emptyContents(null, level, cell, null, bucket)) return;
        c.setItem(slot, new ItemStack(Items.BUCKET));
        c.animateAction(1, cell, bucket);
        poured = cell; down = false; told = false; pouredAt = level.getGameTime();
        event("water_bucket_poured");
    }

    private void settle(ServerLevel level) {
        if (!down && (c.onGround() || c.isInWater())) landed(level);
        if (!level.getFluidState(poured).isSourceOfType(Fluids.WATER)) leave("gone");
        else if (down && reachable()) { if (!scoop(level)) leave("no_bucket"); }
        else if (level.getGameTime() - pouredAt > FETCH) leave("late");
    }

    private void landed(ServerLevel level) {
        down = true;
        event("water_bucket_landed");
        long now = level.getGameTime();
        if (c.isInWater() && now - saidAt >= TALK) {
            saidAt = now;
            c.autonomySay(SAVED.get(c.getRandom().nextInt(SAVED.size())), false);
        }
    }

    /** Within his reach and in his sight, as a player's bucket reaches: not through a wall. */
    private boolean reachable() {
        Vec3 eyes = c.getEyePosition(), water = Vec3.atCenterOf(poured);
        if (eyes.distanceToSqr(water) > REACH * REACH) return false;
        BlockHitResult hit = c.level().clip(new ClipContext(eyes, water, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(poured);
    }

    /** Back into a bucket of his: any empty one, with a free cell if it lies in a stack. False if he has none. */
    private boolean scoop(ServerLevel level) {
        BlockPos cell = poured;
        BlockState state = level.getBlockState(cell);
        if (!(state.getBlock() instanceof BucketPickup pickup)) return false;
        int empty = -1, free = -1;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (stack.is(Items.BUCKET) && (empty < 0 || stack.getCount() < c.getItem(empty).getCount())) empty = slot;
            else if (stack.isEmpty() && free < 0) free = slot;
        }
        if (empty < 0 || c.getItem(empty).getCount() > 1 && free < 0) return false;
        ItemStack filled = pickup.pickupBlock(null, level, cell, state);
        if (filled.isEmpty()) return false;
        if (c.getItem(empty).getCount() == 1) c.setItem(empty, filled);
        else { c.removeItem(empty, 1); c.setItem(free, filled); }
        pickup.getPickupSound(state).ifPresent(sound -> level.playSound(null, cell, sound, SoundSource.BLOCKS, 1.0F, 1.0F));
        level.gameEvent(c, GameEvent.FLUID_PICKUP, cell);
        c.animateAction(1, cell, filled);
        event("water_bucket_scooped");
        poured = null;
        return true;
    }

    /** He gives his water up: it stays where it is, his bucket stays empty, and he says so (the owner, 05.11). */
    private void leave(String why) {
        event("water_bucket_left", "why", why);
        poured = null;
        c.autonomySay(LEFT, false);
    }

    /** He is down out of reach of his water: he goes back for it. */
    boolean astray() { return poured != null && down && !reachable(); }

    void fetchBegin() {
        noWay = 0; lookedAt = Long.MIN_VALUE / 2;
        c.getNavigation().stop();
        if (told) return;
        told = true;
        c.autonomySay(FETCHING, c.hasActiveWorkOrder());
        event("water_bucket_fetch");
    }

    /** A way to his water when the last one is walked; three looks without one, or his water too far, and he gives it up. */
    void fetchTick() {
        if (poured == null) return;
        if (c.distanceToSqr(Vec3.atCenterOf(poured)) > FAR * FAR) { leave("far"); return; }
        long now = c.level().getGameTime();
        if (!c.getNavigation().isDone() && !c.getNavigation().isStuck() || now - lookedAt < AGAIN) return;
        lookedAt = now;
        int busy = c.work.route.busyAnswers();
        Path path = c.work.route.toCells(Set.of(poured), 2);
        if (path != null) { noWay = 0; c.work.route.walk(path); }
        else if (c.work.route.busyAnswers() == busy && ++noWay >= LOOKS) leave("no_way");
    }

    /**
     * Back to his water standing out of his reach, the work waiting meanwhile (WorkGoal pauses it): the next fling would find his
     * bucket empty (the owner, 05.11). Beside the self-rescue in rank, under his air.
     */
    static final class FetchGoal extends Goal {
        private final CompanionEntity c;
        FetchGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.waterBucket.astray(); }
        @Override public boolean canContinueToUse() { return c.waterBucket.poured != null; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.waterBucket.fetchBegin(); }
        @Override public void tick() { c.waterBucket.fetchTick(); }
        @Override public void stop() { c.getNavigation().stop(); }
    }

    /** The journal's line: where he is, where his water stands, his health and fall, the ticks since he poured it. */
    private void event(String name, Object... more) {
        Object[] fields = {"at", c.blockPosition().toShortString(), "water", poured == null ? "none" : poured.toShortString(),
                "health", c.getHealth(), "fall", c.fallDistance, "ticks", c.level().getGameTime() - pouredAt};
        Object[] all = java.util.Arrays.copyOf(fields, fields.length + more.length);
        System.arraycopy(more, 0, all, fields.length, more.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}
