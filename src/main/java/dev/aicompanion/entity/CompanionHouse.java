package dev.aicompanion.entity;

import dev.aicompanion.schematic.CompanionSchematic;
import dev.aicompanion.schematic.CompanionSchematics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A building from a schematic the player set down in the world, built from real items block by block, bottom up. Blocks of the
 * world that stand in its way are broken first (never a chest with things in it). Progress is the world itself: whatever already
 * stands where the schematic wants it counts as done, so a stopped, reloaded or half-built house is simply continued.
 */
public final class CompanionHouse {
    public enum State { WORKING, NEED_MATERIALS, DONE, FAILED }
    public record Result(State state, String message) {
        static Result working() { return new Result(State.WORKING, ""); }
        static Result failed(String message) { return new Result(State.FAILED, message); }
    }
    /** An item to have in the bag before the next batch of blocks: the total, not the missing part. */
    public record Need(String item, int count) { }
    /** The next round of a house order: what to get, how many blocks it is for, or the end. */
    public record Round(List<Need> needs, int batch, boolean done, String refusal, String note) {
        static Round refused(String why) { return new Round(List.of(), 0, false, why, ""); }
    }
    /** stripped: set down from the plain log or wood and stripped with an axe on the spot, as a player does it. */
    private record Step(BlockPos pos, BlockState state, Item item, int items, BlockPos partner, BlockState partnerState, int stage, boolean stripped) { }
    private enum Cost { CHEAP, COSTLY, NONE }

