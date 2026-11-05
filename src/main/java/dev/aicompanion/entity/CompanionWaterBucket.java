package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The speedrunners' water bucket: falling to a landing that would hurt, with a water bucket in his bag, he pours it about his body in
 * the last ticks before he lands (poured water starts to run five ticks later), and scoops it back the moment he is down. The game's
 * own bucket pours and scoops, sounds and all, and the water stops the fall as it does a player's (LivingEntity.checkFallDamage looks
 * for water before it counts the fall). Not in the Nether, where water boils away.
 */
final class CompanionWaterBucket {
    /** The least health a landing must cost him to be worth the bucket, unless it kills him (decided 05.11: two hearts). */
    private static final int WORTH = 4;
    /** How many ticks ahead his landing is looked for. */
    private static final int AHEAD = 2;
    /** A player's reach for a block: the block interaction range. */
    private static final double REACH = 4.5;
    /** He says what the bucket did at most this often (ticks): a dragon may fling him again and again (decided 05.11). */
    private static final int TALK = 1200;
    /** How long his water may stand before he gives it up (ticks): 30 seconds (decided 05.11). */
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
    /** Water he poured and has not scooped back, oldest first: flung again before he scooped it, he may pour a second bucket. */
    private final List<Pool> pools = new ArrayList<>();
    private long saidAt = Long.MIN_VALUE / 2;
    /** The water he is going back for, and his looks for a way to it. */
    private Pool fetching;
    private int noWay;
    private long lookedAt;

    CompanionWaterBucket(CompanionEntity c) { this.c = c; }

    /** Water of his: where, when he poured it, whether he has come down since, whether he has said he goes back for it. */
    private static final class Pool {
        final BlockPos cell;
        final long at;
        boolean down, told;
        Pool(BlockPos cell, long at) { this.cell = cell; this.at = at; }
    }

    /** His landing: his box as he touches down, the fall counted by then, the block he lands on. */
    private record Landing(AABB box, float fall, BlockPos on) {}

    /** Every server tick, whatever he does: no order or «стоп» holds a reflex back. */
    void tick() {
        if (!(c.level() instanceof ServerLevel level) || !c.isAlive()) return;
        settle(level);
        if (c.onGround() || c.isInWater() || c.isPassenger() || c.isFallFlying() || c.getDeltaMovement().y >= 0
                || level.dimensionType().ultraWarm()) return;
        int slot = waterBucket();
        if (slot < 0) return;
        Landing landing = landing(level);
        if (landing == null) return;
        float loss = loss(level, landing);
        // Two hearts and more, or his death, are worth the bucket; a step's knock is not (decided 05.11).
        if (loss < WORTH && loss < c.getHealth()) return;
        BlockPos cell = cellFor(level, landing.box, loss >= c.getHealth());
        if (cell != null && inReach(level, cell)) pour(level, slot, cell, loss);
    }

    private int waterBucket() {
        for (int slot = 0; slot < c.getContainerSize(); slot++) if (c.getItem(slot).is(Items.WATER_BUCKET)) return slot;
        return -1;
    }

    /**
     * Where he lands within the next ticks, moved as the game moves him through the air (gravity, the air's drag), with the fall he
     * will have by then: the landing move itself is not counted (Entity.checkFallDamage). Null if not so soon, or if the game ends the
     * fall before, as it does: a climbable block at his feet before a move (LivingEntity.onClimbable); along the line of his feet a
     * move of a block or more through water or a fall-resetting block (Entity.move); water about his body after a move; a cobweb, berry
     * bush or powder snow after a move that is not the landing (their entityInside comes after the fall is counted). Lava ends no
     * fall (it halves it a tick later): over lava he pours as anywhere.
     */
    private Landing landing(ServerLevel level) {
        Vec3 motion = c.getDeltaMovement();
        AABB box = c.getBoundingBox();
        float fall = c.fallDistance;
        for (int tick = 0; tick < AHEAD; tick++) {
            if (level.getBlockState(feet(box)).is(BlockTags.CLIMBABLE)) return null;
            Vec3 moved = Entity.collideBoundingBox(c, motion, box, level, List.of());
            Vec3 from = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
            if (fall != 0 && moved.lengthSqr() >= 1 && level.clip(new ClipContext(from, from.add(moved), ClipContext.Block.FALLDAMAGE_RESETTING,
                    ClipContext.Fluid.WATER, c)).getType() != HitResult.Type.MISS) return null;
            AABB next = box.move(moved);
            if (inWater(level, next)) return null;
            if (moved.y > motion.y + 1.0E-7) return new Landing(next, fall, on(level, next));
            if (holds(level, next)) return null;
            fall -= (float) moved.y;
            box = next;
            motion = new Vec3(motion.x * .91, (motion.y - c.getGravity()) * .98, motion.z * .91);
        }
        return null;
    }

