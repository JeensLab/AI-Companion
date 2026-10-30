package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * farm_crops and harvest_crops, like a player with a hoe. A field is dirt or grass within four blocks of water, the way vanilla
 * farmland is hydrated (water is never made). It is tilled with a hoe that wears, and sown from the seeds in the bag: wheat seeds
 * fall from the grass he tears out, carrots and potatoes are only the ones he carries. A harvest breaks the ripe crops only, picks
 * the drops up from the ground and sows the same farmland again. Every step is read from the world as it is, so a stopped or
 * interrupted order leaves nothing half done: farmland without a crop, or a crop, and the seeds in the bag are what is left.
 */
final class CompanionFarming {
    /** The field is looked for this far from the player at most: the whole search radius would be a world to scan. */
    static final int SCAN_RADIUS = 32;
    /** Vanilla hydration: water this far sideways, at the level of the farmland or a block above it. */
    private static final int WET = 4;
    /** Grass is torn out for seeds within this far of the field, at most so many times and so long (a seed falls once in eight). */
    private static final int GRASS_RADIUS = 16, MAX_GRASS = 120, SEEDS_TICKS = 20 * 120;
    /** How many of the tiles nearest the player are compared on foot to find where the field begins. */
    private static final int NEAR_TILES = 48;
    /** Working ticks one tile or crop may take, walking included, before it is given up. */
    private static final int TARGET_TICKS = 20 * 20;
    /** Farther from the next tile than this he walks in legs first (CompanionTravel). */
    private static final int FAR = 12;
    private enum Stage { LOOK, SEEDS, WORK }
    private enum Sowing { SOWN, NO_SEEDS, UNFIT }
    private record Again(Block crop, Item seed) { }

    final CompanionWork w;
    private Stage stage = Stage.LOOK;
    private boolean harvest;
    private CompanionCrops.Kind kind;
    private int wanted;
    /** The farmland (or soil to be) of what is left to do; the plant stands above it. */
    private final List<BlockPos> todo = new ArrayList<>(), spare = new ArrayList<>(), grass = new ArrayList<>();
    private final Set<BlockPos> tilledNow = new HashSet<>();
    private final Map<BlockPos, Again> replant = new HashMap<>();
    private final Map<Item, Integer> bagBefore = new HashMap<>();
    private int total, found, waterCells, tilled, planted, reused, cleared, grassCut, unreachable, unfit, changed, harvested, replanted, noSeeds, growing, spent;
    private boolean seedShort, bagFull;
    private long seedsUntil;
    /** The soil of the tile (or of the tuft of grass) he is at now. */
    private BlockPos current;

    CompanionFarming(CompanionWork w) { this.w = w; }

