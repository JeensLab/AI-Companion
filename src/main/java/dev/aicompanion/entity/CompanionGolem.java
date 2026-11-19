package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * build_golem: an iron or a snow golem made as a player makes one (decisions 9–12 of small-use). Within six blocks of the player, on
 * firm ground with room for it (3×3×3 for iron, a column of three for snow), not on the player's buildings nor under him, he sets the
 * body blocks of his bag and the head last: a carved pumpkin or a jack o'lantern of his, or his pumpkin set there and carved with his
 * shears as a player carves one. The game itself makes the golem of them (CarvedPumpkinBlock.trySpawnGolem); he checks it stood up.
 * What the body or the head lacks is a shortage the plan gets first (CompanionPlanRunner), one thing at a time; iron is a long way
 * (ore, a furnace) and is said before it is gone for. The next step is read from the world every tick; a golem stopped half way is
 * built on where it stands by the next order of its kind, so no block is asked for twice.
 */
public final class CompanionGolem {
    /** The kinds the model may name (CompanionPlanCodec). */
    public static final Set<String> KINDS = Set.of("iron", "snow");
    static final String NO_PLACE_IRON = "Не нашёл ровного свободного места 3×3×3 рядом для голема.";
    static final String NO_PLACE_SNOW = "Не нашёл ровного свободного места рядом для снеговика: нужен столбик в три блока.";
    static final String MELTS = "Снеговик тут растает: здесь слишком жарко.";
    static final String NO_WAY = "Не могу подойти к месту голема.";
    static final String TAKEN = "Место голема кто-то занял — построю заново по новой просьбе.";
    static final String NOT_ALIVE = "Голем не ожил — блоки стоят, как я их поставил.";
    static final String RAIN = " Идёт дождь: снеговик под ним тает, укрой его.";
    /** A player's reach for a block (CompanionCobbleGen). */
    private static final double REACH = 4.5;
    /** How far from the player the golem may stand (blocks). */
    private static final int SITE = 6;
    /** Walks to one place before it is passed over; ticks a cell is waited for to be free, and the carved pumpkin's seeds to be picked up. */
    private static final int LEGS = 8, WAIT_TICKS = 200, PICK_TICKS = 100;
    /** How near the player a golem stopped half way is built on (blocks); farther off, a new one is made. */
    private static final double KEPT = 16;

    enum Kind {
        IRON(Items.IRON_BLOCK, Blocks.IRON_BLOCK, "железный"), SNOW(Items.SNOW_BLOCK, Blocks.SNOW_BLOCK, "снежный");
        final Item item;
        final Block block;
        final String word;
        Kind(Item item, Block block, String word) { this.item = item; this.block = block; this.word = word; }
        static Kind of(String id) { return "iron".equals(id) ? IRON : "snow".equals(id) ? SNOW : null; }
    }

    private enum Act { CLEAR, BODY, HEAD, PUMPKIN, CARVE }
    private record Step(Act act, BlockPos cell) { }