    /** Water that counts as him in it: in his box, its surface at his feet or above (Entity.updateFluidHeightAndDoFluidPushing). */
    private static boolean inWater(ServerLevel level, AABB box) {
        AABB inside = box.deflate(.001);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(inside.minX), Mth.floor(inside.minY), Mth.floor(inside.minZ),
                Mth.ceil(inside.maxX) - 1, Mth.ceil(inside.maxY) - 1, Mth.ceil(inside.maxZ) - 1)) {
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && pos.getY() + fluid.getHeight(level, pos) >= inside.minY) return true;
        }
        return false;
    }

    private static BlockPos feet(AABB box) { return BlockPos.containing(box.getCenter().x, box.minY, box.getCenter().z); }

    /** A cobweb or berry bush in his box, or powder snow at his feet: it holds him and so ends the fall (Entity.makeStuckInBlock). */
    private static boolean holds(ServerLevel level, AABB box) {
        if (level.getBlockState(feet(box)).is(Blocks.POWDER_SNOW)) return true;
        AABB inside = box.deflate(1.0E-5);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(inside.minX), Mth.floor(inside.minY), Mth.floor(inside.minZ),
                Mth.floor(inside.maxX), Mth.floor(inside.maxY), Mth.floor(inside.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH)) return true;
        }
        return false;
    }

    /**
     * The block the game counts him to land on (Entity.getOnPosLegacy): a fence or wall he stands on itself, any other supporting
     * block the one 0.2 below his feet in its column (so a carpet on hay counts as the hay).
     */
    private BlockPos on(ServerLevel level, AABB box) {
        BlockPos under = BlockPos.containing(box.getCenter().x, box.minY - .2, box.getCenter().z);
        BlockPos support = level.findSupportingBlock(c, new AABB(box.minX, box.minY - 1.0E-6, box.minZ, box.maxX, box.minY, box.maxZ)).orElse(null);
        if (support == null) return under;
        return level.getBlockState(support).collisionExtendsVertically(level, support, c) ? support : support.atY(under.getY());
    }

    /**
     * The health the landing would cost him, as the game counts it: what the block he lands on makes of the fall (BedBlock, HayBlock,
     * HoneyBlock, SlimeBlock, PowderSnowBlock, PointedDripstoneBlock.fallOn), then Resistance, the protection of his enchantments
     * (Feather Falling) and his absorption (LivingEntity.getDamageAfterMagicAbsorb and actuallyHurt; NeoForge's version of the first
     * may only run in a hit).
     */
    private float loss(ServerLevel level, Landing landing) {
        BlockState on = level.getBlockState(landing.on);
        float fall = landing.fall, times = 1;
        if (on.getBlock() instanceof BedBlock) fall *= .5F;
        else if (on.is(Blocks.HAY_BLOCK) || on.is(Blocks.HONEY_BLOCK)) times = .2F;
        else if (on.is(Blocks.SLIME_BLOCK) || on.is(Blocks.POWDER_SNOW)) times = 0;
        else if (on.is(Blocks.POINTED_DRIPSTONE) && on.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP
                && on.getValue(PointedDripstoneBlock.THICKNESS) == DripstoneThickness.TIP) { fall += 2; times = 2; }
        float damage = c.fallDamage(fall, times);
        var resistance = c.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) damage = Math.max(damage * (25 - (resistance.getAmplifier() + 1) * 5) / 25F, 0);
        float protection = EnchantmentHelper.getDamageProtection(level, c, c.damageSources().fall());
        if (damage > 0 && protection > 0) damage = CombatRules.getDamageAfterMagicAbsorb(damage, protection);
        return Math.max(0, damage - c.getAbsorptionAmount());
    }

    /**
     * The cell to pour into: the lowest about his body as he lands that takes water without harm, empty or a block that holds water (a
     * slab, a fence). Water anywhere about his body stops the fall (Entity.updateFluidHeightAndDoFluidPushing), so over ripe wheat it
     * goes about his chest, as a player's bucket put on the wheat's top does. A block the water would wash away (a crop, a torch,
     * grass) only if the fall would kill him (decided 05.11); null if none.
     */
    private static BlockPos cellFor(ServerLevel level, AABB box, boolean deadly) {
        BlockPos washable = null;
        for (int y = Mth.floor(box.minY + 1.0E-3); y < box.maxY; y++) {
            BlockPos cell = BlockPos.containing(box.getCenter().x, y, box.getCenter().z);
            BlockState state = level.getBlockState(cell);
            if (state.isAir() || holdsWater(level, cell, state)) return cell;
            if (washable == null && state.getFluidState().isEmpty() && state.canBeReplaced(Fluids.WATER)) washable = cell;
        }
        return deadly ? washable : null;
    }

    private static boolean holdsWater(ServerLevel level, BlockPos cell, BlockState state) {
        return state.getBlock() instanceof LiquidBlockContainer holder && holder.canPlaceLiquid(null, level, cell, state, Fluids.WATER);
    }

    /**
     * Whether a player could pour there from his eyes: on the block that holds the water, or on the one under the cell, within his reach
     * of its outline (a bucket goes by Item.getPlayerPOVHitResult, without the server's allowance for useItemOn). Too far yet, he
     * pours a tick later; coming down faster than that, he may not manage it, as a player would not.
     */
    private boolean inReach(ServerLevel level, BlockPos cell) {
        BlockPos clicked = holdsWater(level, cell, level.getBlockState(cell)) ? cell : cell.below();
        var shape = level.getBlockState(clicked).getShape(level, clicked);
        AABB outline = shape.isEmpty() ? new AABB(clicked) : shape.bounds().move(clicked);
        return outline.distanceToSqr(c.getEyePosition()) <= REACH * REACH;
    }

    /** Pours this bucket into the cell; the journal keeps the health the landing would have cost him. */
    private void pour(ServerLevel level, int slot, BlockPos cell, float loss) {
        ItemStack bucket = c.getItem(slot);
        if (!((BucketItem) bucket.getItem()).emptyContents(null, level, cell, null, bucket)) return;
        c.setItem(slot, new ItemStack(Items.BUCKET));
        c.animateAction(1, cell, bucket);
        Pool pool = new Pool(cell, level.getGameTime());
        pools.add(pool);
        event("water_bucket_poured", pool, "loss", loss);
    }

    /** His water, each pool: down by it he scoops it; gone, given up, too far or too late, he leaves it. */
    private void settle(ServerLevel level) {
        boolean landed = c.onGround() || c.isInWater();
        for (Pool pool : List.copyOf(pools)) {
            if (!pool.down && landed) landed(level, pool);
            if (!level.getFluidState(pool.cell).isSourceOfType(Fluids.WATER)) leave(pool, "gone");
            else if (pool.down && reachable(pool.cell)) { if (!scoop(level, pool)) leave(pool, "no_bucket"); }
            else if (level.getGameTime() - pool.at > FETCH) leave(pool, "late");
            else if (pool.down && c.distanceToSqr(Vec3.atCenterOf(pool.cell)) > FAR * FAR) leave(pool, "far");
        }
    }

    /** Down after pouring: in this water of his, he says what the bucket did (not in a river he was flung into). */
    private void landed(ServerLevel level, Pool pool) {
        pool.down = true;
        event("water_bucket_landed", pool);
        long now = level.getGameTime();
        if (c.isInWater() && c.getBoundingBox().intersects(new AABB(pool.cell)) && now - saidAt >= TALK) {
            saidAt = now;
            c.autonomySay(SAVED.get(c.getRandom().nextInt(SAVED.size())), false);
        }
    }

    /** Within his reach and in his sight, as a player's bucket reaches: not through a wall. */
    private boolean reachable(BlockPos cell) {
        Vec3 eyes = c.getEyePosition(), water = Vec3.atCenterOf(cell);
        if (eyes.distanceToSqr(water) > REACH * REACH) return false;
        BlockHitResult hit = c.level().clip(new ClipContext(eyes, water, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(cell);
    }

    /** Back into a bucket of his: any empty one, with a free cell if it lies in a stack. False if he has none. */
    private boolean scoop(ServerLevel level, Pool pool) {
        BlockPos cell = pool.cell;
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
        pools.remove(pool);
        event("water_bucket_scooped", pool);
        return true;
    }

    /** He gives this water up: it stays where it is, a bucket of his stays empty, and he says so (decided 05.11). */
    private void leave(Pool pool, String why) {
        pools.remove(pool);
        event("water_bucket_left", pool, "why", why);
        c.autonomySay(LEFT, false);
    }

    /** The first water of his he is down out of reach of, or null: he goes back for it, unless the player has his bag open. */
    private Pool astray() {
        if (c.inventoryViewers > 0) return null;
        for (Pool pool : pools) if (pool.down && !reachable(pool.cell)) return pool;
        return null;
    }

    void fetchBegin() { fetching = null; }

    /**
     * A way to his water when the last one is walked; a search this tick had no share for is asked again the next. Three looks without
     * a way and he gives it up.
     */
    void fetchTick() {
        Pool pool = astray();
        if (pool == null) return;
        if (pool != fetching) { fetching = pool; noWay = 0; lookedAt = Long.MIN_VALUE / 2; c.getNavigation().stop(); }
        if (!pool.told) {
            pool.told = true;
            c.autonomySay(FETCHING, c.hasActiveWorkOrder());
            event("water_bucket_fetch", pool);
        }
        long now = c.level().getGameTime();
        if (!c.getNavigation().isDone() && !c.getNavigation().isStuck() || now - lookedAt < AGAIN) return;
        int busy = c.work.route.busyAnswers();
        Path path = c.work.route.toCells(Set.of(pool.cell), 2);
        if (c.work.route.busyAnswers() != busy) return;
        lookedAt = now;
        if (path != null) { noWay = 0; c.work.route.walk(path); }
        else if (++noWay >= LOOKS) leave(pool, "no_way");
    }

    /**
     * Back to his water standing out of his reach, the work waiting meanwhile (WorkGoal pauses it): the next fling would find his
     * bucket empty (decided 05.11). Beside the self-rescue in rank, under his air.
     */
    static final class FetchGoal extends Goal {
        private final CompanionEntity c;
        FetchGoal(CompanionEntity c) { this.c = c; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return c.waterBucket.astray() != null; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { c.waterBucket.fetchBegin(); }
        @Override public void tick() { c.waterBucket.fetchTick(); }
        @Override public void stop() { c.getNavigation().stop(); }
    }

    /** The journal's line: where he is, where this water stands, his health and fall, the ticks since he poured it. */
    private void event(String name, Pool pool, Object... more) {
        Object[] fields = {"at", c.blockPosition().toShortString(), "water", pool.cell.toShortString(), "health", c.getHealth(),
                "fall", c.fallDistance, "ticks", c.level().getGameTime() - pool.at, "pools", pools.size()};
        Object[] all = java.util.Arrays.copyOf(fields, fields.length + more.length);
        System.arraycopy(more, 0, all, fields.length, more.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}
