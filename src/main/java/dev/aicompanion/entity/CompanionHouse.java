package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One modest house, built from real items. The saved blueprint also allows interrupted work to resume. The same loop builds the
 * 5×5 plank house or, in an instance made for it, a building of the catalogue from the game's structure template.
 */
final class CompanionHouse {
    static final String TEMPLATE_KEY = "TemplateBuild";
    enum State { WORKING, NEED_WOOD, DONE, FAILED }
    record Result(State state, String message, int logs) {
        static Result working() { return new Result(State.WORKING, "", 0); }
        static Result failed(String message) { return new Result(State.FAILED, message, 0); }
    }
    record Step(BlockPos pos, BlockState state, int stage) {
        boolean door() { return state.getBlock() instanceof DoorBlock; }
        /** The cell of the other half put down with this one (a door's upper half, a bed's head), or null for a single block. */
        BlockPos second() { return door() ? pos.above() : state.getBlock() instanceof BedBlock ? pos.relative(state.getValue(BedBlock.FACING)) : null; }
        BlockState secondState() { return door() ? state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER) : state.setValue(BedBlock.PART, BedPart.HEAD); }
        Item item() { return state.getBlock().asItem(); }
    }
    private final CompanionEntity owner;
    /** Builds a building of the catalogue (saved under its own key) instead of the plank house. */
    private final boolean template;
    private CompanionVillageBuildings.Building building;
    /** The turn of the template the building is built in; saved with it (a save without one is not turned). */
    private Rotation rotation = Rotation.NONE;
    /** The game time the building waits for the player to set it down until (CompanionBuildPlacement); -1 while it waits for no one. */
    private long awaitUntil = -1;
    /** What the player was told of the building: shown while he is asked for the place. */
    private String awaitText = "";
    private CompanionTemplates.Loaded loaded;
    /** The number of blocks of the saved building: known before its template is read again after a load. */
    private int total;
    private BlockPos base;
    private String wood = "oak";
    private final BitSet placed = new BitSet();
    private List<Step> steps = List.of();
    private boolean prepared;
    private int target = -1;
    private int cooldown;
    private Path path;
    private BlockPos workbench;
    /** His house in another dimension, kept as saved until he is back there: a summon across dimensions goes through the save. */
    private CompoundTag elsewhere;
    private long progressTime;
    private double bestDistance;
    private final java.util.HashSet<Integer> unreachable = new java.util.HashSet<>();
    /** Dirt scaffolding of a building (template instance): what is out of reach from the ground is placed from a column he stands on. */
    private final CompanionScaffold scaffold;
    private boolean onScaffold;
    private int scaffolds;
    private static final int MAX_SCAFFOLDS = 12;
    /** The feet of the columns put up since the last block was set down: one that did not help is not put up again, another spot may. */
    private final java.util.HashSet<BlockPos> scaffoldTried = new java.util.HashSet<>();
    /** Blocks of the stage no column reaches, found so far this pass: a few blocks are searched per tick, the rest the next. */
    private final java.util.HashSet<Integer> noColumn = new java.util.HashSet<>();  private static final int COLUMN_SEARCHES_PER_TICK = 4;
    /** Cells somebody stands in (the player too), and what is above them: left alone while others can be set down. */
    private final CompanionHouseRoom room;
    /** The end of the work (done or failed) waits for the scaffolding to come down: none stands in a building reported done. */
    private Result afterScaffold;
    /** The site of a template building: its floor is sunk into the ground, what stands on it is cleared (read from the world again after a load). */
    private final CompanionSiteClearing clearing;
    private boolean cleared;
    private final CompanionHouseSite site;
    /** The wood and the rounds of getting what a template building lacks. */
    private final CompanionHouseMaterials materials;
    /** The blocks the bag has material for cannot be reached: the next round gets the rest first. Set down a block, and it is over. */
    private boolean skipAffordable;
    /** The game time of the last tick of this house's work: with two houses unfinished, «дострой» goes on with the one worked on last (saved with it). */
    private long touched;
    /** An order for a building whose place the player has not chosen yet (it waits, or the work was stopped while it did). */
    private boolean unplaced;
    /** What an order for another building left of the old one while its place is asked for (as it would be saved): kept until the place is confirmed, restored if it is not. */
    private CompoundTag stash;
    private String stashName = "", dropNote = "";
    /** What the scaffolding left on the ground (the bag was full on the way down), said with the end of the work. */
    private String lost = "";

    CompanionHouse(CompanionEntity owner) { this(owner, false); }
    CompanionHouse(CompanionEntity owner, boolean template) { this.owner = owner; this.template = template; scaffold = new CompanionScaffold(owner); clearing = new CompanionSiteClearing(owner, this); site = new CompanionHouseSite(this, owner, template, clearing); room = new CompanionHouseRoom(owner, site); materials = new CompanionHouseMaterials(owner); }

    /**
     * A building of the catalogue: the one already under way goes on, any other (or a finished one) starts at a new site. What is left of the old
     * one is kept (as it would be saved) until the new one has its place: a player who gives the place up still has it to finish. Returns what
     * the player is told when an unfinished building of another name is dropped for it (empty when nothing is).
     */
    String begin(CompanionVillageBuildings.Building next) {
        String dropped = "";
        CompoundTag kept = null;
        String keptName = "";
        if (building != next || base != null && placed.cardinality() == total) {
            if (unfinished() || elsewhere != null) kept = record();
            if (building != next && blocksLeft()) dropped = "Бросаю недостроенную «" + building.name() + "» на " + base.toShortString() + " и начинаю «" + next.name() + "». ";
            else if (building == null && elsewhereWhat() != null) dropped = "Бросаю недостроенную " + elsewhereWhat() + " в другом измерении и начинаю «" + next.name() + "». ";
            keptName = dropped.isEmpty() ? "" : building != null ? building.name() : elsewhere.getString("Building");
            building = next; loaded = null; steps = List.of(); total = 0; base = null; placed.clear(); workbench = null; elsewhere = null; materials.reset(); rotation = Rotation.NONE;
        }
        begin();
        unplaced = false; stash = kept; stashName = keptName; dropNote = "";
        return dropped;
    }

    boolean sited() { return base != null; }
    CompanionVillageBuildings.Building building() { return building; }
    /** From a fresh order until the player confirms where it goes (or the game time {@code until} comes): nothing is placed, dug or gathered. */
    void awaitPlacement(long until, String told) { awaitUntil = until; awaitText = told; unplaced = true; }
    boolean awaiting() { return awaitUntil >= 0; }
    /** The player gave the place up: nothing waits for it any more. */
    void forgetPlacement() { unplaced = false; }
    /** A building was ordered and no place chosen for it. */
    boolean unplaced() { return template && unplaced && building != null && base == null; }
    /** What is told to the player while the place for a new building is asked for and an unfinished one is kept for now; empty when there is none. */
    String waitingNote() { return stash == null || stashName.isEmpty() ? "" : "Недостроенную «" + stashName + "» пока не бросаю: брошу, когда выберешь место для новой. "; }
    /** What the player is told once the place is confirmed and the unfinished building is dropped (once). */
    String takeDropNote() { String note = dropNote; dropNote = ""; return note; }
    long touched() { return touched; }
    boolean is(String said) { return building != null && CompanionVillageBuildings.is(building, said); }
    /** What this house is called to the player. */
    String what() { return template ? "«" + building.name() + "»" : "дом 5×5"; }
    private boolean elsewhereUnbuilt() { return elsewhere != null && (!elsewhere.contains("Total") || elsewhere.getInt("Total") > BitSet.valueOf(elsewhere.getLongArray("Placed")).cardinality()); }
    /** The unfinished house kept for another dimension, named; null when there is none. */
    String elsewhereWhat() { return !elsewhereUnbuilt() ? null : template ? "«" + elsewhere.getString("Building") + "»" : "дом 5×5"; }
    /** A round of getting materials is under way. */
    boolean gathering() { return materials.gathering(); }

    /** Sets the building down with its lowest corner at {@code corner}, turned; the reason it cannot stand there, or null once it is set. */
    String place(ServerLevel level, BlockPos corner, Rotation turn) {
        var read = CompanionTemplates.load(level, building, turn);
        if (read == null) return "не нашёл в игре шаблон постройки «" + building.name() + "».";
        if (level.isOutsideBuildHeight(corner.below(2)) || level.isOutsideBuildHeight(corner.above(read.sizeY()))) return "это за пределами высоты мира.";
        clearing.use(read);
        if (clearing.assess(level, corner, true, clearing.playerSites(level), false) < 0) return clearing.refusal();
        Entity inTheWay = site.standingIn(level, corner, read.sizeX(), read.sizeY(), read.sizeZ());
        if (inTheWay != null) return "на площадке «" + inTheWay.getName().getString() + "» (" + inTheWay.blockPosition().toShortString() + "): рамки, картины и стойки я не трогаю; убери её или сдвинь призрак.";
        loaded = read; rotation = turn; base = corner.immutable(); awaitUntil = -1; unplaced = false;
        // The place is confirmed: now the old building is dropped (its scaffolding is still his to take down first).
        dropNote = stashName.isEmpty() ? "" : "Бросил недостроенную «" + stashName + "». "; stash = null; stashName = "";
        return null;
    }

    /** A saved building or house with blocks still to place. */
    boolean blocksLeft() {
        int size = steps.isEmpty() ? total : steps.size();
        return base != null && (size == 0 || placed.cardinality() < size);
    }
    /** A saved building or house with blocks still to place or scaffolding still to take down. */
    boolean unfinished() { return blocksLeft() || base != null && scaffold.hasBlocks(); }

    void begin() {
        prepared = false;
        cancel();
        unreachable.clear(); forgetColumns(); afterScaffold = null; materials.restart(); skipAffordable = false;
        // Scaffolding left by an interruption comes down first.
        if (scaffold.hasBlocks()) scaffold.startDescend();
    }

    void cancel() {
        target = -1;
        path = null;
        cooldown = 0;
        onScaffold = false; scaffold.reset();
        clearing.reset(); cleared = false; room.reset();
        // An order that waited for its place and ended without one: what it was to replace is still his.
        if (stash != null && awaiting()) restoreDropped(); else stash = null;
        awaitUntil = -1;
        // Placed blocks and the blueprint belong to the world, not the active command.
    }
    private void restoreDropped() {
        CompoundTag tag = new CompoundTag();
        tag.put(key(), stash);
        stash = null;
        load(tag);
    }

    boolean needsPreparation() { return !prepared && !(template && (scaffold.hasBlocks() || !cleared)); }
    /** The climb and the descent need a tick in the air too, between two jumps. */
    boolean scaffolding() { return template && scaffold.busy(); }
    BlockPos interiorAnchor() { return base == null ? null : base.offset(2, 1, 2); }
    boolean inside(BlockPos pos) { return CompanionPlankHouse.inside(base, pos); }
    /** Inside the house he finished; null while it is not finished and once a loaded chunk shows it gone. An unloaded house is taken on memory. */
    BlockPos finishedAnchor(ServerLevel level) {
        if (base == null || steps.isEmpty() || placed.cardinality() != steps.size()) return null;
        BlockPos center = interiorAnchor();
        boolean loaded = level.hasChunkAt(center) && level.hasChunkAt(center.above(3));
        return !loaded || interiorAvailable(level) ? center : null;
    }
    boolean interiorAvailable(ServerLevel level) { return CompanionPlankHouse.interiorAvailable(level, base); }
    private String key() { return template ? TEMPLATE_KEY : "SmallHouse"; }
    private int width() { return template ? loaded.sizeX() : 5; }
    private int depth() { return template ? loaded.sizeZ() : 5; }
    private int height() { return template ? loaded.sizeY() : 6; }
    private boolean planks(Item item) { return template ? new ItemStack(item).is(ItemTags.PLANKS) : item == block("planks").asItem(); }
    private String label() { return template ? building.name() : "дом"; }

    private Result loadTemplate(ServerLevel level) {
        if (!template || loaded != null) return null;
        loaded = CompanionTemplates.load(level, building, rotation);
        if (loaded != null) clearing.use(loaded);
        return loaded == null ? Result.failed("Не нашёл в игре шаблон постройки «" + building.name() + "».") : null;
    }

    Result ensureSite(ServerLevel level) {
        if (awaiting()) return null;
        Result unreadable = loadTemplate(level);
        if (unreadable != null) return unreadable;
        if (base != null) return null;
        int busy = owner.work.route.busyAnswers();
        base = findSite(level);
        // A site it could not search to for want of this tick's cells is not rejected: tick() looks again next tick.
        return base == null && owner.work.route.busyAnswers() == busy ? Result.failed("Не нашёл ровную свободную площадку " + (width() + 2) + "×" + (depth() + 2) + " рядом. Давай выберем место попросторнее.") : null;
    }

    Result tick(ServerLevel level) {
        touched = level.getGameTime();
        Result result = step(level);
        if (lost.isEmpty() || result.state() == State.WORKING || result.state() == State.NEED_WOOD) return result;
        String said = lost;
        lost = "";
        return new Result(result.state(), result.message() + " " + said, result.logs());
    }

    private Result step(ServerLevel level) {
        if (awaiting()) {
            if (level.getGameTime() < awaitUntil) { owner.workStatus(awaitText); return Result.working(); }
            String said = "Место для «" + building.name() + "» не выбрано за " + CompanionBuildPlacement.TIMEOUT_TICKS / 1200 + " минуты, строить не буду. Ничего не тронул."
                    + (stash == null || stashName.isEmpty() ? "" : " Недостроенная «" + stashName + "» осталась: скажи «дострой», чтобы продолжить её.");
            awaitUntil = -1; unplaced = false;
            if (stash != null) restoreDropped();
            return Result.failed(said);
        }
        if (!template) return build(level);
        Result onTheColumn = scaffoldStep(level);
        if (onTheColumn != null) return onTheColumn;
        Result result = build(level);
        if ((result.state() == State.DONE || result.state() == State.FAILED) && scaffold.hasBlocks()) {
            afterScaffold = result; onScaffold = false; scaffold.startDescend();
            return Result.working();
        }
        return result;
    }

    /** The climb up the column, the descent from it; null when the building itself is what to do now. */
    private Result scaffoldStep(ServerLevel level) {
        if (scaffold.descending()) {
            var outcome = scaffold.descend(level);
            if (outcome == CompanionScaffold.Outcome.WORKING) { owner.workStatus("Разбираю леса."); return Result.working(); }
            onScaffold = false; target = -1; path = null; lost += scaffold.takeNote();
            if (outcome == CompanionScaffold.Outcome.FAILED) { afterScaffold = null; return Result.failed(scaffold.failure() + " Стройку сохранил; скажи «дострой», когда будет можно."); }
            Result end = afterScaffold;
            afterScaffold = null;
            return end;
        }
        if (!scaffold.climbing()) return null;
        var outcome = scaffold.climb(level);
        if (outcome == CompanionScaffold.Outcome.WORKING) { owner.workStatus("Поднимаюсь на леса."); return Result.working(); }
        if (outcome == CompanionScaffold.Outcome.FAILED) {
            // Found below the column (pushed, fell): no end of the order. The rest comes down and the block gets a column again (their number is limited).
            boolean slipped = scaffold.slipped();
            if (slipped) scaffoldTried.clear();
            Result failed = Result.failed(scaffold.failure() + (scaffold.hasBlocks() ? " Леса разберу." : ""));
            if (!scaffold.hasBlocks()) return slipped ? Result.working() : failed;
            afterScaffold = slipped ? null : failed;
            return Result.working();
        }
        if (!onScaffold) { onScaffold = true; target = -1; path = null; unreachable.clear(); }
        return null;
    }

    private Result build(ServerLevel level) {
        if (!owner.onGround()) return Result.working();
        if (template) materials.settle();
        Result unreadable = loadTemplate(level);
        if (unreadable != null) return unreadable;
        if (base == null) {
            int busy = owner.work.route.busyAnswers();
            base = findSite(level);
            if (base == null && owner.work.route.busyAnswers() != busy) return Result.working();
            if (base == null) return Result.failed("Не нашёл ровную свободную площадку " + (width() + 2) + "×" + (depth() + 2) + " рядом. Давай выберем место попросторнее.");
        }
        if (!prepared) {
            if (placed.isEmpty() && !template) wood = chooseWood();
            if (template && !materials.decided() && !woodPlaced()) materials.chooseWoods(level, loaded.cells());
            steps = blueprint();
            total = steps.size();
            Result checked = checkBlueprint(level);
            if (checked != null) return checked;
            if (placed.cardinality() == steps.size()) return finished();
            if (template && !cleared) {
                switch (clearing.tick(level, base, builtCells())) {
                    case WORKING -> { return Result.working(); }
                    case BLOCKED -> { return Result.failed(clearing.message()); }
                    case READY -> cleared = true;
                }
            }
            Result stocked = prepareMaterials(level);
            if (stocked != null) return stocked;
            if (template) {
                Result round = round(level);
                if (round != null) return round;
            }
            prepared = true;
            owner.workStatus("Материалы готовы. Строю дом на " + base.toShortString() + ".");
        }
        if (cooldown > 0) { cooldown--; return Result.working(); }
        if (placed.cardinality() == steps.size()) {
            Result checked = checkBlueprint(level);
            if (checked != null) return checked;
            if (placed.cardinality() != steps.size()) { prepared = false; return Result.working(); }
            return finished();
        }
        if (target < 0) {
            room.refresh(level, steps, placed);
            int stage = Integer.MAX_VALUE;
            // A template building is set down from what the bag has material for; the rest is got when that is all stood.
            int gate = template ? gate() : Integer.MAX_VALUE;
            for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && !room.skips(index, steps) && (!template || buildable(index, gate))) stage = Math.min(stage, steps.get(index).stage);
            if (template && stage == Integer.MAX_VALUE) {
                // Nothing more can be set down. A cell somebody stands in is waited for only when no material is left to get first.
                if (!room.isEmpty() && (skipAffordable || !lacksMaterial())) return room.wait(level, steps);
                // The scaffolding comes down before the trip for materials.
                if (onScaffold) { onScaffold = false; scaffold.startDescend(); return Result.working(); }
                if (!room.isEmpty()) skipAffordable = true;
                prepared = false; return Result.working();
            }
            List<Integer> candidates = new ArrayList<>();
            for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && steps.get(index).stage == stage && !room.skips(index, steps) && !unreachable.contains(index) && (!template || buildable(index, gate))) candidates.add(index);
            candidates.sort(Comparator.comparingDouble(index -> owner.distanceToSqr(steps.get(index).pos.getCenter())));
            for (int index : candidates) {
                Step step = steps.get(index);
                if (!site.free(level, step)) {
                    if (!template) return Result.failed("На месте стройки появился другой блок. Не буду его ломать. Освободи место и попроси достроить дом.");
                    // A cell of a template building may hold what he digs out first: the ground the floor is sunk into, a bush grown since.
                    String why = obstruction(level, step);
                    if (why != null) return Result.failed(why);
                    select(index, level); break;
                }
                if (!site.supported(level, step)) continue;
                if (canPlaceFromHere(level, step)) { select(index, level); break; }
                // From the column only what is within reach is placed; the rest waits for the descent.
                if (onScaffold) continue;
                Path route = route(level, step);
                if (route != null && route.canReach()) { select(index, level); path = route; break; }
                // The tick's cells went elsewhere: the rest of the steps are asked next tick, none is written off.
                if (route == null && routeBusy) return Result.working();
                unreachable.add(index);
            }
            if (target < 0 && onScaffold) { onScaffold = false; scaffold.startDescend(); return Result.working(); }
            if (target < 0 && template) {
                Result up = scaffoldFor(level, stage);
                if (up != null) return up;
            }
            if (target < 0 && template && !skipAffordable && lacksMaterial()) { skipAffordable = true; prepared = false; return Result.working(); }
            if (target < 0) return Result.failed("Не могу подойти к оставшимся блокам дома. Освободи проход и скажи «дострой дом».");
        }
        Step step = steps.get(target);
        if (!site.free(level, step)) {
            if (!template) return Result.failed("Место для следующего блока занято. Сохранил стройку; продолжу, когда освободишь место.");
            return switch (clearing.clear(level, base, site.vacant(level, step.pos) ? step.second() : step.pos)) {
                case BLOCKED -> Result.failed(clearing.message());
                case WORKING, READY -> Result.working();
            };
        }
        owner.getLookControl().setLookAt(step.pos.getX() + .5, step.pos.getY() + .5, step.pos.getZ() + .5);
        double distance = owner.distanceToSqr(step.pos.getCenter());
        if (distance + .05 < bestDistance) { bestDistance = distance; progressTime = level.getGameTime(); }
        if (canPlaceFromHere(level, step)) {
            owner.getNavigation().stop();
            ItemStack material = heldMaterial(step.item());
            if (material.isEmpty()) { prepared = false; cancel(); return Result.working(); }
            if (!site.supported(level, step) || !step.state.canSurvive(level, step.pos)) return Result.failed("Опора дома изменилась. Сначала нужно восстановить площадку.");
            // Somebody standing in the cell (the player too) keeps the block out of it: the other blocks go first, this one when he steps aside.
            if (template && room.occupant(level, step) != null) { room.add(target); target = -1; path = null; return Result.working(); }
            BlockState previous = level.getBlockState(step.pos);
            BlockPos second = step.second();
            BlockState beside = second == null ? null : level.getBlockState(second);
            if (!level.setBlock(step.pos, step.state, 3)) return Result.failed("Не получилось поставить блок дома. Материалы сохранил.");
            if (second != null && !level.setBlock(second, step.secondState(), 3)) {
                level.setBlock(step.pos, previous, 3);
                level.setBlock(second, beside, 3);
                return Result.failed("Не получилось установить дверь или кровать. Материалы сохранил.");
            }
            owner.animateAction(1, step.pos, material);
            owner.rememberPlaced(step.pos);
            material.shrink(1);
            owner.setChanged();
            owner.swing(InteractionHand.MAIN_HAND);
            var sound = step.state.getSoundType();
            level.playSound(null, step.pos, sound.getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
            placed.set(target); skipAffordable = false; room.placed();
            owner.workStatus("Строю " + label() + ": " + placed.cardinality() + " из " + steps.size() + " блоков. Место: " + base.toShortString() + ".");
            target = -1; path = null; cooldown = 5; unreachable.clear(); forgetColumns();
            if (template) owner.work.progressed();
        } else {
            if (onScaffold) { target = -1; path = null; return Result.working(); }
            if (level.getGameTime() - progressTime > 160) {
                unreachable.add(target); target = -1; path = null; owner.getNavigation().stop();
                return Result.working();
            }
            if (path == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck()) path = route(level, step);
            // The route search had no cells left this tick: that is a wait, not a block he cannot get to.
            if (path == null && routeBusy) progressTime = level.getGameTime();
            if (path != null) {
                owner.work.route.walk(path);
            }
        }
        return Result.working();
    }

    /** What the building takes of the item: exactly what its unbuilt cells still need, the template being read when that has not been done (after a load, or before the place is chosen). */
    int reserved(Item item) { return readable() ? stillNeeded(item) : 0; }
    /** The template building is known (read again when that has not been done since a load, or before the place was chosen). */
    private boolean readable() { return template && building != null && owner.level() instanceof ServerLevel level && loadTemplate(level) == null; }
    /** What the blocks not yet built still take of an item: the scaffolding does not stand on the building's own material. */
    private int stillNeeded(Item item) { return (int) unbuilt().filter(item::equals).count(); }
    /** The items of the blocks not yet built: of the blueprint, or of the template (the same cells in the same order) before the blueprint is made. */
    private java.util.stream.Stream<Item> unbuilt() {
        int size = !steps.isEmpty() ? steps.size() : loaded == null ? 0 : loaded.cells().size();
        return java.util.stream.IntStream.range(0, size).filter(index -> !placed.get(index))
                .mapToObj(index -> !steps.isEmpty() ? steps.get(index).item() : loaded.cells().get(index).state().getBlock().asItem());
    }

    /** Nothing of this stage is reached from the ground: a column of dirt for the nearest block that one reaches. Null when no column helps. */
    private Result scaffoldFor(ServerLevel level, int stage) {
        if (scaffolds >= MAX_SCAFFOLDS) return null;
        List<Integer> unreached = new ArrayList<>();
        int gate = gate();
        for (int index = 0; index < steps.size(); index++)
            if (!placed.get(index) && steps.get(index).stage == stage && site.supported(level, steps.get(index)) && buildable(index, gate)) unreached.add(index);
        unreached.sort(Comparator.comparingDouble(index -> owner.distanceToSqr(steps.get(index).pos.getCenter())));
        Set<BlockPos> cells = new java.util.HashSet<>();
        for (Step step : steps) { cells.add(step.pos); if (step.second() != null) cells.add(step.second()); }
        int searched = 0;
        for (int index : unreached) {
            if (noColumn.contains(index)) continue;
            // Each search is many ray casts, and no column may help any block of the stage: a few per tick, the rest the next tick.
            if (searched++ >= COLUMN_SEARCHES_PER_TICK) return Result.working();
            CompanionScaffoldColumns.Column column = CompanionScaffoldColumns.find(this, owner, level, base.getY(), steps.get(index).pos, steps.get(index).second(), cells, scaffoldTried);
            if (column == CompanionScaffoldColumns.BUSY) return Result.working();
            if (column == null) { noColumn.add(index); continue; }
            int have = CompanionScaffold.supply(owner, this::stillNeeded);
            if (have < column.levels()) {
                // Dirt is dug where it lies; only when none is found is it asked of the player.
                Result dig = materials.dirt(level, column.levels() - have);
                return dig != null ? dig : Result.failed("Не хватает земли для лесов: нужно " + column.levels() + ", в сумке " + have + ". Положи землю в сумку и скажи «дострой».");
            }
            scaffoldTried.add(column.ground()); scaffolds++;
            scaffold.begin(column.ground(), column.levels(), this::stillNeeded);
            return Result.working();
        }
        return null;
    }

    /** He is waiting for a cell somebody stands in (the work does not count it as standing idle). */
    boolean waitingForRoom() { return owner.level() instanceof ServerLevel level && room.waiting(level); }
    private void forgetColumns() { scaffoldTried.clear(); scaffolds = 0; noColumn.clear(); }
    private void select(int index, ServerLevel level) {
        target = index; path = null; progressTime = level.getGameTime(); bestDistance = Double.MAX_VALUE;
    }

    private Result finished() {
        if (template) return new Result(State.DONE, "Построил: " + building.name() + ". Место: " + base.toShortString() + ".", 0);
        return new Result(State.DONE, "Дом готов. Вход с северной стороны, координаты: " + base.offset(2, 1, 0).toShortString() + ".", 0);
    }

    private List<Step> blueprint() {
        List<Step> result = new ArrayList<>();
        if (template) {
            for (var cell : loaded.cells()) result.add(new Step(base.offset(cell.rel()), materials.adapt(cell.state()), cell.stage()));
            return List.copyOf(result);
        }
        return CompanionPlankHouse.blueprint(base, wood);
    }

    private Block block(String suffix) { return CompanionPlankHouse.block(wood, suffix); }

    private String chooseWood() {
        List<ItemStack> carried = inventory();
        carried.add(owner.getMainHandItem()); carried.add(owner.getOffhandItem());
        return CompanionPlankHouse.chooseWood(carried, wood);
    }

    /** All recipes are simulated before changing the bag or the workbench in the world. */
    private Result prepareMaterials(ServerLevel level) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            if (owner.getItemBySlot(slot).is(ItemTags.LOGS) || owner.getItemBySlot(slot).is(ItemTags.PLANKS)
                    || steps.stream().anyMatch(step -> owner.getItemBySlot(slot).is(step.item()))) {
                CompanionActionResult putAway = owner.unequipForSupport(slot.getName());
                if (!putAway.success()) return Result.failed(putAway.message());
            }
        }
        Map<Item, Integer> required = new LinkedHashMap<>();
        // Planks must be planned last: the other recipes consume some of them.
        for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && !planks(steps.get(index).item())) required.merge(steps.get(index).item(), 1, Integer::sum);
        for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && planks(steps.get(index).item())) required.merge(steps.get(index).item(), 1, Integer::sum);
        List<ItemStack> trial = inventory();
        boolean tableNearby = owner.hasCraftingTableNearby();
        boolean needsStation = false;
        for (var entry : required.entrySet()) if (!planks(entry.getKey()) && count(trial, entry.getKey()) < entry.getValue()) needsStation = true;
        if (needsStation && !tableNearby && workbench != null && level.hasChunkAt(workbench) && level.getBlockState(workbench).is(Blocks.CRAFTING_TABLE)) {
            Step station = new Step(workbench, Blocks.CRAFTING_TABLE.defaultBlockState(), 0);
            Path returnPath = route(level, station);
            if (returnPath != null && returnPath.canReach()) {
                owner.work.route.walk(returnPath);
                owner.workStatus("Вернусь к верстаку подготовить материалы для дома.");
                return Result.working();
            }
        }
        boolean placeTable = false;
        for (var entry : required.entrySet()) {
            while (count(trial, entry.getKey()) < entry.getValue()) {
                String made = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                int want = Math.min(64, entry.getValue() - count(trial, entry.getKey()));
                // A building takes what the bag makes now, as much as it can; the rest is got afterwards (CompanionHouseMaterials).
                var plan = template ? CompanionHouseMaterials.craftMost(level, trial, made, want, tableNearby || placeTable)
                        : CompanionCrafting.plan(level, trial, made, want, tableNearby || placeTable);
                if (!plan.success()) {
                    if (plan.message().contains("места") || !template && plan.message().contains("сложная")) return Result.failed(plan.message());
                    if (template) break;
                    int equivalent = required.getOrDefault(block("planks").asItem(), 0)
                            + ((required.getOrDefault(block("slab").asItem(), 0) + 5) / 6) * 3
                            + ((required.getOrDefault(block("stairs").asItem(), 0) + 3) / 4) * 6
                            + ((required.getOrDefault(block("door").asItem(), 0) + 2) / 3) * 6 + (tableNearby ? 0 : 4);
                    int available = 0;
                    for (ItemStack stack : inventory()) {
                        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                        if (stack.is(block("planks").asItem())) available += stack.getCount();
                        if (stack.is(ItemTags.LOGS) && (id.startsWith(wood + "_") || id.startsWith("stripped_" + wood + "_"))) available += stack.getCount() * 4;
                    }
                    return new Result(State.NEED_WOOD, "Для дома нужно ещё немного дерева.", Math.clamp((equivalent - available + 3) / 4, 4, 32));
                }
                trial = plan.inventory();
                placeTable |= plan.placeTable();
            }
        }
        if (placeTable) {
            // A bench within a short walk was already walked to by the work (approachCraftingTable). One farther away is not
            // worth the trip: like a player at a new building site, set down one here (game log 22.10: the house was refused).
            BlockPos table = site.tableSite(level, base, width(), depth());
            if (table == null || !level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 3)) return Result.failed("Не нашёл безопасное место для верстака рядом со стройкой. Материалы сохранил.");
            workbench = table.immutable();
            owner.rememberPlaced(table);
        } else if (workbench == null) {
            for (BlockPos pos : BlockPos.betweenClosed(owner.blockPosition().offset(-4, -2, -4), owner.blockPosition().offset(4, 2, 4))) {
                if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) { workbench = pos.immutable(); break; }
            }
        }
        for (int slot = 0; slot < owner.getContainerSize(); slot++) owner.setItem(slot, trial.get(slot).copy());
        owner.setChanged();
        return null;
    }

    /** The block has its material in the bag or the hands. */
    private boolean affordable(Step step) {
        Item item = step.item();
        if (owner.getMainHandItem().is(item) || owner.getOffhandItem().is(item)) return true;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).is(item)) return true;
        return false;
    }
    /**
     * The layer the building may be built up to now: a block missing its material (one he can get) holds the layers above it back,
     * as a roof is not put on walls that are not there (the last blocks of the walls would be hidden behind it).
     */
    private int gate() {
        int gate = Integer.MAX_VALUE;
        for (int index = 0; index < steps.size(); index++)
            if (!placed.get(index) && !affordable(steps.get(index)) && !materials.cannotGet(steps.get(index).item())) gate = Math.min(gate, steps.get(index).stage);
        return gate;
    }
    /**
     * A door is the last block of all: put in before what is still to be carried in (a bed, a torch asked of the player) it would shut the
     * way. He opens a wooden door, but a game run (12.11, the log house resumed behind its door) found him standing in the door's cell with
     * the door shut on him, the navigation stopped and nothing to open it again: no building waits for him to go in through a door he set.
     */
    private boolean buildable(int index, int gate) {
        Step step = steps.get(index);
        if (placed.get(index) || step.stage > gate || !affordable(step)) return false;
        if (!step.door()) return true;
        for (int other = 0; other < steps.size(); other++) if (other != index && !placed.get(other) && !steps.get(other).door()) return false;
        return true;
    }
    private boolean anyBuildable() {
        int gate = gate();
        for (int index = 0; index < steps.size(); index++) if (buildable(index, gate)) return true;
        return false;
    }
    private boolean lacksMaterial() {
        for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && !affordable(steps.get(index))) return true;
        return false;
    }
    /** Stone the unfinished building still takes: not to be made into a tool. */
    int stoneReserve() { return readable() ? (int) unbuilt().filter(item -> new ItemStack(item).is(ItemTags.STONE_TOOL_MATERIALS)).count() : 0; }
    /** The ground the building stands on and walks in from (its box and a block around it, two below the floor), and its scaffolding: no dirt is dug there. */
    boolean holds(BlockPos pos) {
        return base != null && (scaffold.owns(pos) || loaded != null && pos.getX() >= base.getX() - 1 && pos.getX() <= base.getX() + width() && pos.getZ() >= base.getZ() - 1
                && pos.getZ() <= base.getZ() + depth() && pos.getY() >= base.getY() - 2 && pos.getY() <= base.getY() + height());
    }
    /** The species of the logs being cut for the building; null for any. */
    CompanionWoodKind logKind() { return materials.logKind(); }

    /**
     * Nothing in the bag can be set down (or what can is out of reach): get what is missing, a few kinds at a time, or say what he
     * cannot get. Null when there is a block to set down, or nothing more to try.
     */
    private Result round(ServerLevel level) {
        boolean buildable = anyBuildable();
        if (buildable && !skipAffordable) return null;
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) if (!placed.get(index)) order.add(index);
        order.sort(Comparator.comparingInt(index -> steps.get(index).stage));
        Map<Item, Integer> required = new LinkedHashMap<>();
        // Planks last, as in prepareMaterials: what is crafted of them is planned first.
        for (boolean last : new boolean[]{false, true}) for (int index : order) if (planks(steps.get(index).item()) == last) required.merge(steps.get(index).item(), 1, Integer::sum);
        var next = materials.next(level, required);
        if (next.gather() != null) {
            // A new round is a new start for the scaffolding: its limits were for the blocks set down from the bag as it was.
            forgetColumns();
            return materials.start(level, next.gather());
        }
        if (!next.lacking().isEmpty()) {
            // What he cannot get does not hold the layers above it back: the rest is built first, and only then the player is asked.
            if (!skipAffordable && anyBuildable()) return null;
            return Result.failed(CompanionHouseMaterials.lackingText(next.lacking(), placed.cardinality(), steps.size(), building.name()));
        }
        return buildable ? null : Result.failed("Не получается собрать материалы для «" + building.name() + "». Положи недостающее в сумку и скажи «дострой».");
    }

    private List<ItemStack> inventory() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) result.add(owner.getItem(slot).copy());
        return result;
    }
    private static int count(List<ItemStack> bag, Item item) { return bag.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private ItemStack heldMaterial(Item item) {
        if (!owner.getMainHandItem().is(item)) {
            var equipped = owner.equipForWork(BuiltInRegistries.ITEM.getKey(item).toString());
            if (!equipped.success()) return ItemStack.EMPTY;
        }
        return owner.getMainHandItem();
    }

    private BlockPos findSite(ServerLevel level) { return site.find(level, width(), depth(), height()); }

    private Result checkBlueprint(ServerLevel level) {
        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            if (!level.hasChunkAt(step.pos)) return Result.failed("Стройка сейчас за пределами загруженной области. Вернёмся поближе и продолжим.");
            if (placed.get(index) && !site.matches(level, step)) placed.clear(index);
            // A building is judged by its blocks in the world: one that already stands (a saved bit lost) is not placed again.
            if (template && !placed.get(index) && site.matches(level, step)) placed.set(index);
            if (!placed.get(index) && !site.free(level, step)) {
                if (!template) return Result.failed("На месте дома появились чужие блоки. Не буду их ломать; освободи площадку и попроси достроить дом.");
                String why = obstruction(level, step);
                if (why != null) return Result.failed(why);
            }
        }
        return null;
    }
    /** A block of wood of the template stands already: the species the building is made of is no longer a choice. */
    private boolean woodPlaced() {
        for (int index = placed.nextSetBit(0); index >= 0 && index < loaded.cells().size(); index = placed.nextSetBit(index + 1))
            if (CompanionHouseMaterials.wooden(loaded.cells().get(index).state())) return true;
        return false;
    }
    /** Why a cell of the step cannot be dug out by him; null when each is vacant or his to dig. */
    private String obstruction(ServerLevel level, Step step) {
        for (BlockPos cell : step.second() == null ? new BlockPos[]{step.pos} : new BlockPos[]{step.pos, step.second()}) {
            String why = site.vacant(level, cell) ? null : clearing.obstruction(level, cell);
            if (why != null) return why;
        }
        return null;
    }
    /** What of the building already stands as planned: not to be cleared away with the rest of the box. */
    private Set<BlockPos> builtCells() {
        Set<BlockPos> cells = new java.util.HashSet<>();
        for (int index = 0; index < steps.size(); index++) if (placed.get(index)) { cells.add(steps.get(index).pos); if (steps.get(index).second() != null) cells.add(steps.get(index).second()); }
        return cells;
    }
    private boolean canPlaceFromHere(ServerLevel level, Step step) {
        if (!owner.onGround() || !template && step.door() && owner.getZ() >= base.getZ()) return false;
        AABB box = new AABB(step.pos);
        if (step.second() != null) box = box.minmax(new AABB(step.second()));
        return level.noCollision(box) && visible(level, owner.getEyePosition(), step.pos);
    }
    boolean visible(ServerLevel level, Vec3 eye, BlockPos pos) {
        // Use visible faces/edges, as a player would. A ray to the cube center alone is
        // hidden by the finished wall beneath a roof tile or by an adjacent half-slab.
        double x = Math.clamp(eye.x, pos.getX() + .01, pos.getX() + .99);
        double z = Math.clamp(eye.z, pos.getZ() + .01, pos.getZ() + .99);
        double y = Math.clamp(eye.y, pos.getY() + .01, pos.getY() + .99);
        for (Vec3 aim : new Vec3[]{new Vec3(x, y, z), new Vec3(x, pos.getY() + .49, z), pos.getCenter()}) {
            if (eye.distanceToSqr(aim) > 4.5 * 4.5) continue;
            var hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }
    private boolean routeBusy;  boolean routeBusy() { return routeBusy; }
    /** The way to a cell from which the block at {@code pos} is within reach and in sight (for the site clearing); null when there is none. */
    Path routeToBlock(ServerLevel level, BlockPos pos) { return route(level, new Step(pos, Blocks.AIR.defaultBlockState(), 0)); }
    private Path route(ServerLevel level, Step step) {
        Set<BlockPos> feet = new java.util.HashSet<>();
        for (int y = 0; y <= 1; y++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos pos = new BlockPos(step.pos.getX() + dx, base.getY() + y, step.pos.getZ() + dz);
            if (!template && step.door() && pos.getZ() >= base.getZ()) continue;
            if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below()) || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            AABB body = new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2, pos.getX() + .8, pos.getY() + owner.getBbHeight(), pos.getZ() + .8);
            if (body.intersects(new AABB(step.pos)) || step.second() != null && body.intersects(new AABB(step.second()))) continue;
            if (!level.noCollision(owner, body) || !visible(level, Vec3.atBottomCenterOf(pos).add(0, owner.getEyeHeight(), 0), step.pos)) continue;
            feet.add(pos);
        }
        var reach = feet.isEmpty() ? null : owner.work.route.cells(feet, 0);
        routeBusy = reach != null && reach.busy();
        return reach == null ? null : reach.path();
    }

    void save(CompoundTag tag) {
        // While the place of a new building is asked for, the old one is what the world keeps.
        CompoundTag data = awaiting() && stash != null ? stash : record();
        if (data != null) tag.put(key(), data.copy());
    }
    /** The record of this house as the world keeps it; null when there is none. Only the scaffolding of a building not yet set down is kept. */
    private CompoundTag record() {
        if (base == null) {
            if (elsewhere != null) return elsewhere.copy();
            if (!template || !scaffold.hasBlocks()) return null;
            CompoundTag only = new CompoundTag();
            scaffold.save(only); only.putString("Dimension", owner.level().dimension().location().toString());
            return only;
        }
        CompoundTag data = new CompoundTag();
        data.putLong("Base", base.asLong());
        if (template) { data.putString("Building", building.name()); data.putInt("Rotation", rotation.ordinal()); data.putInt("Total", total); scaffold.save(data); data.put("Woods", materials.save()); }
        else data.putString("Wood", wood);
        data.putString("Dimension", owner.level().dimension().location().toString());
        data.putLongArray("Placed", placed.toLongArray());
        data.putLong("Touched", touched);
        if (workbench != null) data.putLong("Workbench", workbench.asLong());
        return data;
    }
    void load(CompoundTag tag) {
        base = null; workbench = null; placed.clear(); scaffold.forget(); begin(); elsewhere = null; unplaced = false; stash = null; touched = 0;
        if (template) { building = null; loaded = null; steps = List.of(); total = 0; materials.reset(); rotation = Rotation.NONE; }
        CompoundTag data = tag.getCompound(key());
        // The scaffolding of a building not set down yet (an order for another one was waiting for its place) is taken down by the next order.
        if (template && !data.contains("Base") && data.contains("Scaffold") && owner.level().dimension().location().toString().equals(data.getString("Dimension"))) { scaffold.load(data); return; }
        if (!data.contains("Base") || (template ? CompanionVillageBuildings.find(data.getString("Building")) == null : !CompanionPlankHouse.WOODS.contains(data.getString("Wood")))) return;
        if (!owner.level().dimension().location().toString().equals(data.getString("Dimension"))) { elsewhere = data.copy(); return; }
        base = BlockPos.of(data.getLong("Base")); touched = data.getLong("Touched");
        if (template) {
            building = CompanionVillageBuildings.find(data.getString("Building")); total = data.getInt("Total"); scaffold.load(data);
            rotation = Rotation.values()[Math.floorMod(data.getInt("Rotation"), Rotation.values().length)];
            if (data.contains("Woods")) materials.load(data.getCompound("Woods"));
        }
        else { wood = data.getString("Wood"); steps = blueprint(); }
        if (data.contains("Workbench")) workbench = BlockPos.of(data.getLong("Workbench"));
        placed.or(BitSet.valueOf(data.getLongArray("Placed")));
        int size = template ? total : steps.size();
        placed.clear(size, Math.max(size, placed.length()));
    }
}