    /** Where it stands: the leg's (snow: the bottom's) cell; an iron golem's arms go along {@code axis}. */
    record Site(Kind kind, BlockPos base, Direction.Axis axis) {
        BlockPos head() { return base.above(2); }
        /** The body in the order he sets it: the leg (bottom), the trunk, then the arms. */
        List<BlockPos> body() {
            if (kind == Kind.SNOW) return List.of(base, base.above());
            BlockPos trunk = base.above();
            return List.of(base, trunk, trunk.relative(positive()), trunk.relative(positive().getOpposite()));
        }
        /** Beside the leg and the head of an iron golem: the game wants air there (CarvedPumpkinBlock, '~'). */
        List<BlockPos> air() {
            if (kind == Kind.SNOW) return List.of();
            return List.of(base.relative(positive()), base.relative(positive().getOpposite()), head().relative(positive()), head().relative(positive().getOpposite()));
        }
        /** The room the golem takes: 3×3×3 for iron, its own column for snow. */
        AABB room() { return kind == Kind.SNOW ? new AABB(base).expandTowards(0, 2, 0) : new AABB(base).inflate(1, 0, 1).expandTowards(0, 2, 0); }
        List<BlockPos> cells() {
            int side = kind == Kind.SNOW ? 0 : 1;
            return BlockPos.betweenClosedStream(base.offset(-side, 0, -side), base.offset(side, 2, side)).map(BlockPos::immutable).toList();
        }
        List<BlockPos> ground() { return cells().stream().filter(cell -> cell.getY() == base.getY()).map(BlockPos::below).toList(); }
        private Direction positive() { return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE); }
    }

    private final CompanionWork w;
    private final CompanionEntity c;
    private Site site;
    /** The site of a golem stopped half way and the world it is in: the next order of the kind goes on with it. */
    private Site stopped;
    private ResourceKey<Level> stoppedIn;
    /** The golem that stood up, and when: its pumpkin's seeds are picked up before the order ends. */
    private AbstractGolem golem;
    private long stoodAt, waitingSince;
    private boolean carved;
    /** The cell he walks to a place to reach, the cells he can reach it from, the walk's path and how many legs of it were walked. */
    private BlockPos aimed;
    private Set<BlockPos> stands = Set.of();
    private Path path;
    private int legs;

    CompanionGolem(CompanionWork w) { this.w = w; this.c = w.owner; }

    /** Null when the work may start, else why not: the kind, the world, a place, or what the body or the head lacks (a shortage). */
    CompanionActionResult begin(String item) {
        reset();
        Kind kind = Kind.of(item);
        if (kind == null) return CompanionActionResult.failed("Не понял, какого голема делать: железного или снежного.");
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Для работы нужен загруженный мир.");
        BlockPos center = CompanionPortalBuild.center(c);
        // Decision 11: where a snow golem melts (desert, savanna, badlands, the Nether), it is not made.
        if (kind == Kind.SNOW && level.getBiome(center).is(BiomeTags.SNOW_GOLEM_MELTS)) return CompanionActionResult.failed(MELTS);
        ServerPlayer player = c.getOwnerPlayer();
        // Never where the player stands or under him; a new place not right beside him either (a stopped golem he came back to may be).
        AABB under = player == null || player.level() != level ? null : player.getBoundingBox().expandTowards(0, -4, 0);
        AABB beside = under == null ? null : under.inflate(1.5, 0, 1.5);
        Predicate<BlockPos> builds = c.getOwnerId() == null ? pos -> false : CompanionPlayerBuilds.get(level.getServer()).sites(c.getOwnerId(), level);
        Site chosen = stopped != null && stopped.kind() == kind && level.dimension() == stoppedIn && stopped.base().closerThan(center, KEPT)
                && started(level, stopped) && fits(level, stopped, builds, under) ? stopped : null;
        if (chosen == null) chosen = choose(level, kind, center, builds, beside);
        if (chosen == null) return CompanionActionResult.failed(kind == Kind.IRON ? NO_PLACE_IRON : NO_PLACE_SNOW);
        // The place itself may lie over a biome's border from the player.
        if (kind == Kind.SNOW && level.getBiome(chosen.base()).is(BiomeTags.SNOW_GOLEM_MELTS)) return CompanionActionResult.failed(MELTS);
        // What is set already (a stopped golem) is not asked for again.
        int body = (int) chosen.body().stream().filter(cell -> !level.getBlockState(cell).is(kind.block)).count();
        int carried = w.items.countCarried(kind.item);
        if (carried < body) {
            // Decision 10: the iron is told before it is gone for, when it is not just smelted and crafted of what he carries.
            int short_ = body - carried;
            if (kind == Kind.IRON && w.items.countCarried(Items.IRON_INGOT) + w.items.countCarried(Items.RAW_IRON) < short_ * 9)
                say(level, "Железных блоков для голема не хватает (нужно ещё " + short_ + ") — накопаю железной руды и переплавлю, это надолго.");
            // obtain_item counts what he should hold in all (review: the part missing was already held, and the plan stopped).
            return shortage("Для голема не хватает блоков.", kind.item, body);
        }
        boolean onHead = level.getBlockState(chosen.head()).is(Blocks.PUMPKIN);
        if (!carvedCarried() && !onHead && w.items.countCarried(Items.PUMPKIN) == 0) return shortage("Для головы голема нужна тыква.", Items.PUMPKIN, 1);
        // His pumpkin already on the body is carved, whatever else he carries (next()).
        if ((onHead || !carvedCarried()) && w.items.countCarried(Items.SHEARS) == 0) return shortage("Вырезать тыкву нечем — нужны ножницы.", Items.SHEARS, 1);
        site = chosen;
        event("golem_site", "kind", kind, "base", chosen.base().toShortString(), "axis", chosen.axis(), "resumed", chosen == stopped);
        return null;
    }

    String jobName() { return site != null && site.kind() == Kind.SNOW ? "постройку снеговика" : "постройку голема"; }

    /** Stopped by the player or another order: a golem with any of its blocks set is kept for the next order of its kind. */
    void cancel() {
        if (site != null && c.level() instanceof ServerLevel level && started(level, site)) { stopped = site; stoppedIn = level.dimension(); }
        reset();
    }

    /** The order ends without a golem: the place is not kept either, the next order would fail the same way there (review). */
    private void end(String message) { stopped = null; site = null; w.finish(false, message); }

    private void reset() { site = null; golem = null; stoodAt = waitingSince = 0; carved = false; aimed = null; stands = Set.of(); path = null; legs = 0; }

    /** One tick: the first thing the golem lacks, read from the world, done from where he reaches it. */
    void tick(ServerLevel level) {
        if (site == null) { w.finish(false, TAKEN); return; }
        if (golem != null) { pick(level); return; }
        if (!level.hasChunkAt(site.base()) || !level.hasChunkAt(site.head())) { end(TAKEN); return; }
        Step step = next(level);
        if (step == null) return;
        // Not from inside the room: his own body would be in the way of a block, and of the golem.
        if (c.getBoundingBox().intersects(site.room()) || !inReach(level, step.cell())) {
            c.workStatus(site.kind() == Kind.SNOW ? "Иду к месту снеговика." : "Иду к месту голема.");
            if (!step.cell().equals(aimed)) { aimed = step.cell(); stands = standsFor(level, step.cell()); legs = 0; path = null; }
            if (path != null && w.route.legOver() && stands.remove(c.blockPosition())) path = null;
            if (!walk()) { event("golem_no_way", "step", step, "stands", stands.size(), "legs", legs); end(NO_WAY); }
            return;
        }
        w.route.stop();
        w.progressed();
        switch (step.act()) {
            case CLEAR -> clear(level, step.cell());
            case BODY -> place(level, step.cell(), site.kind().item, site.kind().block.defaultBlockState());
            case PUMPKIN -> place(level, step.cell(), Items.PUMPKIN, Blocks.PUMPKIN.defaultBlockState());
            case HEAD -> {
                Item lamp = w.items.countCarried(Items.CARVED_PUMPKIN) > 0 ? Items.CARVED_PUMPKIN : Items.JACK_O_LANTERN;
                Block block = lamp == Items.CARVED_PUMPKIN ? Blocks.CARVED_PUMPKIN : Blocks.JACK_O_LANTERN;
                Set<UUID> before = golemsAbout(level);
                if (place(level, step.cell(), lamp, block.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, toward(step.cell())))) stood(level, before);
            }
            case CARVE -> carve(level, step.cell());
        }
    }

    /** The plants beside an iron golem cut, the body set, then the head; null when the order ended or waits. */
    private Step next(ServerLevel level) {
        for (BlockPos cell : site.air()) {
            BlockState state = level.getBlockState(cell);
            if (state.isAir()) continue;
            if (!clearable(state)) { end(TAKEN); return null; }
            return new Step(Act.CLEAR, cell);
        }
        for (BlockPos cell : site.body()) {
            if (level.getBlockState(cell).is(site.kind().block)) continue;
            if (!CompanionPortalBuild.free(level, cell)) { end(TAKEN); return null; }
            if (w.items.countCarried(site.kind().item) == 0) { end("Блоки для голема кончились — их кто-то взял из сумки."); return null; }
            return new Step(Act.BODY, cell);
        }
        BlockState head = level.getBlockState(site.head());
        if (head.is(Blocks.PUMPKIN)) {
            if (w.items.countCarried(Items.SHEARS) == 0) { end("Вырезать тыкву нечем — нужны ножницы."); return null; }
            return new Step(Act.CARVE, site.head());
        }
        if (!CompanionPortalBuild.free(level, site.head())) { end(head.is(Blocks.CARVED_PUMPKIN) || head.is(Blocks.JACK_O_LANTERN) ? NOT_ALIVE : TAKEN); return null; }
        // Blocks of its kind set since beside the head would make the game's golem of them instead (fits()).
        if (stray(level, site)) { end(TAKEN); return null; }
        if (carvedCarried()) return new Step(Act.HEAD, site.head());
        if (w.items.countCarried(Items.PUMPKIN) == 0) { end("Тыква для головы кончилась — её кто-то взял из сумки."); return null; }
        return new Step(Act.PUMPKIN, site.head());
    }

    private boolean carvedCarried() { return w.items.countCarried(Items.CARVED_PUMPKIN) + w.items.countCarried(Items.JACK_O_LANTERN) > 0; }

    /** A block of his set in the cell from his hand, as a player sets one; false while it waits or when the order ended. */
    private boolean place(ServerLevel level, BlockPos cell, Item item, BlockState state) {
        if (!level.isUnobstructed(state, cell, CollisionContext.empty())) {
            if (waitingSince == 0) waitingSince = level.getGameTime();
            if (level.getGameTime() - waitingSince > WAIT_TICKS) { end(TAKEN); return false; }
            c.workStatus("Жду, пока освободится место для блока.");
            return false;
        }
        waitingSince = 0;
        if (!c.getMainHandItem().is(item) && !c.equipForWork(BuiltInRegistries.ITEM.getKey(item).toString()).success()) { end("Не могу взять блок в руку: в сумке нет места."); return false; }
        ItemStack held = c.getMainHandItem();
        if (!held.is(item) || !level.setBlock(cell, state, 3)) { end(TAKEN); return false; }
        c.animateAction(1, cell, held);
        held.shrink(1);
        c.setChanged();
        c.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, cell, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
        level.gameEvent(c, GameEvent.BLOCK_PLACE, cell);
        c.workStatus("Ставлю " + (site.kind() == Kind.SNOW ? "снеговика." : "голема."));
        w.cooldown = 4;
        return true;
    }

    /** His pumpkin on the body carved with his shears, as the game carves one for a player (PumpkinBlock.useItemOn): the golem stands up then. */
    private void carve(ServerLevel level, BlockPos cell) {
        if (!c.getMainHandItem().is(Items.SHEARS) && !c.equipForWork("minecraft:shears").success()) { end("Не могу взять ножницы в руку: в сумке нет места."); return; }
        ItemStack shears = c.getMainHandItem();
        Set<UUID> before = golemsAbout(level);
        c.getLookControl().setLookAt(Vec3.atCenterOf(cell));
        level.playSound(null, cell, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1F, 1F);
        if (!level.setBlock(cell, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, toward(cell)), 11)) { end(TAKEN); return; }
        c.animateAction(1, cell, shears);
        c.swing(InteractionHand.MAIN_HAND);
        shears.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
        level.gameEvent(c, GameEvent.SHEAR, cell);
        // The four seeds fall at his feet, not the pumpkin's (a golem may step on them): his to pick up.
        c.dropHarvest(c.blockPosition(), List.of(new ItemStack(Items.PUMPKIN_SEEDS, 4)));
        c.setChanged();
        carved = true;
        stood(level, before);
    }

    /** The head is on: the golem the game made of the blocks just now, or the honest word that none stood up. */
    private void stood(ServerLevel level, Set<UUID> before) {
        Class<? extends AbstractGolem> type = site.kind() == Kind.IRON ? IronGolem.class : SnowGolem.class;
        golem = level.getEntitiesOfClass(type, site.room().inflate(2), mob -> mob.isAlive() && !before.contains(mob.getUUID())).stream().findFirst().orElse(null);
        event("golem_stood", "kind", site.kind(), "golem", golem == null ? "none" : golem.getUUID(), "head", level.getBlockState(site.head()));
        if (golem == null) { end(NOT_ALIVE); return; }
        stoodAt = level.getGameTime();
        w.cooldown = 4;
    }

    /** After the golem: the seeds of the pumpkin he carved picked up (a short while), then done. */
    private void pick(ServerLevel level) {
        if (carved && level.getGameTime() - stoodAt <= PICK_TICKS
                && !level.getEntitiesOfClass(ItemEntity.class, new AABB(c.blockPosition()).inflate(4), item -> item.isAlive() && item.getItem().is(Items.PUMPKIN_SEEDS)).isEmpty()) {
            c.workStatus("Подбираю семечки.");
            return;
        }
        BlockPos at = site.base();
        // «Стоит на» and not «у»: the spoken reply drops a place with its «на» (CompanionDialogue.speakable), «у» was left hanging (game 16.11).
        String said = "Готово, " + site.kind().word + " голем стоит на " + at.getX() + ", " + at.getY() + ", " + at.getZ() + ".";
        // Decision 11: in the rain a snow golem is built and the player warned.
        if (site.kind() == Kind.SNOW && level.isRainingAt(at)) said += RAIN;
        event("golem_done", "kind", site.kind(), "base", at.toShortString());
        stopped = null;
        reset();
        w.finish(true, said);
    }

    /** A wild plant or a snow layer beside an iron golem broken as he cuts grass (CompanionCobbleGen), its drop picked up. */
    private void clear(ServerLevel level, BlockPos cell) {
        c.workStatus("Расчищаю место голема.");
        if (!w.breaking.tick(level, cell)) return;
        BlockState state = level.getBlockState(cell);
        var drops = CompanionTools.drops(state, level, cell, null, c, c.getMainHandItem());
        if (!level.destroyBlock(cell, false, c, 512)) { end(TAKEN); return; }
        c.dropHarvest(cell, drops);
    }

    private Direction toward(BlockPos cell) {
        Vec3 off = c.position().subtract(Vec3.atCenterOf(cell));
        return Direction.getNearest(off.x, 0, off.z);
    }

    private Set<UUID> golemsAbout(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        for (AbstractGolem mob : level.getEntitiesOfClass(AbstractGolem.class, site.room().inflate(2))) ids.add(mob.getUUID());
        return ids;
    }

    private void say(ServerLevel level, String line) {
        ServerPlayer owner = c.getOwnerPlayer();
        if (owner != null && owner.level() == level) owner.sendSystemMessage(Component.literal(c.getName().getString() + ": " + line));
        event("golem_said", "line", line);
    }

    private static CompanionActionResult shortage(String message, Item item, int count) {
        return CompanionActionResult.shortage(message).withEvidence(new CompanionActionResult.Evidence(0, "build_golem", BuiltInRegistries.ITEM.getKey(item).toString(), count, null, null, null));
    }

    // ---- the place ----

    /** The nearest site to the player that fits: in the order of the distance, an iron golem's arms along x, then z. */
    private Site choose(ServerLevel level, Kind kind, BlockPos center, Predicate<BlockPos> builds, AABB beside) {
        Site best = null;
        double bestFar = 0;
        // The player's own level first, then a step down, up, two down.
        for (int dy : new int[]{0, -1, 1, -2}) for (int dx = -SITE; dx <= SITE; dx++) for (int dz = -SITE; dz <= SITE; dz++) {
            double far = dx * dx + dz * dz;
            if (far > SITE * SITE || best != null && far >= bestFar) continue;
            for (Direction.Axis axis : kind == Kind.IRON ? List.of(Direction.Axis.X, Direction.Axis.Z) : List.of(Direction.Axis.X)) {
                Site s = new Site(kind, center.offset(dx, dy, dz), axis);
                if (fits(level, s, builds, beside) && !started(level, s)) { best = s; bestFar = far; break; }
            }
        }
        return best;
    }

    /**
     * Firm dry ground under all the room, the room free (air or a plant a block is set over) or holding this golem's own blocks, the
     * cells the game wants empty only air or a wild plant he cuts; nothing of it on the player's buildings, nor where he stands or under him.
     */
    private boolean fits(ServerLevel level, Site s, Predicate<BlockPos> builds, AABB beside) {
        if (beside != null && s.room().intersects(beside)) return false;
        List<BlockPos> body = s.body();
        for (BlockPos cell : s.cells()) {
            if (!level.hasChunkAt(cell) || builds.test(cell)) return false;
            BlockState state = level.getBlockState(cell);
            boolean own = body.contains(cell) && state.is(s.kind().block) || cell.equals(s.head()) && state.is(Blocks.PUMPKIN);
            if (!own && !CompanionPortalBuild.free(level, cell)) return false;
        }
        for (BlockPos cell : s.air()) if (!level.getBlockState(cell).isAir() && !clearable(level.getBlockState(cell))) return false;
        if (stray(level, s)) return false;
        for (BlockPos floor : s.ground()) {
            if (!level.hasChunkAt(floor) || builds.test(floor)) return false;
            BlockState state = level.getBlockState(floor);
            if (!state.isFaceSturdy(level, floor, Direction.UP) || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /**
     * A block of the golem's kind beside its head or over it (a snow wall, an igloo, a stack of iron): the game looks for the golem's
     * shape lying or upside down too and might take those, leaving his own blocks standing (BlockPattern.find; review).
     */
    private static boolean stray(ServerLevel level, Site s) {
        BlockPos head = s.head();
        for (BlockPos near : List.of(head.north(), head.south(), head.east(), head.west(), head.above())) if (level.getBlockState(near).is(s.kind().block)) return true;
        return false;
    }

    /** Some of the golem's blocks stand in its cells (a golem stopped half way). */
    private static boolean started(ServerLevel level, Site s) {
        for (BlockPos cell : s.body()) if (level.getBlockState(cell).is(s.kind().block)) return true;
        return level.getBlockState(s.head()).is(Blocks.PUMPKIN);
    }

    /** A wild plant he cuts with one blow (no collision, breaks at once, no sapling or crop; CompanionCobbleGen), or a layer of snow knocked off by hand. */
    private static boolean clearable(BlockState state) {
        if (state.is(Blocks.SNOW)) return true;
        var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
        return state.canBeReplaced() && state.getCollisionShape(empty, BlockPos.ZERO).isEmpty() && state.getDestroySpeed(empty, BlockPos.ZERO) == 0
                && !state.hasBlockEntity() && state.getFluidState().isEmpty() && !state.is(BlockTags.SAPLINGS) && !state.is(BlockTags.CROPS);
    }

    // ---- the walk ----

    /** Cells he can reach this one from, outside the golem's room: down to the ground under the head (CompanionWaterFill looks one lower only). */
    private Set<BlockPos> standsFor(ServerLevel level, BlockPos cell) {
        Set<BlockPos> cells = new HashSet<>();
        AABB room = site.room();
        for (BlockPos stand : BlockPos.betweenClosed(cell.offset(-4, -3, -4), cell.offset(4, 2, 4)))
            if (!room.intersects(new AABB(stand)) && CompanionMiningNavigation.standable(c, stand)
                    && CompanionWaterFill.seen(c, level, Vec3.atBottomCenterOf(stand).add(0, c.getEyeHeight(), 0), cell, REACH - .6)) cells.add(stand.immutable());
        return cells;
    }

    /** As a player's hand reaches a block: to its nearest face, and in sight (CompanionCobbleGen). */
    private boolean inReach(ServerLevel level, BlockPos cell) {
        Vec3 eyes = c.getEyePosition();
        if (new AABB(cell).distanceToSqr(eyes) <= REACH * REACH && CompanionWaterFill.seen(c, level, eyes, cell, REACH + 1)) return true;
        return CompanionWaterFill.seen(c, level, Vec3.atBottomCenterOf(c.blockPosition()).add(0, c.getEyeHeight(), 0), cell, REACH - .6);
    }

    /** One tick of the walk to the stand cells: false when there is no way (or no progress after {@link #LEGS} walks). */
    private boolean walk() {
        CompanionRoute route = w.route;
        if (path != null && !route.legOver()) return true;
        if (stands.isEmpty() || ++legs > LEGS) return false;
        CompanionRoute.Reach reach = route.cells(stands, 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; return true; }
        if (!reach.found()) return false;
        path = reach.path();
        route.walk(path);
        return true;
    }

    private void event(String name, Object... fields) {
        Object[] head = {"at", c.blockPosition().toShortString()};
        Object[] all = java.util.Arrays.copyOf(head, head.length + fields.length);
        System.arraycopy(fields, 0, all, head.length, fields.length);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnosticTrace(), name, all);
    }
}