    static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
    private static final double REACH = 4.5D;
    /** Free bag slots kept for the drops of a clearing and the raw materials of the next crafts. */
    private static final int KEEP_FREE = 3;
    private static final int APPROACH_TICKS = 160, STILL_TICKS = 40;
    private static final int STAGE_WINDOW = 3;
    private static final int MAX_SCAFFOLDS = 24;
    private static final List<Property<?>> ORIENTATION = List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.FACING_HOPPER, BlockStateProperties.VERTICAL_DIRECTION, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS,
            BlockStateProperties.HALF, BlockStateProperties.SLAB_TYPE, BlockStateProperties.DOUBLE_BLOCK_HALF, BlockStateProperties.BED_PART,
            BlockStateProperties.ROTATION_16, BlockStateProperties.ATTACH_FACE, BlockStateProperties.DOOR_HINGE, BlockStateProperties.ORIENTATION,
            BlockStateProperties.BELL_ATTACHMENT, BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.LAYERS,
            BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.RAIL_SHAPE, BlockStateProperties.RAIL_SHAPE_STRAIGHT);
    /** Ground blocks no skill of his can get: plain dirt goes in their place (grass grows over it by itself). */
    private static final Set<Block> AS_DIRT = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.FARMLAND, Blocks.DIRT_PATH);

    private final CompanionEntity owner;
    private final CompanionBlockBreaking breaking;
    private final CompanionScaffold scaffold;
    private volatile CompanionSchematic.Placement placement;
    private CompanionSchematic schematic;
    private String loadError = "";
    private String dimension = "";
    /** Wood species of the schematic replaced by the one at hand: «spruce» → «birch». */
    private final Map<String, String> woods = new LinkedHashMap<>();
    private List<Step> steps = List.of();
    private final BitSet done = new BitSet();
    private final List<BlockPos> clears = new ArrayList<>();
    private Set<Long> interior = Set.of();
    private BlockPos anchor;
    private volatile boolean complete;
    private final Set<Item> unobtainable = new HashSet<>();
    private long lastRoundProgress = -1;
    private int stuckRounds;
    private String lastJobMessage = "";
    /** A warning of a round is said once, not again with every next batch. */
    private String lastNote = "";
    /** The last round only built from the bag: if that set down nothing, the next one gets materials instead. */
    private boolean lastRoundFromBag;
    // The running build_schematic order.
    private int target = -1;
    private BlockPos clearTarget;
    private Path path;
    private int cooldown;
    private long progressTime;
    private double bestDistance;
    private boolean approachFailed;
    private Vec3 stillAt;
    private long stillSince;
    private int window = STAGE_WINDOW;
    private Deque<Integer> candidates;
    private final Set<Integer> unreachable = new HashSet<>();
    private final Set<Integer> blocked = new HashSet<>();
    private final Set<Integer> scaffoldTried = new HashSet<>();
    private final Set<BlockPos> skippedClears = new HashSet<>();
    private final Map<BlockPos, String> protectedBlocks = new LinkedHashMap<>();
    private boolean onScaffold;
    private int scaffolds;
    private int placedThisOrder, clearedThisOrder;
    private long interiorCheckedAt = -1000;
    private boolean interiorCached;

    CompanionHouse(CompanionEntity owner) {
        this.owner = owner; breaking = new CompanionBlockBreaking(owner); scaffold = new CompanionScaffold(owner);
    }

    // ---- The placement ----

    public CompanionSchematic.Placement placement() { return placement; }
    /** A chosen house whose last block is not confirmed yet: what the client keeps showing as a ghost. */
    public CompanionSchematic.Placement unfinished() { return complete ? null : placement; }
    String title() { return placement == null ? "дом" : CompanionSchematic.title(placement.file()); }

    /** The player set the schematic down here. The same placement again keeps the progress; another one starts a new house. */
    CompanionActionResult choose(ServerLevel level, CompanionSchematic.Placement chosen) {
        if (chosen == null) return CompanionActionResult.failed("Место для дома не выбрано.");
        CompanionSchematic loaded;
        try { loaded = CompanionSchematics.load(chosen.file()); }
        catch (IOException broken) { return CompanionActionResult.failed("Схема не загрузилась: " + broken.getMessage() + "."); }
        BoundingBox box = chosen.box(loaded);
        if (box.minY() < level.getMinBuildHeight() || box.maxY() >= level.getMaxBuildHeight())
            return CompanionActionResult.failed("Схема «" + loaded.title() + "» не помещается по высоте мира. Поставь её пониже или повыше.");
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ()))
            return CompanionActionResult.failed("Часть места под схему ещё не загружена. Поставь её поближе.");
        double reach = Math.max(64, owner.getSearchRadius());
        if (owner.distanceToSqr(box.getCenter().getCenter()) > reach * reach)
            return CompanionActionResult.failed("Это место слишком далеко от меня. Поставь схему в пределах " + (int) reach + " блоков.");
        boolean same = chosen.equals(placement) && owner.level().dimension().location().toString().equals(dimension);
        placement = chosen; schematic = loaded; loadError = ""; complete = false;
        dimension = owner.level().dimension().location().toString();
        if (!same) { woods.clear(); done.clear(); }
        unobtainable.clear(); lastRoundProgress = -1; stuckRounds = 0; scaffoldTried.clear(); lastNote = ""; lastJobMessage = ""; interiorCheckedAt = -1000; lastRoundFromBag = false;
        rebuild();
        refresh(level);
        String unknown = loaded.unknown() > 0 ? " Блоков незнакомых этой игре видов: " + loaded.unknown() + " — эти места оставлю как есть." : "";
        return CompanionActionResult.ok((same ? "Продолжаю стройку «" : "Буду строить «") + loaded.title() + "» на " + box.minX() + " " + box.minY() + " " + box.minZ()
                + ": " + steps.size() + " блоков, уже на месте " + done.cardinality() + "." + unknown);
    }

    private boolean ensureLoaded() {
        if (placement == null) return false;
        if (schematic != null) return true;
        try { schematic = CompanionSchematics.load(placement.file()); loadError = ""; rebuild(); return true; }
        catch (IOException broken) { loadError = "Схема «" + title() + "» не загрузилась: " + broken.getMessage() + "."; return false; }
    }

    private void rebuild() {
        List<Step> list = new ArrayList<>();
        CompanionSchematic.Placement where = placement;
        BoundingBox box = where.box(schematic);
        schematic.forEach((x, y, z, raw) -> {
            if (raw.isAir() || raw.getBlock() instanceof LiquidBlock) return;
            BlockState state = prepared(where.state(raw));
            // The second half of a door, a bed or a tall flower is set down together with the first.
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return;
            if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return;
            Item item = state.getBlock().asItem();
            if (item == Items.AIR) return;
            boolean stripped = false;
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (path.startsWith("stripped_")) {
                var bark = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(path.substring("stripped_".length())));
                if (bark.isPresent() && bark.get().asItem() != Items.AIR) { item = bark.get().asItem(); stripped = true; }
            }
            BlockPos pos = where.world(x, y, z);
            BlockPos partner = null;
            BlockState partnerState = null;
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) { partner = pos.above(); partnerState = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER); }
            else if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                partner = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)); partnerState = state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
            }
            boolean full = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            // In each layer what rests on the building goes before what spans or overhangs: the tile capping a wall, once its
            // neighbours are down, can no longer be seen from anywhere (the order the first 5×5 house was built in).
            BlockState under = y > 0 ? schematic.get(x, y - 1, z) : null;
            boolean spans = y > 0 && (under == null || under.isAir());
            list.add(new Step(pos.immutable(), state, item, items(state), partner, partnerState, (pos.getY() - box.minY()) * 4 + (spans ? 2 : 0) + (full ? 0 : 1), stripped));
        });
        list.sort(Comparator.comparingInt(Step::stage));
        steps = List.copyOf(list);
        done.clear(); candidates = null; target = -1;
        computeInterior(where, box);
    }

    /** The state as it can honestly be built: without the water of a waterlogged block, wood of the species at hand, dirt for grass. */
    private BlockState prepared(BlockState state) {
        if (AS_DIRT.contains(state.getBlock())) return Blocks.DIRT.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        if (state.hasProperty(BlockStateProperties.EXTENDED)) state = state.setValue(BlockStateProperties.EXTENDED, false);
        if (state.hasProperty(BlockStateProperties.POWERED)) state = state.setValue(BlockStateProperties.POWERED, false);
        if (state.hasProperty(BlockStateProperties.LIT) && state.is(BlockTags.CANDLES)) state = state.setValue(BlockStateProperties.LIT, false);
        if (woods.isEmpty()) return state;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String from = species(path);
        String to = from == null ? null : woods.get(from);
        if (to == null) return state;
        String swapped = path.replaceFirst("^(stripped_)?" + from + "_", "$1" + to + "_");
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace(swapped));
        return block.isEmpty() ? state : copy(state, block.get());
    }
    static String species(String path) {
        String bare = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
        String found = null;
        for (String wood : WOODS) if (bare.startsWith(wood + "_") && (found == null || wood.length() > found.length())) found = wood;
        return found;
    }
    private static BlockState copy(BlockState from, Block to) {
        BlockState result = to.defaultBlockState();
        for (Property<?> property : from.getProperties()) if (result.hasProperty(property)) result = copyValue(from, result, property);
        return result;
    }
    private static <T extends Comparable<T>> BlockState copyValue(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
    /** Items one block takes: a double slab is two slabs, four candles are four. */
    private static int items(BlockState state) {
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        for (var property : List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT))
            if (state.hasProperty(property)) return state.getValue(property);
        if (state.is(Blocks.SNOW)) return state.getValue(BlockStateProperties.LAYERS);
        return 1;
    }

    private void computeInterior(CompanionSchematic.Placement where, BoundingBox box) {
        Set<Long> cells = new HashSet<>();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 center = Vec3.atCenterOf(box.getCenter());
        for (int x = 0; x < schematic.sizeX(); x++) for (int z = 0; z < schematic.sizeZ(); z++) {
            int roof = -1;
            for (int y = schematic.sizeY() - 1; y >= 0; y--) { BlockState state = schematic.get(x, y, z); if (state != null && !state.isAir()) { roof = y; break; } }
            if (roof < 0) continue;
            boolean floorSeen = false;
            for (int y = 0; y < roof; y++) {
                BlockState state = schematic.get(x, y, z);
                if (state != null && !state.isAir()) { floorSeen = true; continue; }
                if (!floorSeen || state == null) continue;
                BlockPos pos = where.world(x, y, z);
                cells.add(pos.asLong());
                BlockState below = y > 0 ? schematic.get(x, y - 1, z) : null;
                BlockState head = schematic.get(x, y + 1, z);
                if (below == null || !below.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || head == null || !head.isAir()) continue;
                double score = Vec3.atCenterOf(pos).subtract(center).horizontalDistanceSqr() + (pos.getY() - box.minY()) * 50D;
                if (score < bestScore) { bestScore = score; best = pos.immutable(); }
            }
        }
        interior = Set.copyOf(cells); anchor = best;
    }

    // ---- Inside the house (chests and other blocks set down «внутри дома») ----

    BlockPos interiorAnchor() { return anchor; }
    boolean inside(BlockPos pos) { return interior.contains(pos.asLong()); }
    boolean interiorAvailable(ServerLevel level) {
        if (placement == null || anchor == null || !ensureLoaded() || !dimension.equals(level.dimension().location().toString())) return false;
        if (level.getGameTime() - interiorCheckedAt < 40) return interiorCached;
        interiorCheckedAt = level.getGameTime();
        // A saved placement alone does not prove there is still a house in the world.
        int standing = 0;
        for (Step step : steps) if (level.hasChunkAt(step.pos) && matches(level, step)) standing++;
        interiorCached = level.hasChunkAt(anchor) && standing * 10 >= steps.size() * 9
                && level.getBlockState(anchor.below()).isFaceSturdy(level, anchor.below(), Direction.UP)
                && level.getBlockState(anchor).getCollisionShape(level, anchor).isEmpty();
        return interiorCached;
    }

    // ---- Rounds: what to get before the next batch ----

    /** Called between the steps of the order: the world decides what is done, the bag what can be built, the recipes what can be got. */
    Round round(ServerLevel level) {
        if (placement == null) return Round.refused("Сначала нужно выбрать место для дома.");
        if (!dimension.equals(level.dimension().location().toString())) return Round.refused("Наша стройка в другом измерении.");
        if (!ensureLoaded()) return Round.refused(loadError);
        BoundingBox box = placement.box(schematic);
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) return Round.refused("Стройка сейчас далеко, её место не загружено. Подойдём поближе — и продолжу.");
        String note = chooseWoods(level);
        refresh(level);
        int remaining = steps.size() - done.cardinality();
        int clearing = (int) clears.stream().filter(pos -> !skippedClears.contains(pos)).count();
        if (remaining == 0 && clearing == 0 && !scaffold.hasBlocks()) { complete = true; return new Round(List.of(), 0, true, null, note); }
        long progress = done.cardinality() * 100_003L - clearing;
        stuckRounds = progress == lastRoundProgress ? stuckRounds + 1 : 0;
        lastRoundProgress = progress;
        if (stuckRounds >= 3) return Round.refused("Стройка «" + title() + "» не продвигается." + (lastJobMessage.isBlank() ? "" : " " + lastJobMessage));

        Map<Item, Integer> bag = bag();
        int free = freeSlots();
        int budget = Math.max(1, free - KEEP_FREE);
        boolean axe = hasAxe(bag);
        // What the bag already has material for is built first, before any trip for more (game log 27.10: the first round went
        // for iron for an anvil and lanterns before a single block stood; «начни именно стройку, потом добудешь остальное»).
        int fromBag = 0;
        Map<Item, Integer> left = new HashMap<>(bag);
        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            if (done.get(index) || step.stripped && !axe || left.getOrDefault(step.item, 0) < step.items) continue;
            left.merge(step.item, -step.items, Integer::sum);
            fromBag++;
        }
        // Unless the last such batch set down nothing: those blocks wait for others, so get the material of the others.
        boolean bagFirst = fromBag > 0 && !(lastRoundFromBag && placedThisOrder == 0 && clearedThisOrder == 0);
        lastRoundFromBag = bagFirst;
        Map<Item, Integer> wanted = new LinkedHashMap<>();
        int batch = bagFirst ? fromBag : 0;
        if (!bagFirst) {
            // Cheap materials (wood, stone, soil, crafts) for the lowest layers first, a few kinds at a time so that building
            // starts soon; what needs ore or hunting (lanterns, an anvil) only once nothing cheap is left — one kind per round.
            Map<Item, Cost> costs = new HashMap<>();
            for (Cost pass : List.of(Cost.CHEAP, Cost.COSTLY)) {
                wanted.clear(); batch = 0;
                int stacks = 0, kinds = 0, most = pass == Cost.CHEAP ? 4 : 1;
                for (int index = 0; index < steps.size(); index++) {
                    if (done.get(index)) continue;
                    Step step = steps.get(index);
                    int have = bag.getOrDefault(step.item, 0);
                    int before = wanted.getOrDefault(step.item, 0), total = before + step.items;
                    if (total > have) {
                        Cost cost = costs.computeIfAbsent(step.item, item -> cost(item, have + Math.min(64, stillNeeded(item))));
                        if (cost == Cost.NONE || pass == Cost.CHEAP && cost == Cost.COSTLY) continue;
                    }
                    int missingBefore = Math.max(0, before - have), missing = Math.max(0, total - have);
                    // One gathering order takes at most a stack; the rest of that material waits for the next round.
                    if (missing > 64) continue;
                    if (missingBefore == 0 && missing > 0) { if (kinds >= most) continue; kinds++; }
                    int stack = Math.max(1, new ItemStack(step.item).getMaxStackSize());
                    int extra = ceil(missing, stack) - ceil(missingBefore, stack);
                    if (stacks + extra > budget) break;
                    stacks += extra; wanted.put(step.item, total); batch++;
                }
                if (kinds > 0) break;
            }
        }
        List<Need> needs = new ArrayList<>();
        for (var entry : wanted.entrySet()) if (entry.getValue() > bag.getOrDefault(entry.getKey(), 0))
            needs.add(new Need(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue()));
        if (!needs.isEmpty() && !axe) {
            // Stripped logs and wood are stripped by hand with an axe once set down.
            for (var entry : wanted.keySet()) if (steps.stream().anyMatch(step -> step.stripped && step.item == entry)) {
                needs.add(new Need("minecraft:wooden_axe", 1)); break;
            }
        }
        // Tall walls and roofs are reached from scaffolding: a stack of dirt for it, given back when it is taken down.
        if (!needs.isEmpty()) {
            if (tall() && CompanionScaffold.supply(owner, this::stillNeeded) < 12 && free > KEEP_FREE + 1 && !owner.obtainPlan("minecraft:dirt", bag.getOrDefault(Items.DIRT, 0) + 16).refused())
                needs.add(new Need("minecraft:dirt", bag.getOrDefault(Items.DIRT, 0) + 16));
        }
        Map<Item, Integer> lacking = new LinkedHashMap<>();
        needs = ordered(level, needs);
        for (Item item : unobtainable) {
            int count = 0;
            for (int index = 0; index < steps.size(); index++) if (!done.get(index) && steps.get(index).item == item) count += steps.get(index).items;
            if (count > 0) lacking.put(item, Math.max(0, count - bag.getOrDefault(item, 0)));
        }
        lacking.values().removeIf(count -> count <= 0);
        if (batch == 0 && needs.isEmpty() && clearing == 0) {
            if (!lacking.isEmpty()) return Round.refused("Построил всё, что смог: " + done.cardinality() + " из " + steps.size() + " блоков «" + title() + "». "
                    + "Не хватает: " + materials(lacking) + " — это я добыть не умею. Положи мне в сумку и скажи «дострой дом».");
            if (free == 0) return Round.refused("Сумка забита, материалы для дома некуда положить. Разгрузи меня — и продолжу.");
        }
        if (!lacking.isEmpty()) note = (note.isEmpty() ? "" : note + " ") + "Добыть не умею: " + materials(lacking) + " — эти блоки оставлю, если их не дашь.";
        String said = note.equals(lastNote) ? "" : note;
        if (!note.isBlank()) lastNote = note;
        return new Round(List.copyOf(needs), Math.max(1, batch), false, null, said);
    }

    /** How hard a material is to get by his own skills: never, by wood, stone, soil and crafts, or by ore and hunting. */
    private Cost cost(Item item, int count) {
        if (unobtainable.contains(item)) return Cost.NONE;
        var plan = owner.obtainPlan(BuiltInRegistries.ITEM.getKey(item).toString(), Math.max(1, count));
        if (plan.refused()) { unobtainable.add(item); return Cost.NONE; }
        boolean far = plan.steps().size() > 8 || plan.steps().stream().anyMatch(step -> step.type().equals("mine_ore") || step.type().equals("hunt_animals"));
        return far ? Cost.COSTLY : Cost.CHEAP;
    }

    /** Materials that others are made of are got last: planks after the stairs that take planks, stone after the stone bricks. */
    private static List<Need> ordered(ServerLevel level, List<Need> needs) {
        Map<String, Integer> usedBy = new HashMap<>();
        for (Need ingredient : needs) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient.item())));
            int count = 0;
            for (Need product : needs) {
                if (product == ingredient) continue;
                Item made = BuiltInRegistries.ITEM.get(ResourceLocation.parse(product.item()));
                boolean uses = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                        .anyMatch(holder -> holder.value().getResultItem(level.registryAccess()).is(made)
                                && holder.value().getIngredients().stream().anyMatch(part -> !part.isEmpty() && part.test(stack)))
                        || level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).stream()
                        .anyMatch(holder -> holder.value().getResultItem(level.registryAccess()).is(made) && holder.value().getIngredients().getFirst().test(stack));
                if (uses) count++;
            }
            usedBy.put(ingredient.item(), count);
        }
        List<Need> result = new ArrayList<>(needs);
        result.sort(Comparator.comparingInt(need -> usedBy.getOrDefault(need.item(), 0)));
        return result;
    }

    /**
     * Wood comes from the trees he can reach: a species with no wood in the bag and no trees around is built from the species
     * that is there. Said once, so the player knows why the spruce house is birch.
     */
    private String chooseWoods(ServerLevel level) {
        Map<String, Integer> need = new LinkedHashMap<>();
        CompanionSchematic.Placement where = placement;
        schematic.forEach((x, y, z, raw) -> {
            if (raw.isAir()) return;
            String wood = species(BuiltInRegistries.BLOCK.getKey(raw.getBlock()).getPath());
            if (wood != null && !woods.containsKey(wood) && !matchesRaw(level, where, x, y, z, raw)) need.merge(wood, 1, Integer::sum);
        });
        if (need.isEmpty()) return "";
        Map<String, Integer> have = new HashMap<>();
        for (ItemStack stack : carried()) {
            String wood = species(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            if (wood != null) have.merge(wood, stack.getCount() * (stack.is(ItemTags.LOGS) ? 4 : 1), Integer::sum);
        }
        Map<String, Integer> around = new HashMap<>();
        var logs = CompanionLogSearch.find(level, owner.blockPosition(), owner.getSearchRadius());
        for (BlockPos log : logs.subList(0, Math.min(64, logs.size()))) {
            String wood = species(BuiltInRegistries.BLOCK.getKey(level.getBlockState(log).getBlock()).getPath());
            if (wood != null) around.merge(wood, 4, Integer::sum);
        }
        StringBuilder said = new StringBuilder();
        for (var entry : need.entrySet()) {
            String wood = entry.getKey();
            if (have.getOrDefault(wood, 0) + around.getOrDefault(wood, 0) >= Math.min(entry.getValue(), 16)) continue;
            String best = null;
            int bestAmount = 0;
            for (String other : WOODS) {
                int amount = have.getOrDefault(other, 0) * 2 + around.getOrDefault(other, 0);
                if (amount > bestAmount) { bestAmount = amount; best = other; }
            }
            if (best == null || best.equals(wood)) continue;
            woods.put(wood, best);
            said.append("Дерева «").append(woodName(wood)).append("» рядом нет — вместо него возьму «").append(woodName(best)).append("». ");
        }
        if (said.isEmpty()) return "";
        rebuild();
        return said.toString().trim();
    }
    private static String woodName(String wood) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(wood + "_planks"))).getHoverName().getString(); }
    private static boolean matchesRaw(ServerLevel level, CompanionSchematic.Placement where, int x, int y, int z, BlockState raw) {
        BlockPos pos = where.world(x, y, z);
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(raw.getBlock());
    }

    /** The world decides: which blocks already stand right, which cells of air are still filled. */
    private void refresh(ServerLevel level) {
        done.clear();
        for (int index = 0; index < steps.size(); index++) if (level.hasChunkAt(steps.get(index).pos) && matches(level, steps.get(index))) done.set(index);
        clears.clear();
        CompanionSchematic.Placement where = placement;
        schematic.forEach((x, y, z, raw) -> {
            if (!raw.isAir()) return;
            BlockPos pos = where.world(x, y, z);
            if (!level.hasChunkAt(pos) || scaffold.owns(pos)) return;
            BlockState actual = level.getBlockState(pos);
            if (!actual.isAir() && !(actual.getBlock() instanceof LiquidBlock) && actual.getFluidState().isEmpty()) clears.add(pos.immutable());
        });
        // From the top down: nothing falls on him, and a tree goes crown first.
        clears.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
    }

    // ---- The build order ----

    void begin() {
        cancel();
        unreachable.clear(); blocked.clear(); skippedClears.clear(); protectedBlocks.clear(); scaffoldTried.clear();
        placedThisOrder = clearedThisOrder = 0; scaffolds = 0; window = STAGE_WINDOW;
        if (owner.level() instanceof ServerLevel level && ensureLoaded() && dimension.equals(level.dimension().location().toString())) refresh(level);
        // Interrupted on the scaffolding last time: down first.
        if (scaffold.hasBlocks()) scaffold.startDescend();
    }

    void cancel() {
        target = -1; clearTarget = null; path = null; cooldown = 0; candidates = null; onScaffold = false;
        breaking.reset(); scaffold.reset();
        // The placement and the scaffolding belong to the world, not to the order.
    }

    /** One tick of build_schematic: clear the cells that must be empty, then set down what the bag has material for. */
    Result tick(ServerLevel level) {
        if (placement == null) return Result.failed("Сначала нужно выбрать место для дома.");
        if (!ensureLoaded()) return Result.failed(loadError);
        if (scaffold.descending()) {
            var outcome = scaffold.descend(level);
            if (outcome == CompanionScaffold.Outcome.WORKING) { owner.workStatus("Разбираю леса."); return Result.working(); }
            onScaffold = false; candidates = null;
            if (outcome == CompanionScaffold.Outcome.FAILED) lastJobMessage = scaffold.failure();
            return Result.working();
        }
        if (scaffold.climbing()) {
            var outcome = scaffold.climb(level);
            if (outcome == CompanionScaffold.Outcome.WORKING) { owner.workStatus("Поднимаюсь на леса."); return Result.working(); }
            if (outcome == CompanionScaffold.Outcome.FAILED) { lastJobMessage = scaffold.failure(); return Result.working(); }
            if (!onScaffold) { onScaffold = true; candidates = null; }
        }
        if (!owner.onGround()) return Result.working();
        openDoorAround(level);
        if (cooldown > 0) { cooldown--; return Result.working(); }
        if (clearTarget == null && target < 0 && !onScaffold) clearTarget = nextClear(level);
        if (clearTarget != null) return clear(level);
        if (target < 0) {
            Result chosen = select(level);
            if (chosen != null || target < 0) return chosen == null ? Result.working() : chosen;
        }
        return place(level);
    }

    private BlockPos nextClear(ServerLevel level) {
        BlockPos best = null;
        for (BlockPos pos : clears) {
            if (skippedClears.contains(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (best == null || pos.getY() > best.getY() || pos.getY() == best.getY() && owner.distanceToSqr(pos.getCenter()) < owner.distanceToSqr(best.getCenter())) best = pos;
        }
        if (best != null) { path = null; approachFailed = false; bestDistance = Double.MAX_VALUE; progressTime = level.getGameTime(); stillAt = null; }
        return best;
    }

    private Result clear(ServerLevel level) {
        BlockPos pos = clearTarget;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) { clearTarget = null; return Result.working(); }
        String why = unbreakable(level, pos, state);
        if (why != null) { protectedBlocks.put(pos, why); skippedClears.add(pos); clearTarget = null; return Result.working(); }
        if (!approach(level, pos, null)) {
            if (approachFailed) { skippedClears.add(pos); clearTarget = null; path = null; }
            return Result.working();
        }
        owner.getNavigation().stop();
        owner.getLookControl().setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        owner.workStatus("Расчищаю место под «" + title() + "»: убираю «" + state.getBlock().getName().getString() + "».");
        if (breakBlock(level, pos, state)) { clearTarget = null; path = null; cooldown = 2; unreachable.clear(); }
        return Result.working();
    }

    /** The next block to set down, nearest first among the lowest layers that have material; null while still choosing. */
    private Result select(ServerLevel level) {
        Map<Item, Integer> bag = bag();
        if (candidates == null) {
            int lowest = Integer.MAX_VALUE;
            for (int index = 0; index < steps.size(); index++)
                if (!done.get(index) && !blocked.contains(index) && enough(bag, steps.get(index))) lowest = Math.min(lowest, steps.get(index).stage);
            if (lowest == Integer.MAX_VALUE) return stuck(level, bag);
            List<Integer> list = new ArrayList<>();
            for (int index = 0; index < steps.size(); index++) {
                Step step = steps.get(index);
                if (done.get(index) || blocked.contains(index) || !enough(bag, step)) continue;
                if (onScaffold ? owner.getEyePosition().distanceToSqr(step.pos.getCenter()) > (REACH + 1) * (REACH + 1)
                        : unreachable.contains(index) || step.stage > lowest + window) continue;
                list.add(index);
            }
            list.sort(Comparator.comparingInt((Integer index) -> steps.get(index).stage).thenComparingDouble(index -> owner.distanceToSqr(steps.get(index).pos.getCenter())));
            candidates = new ArrayDeque<>(list);
        }
        int routes = 0;
        while (!candidates.isEmpty() && routes < 2) {
            int index = candidates.poll();
            Step step = steps.get(index);
            if (done.get(index)) continue;
            if (matches(level, step)) { done.set(index); continue; }
            BlockPos focus = focus(level, step);
            if (focus.equals(step.pos) && !supportedNow(level, step)) continue;
            BlockPos partner = focus.equals(step.pos) ? step.partner : null;
            if (canWorkFromHere(level, focus, partner)) { select(index, level); return null; }
            if (onScaffold) continue;
            routes++;
            Path route = route(level, focus, partner);
            if (route != null && route.canReach()) { select(index, level); path = route; return null; }
            unreachable.add(index);
        }
        if (!candidates.isEmpty()) return null;
        candidates = null;
        if (onScaffold) { onScaffold = false; scaffold.startDescend(); return null; }
        if (window < Integer.MAX_VALUE / 4 && window < steps.size()) { window = window * 2 + 4; return null; }
        return stuck(level, bag);
    }

    private void select(int index, ServerLevel level) {
        target = index; path = null; approachFailed = false; progressTime = level.getGameTime(); bestDistance = Double.MAX_VALUE;
        stillAt = null;
    }

    /** Nothing more can be set down from where he is with what he has: more material, scaffolding, or the end. */
    private Result stuck(ServerLevel level, Map<Item, Integer> bag) {
        // Whatever comes next, it starts on the ground with the scaffolding back in the bag.
        if (scaffold.hasBlocks()) { onScaffold = false; scaffold.startDescend(); return null; }
        refreshDone(level);
        int pending = 0, funded = 0;
        for (int index = 0; index < steps.size(); index++) if (!done.get(index)) { pending++; if (enough(bag, steps.get(index))) funded++; }
        if (pending == 0) {
            if (scaffold.hasBlocks()) { scaffold.startDescend(); return null; }
            complete = true;
            return new Result(State.DONE, finishedMessage());
        }
        if (funded > 0 && scaffolds < MAX_SCAFFOLDS && startScaffold(level, bag)) return null;
        if (funded < pending) {
            lastJobMessage = funded > 0 ? reason(level) : "";
            return new Result(State.NEED_MATERIALS, "Поставил " + placedThisOrder + " блоков" + (clearedThisOrder > 0 ? ", убрал мешавших " + clearedThisOrder : "")
                    + ". Готово " + done.cardinality() + " из " + steps.size() + "; для остальных нужны материалы.");
        }
        String why = reason(level);
        lastJobMessage = why;
        return Result.failed("Стройка «" + title() + "» встала: готово " + done.cardinality() + " из " + steps.size() + ". " + why);
    }

    private String reason(ServerLevel level) {
        if (!protectedBlocks.isEmpty()) {
            var first = protectedBlocks.entrySet().iterator().next();
            return "Мешает " + first.getValue() + (protectedBlocks.size() > 1 ? " (и ещё " + (protectedBlocks.size() - 1) + ")" : "") + ". Убери — и скажи «дострой дом».";
        }
        BlockPos example = null;
        int floating = 0, unreached = 0;
        for (int index = 0; index < steps.size(); index++) {
            if (done.get(index)) continue;
            Step step = steps.get(index);
            if (!supportedNow(level, step)) floating++; else unreached++;
            if (example == null) example = step.pos;
        }
        String where = example == null ? "" : " (например, " + example.toShortString() + ")";
        if (unreached == 0 && floating > 0) return floating + " блоков висят в воздухе, опереть их не на что" + where + ".";
        return "Не могу подойти к " + (unreached + floating) + " блокам" + where + (scaffold.failure().isBlank() ? "" : ": " + scaffold.failure().toLowerCase())
                + ". Освободи проход и скажи «дострой дом».";
    }

    private String finishedMessage() {
        BoundingBox box = placement.box(schematic);
        String left = protectedBlocks.isEmpty() ? "" : " Не тронул: " + protectedBlocks.values().iterator().next() + ".";
        String high = skippedClears.isEmpty() ? "" : " Не достал до " + skippedClears.size() + " лишних блоков в его объёме.";
        return "Дом «" + title() + "» готов: " + steps.size() + " блоков, " + box.minX() + " " + box.minY() + " " + box.minZ() + " — "
                + box.maxX() + " " + box.maxY() + " " + box.maxZ() + "." + left + high;
    }

    private void refreshDone(ServerLevel level) {
        for (int index = 0; index < steps.size(); index++) if (level.hasChunkAt(steps.get(index).pos)) done.set(index, matches(level, steps.get(index)));
    }

    private Result place(ServerLevel level) {
        Step step = steps.get(target);
        if (matches(level, step)) { done.set(target); release(); return Result.working(); }
        Map<Item, Integer> bag = bag();
        if (!enough(bag, step)) { release(); return Result.working(); }
        BlockPos focus = focus(level, step);
        boolean obstructed = !focus.equals(step.pos) || !replaceable(level, step.pos) || step.partner != null && !replaceable(level, step.partner);
        if (obstructed) {
            BlockState in = level.getBlockState(focus);
            String why = unbreakable(level, focus, in);
            if (why != null) { protectedBlocks.put(focus, why); blocked.add(target); release(); return Result.working(); }
        }
        if (!approach(level, focus, obstructed ? null : step.partner)) {
            if (approachFailed) { unreachable.add(target); release(); }
            return Result.working();
        }
        owner.getNavigation().stop();
        owner.getLookControl().setLookAt(focus.getX() + .5, focus.getY() + .5, focus.getZ() + .5);
        if (obstructed) {
            BlockState in = level.getBlockState(focus);
            owner.workStatus("Ломаю мешающий блок «" + in.getBlock().getName().getString() + "» на " + focus.toShortString() + ".");
            if (breakBlock(level, focus, in)) cooldown = 2;
            return Result.working();
        }
        if (!supportedNow(level, step)) { release(); return Result.working(); }
        BlockState state = step.partner == null ? Block.updateFromNeighbourShapes(step.state, level, step.pos) : step.state;
        if (state.isAir() || !state.is(step.state.getBlock())) state = step.state;
        if (!level.isUnobstructed(state, step.pos, CollisionContext.empty())
                || step.partner != null && !level.isUnobstructed(step.partnerState, step.partner, CollisionContext.empty())) {
            // Somebody stands in the way (or he does): try another block and come back to this one.
            unreachable.add(target); release(); return Result.working();
        }
        ItemStack material = heldMaterial(step.item);
        if (material.isEmpty()) { release(); return Result.working(); }
        ItemStack shown = material.copy();
        BlockState previous = level.getBlockState(step.pos);
        BlockState previousPartner = step.partner == null ? null : level.getBlockState(step.partner);
        if (!level.setBlock(step.pos, state, 3)) { blocked.add(target); release(); return Result.working(); }
        if (step.partner != null && !level.setBlock(step.partner, step.partnerState, 3)) {
            level.setBlock(step.pos, previous, 3);
            level.setBlock(step.partner, previousPartner, 3);
            blocked.add(target); release(); return Result.working();
        }
        consume(step.item, step.items);
        if (step.stripped) strip(level, step.pos);
        owner.animateAction(1, step.pos, shown);
        if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.CHEST) || state.is(Blocks.FURNACE)) owner.rememberPlaced(step.pos);
        owner.setChanged();
        owner.swing(InteractionHand.MAIN_HAND);
        var sound = state.getSoundType();
        level.playSound(null, step.pos, sound.getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
        level.gameEvent(GameEvent.BLOCK_PLACE, step.pos, GameEvent.Context.of(owner, state));
        done.set(target); placedThisOrder++;
        owner.workStatus("Строю «" + title() + "»: " + done.cardinality() + " из " + steps.size() + " блоков.");
        release();
        unreachable.clear(); scaffoldTried.clear(); window = STAGE_WINDOW;
        cooldown = 4;
        return Result.working();
    }

    private void release() { target = -1; path = null; candidates = null; }

    /** The stripping of a placed log costs the axe what a player's right click costs it. */
    private void strip(ServerLevel level, BlockPos pos) {
        for (int slot = 0; slot < owner.getContainerSize(); slot++) {
            ItemStack axe = owner.getItem(slot);
            if (!axe.is(ItemTags.AXES)) continue;
            axe.hurtAndBreak(1, level, owner, broken -> { });
            break;
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1F, 1F);
    }

    /** The cell to work on first: a foreign block in the way of the step or of its second half, else the step itself. */
    private BlockPos focus(ServerLevel level, Step step) {
        if (!replaceable(level, step.pos)) return step.pos;
        if (step.partner != null && !replaceable(level, step.partner)) return step.partner;
        return step.pos;
    }
    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LiquidBlock;
    }

    /** Like a player: a block goes against another block, and where it can stand (a torch on a wall, sand on something). */
    private boolean supportedNow(ServerLevel level, Step step) {
        boolean against = false;
        for (Direction direction : Direction.values()) {
            BlockPos next = step.pos.relative(direction);
            if (!level.hasChunkAt(next)) continue;
            BlockState state = level.getBlockState(next);
            if (!state.isAir() && !state.canBeReplaced()) { against = true; break; }
        }
        if (!against || !step.state.canSurvive(level, step.pos)) return false;
        return !(step.state.getBlock() instanceof FallingBlock) || !FallingBlock.isFree(level.getBlockState(step.pos.below()));
    }

    /** Blocks that are not broken: unbreakable ones, containers with things in them, and what would take too long by hand. */
    private String unbreakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (scaffold.owns(pos)) return null;
        String name = "«" + state.getBlock().getName().getString() + "» на " + pos.toShortString();
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return "неразрушимый блок " + name;
        if (level.getBlockEntity(pos) instanceof Container container && !container.isEmpty()) return name + " с вещами внутри";
        float speed = 1;
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack stack : carried()) if (stack.getDestroySpeed(state) > speed) { speed = stack.getDestroySpeed(state); best = stack; }
        boolean right = !state.requiresCorrectToolForDrops() || !best.isEmpty() && best.isCorrectToolForDrops(state) || owner.getMainHandItem().isCorrectToolForDrops(state);
        if (hardness * (right ? 30 : 100) / speed > 20 * 30) return name + " — без подходящего инструмента его не сломать";
        return null;
    }

    private boolean breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        owner.selectToolForBlock(state);
        if (!breaking.tick(level, pos)) return false;
        ItemStack tool = owner.getMainHandItem();
        var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), owner, tool);
        // Keep what the building or its scaffolding still uses, if it fits; the rest drops where the block was (game log 27.10:
        // a hundred blocks of dirt from the clearing left no room for the crafts of the house).
        boolean useful = drops.stream().anyMatch(drop -> stillNeeded(drop.getItem()) > bag().getOrDefault(drop.getItem(), 0)
                || CompanionScaffold.MATERIALS.contains(drop.getItem()) && tall() && CompanionScaffold.supply(owner, this::stillNeeded) < 24);
        boolean keep = useful && owner.canStoreDrops(drops) && freeSlots() > KEEP_FREE;
        if (!level.destroyBlock(pos, !keep, owner, 512)) { breaking.reset(); return false; }
        tool.getItem().mineBlock(tool, level, state, pos, owner);
        owner.swing(InteractionHand.MAIN_HAND);
        if (keep) owner.dropHarvest(pos, drops);
        breaking.reset();
        clearedThisOrder++;
        unreachable.clear();
        return true;
    }

    /** Blocks still to set down four or more above the bottom: out of reach from the ground, so scaffolding will be needed. */
    private boolean tall() {
        int lowest = placement.box(schematic).minY();
        for (int index = 0; index < steps.size(); index++) if (!done.get(index) && steps.get(index).pos.getY() - lowest >= 4) return true;
        return false;
    }

    /** What the steps not yet built still take of an item: scaffolding does not stand on the building's own material. */
    private int stillNeeded(Item item) {
        int count = 0;
        for (int index = 0; index < steps.size(); index++) if (!done.get(index) && steps.get(index).item == item) count += steps.get(index).items;
        return count;
    }

    private boolean startScaffold(ServerLevel level, Map<Item, Integer> bag) {
        if (CompanionScaffold.supply(owner, this::stillNeeded) == 0) return false;
        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            if (done.get(index) || blocked.contains(index) || scaffoldTried.contains(index) || !enough(bag, step)) continue;
            BlockPos focus = focus(level, step);
            if (focus.equals(step.pos) && !supportedNow(level, step)) continue;
            scaffoldTried.add(index);
            BlockPos[] column = column(level, focus, step);
            if (column == null) continue;
            int levels = column[1].getY();
            scaffold.begin(column[0], levels, this::stillNeeded);
            scaffolds++;
            owner.workStatus("Ставлю леса, чтобы достать до " + focus.toShortString() + ".");
            return true;
        }
        return false;
    }

    /** Where to stand a scaffold for the cell and how tall: {feet, (0, levels, 0)}. */
    private BlockPos[] column(ServerLevel level, BlockPos focus, Step step) {
        record Option(BlockPos feet, int levels, double score) { }
        List<Option> options = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int dy = -CompanionScaffold.MAX_HEIGHT - 2; dy <= 0; dy++) {
                BlockPos feet = focus.offset(dx, dy, dz);
                if (!standable(level, feet)) continue;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double vertical = Math.sqrt(Math.max(0, (REACH - .4) * (REACH - .4) - horizontal * horizontal));
                int least = Math.max(1, (int) Math.ceil(focus.getY() + .5 - (feet.getY() + owner.getEyeHeight()) - vertical));
                int most = Math.min(CompanionScaffold.MAX_HEIGHT, CompanionScaffold.supply(owner, this::stillNeeded));
                // The lowest top from which the cell is in reach and in sight: a tile hidden at the sides is seen from above.
                boolean added = false;
                for (int levels = least; levels <= most && !added; levels++) {
                    boolean clear = true;
                    for (int up = 0; up <= levels + 1 && clear; up++) {
                        BlockPos cell = feet.above(up);
                        BlockState state = level.getBlockState(cell);
                        if (!level.hasChunkAt(cell) || !state.getCollisionShape(level, cell).isEmpty() || !level.getFluidState(cell).isEmpty()
                                || cell.equals(step.pos) || cell.equals(step.partner)) clear = false;
                    }
                    if (!clear) break;
                    Vec3 eye = Vec3.atBottomCenterOf(feet.above(levels)).add(0, owner.getEyeHeight(), 0);
                    if (!visible(level, eye, focus)) continue;
                    options.add(new Option(feet.immutable(), levels, levels * 4D + feet.distSqr(owner.blockPosition()) * .25D + (insideBox(feet) ? 6 : 0)));
                    added = true;
                }
                if (added) break;
            }
        }
        options.sort(Comparator.comparingDouble(Option::score));
        for (Option option : options.subList(0, Math.min(4, options.size()))) {
            Path walk = owner.getNavigation().createPath(option.feet, 0);
            if (walk != null && walk.canReach()) return new BlockPos[]{option.feet, new BlockPos(0, option.levels, 0)};
        }
        return null;
    }
    private boolean insideBox(BlockPos pos) { return placement.box(schematic).isInside(pos); }

    /**
     * A door of the house closed behind him while he stood in its doorway (the door goal shuts what it opened): no path starts
     * inside a closed door, so he opens it like a player would.
     */
    private void openDoorAround(ServerLevel level) {
        for (BlockPos pos : new BlockPos[]{owner.blockPosition(), owner.blockPosition().above()}) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && !state.getValue(DoorBlock.OPEN)) {
                door.setOpen(owner, level, state, pos, true);
                path = null;
                return;
            }
        }
    }

    /** True when the cell can be worked from where he stands; otherwise walks to a spot it can be. */
    private boolean approach(ServerLevel level, BlockPos focus, BlockPos partner) {
        if (canWorkFromHere(level, focus, partner)) return true;
        if (onScaffold) { approachFailed = true; return false; }
        double distance = owner.distanceToSqr(focus.getCenter());
        if (distance + .05 < bestDistance) { bestDistance = distance; progressTime = level.getGameTime(); }
        // Not getting closer for a while, or not moving at all for two seconds: another block first.
        if (stillAt == null || owner.position().distanceToSqr(stillAt) > .01) { stillAt = owner.position(); stillSince = level.getGameTime(); }
        if (level.getGameTime() - progressTime > APPROACH_TICKS || level.getGameTime() - stillSince > STILL_TICKS) {
            approachFailed = true; owner.getNavigation().stop(); return false;
        }
        if (path == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck()) {
            path = route(level, focus, partner);
            if (path == null || !path.canReach()) { approachFailed = true; return false; }
        }
        owner.movementIntent(focus);
        owner.getNavigation().moveTo(path, 1D);
        var end = path.getEndNode();
        if (end != null && owner.distanceToSqr(end.x + .5, end.y, end.z + .5) < 1.5) {
            owner.getNavigation().stop();
            owner.getMoveControl().setWantedPosition(end.x + .5, end.y, end.z + .5, .7);
        }
        return false;
    }

    private boolean canWorkFromHere(ServerLevel level, BlockPos pos) { return canWorkFromHere(level, pos, null); }
    /** In reach and in sight, and not standing in the cell himself (then he steps aside first). */
    private boolean canWorkFromHere(ServerLevel level, BlockPos pos, BlockPos partner) {
        AABB body = owner.getBoundingBox();
        if (body.intersects(new AABB(pos)) || partner != null && body.intersects(new AABB(partner))) return false;
        return owner.onGround() && visible(level, owner.getEyePosition(), pos);
    }

    private boolean visible(ServerLevel level, Vec3 eye, BlockPos pos) {
        // Use visible faces/edges, as a player would. A ray to the cube center alone is
        // hidden by the finished wall beneath a roof tile or by an adjacent half-slab.
        double x = Math.clamp(eye.x, pos.getX() + .01, pos.getX() + .99);
        double z = Math.clamp(eye.z, pos.getZ() + .01, pos.getZ() + .99);
        double y = Math.clamp(eye.y, pos.getY() + .01, pos.getY() + .99);
        for (Vec3 aim : new Vec3[]{new Vec3(x, y, z), new Vec3(x, pos.getY() + .49, z), pos.getCenter()}) {
            if (eye.distanceToSqr(aim) > REACH * REACH) continue;
            var hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }

    private Path route(ServerLevel level, BlockPos focus, BlockPos partner) {
        List<BlockPos> feet = new ArrayList<>();
        for (int dy = -5; dy <= 3; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos pos = focus.offset(dx, dy, dz);
            if (!standable(level, pos)) continue;
            AABB body = new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2, pos.getX() + .8, pos.getY() + owner.getBbHeight(), pos.getZ() + .8);
            if (body.intersects(new AABB(focus)) || partner != null && body.intersects(new AABB(partner))) continue;
            Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, owner.getEyeHeight(), 0);
            if (eye.distanceToSqr(focus.getCenter()) > (REACH + .9) * (REACH + .9) || !level.noCollision(owner, body) || !visible(level, eye, focus)) continue;
            feet.add(pos.immutable());
        }
        if (feet.isEmpty()) return null;
        feet.sort(Comparator.comparingDouble(pos -> pos.distSqr(owner.blockPosition())));
        return owner.getNavigation().createPath(Set.copyOf(feet.subList(0, Math.min(48, feet.size()))), 0);
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    // ---- Matching the world ----

    private boolean matches(ServerLevel level, Step step) {
        if (!same(level.getBlockState(step.pos), step.state)) return false;
        return step.partner == null || same(level.getBlockState(step.partner), step.partnerState);
    }
    /** The same block facing the same way. Connections of fences, stair corners and power follow the neighbours by themselves. */
    private static boolean same(BlockState actual, BlockState wanted) {
        if (wanted.is(Blocks.DIRT)) return actual.is(BlockTags.DIRT) || AS_DIRT.contains(actual.getBlock());
        if (actual.getBlock() != wanted.getBlock()) return false;
        for (Property<?> property : ORIENTATION) if (wanted.hasProperty(property) && actual.hasProperty(property) && !actual.getValue(property).equals(wanted.getValue(property))) return false;
        return true;
    }

    /** Whether the house still stands: the last step of a house order (verify_house). */
    CompanionActionResult check(ServerLevel level) {
        if (placement == null || !ensureLoaded()) return CompanionActionResult.failed(placement == null ? "Дом ещё не выбран." : loadError);
        refreshDone(level);
        int missing = steps.size() - done.cardinality();
        if (missing > 0) { complete = false; return CompanionActionResult.failed("Дом «" + title() + "» не достроен: не хватает " + missing + " блоков из " + steps.size() + "."); }
        complete = true;
        return CompanionActionResult.ok(finishedMessage());
    }

    // ---- The bag ----

    private List<ItemStack> carried() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (!owner.getItem(slot).isEmpty()) result.add(owner.getItem(slot));
        if (!owner.getMainHandItem().isEmpty()) result.add(owner.getMainHandItem());
        if (!owner.getOffhandItem().isEmpty()) result.add(owner.getOffhandItem());
        return result;
    }
    private Map<Item, Integer> bag() {
        Map<Item, Integer> result = new HashMap<>();
        for (ItemStack stack : carried()) result.merge(stack.getItem(), stack.getCount(), Integer::sum);
        return result;
    }
    private static boolean enough(Map<Item, Integer> bag, Step step) {
        return bag.getOrDefault(step.item, 0) >= step.items && (!step.stripped || hasAxe(bag));
    }
    private static boolean hasAxe(Map<Item, Integer> bag) { return bag.keySet().stream().anyMatch(item -> new ItemStack(item).is(ItemTags.AXES)); }
    private int freeSlots() {
        int free = 0;
        for (int slot = 0; slot < owner.getContainerSize(); slot++) if (owner.getItem(slot).isEmpty()) free++;
        return free;
    }
    private ItemStack heldMaterial(Item item) {
        if (!owner.getMainHandItem().is(item)) {
            var equipped = owner.equipForWork(BuiltInRegistries.ITEM.getKey(item).toString());
            if (!equipped.success() || !owner.getMainHandItem().is(item)) return ItemStack.EMPTY;
        }
        return owner.getMainHandItem();
    }
    private void consume(Item item, int count) {
        ItemStack hand = owner.getMainHandItem();
        int taken = hand.is(item) ? Math.min(count, hand.getCount()) : 0;
        hand.shrink(taken);
        if (hand.isEmpty()) owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        for (int slot = 0; slot < owner.getContainerSize() && taken < count; slot++) {
            ItemStack stack = owner.getItem(slot);
            if (!stack.is(item)) continue;
            int part = Math.min(count - taken, stack.getCount());
            stack.shrink(part); taken += part;
        }
    }
    private static String materials(Map<Item, Integer> counts) {
        List<String> parts = new ArrayList<>();
        for (var entry : counts.entrySet()) {
            if (parts.size() >= 6) { parts.add("и ещё " + (counts.size() - 6)); break; }
            parts.add("«" + new ItemStack(entry.getKey()).getHoverName().getString() + "» ×" + entry.getValue());
        }
        return String.join(", ", parts);
    }
    private static int ceil(int value, int by) { return (value + by - 1) / by; }

    String diagnosticState() {
        return "placement=" + placement + ",steps=" + steps.size() + ",done=" + done.cardinality() + ",clears=" + clears.size() + ",target=" + target
                + ",clearTarget=" + clearTarget + ",scaffold=" + scaffold.blocks() + ",woods=" + woods + ",unobtainable=" + unobtainable.size();
    }

    // ---- Save ----

    void save(CompoundTag tag) {
        if (placement == null) return;
        CompoundTag data = placement.save();
        data.putString("Dimension", dimension);
        data.putBoolean("Complete", complete);
        CompoundTag swapped = new CompoundTag();
        woods.forEach(swapped::putString);
        data.put("Woods", swapped);
        scaffold.save(data);
        tag.put("SchematicHouse", data);
    }
    void load(CompoundTag tag) {
        cancel();
        placement = null; schematic = null; loadError = ""; woods.clear(); steps = List.of(); done.clear(); clears.clear(); interior = Set.of(); anchor = null;
        complete = false; unobtainable.clear(); lastRoundProgress = -1; stuckRounds = 0; interiorCheckedAt = -1000;
        CompoundTag data = tag.getCompound("SchematicHouse");
        placement = CompanionSchematic.Placement.load(data);
        if (placement == null) return;
        dimension = data.getString("Dimension");
        complete = data.getBoolean("Complete");
        CompoundTag swapped = data.getCompound("Woods");
        for (String key : swapped.getAllKeys()) if (WOODS.contains(key) && WOODS.contains(swapped.getString(key))) woods.put(key, swapped.getString(key));
        scaffold.load(data);
        ensureLoaded();
    }
}
