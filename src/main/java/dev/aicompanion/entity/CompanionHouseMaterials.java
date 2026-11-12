package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where the materials of a template building come from. The wood of the building is the species that grows (the template's own when
 * it does), grass and podzol are dirt. What the bag lacks is got in rounds: the cheap things his skills reach (wood, stone, soil,
 * sand and what is crafted of them) for the lowest unfinished layers first, a few kinds at a time and exactly as many as are missing;
 * what they do not reach is named to the player. A round is planned from the bag and the world as they are, so a stop, a reload or a
 * gathering that fell short needs no saved round: the next one is simply planned again.
 */
final class CompanionHouseMaterials {
    /** The species of the overworld, the order a substitute is preferred in only breaks ties. */
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
    private static final Set<String> LOOSE = Set.of("minecraft:sand", "minecraft:red_sand", "minecraft:gravel");
    /** Kinds of things one round plans for: the building starts soon, and the next round sees the real bag. */
    private static final int MAX_KINDS = 4, MAX_ROUNDS = 60;

    /** What to get: {@code logs:<species>}, {@code stone}, {@code dirt} or the id of a loose block, and how many. */
    record Gather(String raw, int count) { }
    /** The next gathering, or what he cannot get (both empty: nothing is left to do). */
    record Round(Gather gather, Map<Item, Integer> lacking) { }

    private final CompanionEntity owner;
    /** The species the template wants and the one that stands in its place. */
    private final Map<String, String> woods = new HashMap<>();
    private boolean decided;
    /** Raw materials a gathering found none of: what needs them is told to the player instead of tried again. */
    private final Set<String> exhausted = new HashSet<>();
    /** What a round found no way to get: it does not hold the layers above it back. */
    private final Set<Item> unobtainable = new HashSet<>();
    private Gather last;
    private int lastBefore, rounds;
    private CompanionWoodKind logKind;

    CompanionHouseMaterials(CompanionEntity owner) { this.owner = owner; }

