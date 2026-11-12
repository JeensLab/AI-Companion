package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * compost_items, as a player at a composter and only on an order (decided 10.11): plants from his bag go in one by one, each raising
 * the level by the chance the game gives it, and the bone meal of a full one is taken out, falls and is picked up. With nothing named
 * it is plant scrap only (saplings, seeds, leaves, grass, flowers), food and harvest are left, and some saplings and seeds are kept
 * for his own planting; what the player names goes in as said. No composter a few steps away: his own, made from his bag and left standing.
 */
public final class CompanionComposting {
    /** What an order may name besides an item: scrap is the plants nobody eats, the rest are its parts. */
    public static final Set<String> GROUPS = Set.of("scrap", "saplings", "seeds", "leaves");
    /** No number in the order is «all there is»: of scrap, bone meal until the scrap ends; of a named thing, every one of it. */
    public static final int ALL = CompanionWork.ALL_CARRIED;
    static final String NO_COMPOSTER = "Рядом нет компостера, а сделать не из чего.";
    /** Of scrap he keeps this many of each sapling (a 2×2 tree to plant again) and of each seed (a field). */
    static final int KEEP_SAPLINGS = 4, KEEP_SEEDS = 16;
    /** Ticks between two things put in, as a player clicks. */
    private static final int PACE = 6;
    private static final Set<Item> SEEDS = Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS);
    /** Flowers that are no scrap: a wither rose is fought for, the rest come from a sniffer or a lush cave only (review 10.11). */
    private static final Set<Item> RARE = Set.of(Items.WITHER_ROSE, Items.SPORE_BLOSSOM, Items.TORCHFLOWER, Items.PITCHER_PLANT);
    private static final Set<Item> GREENS = Set.of(Items.SHORT_GRASS, Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN, Items.KELP, Items.SEAGRASS,
            Items.VINE, Items.MOSS_CARPET, Items.PINK_PETALS, Items.HANGING_ROOTS, Items.SMALL_DRIPLEAF, Items.BIG_DRIPLEAF, Items.GLOW_LICHEN);

    private final CompanionWork w;
    /** The order: a group or an item id, and how much (bone meal of scrap, pieces of a named thing). */
    private String what = "scrap";
    private int count;
    /**
     * compost_meal, a step of «добудь костную муку»: the scrap of his bag goes in until {@code count} bone meal is made. Fewer is
     * no success, so the chain plans the rest again; a composter a little farther off is walked back to from where he picked.
     */
    private boolean meal;
    static final int MEAL_WALK = 32;
    private BlockPos composter;
    /** His own was tried (made or not); and set down, so a broken one is gone, not «nothing to make it of». */
    private boolean ownTried, ownPlaced;
    private int put, made;
    /** Bone meal he made and a hopper under the composter took before he could: counted toward the order, said in the report. */
    private int taken;
    /** The composter he left full (level 7) a moment ago: only there an empty one later means a hopper took his bone meal. */
    private BlockPos ripening;
    private long nextAt;

    /** Made with the work, before its owner is set: the owner is read when used. */
    CompanionComposting(CompanionWork w) { this.w = w; }

    private static boolean matches(ItemStack stack, String what) {
        if (stack.isEmpty() || ComposterBlock.getValue(stack) <= 0) return false;
        return switch (what) {
            case "scrap" -> stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LEAVES) || stack.is(ItemTags.FLOWERS) && !RARE.contains(stack.getItem()) || SEEDS.contains(stack.getItem()) || GREENS.contains(stack.getItem());
            case "saplings" -> stack.is(ItemTags.SAPLINGS);
            case "seeds" -> SEEDS.contains(stack.getItem());
            case "leaves" -> stack.is(ItemTags.LEAVES);
            default -> what.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        };
    }

    /** Of scrap he keeps the last saplings and seeds of a kind. */
    private static int reserve(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) ? KEEP_SAPLINGS : SEEDS.contains(stack.getItem()) ? KEEP_SEEDS : 0;
    }

    static boolean scrap(ItemStack stack) { return matches(stack, "scrap"); }

    /** The layers the scrap of a bag fills on average, the reserve left out: seven make one bone meal. */
    static double scrapLayers(List<ItemStack> carried) {
        java.util.Map<Item, Integer> spare = new java.util.HashMap<>();
        for (ItemStack stack : carried) if (scrap(stack)) spare.merge(stack.getItem(), stack.getCount(), (had, more) -> had + more);
        double layers = 0;
        for (var entry : spare.entrySet()) {
            ItemStack one = new ItemStack(entry.getKey());
            layers += Math.max(0, entry.getValue() - reserve(one)) * ComposterBlock.getValue(one);
        }
        return layers;
    }

    /** The next thing of the bag to put in; of scrap, never the last saplings and seeds of a kind. */
    private ItemStack next() {
        CompanionEntity c = w.owner;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (!matches(stack, what)) continue;
            int keep = !"scrap".equals(what) ? 0 : reserve(stack);
            if (keep == 0 || c.companionInventory.stream().filter(other -> other.is(stack.getItem())).mapToInt(ItemStack::getCount).sum() > keep) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** null when the work may start, else why not. */
    CompanionActionResult begin(String item, int count) { return begin(item, count, false); }

    CompanionActionResult begin(String item, int count, boolean meal) {
        CompanionEntity c = w.owner;
        this.meal = meal;
        what = meal || item == null || item.isBlank() ? "scrap" : item;
        this.count = Math.max(ALL, count); composter = null; ownTried = false; ownPlaced = false; put = 0; made = 0; taken = 0; ripening = null; nextAt = 0;
        if (!(c.level() instanceof ServerLevel level)) return CompanionActionResult.failed("Не могу без загруженного мира.");
        if (!GROUPS.contains(what)) {
            ResourceLocation id = ResourceLocation.tryParse(what);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return CompanionActionResult.failed("Не понял, что положить в компостер.");
            if (ComposterBlock.getValue(new ItemStack(BuiltInRegistries.ITEM.get(id))) <= 0)
                return CompanionActionResult.failed("«" + CompanionWork.itemName(BuiltInRegistries.ITEM.get(id)) + "» в компостер не кладут: он берёт только растения и еду из них.");
        }
        composter = c.placement.nearest(Blocks.COMPOSTER, walk());
        // Full or a moment from full: there is bone meal to take even with nothing to put in.
        boolean ready = composter != null && level.getBlockState(composter).getValue(ComposterBlock.LEVEL) >= ComposterBlock.MAX_LEVEL;
        // The bone meal must have a place in the bag before anything goes in, or the scrap is spent for meal left in the composter.
        if (!w.items.canStore(List.of(new ItemStack(Items.BONE_MEAL)))) return CompanionActionResult.failed("Сумка полна — костную муку забрать будет некуда.");
        if (next().isEmpty() && !ready) return CompanionActionResult.failed("scrap".equals(what)
                ? "Класть в компостер нечего: растительного хлама у меня нет, а последние саженцы и семена я берегу для посадки. Скажи, что именно положить."
                : "У меня этого нет — в компостер класть нечего.");
        if (composter == null && !carries(Items.COMPOSTER)
                && !CompanionCrafting.plan(level, c.companionInventory, "minecraft:composter", 1, true).success()) return CompanionActionResult.failed(NO_COMPOSTER);
        return null;
    }

    String jobName() { return "костная мука в компостере"; }

    void tick(ServerLevel level) {
        CompanionEntity c = w.owner;
        // At the one he chose (a full one, say), not at whichever of a row the look meets first (review 10.11).
        if (composter != null && c.placement.reaches(composter, Blocks.COMPOSTER)) { work(level, composter); return; }
        if (composter != null && !level.getBlockState(composter).is(Blocks.COMPOSTER)) composter = c.placement.nearest(Blocks.COMPOSTER, walk());
        if (composter == null) {
            if (ownTried) w.finish(false, (ownPlaced ? "Мой компостер пропал, а второго у меня нет." : NO_COMPOSTER) + done()); else own(level);
            return;
        }
        // In the air no way is searched (as to a bench); in water he goes on (as to the stonecutter).
        if (!c.onGround() && !c.isInWater()) return;
        if (!composter.equals(w.heading)) { w.heading = composter; w.resetApproach(); }
        c.movementIntent(composter);
        if (w.approach(level, composter, 2.8D)) { c.workStatus("Иду к компостеру на " + composter.toShortString() + "."); return; }
        w.finish(false, "Не смог подойти к компостеру на " + composter.toShortString() + "." + done());
    }

    /** His own composter, from the bag only (the order counted on it): made at a bench, set down beside him and left standing. */
    private void own(ServerLevel level) {
        CompanionEntity c = w.owner;
        if (!carries(Items.COMPOSTER)) {
            if (w.crafting.approachCraftingTable()) return;
            if (!c.craftFromBag("minecraft:composter", 1).success()) { ownTried = true; w.finish(false, NO_COMPOSTER + done()); return; }
        }
        ownTried = true;
        BlockPos at = c.placement.findPlacementPosition(level, Blocks.COMPOSTER, c.blockPosition(), pos -> true);
        ItemStack item = c.companionInventory.stream().filter(stack -> stack.is(Items.COMPOSTER)).findFirst().orElse(ItemStack.EMPTY);
        if (at == null || item.isEmpty()) { w.finish(false, "Компостер у меня есть, а поставить его рядом некуда."); return; }
        level.setBlock(at, Blocks.COMPOSTER.defaultBlockState(), 3);
        c.animateAction(1, at, item);
        c.rememberPlaced(at);
        item.shrink(1);
        c.refreshInventorySummary();
        level.playSound(null, at, Blocks.COMPOSTER.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
        dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "block_placed", "entity", c.getUUID(), "block", "minecraft:composter", "position", at, "reason", "compost");
        composter = at; ownPlaced = true;
        c.workStatus("Поставил свой компостер.");
    }

    private boolean carries(Item item) { return w.owner.companionInventory.stream().anyMatch(stack -> stack.is(item)); }

    /** At the composter, a step each few ticks: a full one is emptied, a filling one waited for, else the next thing goes in. */
    private void work(ServerLevel level, BlockPos at) {
        CompanionEntity c = w.owner;
        if (level.getGameTime() < nextAt) return;
        nextAt = level.getGameTime() + PACE;
        BlockState state = level.getBlockState(at);
        int filled = state.getValue(ComposterBlock.LEVEL);
        if (filled == ComposterBlock.READY) {
            List<ItemStack> meal = List.of(new ItemStack(Items.BONE_MEAL));
            if (!w.items.canStore(meal)) { c.refreshInventorySummary(); w.finish(made > 0, "Костная мука готова в компостере, а сумка полна — забрать некуда." + done()); return; }
            // What ComposterBlock.extractProduce does, the meal dropped as his own so that the pickup is his and waited for.
            BlockState emptied = state.setValue(ComposterBlock.LEVEL, 0);
            level.setBlock(at, emptied, 3);
            level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, at, net.minecraft.world.level.gameevent.GameEvent.Context.of(c, emptied));
            level.playSound(null, at, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1F, 1F);
            c.animateAction(1, at, meal.getFirst());
            c.dropHarvest(c.blockPosition(), meal);
            made++; ripening = null;
            return;
        }
        // The last layer turns into bone meal a second later (the block's own tick).
        if (filled == ComposterBlock.MAX_LEVEL) { ripening = at.immutable(); return; }
        // Full a moment ago and empty now, not by his hand: a hopper under the composter took the bone meal he made (review 10.11).
        // Another composter (the first broken, say) is no such sign (review 10.11, round 2).
        if (ripening != null) { if (filled == 0 && ripening.equals(at)) taken++; ripening = null; }
        boolean enough = count != ALL && ("scrap".equals(what) ? made + taken >= count : put >= count);
        ItemStack next = enough ? ItemStack.EMPTY : next();
        if (next.isEmpty()) {
            dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "items_composted", "entity", c.getUUID(), "what", what, "put", put, "made", made, "composter", at);
            c.refreshInventorySummary();
            if (put == 0 && made == 0) { w.finish(false, "Класть в компостер больше нечего." + (filled > 0 ? " Он заполнен на " + filled + " из 7." : "")); return; }
            // For the chain only the bone meal in his bag counts: what a hopper took ends it (he would pick and fill for ever), fewer is planned again.
            w.finish((!meal || made >= count ? CompanionActionResult.ok(report(filled)) : taken > 0 ? CompanionActionResult.blocked(report(filled)) : CompanionActionResult.failed(report(filled)))
                    .withEvidence(new CompanionActionResult.Evidence(w.taskId, "compost_items", what, made, c.getUUID(), null, null)));
            return;
        }
        ItemStack shown = next.copyWithCount(1);
        BlockState after = ComposterBlock.insertItem(c, state, level, next, at);
        level.levelEvent(1500, at, after != state ? 1 : 0);
        c.animateAction(1, at, shown);
        put++;
        c.refreshInventorySummary();
        c.workStatus("Закладываю в компостер: " + put + " шт.");
    }

    private int walk() { return meal ? MEAL_WALK : CompanionToolSense.BENCH_WALK; }

    private String report(int filled) {
        String rest = filled > 0 && filled < ComposterBlock.READY ? " В компостере осталось на " + filled + " из 7." : "";
        String hopper = taken > 0 ? " Костную муку (" + taken + " шт.) из компостера забрала воронка под ним." : "";
        if (made > 0) return "Сделал в компостере костную муку: " + made + " шт." + (put > 0 ? " Ушло " + put + " шт. растительного." : "") + hopper + rest
                + ("scrap".equals(what) && count != ALL && made + taken < count ? " Просил " + count + " — растительного хлама больше нет." : "");
        String fewer = "scrap".equals(what) && count != ALL && made + taken < count ? " Просил " + count + " — растительного хлама больше нет." : "";
        return "Заложил в компостер " + put + " шт." + (taken > 0 ? hopper : " На костную муку пока не хватило.") + rest + fewer;
    }

    /** What was done before a failure, for its line: bone meal made and things put in are not lost with it. */
    private String done() { return made > 0 || put > 0 ? " " + report(0) : ""; }
}