    /** Checks the order and forgets the one before; the problem in words, or null. */
    String begin(String action, String item, int count) {
        harvest = "harvest_crops".equals(action);
        kind = CompanionCrops.Kind.of(item);
        if (kind == null && !CompanionCrops.anyKind(item)) return "Не понял, " + (harvest ? "какой урожай собрать" : "что сажать") + ": пшеницу, морковь, картофель или свёклу.";
        if (kind == null && !harvest) kind = CompanionCrops.Kind.WHEAT;
        if (!harvest && count > CompanionCrops.MAX_TILES) return "За раз вскапываю до " + CompanionCrops.MAX_TILES + " клеток.";
        wanted = count;
        stage = Stage.LOOK;
        todo.clear(); spare.clear(); grass.clear(); tilledNow.clear(); replant.clear(); bagBefore.clear();
        total = found = waterCells = tilled = planted = reused = cleared = grassCut = unreachable = unfit = changed = harvested = replanted = noSeeds = growing = spent = 0;
        seedShort = bagFull = false;
        current = null;
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty()) bagBefore.merge(stack.getItem(), stack.getCount(), Integer::sum);
        return null;
    }

    String describe() { return harvest ? "собирает урожай, собрал " + harvested : "делает грядки, посадил " + planted + " из " + wanted; }
    String jobName() { return harvest ? "сбор урожая (" + harvested + ")" : "посадку на грядках (" + planted + " из " + wanted + ")"; }

    void tick(ServerLevel level) {
        if (!w.owner.onGround() && !w.owner.isInWater()) return;
        switch (stage) {
            case LOOK -> { if (harvest) lookForCrops(level); else lookForField(level); }
            case SEEDS -> tearGrass(level);
            case WORK -> work(level);
        }
    }

    // ---- looking round -------------------------------------------------------------------------------------------------------

    /** The tile or tuft in hand is done with or let go; the generic work resets its own target now and then (after the wood), this one is his. */
    private void next() {
        current = null;
        w.resetTarget();
    }

    private int radius() { return Math.min(w.owner.getSearchRadius(), SCAN_RADIUS); }

    /** The tiles for the field, the nearest to the player first, and what is missing for them said before anything is touched. */
    private void lookForField(ServerLevel level) {
        BlockPos centre = w.placementAnchor;
        int radius = radius();
        List<BlockPos> soil = hydratedSoil(level, centre, radius);
        if (waterCells == 0) {
            w.finish(CompanionActionResult.blocked("Воды в " + radius + " блоках от тебя нет, а без неё грядки пересохнут. Воду я не создаю: подойдём к реке или озеру — вскопаю там."));
            return;
        }
        if (soil.isEmpty()) {
            w.finish(CompanionActionResult.blocked("У воды рядом нет подходящей земли: нужна земля или трава не дальше " + WET + " блоков от воды, со свободным местом и светом над ней."));
            return;
        }
        // One compact patch: round the tile he reaches first among those nearest the player, and the ones nearest to that.
        BlockPos heart = nearestOnFoot(soil, centre);
        List<BlockPos> nearest = soil.stream().sorted(Comparator.comparingDouble(tile -> spread(tile, heart))).toList();
        todo.addAll(nearest.subList(0, Math.min(wanted, nearest.size())));
        // The next nearest stand in for a tile that turns out unusable on the way (a bench set down on it, no route to it).
        spare.addAll(nearest.subList(todo.size(), Math.min(nearest.size(), todo.size() + CompanionCrops.MAX_TILES)));
        found = todo.size();
        int have = w.items.countCarried(kind.seed);
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "farm_field_chosen", "entity", w.owner.getUUID(), "crop", kind,
                "tiles", found, "wanted", wanted, "water", waterCells, "seeds", have, "heart", heart);
        if (kind == CompanionCrops.Kind.WHEAT) {
            collectGrass(level, heart);
            if (have == 0 && grass.isEmpty()) {
                w.finish(CompanionActionResult.blocked("У меня нет семян пшеницы, а травы, с которой они падают, рядом не нашёл. Дай мне семена, морковь или картофель — посажу."));
                return;
            }
            if (have < todo.size()) { stage = Stage.SEEDS; seedsUntil = level.getGameTime() + SEEDS_TICKS; next(); return; }
        } else {
            if (have == 0) {
                w.finish(CompanionActionResult.blocked("У меня нет " + kind.lacking + " для посадки. Дай мне их или попроси посадить пшеницу — семена возьму с травы."));
                return;
            }
            if (have < todo.size()) { todo.subList(have, todo.size()).clear(); seedShort = true; }
        }
        startWork();
    }

    /** Dirt and grass (or farmland waiting for a crop) with room above that water reaches, as vanilla farmland would be hydrated. */
    private List<BlockPos> hydratedSoil(ServerLevel level, BlockPos centre, int radius) {
        int low = Math.max(level.getMinBuildHeight(), centre.getY() - 6), high = Math.min(level.getMaxBuildHeight() - 2, centre.getY() + 4);
        int x0 = centre.getX() - radius - WET, z0 = centre.getZ() - radius - WET, side = 2 * (radius + WET) + 1, rows = high - low + 2;
        BitSet water = new BitSet(side * side * rows);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        waterCells = 0;
        for (int dx = 0; dx < side; dx++) for (int dz = 0; dz < side; dz++) {
            if (!level.hasChunkAt(at.set(x0 + dx, low, z0 + dz))) continue;
            for (int y = low; y <= high + 1; y++) if (level.getFluidState(at.setY(y)).is(FluidTags.WATER)) { water.set((dx * side + dz) * rows + y - low); waterCells++; }
        }
        List<BlockPos> soil = new ArrayList<>();
        if (waterCells == 0) return soil;
        for (int dx = WET; dx < side - WET; dx++) for (int dz = WET; dz < side - WET; dz++) {
            long ox = x0 + dx - centre.getX(), oz = z0 + dz - centre.getZ();
            if (ox * ox + oz * oz > (long) radius * radius || !level.hasChunkAt(at.set(x0 + dx, low, z0 + dz))) continue;
            for (int y = low; y <= high; y++) {
                BlockState ground = level.getBlockState(at.setY(y));
                boolean farmland = ground.is(Blocks.FARMLAND);
                if (!farmland && !ground.is(Blocks.DIRT) && !ground.is(Blocks.GRASS_BLOCK)) continue;
                BlockPos cell = at.above();
                BlockState above = level.getBlockState(cell);
                // A crop needs light to be planted at all (CropBlock.canSurvive): no tile is chosen in a cellar or under a thick roof.
                if (!above.isAir() && (farmland || !clearable(above)) || nearFlower(level, cell) || !CropBlock.hasSufficientLight(level, cell) || !wet(water, dx, dz, y - low, side, rows)) continue;
                soil.add(at.immutable());
            }
        }
        return soil;
    }

    /** The nearest tiles by the straight line are compared on foot in one route search: not the one across a river or a fence. */
    private BlockPos nearestOnFoot(List<BlockPos> soil, BlockPos centre) {
        List<BlockPos> near = soil.stream().sorted(Comparator.comparingDouble(tile -> spread(tile, centre))).limit(NEAR_TILES).toList();
        Map<BlockPos, BlockPos> standing = new HashMap<>();
        for (BlockPos tile : near) standing.putIfAbsent(tile.above(), tile);
        Path route = w.owner.getNavigation().createPath(standing.keySet(), 1);
        return route != null && route.canReach() && standing.containsKey(route.getTarget()) ? standing.get(route.getTarget()) : near.getFirst();
    }

    private static boolean wet(BitSet water, int dx, int dz, int row, int side, int rows) {
        for (int x = dx - WET; x <= dx + WET; x++) for (int z = dz - WET; z <= dz + WET; z++) {
            int column = (x * side + z) * rows + row;
            if (water.get(column) || water.get(column + 1)) return true;
        }
        return false;
    }

    private static double spread(BlockPos tile, BlockPos from) {
        double dy = tile.getY() - from.getY();
        return tile.distSqr(from) - dy * dy + 9 * dy * dy;
    }

    /** What a hand pulls out of the way of a hoe: wild grass, ferns, a dead bush — never a flower, someone may have planted it. */
    private static boolean clearable(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH);
    }

    /**
     * A flower on the cell or right beside it: the player's flower bed by the pond (review of PR #39). Its gaps are not dug up either,
     * the field goes to plain grass instead.
     */
    private static boolean nearFlower(ServerLevel level, BlockPos cell) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos at = cell.offset(dx, 0, dz);
            if (level.hasChunkAt(at) && level.getBlockState(at).is(BlockTags.FLOWERS)) return true;
        }
        return false;
    }

    /** Grass that drops wheat seeds (one half of a tall plant). */
    private static boolean seedGrass(BlockState state) {
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) return true;
        return (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private void collectGrass(ServerLevel level, BlockPos heart) {
        grass.clear();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -GRASS_RADIUS; dx <= GRASS_RADIUS; dx++) for (int dz = -GRASS_RADIUS; dz <= GRASS_RADIUS; dz++) for (int dy = -3; dy <= 4; dy++) {
            at.set(heart.getX() + dx, heart.getY() + dy, heart.getZ() + dz);
            if (level.hasChunkAt(at) && seedGrass(level.getBlockState(at))) grass.add(new BlockPos(at.getX(), at.getY() - 1, at.getZ()));
        }
    }

    /** Every crop within reach of the player: the ripe ones to reap, the rest only counted. */
    private void lookForCrops(ServerLevel level) {
        BlockPos centre = w.placementAnchor;
        int radius = radius();
        int low = Math.max(level.getMinBuildHeight(), centre.getY() - 8), high = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + 6);
        for (int cx = (centre.getX() - radius) >> 4; cx <= (centre.getX() + radius) >> 4; cx++) for (int cz = (centre.getZ() - radius) >> 4; cz <= (centre.getZ() + radius) >> 4; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (int index = level.getSectionIndex(low); index <= level.getSectionIndex(high); index++) {
                var section = chunk.getSection(index);
                if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof CropBlock)) continue;
                int bottom = level.getSectionYFromSectionIndex(index) << 4;
                for (int y = Math.max(low, bottom); y <= Math.min(high, bottom + 15); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, y & 15, z);
                    if (!(state.getBlock() instanceof CropBlock crop) || kind != null && state.getBlock() != kind.crop) continue;
                    long ox = (cx << 4) + x - centre.getX(), oz = (cz << 4) + z - centre.getZ();
                    if (ox * ox + oz * oz > (long) radius * radius) continue;
                    if (crop.isMaxAge(state)) todo.add(new BlockPos((cx << 4) + x, y - 1, (cz << 4) + z)); else growing++;
                }
            }
        }
        found = todo.size();
        if (todo.isEmpty()) {
            w.finish(CompanionActionResult.blocked(growing > 0 ? "Пока нечего собирать: спелых нет, ещё растёт посадок: " + growing + "."
                    : "Посадок в " + radius + " блоках от тебя не нашёл."));
            return;
        }
        startWork();
    }

    // ---- wheat seeds from the grass -----------------------------------------------------------------------------------------

    /** Tears out grass one tuft at a time, the nearest first, until the seeds for the field are in the bag. */
    private void tearGrass(ServerLevel level) {
        int have = w.items.countCarried(kind.seed);
        if (have >= todo.size() || grassCut >= MAX_GRASS || level.getGameTime() > seedsUntil) { endSeeds(have); return; }
        if (current == null) {
            current = nearest(grass);
            spent = 0;
            if (current == null) { endSeeds(have); return; }
        }
        BlockPos soil = current, plant = soil.above();
        BlockState state = level.getBlockState(plant);
        if (!seedGrass(state) || ++spent > TARGET_TICKS) { next(); return; }
        int reach = w.items.reachBlock(level, soil, false);
        if (reach < 0) { next(); return; }
        if (reach == 0) { w.owner.workStatus("Иду за травой — нужны семена пшеницы."); return; }
        w.owner.workStatus("Рву траву ради семян: " + have + " из " + todo.size() + ".");
        if (!w.breaking.tick(level, plant)) return;
        if (!pluck(level, plant, state)) { endSeeds(have); return; }
        grassCut++; w.gathered++;
        next();
    }

    private void endSeeds(int have) {
        next();
        if (have == 0) {
            w.finish(CompanionActionResult.blocked(bagFull ? FULL_BAG : "Семян пшеницы не набрал: траву рвал (сорвано: " + grassCut + "), а семена выпадают редко. Дай мне семена или попроси позже."));
            return;
        }
        if (have < todo.size()) { todo.subList(have, todo.size()).clear(); seedShort = true; }
        startWork();
    }

    private void startWork() {
        total = harvest ? Math.min(wanted, todo.size()) : todo.size();
        stage = Stage.WORK;
    }

    // ---- the field and the harvest ------------------------------------------------------------------------------------------

    private void work(ServerLevel level) {
        if (current == null) {
            if (harvest && harvested >= wanted) { finishWork(); return; }
            current = nearest(todo);
            spent = 0;
            if (current == null) { finishWork(); return; }
        }
        BlockPos soil = current;
        if (++spent > TARGET_TICKS) { unreachable++; letGo(); return; }
        if (w.owner.distanceToSqr(soil.getCenter()) > FAR * FAR) {
            var step = w.owner.travel().toward(soil, 8);
            if (step == CompanionTravel.Step.WALKING) { w.owner.workStatus(harvest ? "Иду к посадкам." : "Иду к воде, где будут грядки."); return; }
            w.owner.travel().reset();
            if (step == CompanionTravel.Step.FAILED) { unreachable++; letGo(); return; }
        }
        int reach = w.items.reachBlock(level, soil, false);
        if (reach < 0) { unreachable++; letGo(); return; }
        if (reach == 0) { w.owner.workStatus(harvest ? "Иду к спелым посадкам." : "Иду к грядке."); return; }
        if (harvest) harvestStep(level, soil); else farmStep(level, soil);
    }

    /** One tile: grass off it, the hoe in, the seed down — whichever the tile needs now. */
    private void farmStep(ServerLevel level, BlockPos soil) {
        BlockPos cell = soil.above();
        BlockState ground = level.getBlockState(soil), above = level.getBlockState(cell);
        boolean farmland = ground.is(Blocks.FARMLAND);
        if (farmland && above.is(kind.crop)) { next(); return; }
        if (!farmland && !ground.is(Blocks.DIRT) && !ground.is(Blocks.GRASS_BLOCK) || !above.isAir() && !clearable(above) || !farmland && nearFlower(level, cell)) { changed++; letGo(); return; }
        if (!farmland && !CropBlock.hasSufficientLight(level, cell)) { unfit++; letGo(); return; }
        if (!above.isAir()) {
            w.owner.workStatus("Расчищаю грядку от травы: " + planted + " из " + total + ".");
            if (!w.breaking.tick(level, cell)) return;
            if (!pluck(level, cell, above)) { finishWork(); return; }
            cleared++; w.gathered++;
            return;
        }
        if (!farmland) {
            if (!w.owner.getMainHandItem().is(ItemTags.HOES)) { prepareHoe(); return; }
            w.owner.workStatus("Вскапываю грядку: " + planted + " из " + total + ".");
            till(level, soil);
            return;
        }
        w.owner.workStatus("Сажу: " + planted + " из " + total + ".");
        switch (sow(level, soil, kind.crop, kind.seed)) {
            case SOWN -> { planted++; if (!tilledNow.contains(soil)) reused++; w.gathered++; next(); }
            case NO_SEEDS -> { noSeeds++; finishWork(); }
            case UNFIT -> { unfit++; letGo(); }
        }
    }

    /** A tile that cannot be worked is let go, and the next nearest one stands in for it. */
    private void letGo() {
        next();
        if (!harvest && !spare.isEmpty()) todo.add(spare.removeFirst());
    }

    /** A hoe on dirt or grass makes farmland: a swing, the sound, and a notch off the hoe. */
    private void till(ServerLevel level, BlockPos soil) {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState();
        level.setBlock(soil, farmland, 11);
        level.playSound(null, soil, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(GameEvent.BLOCK_CHANGE, soil, GameEvent.Context.of(w.owner, farmland));
        w.owner.swing(InteractionHand.MAIN_HAND);
        w.owner.getMainHandItem().hurtAndBreak(1, w.owner, EquipmentSlot.MAINHAND);
        tilledNow.add(soil.immutable());
        tilled++; w.gathered++;
        w.cooldown = 6;
    }

    /** A hoe into his hand: the one in the bag, else a wooden one made the usual way (bench, planks, sticks, wood if short). */
    private void prepareHoe() {
        BlockState sample = Blocks.HAY_BLOCK.defaultBlockState();
        if (CompanionTools.equip(w.owner, ItemTags.HOES, sample)) return;
        if (w.crafting.approachCraftingTable() || !w.items.moveHandMaterialsToBag()) return;
        if (CompanionTools.craft(w.owner, "hoe", sample, ItemTags.HOES)) return;
        w.owner.workStatus("Нужна мотыга, сделаю деревянную.");
        w.crafting.requestWood(CompanionWork.Phase.FARM, 2);
    }

    /** One seed of the bag into the ground above the farmland, the way a player plants: the gesture, the sound, the crop. */
    private Sowing sow(ServerLevel level, BlockPos soil, Block crop, Item seed) {
        BlockPos cell = soil.above();
        BlockState sown = crop.defaultBlockState();
        if (w.items.countCarried(seed) == 0) return Sowing.NO_SEEDS;
        if (!level.getBlockState(cell).isAir() || !sown.canSurvive(level, cell)) return Sowing.UNFIT;
        ItemStack taken = w.items.takeCarried(seed, 1);
        level.setBlock(cell, sown, 3);
        var sound = sown.getSoundType();
        level.playSound(null, cell, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, cell, GameEvent.Context.of(w.owner, sown));
        w.owner.animateAction(1, cell, taken);
        return Sowing.SOWN;
    }

    /** One ripe crop: broken, its drops picked up from the ground, then the same farmland sown again from them. */
    private void harvestStep(ServerLevel level, BlockPos soil) {
        BlockPos cell = soil.above();
        BlockState state = level.getBlockState(cell);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && !replant.containsKey(cell)) {
            w.owner.workStatus("Собираю урожай: " + harvested + " из " + total + ".");
            if (!w.breaking.tick(level, cell)) return;
            Item seed = crop.getCloneItemStack(level, cell, state).getItem();
            if (!pluck(level, cell, state)) { finishWork(); return; }
            replant.put(cell.immutable(), new Again(crop, seed));
            harvested++; w.gathered++;
            // The work waits for the pickup by itself; the next step finds the cell empty and sows it.
            return;
        }
        Again again = replant.get(cell);
        if (again != null && state.isAir()) {
            w.owner.workStatus("Сажу заново: " + replanted + " из " + harvested + ".");
            switch (sow(level, soil, again.crop, again.seed)) {
                case SOWN -> { replanted++; w.gathered++; replant.remove(cell); next(); }
                case NO_SEEDS -> { noSeeds++; replant.remove(cell); next(); }
                case UNFIT -> { unfit++; replant.remove(cell); next(); }
            }
            return;
        }
        next();
    }

    /** Breaks a plant with the hand: the drops fall into the world to be picked up. False when the bag could not take them. */
    private boolean pluck(ServerLevel level, BlockPos cell, BlockState state) {
        var drops = Block.getDrops(state, level, cell, null, w.owner, w.owner.getMainHandItem());
        if (!drops.isEmpty() && !w.items.canStore(drops)) { bagFull = true; return false; }
        w.clearCracks();
        level.destroyBlock(cell, false, w.owner, 512);
        if (!drops.isEmpty()) w.owner.dropHarvest(cell, drops);
        return true;
    }

    /** The target nearest to him, taken off the list. */
    private BlockPos nearest(List<BlockPos> list) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : list) {
            double d = w.owner.distanceToSqr(pos.getCenter());
            if (d < distance) { distance = d; best = pos; }
        }
        if (best != null) list.remove(best);
        return best;
    }

    // ---- the report ---------------------------------------------------------------------------------------------------------

    private static final String FULL_BAG = "В сумке нет места: освободи ячейки или попроси сложить вещи в сундук.";

    private static String plots(int n, String one, String many) { return n + " " + (n % 10 == 1 && n % 100 != 11 ? one : many); }

    private void finishWork() {
        next();
        if (harvest) finishHarvest(); else finishField();
    }

    private void finishField() {
        boolean ok = planted >= wanted;
        String text = planted == 0 ? "Ничего не посадил." : (ok ? "Готово: посадил " : "Посадил ") + kind.plant + " на " + plots(planted, "грядке", "грядках")
                + (ok ? "" : " из " + wanted) + (tilled > 0 ? " (вскопал " + tilled + ")" : "") + ".";
        if (ok) text += " Вырастет не сразу — позови собрать, когда созреет.";
        List<String> why = new ArrayList<>();
        if (bagFull) why.add(FULL_BAG);
        if (seedShort || noSeeds > 0) why.add(planted == 0 ? cap(kind.lacking) + " не хватило." : cap(kind.lacking) + " хватило только на " + planted + ".");
        if (!ok && found < wanted) why.add("Подходящей земли у воды нашлось только на " + found + ".");
        if (unreachable > 0) why.add("До " + unreachable + " не смог дойти.");
        if (unfit > 0) why.add("На " + unfit + " слишком темно: там ничего не вырастет.");
        if (changed > 0) why.add(changed + " изменились по дороге, их пропустил.");
        if (grassCut > 0) why.add("Траву рвал ради семян: " + grassCut + ".");
        if (reused > 0) why.add("Уже вскопанных грядок использовал: " + reused + ".");
        w.finish(new CompanionActionResult(ok, text + (why.isEmpty() ? "" : " " + String.join(" ", why)) + " В сумке: " + w.owner.getStoredItemsSummary() + ".")
                .withEvidence(new CompanionActionResult.Evidence(w.taskId, "farm_crops", kind.seedId(), planted, w.owner.getUUID(), null, null)));
    }

    private void finishHarvest() {
        boolean ok = harvested > 0 && !bagFull;
        String gained = gained();
        String text = harvested == 0 ? "Ничего не собрал." : "Собрал урожай с " + plots(harvested, "грядки", "грядок")
                + (replanted > 0 ? ", заново посадил " + replanted : "") + (gained.isEmpty() ? "" : ": в сумке прибавилось " + gained) + ".";
        List<String> why = new ArrayList<>();
        if (bagFull) why.add(FULL_BAG + " Остальное осталось на грядках.");
        if (noSeeds > 0) why.add("Семян на " + noSeeds + " посадок не хватило: там пусто.");
        if (unfit > 0) why.add("На " + unfit + " посадить заново не вышло.");
        if (unreachable > 0) why.add("До " + unreachable + " спелых не смог дойти.");
        if (growing > 0) why.add("Ещё не созрело: " + growing + ".");
        w.finish(new CompanionActionResult(ok, text + (why.isEmpty() ? "" : " " + String.join(" ", why)) + " В сумке: " + w.owner.getStoredItemsSummary() + ".")
                .withEvidence(new CompanionActionResult.Evidence(w.taskId, "harvest_crops", kind == null ? "" : kind.seedId(), harvested, w.owner.getUUID(), null, null)));
    }

    /** What the bag gained since the order began, item by item: «5 «Пшеница», 3 «Семена пшеницы»». */
    private String gained() {
        Map<Item, Integer> now = new LinkedHashMap<>();
        for (ItemStack stack : w.items.carriedItems()) if (!stack.isEmpty()) now.merge(stack.getItem(), stack.getCount(), Integer::sum);
        List<String> more = new ArrayList<>();
        now.forEach((item, count) -> { int plus = count - bagBefore.getOrDefault(item, 0); if (plus > 0) more.add(plus + " «" + CompanionWork.itemName(item) + "»"); });
        return String.join(", ", more);
    }

    private static String cap(String text) { return Character.toUpperCase(text.charAt(0)) + text.substring(1); }
}
