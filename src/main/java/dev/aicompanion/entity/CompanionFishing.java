package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * fish, as a player with a rod (the player 24.11): the rod of his bag goes into his hand on the bank of the nearest water, the
 * bobber is cast out on it (CompanionBobber), the bite is waited for and pulled. The catch is the game's own fishing table's, fish,
 * junk and treasure; it flies to him and is picked up, all of it kept, and the rod wears by each catch. So many fish more in his
 * bag, or with no number until «стоп», a broken rod, a full bag or {@link #LIMIT_SECONDS}. No rod is a shortage the plan gets first.
 */
public final class CompanionFishing {
    /** No number in the order: until he is stopped. */
    public static final int ALL = CompanionWork.ALL_CARRIED;
    /** The order's item for any fish; else one of {@link #FISH}. */
    public static final String ANY = "fish";
    /** What counts toward the order; the rest of a catch is kept and told. */
    public static final List<Item> FISH = List.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH);
    /** What an order may name: any fish, or one of the four by its id. */
    public static final Set<String> NAMES = Set.of(ANY, "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish");
    /** Until «стоп» is at most this long, as a walk by a heading is (CompanionCourse): the plan's step ends with what he caught. */
    public static final int LIMIT_SECONDS = 20 * 60;
    static final String NO_ROD = "Удочки нет: рыбачить нечем.";
    static final String ROD = "minecraft:fishing_rod";
    /** Blocks above and below him water is looked for. */
    private static final int DEPTH = 12;
    /** Blocks out from the bank the bobber is cast at most, as a player's throw. */
    private static final int THROW = 6;
    /** Casts in a row that came down on no water before the place is given up, places given up before the order is. */
    private static final int MISSES = 3, SKIPS = 16;
    /** Ticks a bobber may lie on the ground before it is pulled back. */
    private static final int GROUNDED = 20;
    /** Walks to one bank and ticks no search for the way comes about before it is passed over (as CompanionChores). */
    private static final int LEGS = 8, WAIT = 100;

    private final CompanionWork w;
    private CompanionEntity c;
    /** One kind of fish, or null for any. */
    private Item item;
    private int wanted, casts, misses, legs, waited;
    private long began;
    private final Map<Item, Integer> before = new HashMap<>();
    private CompanionBobber bobber;
    /** The rod went into his hand for this work: it goes back into his bag with the work's end. */
    private boolean held;
    /** The water by the bank he walks to, the cells it is fished from, and the water farther out the bobber is cast on. */
    private BlockPos shore, aim;
    private Set<BlockPos> stands = Set.of();
    private Path path;
    private final Set<BlockPos> skipped = new HashSet<>();

    /** Made with the work, before its owner is set: the owner is read at the order. */
    CompanionFishing(CompanionWork w) { this.w = w; }

    /** Still water under the open air: a source with air over it. */
    private static boolean surface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir();
    }

    /** The nearest water he may fish in, or null. */
    private static BlockPos water(CompanionEntity c, ServerLevel level, Set<BlockPos> skipped) {
        return CompanionObsidian.nearest(c, level, DEPTH, state -> state.is(Blocks.WATER), pos -> !skipped.contains(pos) && c.leashAllows(pos) && surface(level, pos));
    }

    /** Whether there is water about him to fish in (the planner of «добудь»). */
    static boolean about(CompanionEntity c, ServerLevel level) { return water(c, level, Set.of()) != null; }

    /** {@code count} more fish in his bag, {@link #ALL} until he is stopped. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, int count) {
        c = w.owner;
        ResourceLocation key = id == null || id.isBlank() || ANY.equals(id) ? null : ResourceLocation.tryParse(id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (id != null && !id.isBlank() && !ANY.equals(id) && !FISH.contains(asked)) return CompanionActionResult.failed("Такого я удочкой не ловлю: " + id + ".");
        if (w.items.countCarried(Items.FISHING_ROD) == 0)
            return CompanionActionResult.shortage(NO_ROD).withEvidence(new CompanionActionResult.Evidence(0, "fish", ROD, 1, null, null, null));
        item = asked; wanted = count; casts = misses = 0;
        began = c.level().getGameTime();
        before.clear();
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty()) before.merge(stack.getItem(), stack.getCount(), Integer::sum);
        skipped.clear();
        release();
        return null;
    }

    String jobName() { return "рыбалка (" + got() + (wanted == ALL ? "" : " из " + wanted) + ")"; }

    private int more(Item kind) { return Math.max(0, w.items.countCarried(kind) - before.getOrDefault(kind, 0)); }

    private int got() {
        if (item != null) return more(item);
        int got = 0;
        for (Item kind : FISH) got += more(kind);
        return got;
    }

    /** What the bag has more of than at the order, the fish first: the catch as it lies in his bag, not as it was rolled. */
    private String report() {
        Map<Item, Integer> now = new HashMap<>();
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty()) now.merge(stack.getItem(), stack.getCount(), Integer::sum);
        StringBuilder fish = new StringBuilder(), rest = new StringBuilder();
        for (Item kind : FISH) if (more(kind) > 0) fish.append(fish.isEmpty() ? "" : ", ").append(name(kind)).append(" — ").append(more(kind));
        for (var entry : now.entrySet()) {
            int gained = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (gained > 0 && !FISH.contains(entry.getKey())) rest.append(rest.isEmpty() ? "" : ", ").append(name(entry.getKey())).append(" — ").append(gained);
        }
        if (fish.isEmpty() && rest.isEmpty()) return "Ничего не поймал.";
        return (fish.isEmpty() ? "Рыбы не наловил." : "Наловил: " + fish + ".") + (rest.isEmpty() ? "" : " Ещё попалось: " + rest + ".");
    }

    private static String name(Item kind) { return new ItemStack(kind).getHoverName().getString(); }

    /** An order with no number ends well by any of its bounds; a counted one that fell short is planned again by «добудь» or told. */
    private void bound(String why, boolean blocked) {
        String told = why + " " + report();
        end(wanted == ALL ? CompanionActionResult.ok(told) : blocked ? CompanionActionResult.blocked(told) : CompanionActionResult.failed(told));
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (wanted != ALL && got() >= wanted) { end(CompanionActionResult.ok(report())); return; }
        if (w.items.countCarried(Items.FISHING_ROD) == 0) { bound("Удочка сломалась.", false); return; }
        if (now - began >= LIMIT_SECONDS * 20L) { end(CompanionActionResult.ok("Рыбачу уже двадцать минут, сматываю удочку. " + report())); return; }
        // A catch is anything: boots or a saddle want a slot of their own.
        if (!w.items.canStore(List.of(new ItemStack(Items.SADDLE)))) { bound(CompanionPicking.FULL_BAG, true); return; }
        // Gone by itself: the rod left his hand (a blade taken against a monster, food).
        if (bobber != null && bobber.isRemoved()) bobber = null;
        // A fight: the line is in until it is over.
        if (c.getTarget() != null) { if (bobber != null) reel(level); return; }
        if (bobber != null) { watch(level); return; }
        if (shore == null) {
            BlockPos found = skipped.size() >= SKIPS ? null : water(c, level, skipped);
            if (found == null) {
                String why = skipped.isEmpty() ? "Рядом не вижу воды, где порыбачить." : "Воду вижу, но встать у неё и закинуть удочку не смог.";
                if (got() > 0 || wanted == ALL && casts > 0) bound(why, true); else end(CompanionActionResult.blocked(why));
                return;
            }
            Set<BlockPos> cells = CompanionWaterFill.standsFor(c, level, List.of(found));
            if (cells.isEmpty()) { skipped.add(found); return; }
            shore = found; stands = cells; path = null; aim = null; legs = waited = misses = 0;
        }
        if (!stands.contains(c.blockPosition())) {
            c.workStatus("Иду к воде порыбачить.");
            CompanionRoute route = w.route;
            if (path != null && !route.legOver()) return;
            if (++legs <= LEGS) {
                CompanionRoute.Reach reach = route.cells(stands, 0);
                // He is in the air or the search goes on: asked again at the next tick, though not for ever.
                if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; if (++waited < WAIT) return; }
                else if (reach.found()) { waited = 0; path = reach.path(); route.walk(path); return; }
            }
            skipped.add(shore); release();
            return;
        }
        c.getNavigation().stop();
        if (!c.getMainHandItem().is(Items.FISHING_ROD)) {
            if (!c.equipForWork(ROD).success()) { bound("Не могу взять удочку в руку: в сумке нет места.", true); return; }
            held = true;
        }
        if (aim == null) aim = aim(level);
        Vec3 at = Vec3.atCenterOf(aim);
        c.getLookControl().setLookAt(at.x, at.y, at.z);
        c.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, .5F, .4F / (level.getRandom().nextFloat() * .4F + .8F));
        bobber = CompanionBobber.cast(c, level, c.getMainHandItem(), at);
        casts++;
        // Each cast is told anew: a wait for a bite is no «стоял без продвижения» to the work's watch.
        c.workStatus("Закинул удочку, жду поклёвку (заброс " + casts + ").");
    }

    /**
     * Where the bobber goes from where he stands: of the water he sees within a throw, the cell with the most water about it (the
     * game gives its treasure only on open water, five by five), the nearer of two alike; the water by the bank when he sees no other.
     */
    private BlockPos aim(ServerLevel level) {
        Vec3 eyes = c.getEyePosition();
        BlockPos feet = c.blockPosition(), best = shore;
        int most = -1;
        double nearest = Double.MAX_VALUE;
        for (BlockPos cell : BlockPos.betweenClosed(feet.offset(-THROW, -3, -THROW), feet.offset(THROW, 0, THROW))) {
            double away = Math.sqrt(cell.distSqr(feet));
            if (away < 2 || away > THROW || !surface(level, cell)) continue;
            Vec3 top = Vec3.atCenterOf(cell).add(0, .6, 0);
            if (level.clip(new ClipContext(eyes, top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c)).getType() != HitResult.Type.MISS) continue;
            int open = 0;
            for (BlockPos near : BlockPos.betweenClosed(cell.offset(-2, 0, -2), cell.offset(2, 0, 2))) if (surface(level, near)) open++;
            if (open > most || open == most && away < nearest) { most = open; nearest = away; best = cell.immutable(); }
        }
        return best;
    }

    /** The bobber is out: a bite is pulled at once, one that came down on no water is pulled back and cast again. */
    private void watch(ServerLevel level) {
        c.getNavigation().stop();
        c.getLookControl().setLookAt(bobber, 30, 30);
        if (bobber.biting()) { reel(level); misses = 0; return; }
        if (bobber.grounded() < GROUNDED) return;
        reel(level);
        if (++misses >= MISSES) { skipped.add(shore); release(); }
    }

    /** Pulled in as a player pulls: the catch (if a fish was on) is his to pick up; the rod wears as the game wears it. */
    private void reel(ServerLevel level) {
        ItemStack rod = c.getMainHandItem();
        boolean ground = bobber.onGround();
        List<ItemEntity> catches = bobber.reel(c, rod);
        bobber = null;
        c.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1F, .4F / (level.getRandom().nextFloat() * .4F + .8F));
        int wear = ground ? 2 : catches.isEmpty() ? 0 : 1;
        if (wear > 0 && rod.is(Items.FISHING_ROD)) rod.hurtAndBreak(wear, c, EquipmentSlot.MAINHAND);
        c.claimDrops(catches);
        c.setChanged();
        w.cooldown = 10;
    }

    private void release() { shore = aim = null; stands = Set.of(); path = null; legs = waited = misses = 0; }

    /** The line is taken in with the work: «стоп», another order, the end. */
    void cancel() {
        if (bobber != null) bobber.discard();
        bobber = null;
        if (held && c.getMainHandItem().is(Items.FISHING_ROD)) c.unequipForSupport("mainhand");
        held = false;
        release();
    }

    private void end(CompanionActionResult result) {
        cancel();
        w.finish(result);
    }
}