    /** A new building. */
    void reset() { woods.clear(); decided = false; restart(); }
    /** The same building taken up again: earlier failures say nothing about the world as it is now (nor does the species chosen with nothing placed yet). */
    void restart() { exhausted.clear(); unobtainable.clear(); last = null; rounds = 0; logKind = null; decided = false; }
    /** A gathering round is under way: a failure of the work in that time is the round's, not the building's. */
    boolean gathering() { return last != null; }
    boolean cannotGet(Item item) { return unobtainable.contains(item); }
    boolean decided() { return decided; }
    /** The species the log gathering in progress is after, null for any log. */
    CompanionWoodKind logKind() { return logKind; }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        woods.forEach(tag::putString);
        return tag;
    }
    void load(CompoundTag tag) {
        woods.clear();
        for (String wood : tag.getAllKeys()) if (WOODS.contains(wood) && WOODS.contains(tag.getString(wood))) woods.put(wood, tag.getString(wood));
        decided = true;
    }

    private static String species(String path) {
        String rest = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
        for (String wood : WOODS) if (rest.startsWith(wood + "_")) return wood;
        return null;
    }
    private static String path(Item item) { return BuiltInRegistries.ITEM.getKey(item).getPath(); }

    /** The block as it is built here: its wood replaced where the template's species is not to be had, grass, podzol and a path as plain dirt (a path block is had only with silk touch). */
    BlockState adapt(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.DIRT_PATH)) return Blocks.DIRT.defaultBlockState();
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        String wood = species(path);
        String instead = wood == null ? null : woods.get(wood);
        if (instead == null) return state;
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path.replaceFirst(wood, instead)));
        if (block == Blocks.AIR) return state;
        BlockState result = block.defaultBlockState();
        for (Property<?> property : state.getProperties()) result = copy(result, state, property);
        return result;
    }
    private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    /**
     * The species the building is made of, before its first block: each one the template wants that is neither in the bag nor
     * in a tree he would cut within his search is replaced by the species he has most of (the bag and the trees together), the same
     * for every part. A species whose round came back empty counts as having no trees. Once a block stands the choice is kept.
     */
    void chooseWoods(ServerLevel level, List<CompanionTemplates.Cell> cells) {
        decided = true; woods.clear();
        Set<String> wanted = new LinkedHashSet<>();
        for (var cell : cells) {
            String wood = species(BuiltInRegistries.BLOCK.getKey(cell.state().getBlock()).getPath());
            if (wood != null) wanted.add(wood);
        }
        Map<String, Integer> have = new HashMap<>();
        for (ItemStack stack : bag()) {
            String wood = species(path(stack.getItem()));
            if (wood != null) have.merge(wood, stack.getCount() * (stack.is(ItemTags.LOGS) ? 4 : 1), Integer::sum);
        }
        Map<String, Integer> around = new HashMap<>();
        for (String wood : WOODS) around.put(wood, exhausted.contains("logs:" + wood) ? 0 : cuttable(level, CompanionWoodKind.valueOf(wood.toUpperCase(Locale.ROOT))));
        List<String> absent = wanted.stream().filter(wood -> have.getOrDefault(wood, 0) + around.get(wood) == 0).toList();
        String best = null;
        int most = 0;
        for (String wood : WOODS) {
            int amount = have.getOrDefault(wood, 0) + around.get(wood);
            if (amount > most) { most = amount; best = wood; }
        }
        if (best != null) for (String wood : absent) woods.put(wood, best);
    }

    /**
     * How many logs of the species the gathering would cut within his search (at most 64): not a player's log walls or the logs of the
     * building itself, which it leaves standing (CompanionBuildings), so such a wall is no tree that «grows nearby».
     */
    private int cuttable(ServerLevel level, CompanionWoodKind kind) {
        Set<BlockPos> seen = new HashSet<>();
        int count = 0;
        for (BlockPos seed : CompanionLogSearch.find(level, owner.blockPosition(), owner.getSearchRadius(), kind.blocks)) {
            if (seen.contains(seed)) continue;
            Set<BlockPos> group = CompanionLogSearch.connected(level, seed, kind.blocks);
            seen.addAll(group);
            if (CompanionBuildings.builtLogs(level, group) || owner.ownPlacesOnly() && CompanionBuildings.decorated(level, group)) continue;
            count += group.size();
            if (count >= 64) return 64;
        }
        return count;
    }

    /** What to get next for what is still to build ({@code required}: items by the layer they are first needed in), given nothing can be set down now. */
    Round next(ServerLevel level, Map<Item, Integer> required) {
        if (++rounds > MAX_ROUNDS) return new Round(null, Map.of());
        List<ItemStack> virtual = bag();
        boolean table = owner.hasCraftingTableNearby();
        String main = mainWood(required);
        Map<String, Integer> raws = new LinkedHashMap<>();
        Map<Item, Integer> lacking = new LinkedHashMap<>();
        int kinds = 0;
        for (var entry : required.entrySet()) {
            Item item = entry.getKey();
            if (entry.getValue() <= count(virtual, item)) continue;
            int missing = entry.getValue() - count(virtual, item);
            String wood = species(path(item));
            // Wood of another species is no use for this one: it is neither counted nor spent, and stays in the bag.
            List<ItemStack> usable = new ArrayList<>(), apart = new ArrayList<>();
            for (ItemStack stack : virtual) {
                String other = species(path(stack.getItem()));
                (wood != null && other != null && !other.equals(wood) ? apart : usable).add(stack);
            }
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            List<Gather> gathers = new ArrayList<>();
            if (!obtainable(CompanionObtain.plan(level, usable, table, id, entry.getValue()), wood == null ? main : wood, gathers)) { lacking.put(item, missing); continue; }
            if (++kinds > MAX_KINDS) continue;
            for (Gather gather : gathers) usable = with(usable, rawItem(gather), gather.count());
            int still = entry.getValue() - count(usable, item);
            if (still > 0) {
                var crafted = CompanionCrafting.plan(level, usable, id, Math.min(64, still), table);
                // The recipes do not make it from what the plan gathers: not something to go for.
                if (!crafted.success()) { lacking.put(item, missing); kinds--; continue; }
                table |= crafted.placeTable();
                usable = new ArrayList<>(crafted.inventory());
            }
            for (Gather gather : gathers) raws.merge(gather.raw(), gather.count(), Integer::sum);
            virtual = new ArrayList<>(usable);
            virtual.addAll(apart);
        }
        unobtainable.addAll(lacking.keySet());
        if (!raws.isEmpty()) {
            var first = raws.entrySet().iterator().next();
            // What cannot be got is told along with the first round (the summary to the player), though the round itself goes first.
            return new Round(new Gather(first.getKey(), first.getValue()), lacking);
        }
        return new Round(null, lacking);
    }

    /** The plan only if every step is one he does here: gathering wood, stone, soil or sand, and crafting; no ore, hunting, furnace or flint. */
    private boolean obtainable(CompanionObtain.Plan plan, String wood, List<Gather> out) {
        if (plan.refused()) return false;
        for (var step : plan.steps()) {
            switch (step.type()) {
                case "craft_item" -> { }
                case "gather_wood" -> {
                    String of = step.item().isEmpty() ? wood : species(ResourceLocation.parse(step.item()).getPath());
                    if (of == null) return false;
                    out.add(new Gather("logs:" + of, step.count()));
                }
                case "gather_stone" -> out.add(new Gather("stone", step.count()));
                case "gather_dirt" -> out.add(new Gather("dirt", step.count()));
                case "gather_loose" -> { if (!LOOSE.contains(step.item())) return false; out.add(new Gather(step.item(), step.count())); }
                default -> { return false; }
            }
        }
        for (Gather gather : out) if (exhausted.contains(gather.raw())) return false;
        return true;
    }

    /** The species most of the building is made of: what a thing with no species of its own (a bench, a chest) is made of. */
    private String mainWood(Map<Item, Integer> required) {
        Map<String, Integer> counts = new HashMap<>();
        for (var entry : required.entrySet()) {
            String wood = species(path(entry.getKey()));
            if (wood != null) counts.merge(wood, entry.getValue(), Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("oak");
    }

    private static Item rawItem(Gather gather) {
        if (gather.raw().startsWith("logs:")) return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(gather.raw().substring("logs:".length()) + "_log"));
        return switch (gather.raw()) {
            case "stone" -> Items.COBBLESTONE;
            case "dirt" -> Items.DIRT;
            default -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(gather.raw()));
        };
    }

    /** Sets out for it: the gathering is a stage of the same work, which comes back to the building when it is over. */
    CompanionHouse.Result start(ServerLevel level, Gather gather) {
        var work = owner.work;
        work.woodAttempts = 0;
        work.deadline = level.getGameTime() + 20 * 600;
        last = gather; lastBefore = have(gather); logKind = null;
        if (gather.raw().startsWith("logs:")) {
            logKind = CompanionWoodKind.valueOf(gather.raw().substring("logs:".length()).toUpperCase(Locale.ROOT));
            if (cuttable(level, logKind) == 0) { exhausted.add(gather.raw()); return CompanionHouse.Result.working(); }
            owner.workStatus("Не хватает дерева для постройки: иду рубить " + gather.count() + " брёвен " + logKind.genitive() + ".");
            work.crafting.requestWood(CompanionWork.Phase.BUILD, gather.count());
        } else if (gather.raw().equals("stone")) {
            owner.workStatus("Не хватает камня для постройки: иду добывать " + gather.count() + ".");
            work.crafting.requestStone(gather.count());
        } else {
            owner.workStatus("Не хватает материала для постройки: иду копать " + gather.count() + " «" + CompanionWork.itemName(rawItem(gather)) + "».");
            work.crafting.requestDig(gather.raw().equals("dirt") ? CompanionGround.SOIL : CompanionGround.of(gather.raw()), gather.count());
        }
        return CompanionHouse.Result.working();
    }

    /** Dirt for the scaffolding; null when none was found before. */
    CompanionHouse.Result dirt(ServerLevel level, int count) {
        return exhausted.contains("dirt") ? null : start(level, new Gather("dirt", count));
    }

    /**
     * Called when the work is back at the building: a gathering that brought nothing is not tried again (what needs it is told to the
     * player, the rest is built). A species with no tree to cut is chosen again, while nothing of the building stands (the caller's rule).
     */
    void settle() {
        if (last == null) return;
        if (have(last) <= lastBefore) { exhausted.add(last.raw()); decided &= !last.raw().startsWith("logs:"); }
        last = null;
    }
    private int have(Gather gather) {
        if (gather.raw().equals("stone")) return owner.collectedStone();
        return count(bag(), rawItem(gather));
    }

    /** What a round of nothing to gather says: the items, counted, and what stands so far. */
    static String lackingText(Map<Item, Integer> lacking, int placed, int total, String name) {
        List<String> list = new ArrayList<>();
        lacking.forEach((item, count) -> list.add(count + " × " + new ItemStack(item).getHoverName().getString()));
        return "Построил всё, что смог: " + placed + " из " + total + " блоков «" + name + "». Не хватает: " + String.join(", ", list)
                + " — это я не добуду сам. Положи в сумку и скажи «дострой».";
    }

    /**
     * The largest number of the item (up to {@code want}) the bag's materials make, as a plan; the plan of the failure when not even one.
     */
    static CompanionCrafting.Plan craftMost(ServerLevel level, List<ItemStack> bag, String id, int want, boolean table) {
        var all = CompanionCrafting.plan(level, bag, id, want, table);
        if (all.success() || want == 1) return all;
        var failure = all;
        CompanionCrafting.Plan best = null;
        int low = 0, high = want - 1;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            var plan = CompanionCrafting.plan(level, bag, id, middle, table);
            if (plan.success()) { best = plan; low = middle; } else { failure = plan; high = middle - 1; }
        }
        return best != null ? best : failure;
    }

    private List<ItemStack> bag() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) result.add(owner.getItem(slot).copy());
        return result;
    }
    private static int count(List<ItemStack> bag, Item item) { return bag.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static List<ItemStack> with(List<ItemStack> bag, Item item, int count) {
        List<ItemStack> result = new ArrayList<>(bag);
        result.add(new ItemStack(item, count));
        return result;
    }
}
