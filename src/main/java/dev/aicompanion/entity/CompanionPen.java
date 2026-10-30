package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * build_pen: a small pen, 4×4 inside, of his own fences and one gate, set up round the animals where they stand (nobody lures
 * animals but a player holding food, so none is led, pushed or moved: the ring goes up and the ones inside stay). Fences and gate
 * are made from his planks and sticks by the ordinary recipes, wood is cut when it is short; the blocks go in one by one with the
 * placing gesture, from outside the ring. An unfinished pen is gone on with where it stands.
 */
final class CompanionPen {
    /** Cells inside along a side, and along a side of the ring of fences round them. */
    static final int INSIDE = 4, OUTER = INSIDE + 2, RING = 4 * OUTER - 4;
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped");
    /** Animals standing in the way of a fence are given this long to move on. */
    private static final int WAIT_TICKS = 20 * 20;

    final CompanionWork w;
    private CompanionGame.Kind kind;
    private int wanted;
    /** North-west cell of the ring, at the height of the fences. */
    private BlockPos base;
    private Direction gateSide = Direction.SOUTH;
    private String wood = "oak";
    /** The pen stands complete: the next order is for another one. */
    private boolean done, prepared, gateNow;
    /** The only fit sites round the animals would have stood on the player: he is told to step aside, not that there is no room. */
    private boolean playerInTheWay;
    private int fencesNow, cooldown;
    private BlockPos target;
    private Path path;
    private long progressTime, waitingSince;
    private double bestDistance;
    private final Set<BlockPos> unreachable = new HashSet<>();

    CompanionPen(CompanionWork w) { this.w = w; }

    /** Null if the order is taken. An unfinished pen of the same animals is not begun again from the first fence. */
    CompanionActionResult begin(String item, int count) {
        CompanionGame.Kind requested = CompanionGame.Kind.of(item);
        if (requested == null && !CompanionGame.anyKind(item)) return CompanionActionResult.failed("Не понял, для каких животных загон: коровы, овцы, свиньи, куры или кролики.");
        if (count < 1 || count > CompanionGame.MAX_PEN) return CompanionActionResult.failed("В загоне держу от 1 до " + CompanionGame.MAX_PEN + " животных.");
        cancel();
        if (base == null || done || requested != kind) base = null;
        kind = requested; wanted = count; done = prepared = gateNow = false; fencesNow = 0; unreachable.clear();
        return null;
    }

    /** Placed blocks belong to the world, not to the order: only the walk is dropped. */
    void cancel() { target = null; path = null; cooldown = 0; waitingSince = 0; }

    void tick(ServerLevel level) {
        if (!w.owner.onGround()) return;
        // The fences are made first, where he stands: a bench set down while he walks round the ring could land in it.
        if (!prepared) {
            if (!prepare(level)) return;
            prepared = true;
            w.owner.workStatus("Материалы для загона готовы.");
        }
        if (base == null && !chooseSite(level)) return;
        // Back from the trees, or far from the place for another reason: the way to it is walked in legs.
        if (w.owner.blockPosition().distSqr(base) > 24 * 24) {
            var step = w.owner.travel().toward(base, 12);
            if (step == CompanionTravel.Step.WALKING) { w.owner.workStatus("Иду к месту загона на " + base.toShortString() + "."); return; }
            w.owner.travel().reset();
            if (step == CompanionTravel.Step.FAILED) { w.finish(false, "Не смог дойти до места загона на " + base.toShortString() + ". Материалы сохранил."); return; }
        }
        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos cell : ring()) {
            if (!level.hasChunkAt(cell)) { w.finish(false, "Место загона сейчас за пределами загруженной области. Вернёмся поближе и продолжим."); return; }
            if (stands(level, cell)) continue;
            if (!free(level, cell)) {
                w.finish(false, "На месте загона появился чужой блок на " + cell.toShortString() + ". Не буду его ломать: освободи место и попроси достроить загон.");
                return;
            }
            missing.add(cell);
        }
        if (missing.isEmpty()) { complete(level); return; }
        if (cooldown > 0) { cooldown--; return; }
        place(level, missing);
    }

    // ---- the place ----

    private boolean chooseSite(ServerLevel level) {
        List<Animal> around = animals(level, kind);
        if (kind == null && !around.isEmpty()) {
            Map<CompanionGame.Kind, Long> counts = around.stream().collect(Collectors.groupingBy(CompanionGame.Kind::of, Collectors.counting()));
            kind = java.util.Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
            around = around.stream().filter(animal -> CompanionGame.Kind.of(animal) == kind).toList();
        }
        BlockPos anchor = w.placementAnchor != null ? w.placementAnchor : w.owner.blockPosition();
        base = around.isEmpty() ? nearestClear(level, anchor) : bestAround(level, around, anchor);
        if (base == null) {
            w.finish(false, playerInTheWay ? "Ты стоишь прямо среди животных: загон встал бы вокруг тебя. Отойди в сторону, и я его поставлю."
                    : around.isEmpty() ? "Не нашёл ровную свободную площадку 6×6 рядом. Давай выберем место попросторнее."
                    : "Рядом с животными не нашёл ровную свободную площадку 6×6 для загона. Нужно место попросторнее.");
            return false;
        }
        double nearest = Double.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            double distance = gateCell(base, side).distSqr(anchor);
            if (distance < nearest) { nearest = distance; gateSide = side; }
        }
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "pen_site", "entity", w.owner.getUUID(), "base", base,
                "animals", around.size(), "kind", kind == null ? "any" : kind.key, "gate", gateSide);
        return true;
    }

    /** The animals this order is for: of the asked kind, or of any kind, within his search radius. */
    private List<Animal> animals(ServerLevel level, CompanionGame.Kind wanted) {
        int radius = Math.clamp(w.owner.getSearchRadius(), 16, 32);
        return level.getEntitiesOfClass(Animal.class, new AABB(w.owner.blockPosition()).inflate(radius, 8, radius),
                animal -> animal.isAlive() && CompanionGame.Kind.of(animal) != null && (wanted == null || CompanionGame.Kind.of(animal) == wanted));
    }

    /**
     * The ring that holds the most of them inside (up to the number asked), the nearest among equals. The sites are ranked by the
     * animals first and only then looked at for clear ground; one that would stand on the player or on himself comes last.
     */
    private BlockPos bestAround(ServerLevel level, List<Animal> animals, BlockPos anchor) {
        Map<BlockPos, Long> scores = new HashMap<>();
        for (Animal seed : animals.stream().sorted(java.util.Comparator.comparingDouble(w.owner::distanceToSqr)).limit(12).toList()) {
            for (int dx = -INSIDE; dx <= -1; dx++) for (int dz = -INSIDE; dz <= -1; dz++) for (int dy : new int[]{0, -1, 1}) {
                BlockPos candidate = seed.blockPosition().offset(dx, dy, dz);
                if (scores.containsKey(candidate)) continue;
                AABB inside = interior(candidate);
                int count = (int) animals.stream().filter(animal -> inside.contains(animal.position())).count();
                scores.put(candidate, Math.min(count, wanted) * 100_000L + count * 1_000L - (long) w.owner.distanceToSqr(Vec3.atCenterOf(candidate)));
            }
        }
        List<BlockPos> ranked = scores.entrySet().stream().sorted(Map.Entry.<BlockPos, Long>comparingByValue().reversed()).map(Map.Entry::getKey).toList();
        playerInTheWay = false;
        for (boolean avoid : new boolean[]{true, false}) for (BlockPos candidate : ranked) {
            if (avoid && (near(candidate, anchor) || near(candidate, w.owner.blockPosition())) || !siteClear(level, candidate)) continue;
            if (!onPlayer(candidate)) return candidate;
            playerInTheWay = true;
        }
        return null;
    }

    /** The ring, with a block of margin, would stand on the player: he would be shut in, never mind how many animals it holds. */
    private boolean onPlayer(BlockPos corner) {
        var player = w.owner.getOwnerPlayer();
        return player != null && near(corner, player.blockPosition());
    }

    /** No animals to round up: the nearest clear 6×6 to the player. */
    private BlockPos nearestClear(ServerLevel level, BlockPos anchor) {
        for (int radius = 3; radius <= 16; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            for (int dy : new int[]{0, -1, 1, -2, 2}) {
                BlockPos candidate = anchor.offset(dx - 2, dy, dz - 2);
                if (!near(candidate, anchor) && !near(candidate, w.owner.blockPosition()) && !onPlayer(candidate) && siteClear(level, candidate)) return candidate;
            }
        }
        return null;
    }

    /** The pen, with a block of margin, would stand on this cell. */
    private static boolean near(BlockPos corner, BlockPos pos) {
        return pos.getX() >= corner.getX() - 1 && pos.getX() <= corner.getX() + OUTER && pos.getZ() >= corner.getZ() - 1 && pos.getZ() <= corner.getZ() + OUTER;
    }

    /** Flat, dry, free ground for the ring and the animals inside, and a way round it for him. */
    private boolean siteClear(ServerLevel level, BlockPos corner) {
        for (int i = 0; i < OUTER; i++) for (int j = 0; j < OUTER; j++) {
            BlockPos cell = corner.offset(i, 0, j), floor = cell.below();
            if (!level.hasChunkAt(cell) || !level.hasChunkAt(floor) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty()
                    || !level.getFluidState(cell).isEmpty() || level.getBlockEntity(cell) != null) return false;
            boolean ring = i == 0 || j == 0 || i == OUTER - 1 || j == OUTER - 1;
            BlockState at = level.getBlockState(cell);
            if (ring ? !at.canBeReplaced() && !at.is(BlockTags.FENCES) : !at.getCollisionShape(level, cell).isEmpty()
                    || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()) return false;
        }
        // No door of the player's in the pen or right beside it, no gate of his just outside: the ring must not shut his way in or out.
        for (int i = -1; i <= OUTER; i++) for (int j = -1; j <= OUTER; j++) {
            BlockState at = level.getBlockState(corner.offset(i, 0, j));
            boolean outside = i < 0 || j < 0 || i >= OUTER || j >= OUTER;
            if (at.is(BlockTags.DOORS) || outside && at.is(BlockTags.FENCE_GATES)) return false;
        }
        Set<BlockPos> doors = Set.of(corner.offset(2, 0, -1), corner.offset(2, 0, OUTER), corner.offset(-1, 0, 2), corner.offset(OUTER, 0, 2));
        if (doors.stream().noneMatch(cell -> CompanionMiningNavigation.standable(w.owner, cell))) return false;
        Path approach = w.owner.getNavigation().createPath(doors, 0);
        return approach != null && approach.canReach();
    }

    // ---- the ring ----

    private static BlockPos gateCell(BlockPos corner, Direction side) {
        return switch (side) {
            case NORTH -> corner.offset(2, 0, 0);
            case SOUTH -> corner.offset(2, 0, OUTER - 1);
            case WEST -> corner.offset(0, 0, 2);
            default -> corner.offset(OUTER - 1, 0, 2);
        };
    }
    private BlockPos gateCell() { return gateCell(base, gateSide); }

    /** The cells of the ring, the gate last. */
    private List<BlockPos> ring() {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i < OUTER; i++) for (int j = 0; j < OUTER; j++)
            if ((i == 0 || j == 0 || i == OUTER - 1 || j == OUTER - 1) && !base.offset(i, 0, j).equals(gateCell())) cells.add(base.offset(i, 0, j));
        cells.add(gateCell());
        return cells;
    }

    private static AABB interior(BlockPos corner) {
        return new AABB(corner.getX() + 1, corner.getY() - .5, corner.getZ() + 1, corner.getX() + 1 + INSIDE, corner.getY() + 2.5, corner.getZ() + 1 + INSIDE);
    }
    private boolean insideRing(BlockPos pos) {
        return pos.getX() >= base.getX() && pos.getX() < base.getX() + OUTER && pos.getZ() >= base.getZ() && pos.getZ() < base.getZ() + OUTER
                && pos.getY() >= base.getY() - 1 && pos.getY() <= base.getY() + 2;
    }

    private Block fence() { return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_fence")); }
    private Block gate() { return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_fence_gate")); }
    /** A fence of any wood stands for a fence: a pen begun from other planks is finished with what he has. */
    private boolean stands(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return cell.equals(gateCell()) ? state.is(BlockTags.FENCE_GATES) : state.is(BlockTags.WOODEN_FENCES);
    }
    private boolean free(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        return state.canBeReplaced() && level.getFluidState(cell).isEmpty() && level.getBlockEntity(cell) == null && level.getWorldBorder().isWithinBounds(cell);
    }
    /** Nobody stands in the cell: an animal, the player or he himself. */
    private boolean clear(ServerLevel level, BlockPos cell) {
        return level.isUnobstructed(fence().defaultBlockState(), cell, CollisionContext.empty()) && !w.owner.getBoundingBox().intersects(new AABB(cell));
    }

    // ---- materials ----

    /** True when the fences and the gate are in the bag; otherwise the crafting, the walk to a bench or the wood is under way. */
    private boolean prepare(ServerLevel level) {
        int fences = RING - 1, gates = 1;
        if (base == null) chooseWood();
        else {
            fences = gates = 0;
            for (BlockPos cell : ring()) if (level.hasChunkAt(cell) && !stands(level, cell)) { if (cell.equals(gateCell())) gates = 1; else fences++; }
        }
        int fenceShort = fences - w.items.countCarried(fence().asItem());
        int gateShort = gates - w.items.countCarried(gate().asItem());
        if (fenceShort <= 0 && gateShort <= 0) return true;
        String fenceId = BuiltInRegistries.ITEM.getKey(fence().asItem()).toString();
        if (w.crafting.needsWorkbench(fenceId) && w.crafting.approachCraftingTable()) return false;
        if (!w.items.moveHandMaterialsToBag()) return false;
        for (Block block : new Block[]{fence(), gate()}) {
            int lacking = block == fence() ? fenceShort : gateShort;
            if (lacking <= 0) continue;
            CompanionActionResult made = w.owner.craftFromBag(BuiltInRegistries.ITEM.getKey(block.asItem()).toString(), lacking);
            if (made.success()) continue;
            if (made.status() == CompanionActionResult.Status.SHORTAGE) w.crafting.requestWood(CompanionWork.Phase.PEN, logsToCut(fenceShort, gateShort));
            else w.finish(false, made.message());
            return false;
        }
        return true;
    }

    /** The wood the bag holds most of; with a tie, the one already chosen. */
    private void chooseWood() {
        String best = wood;
        int most = -1;
        for (String type : WOODS) {
            int amount = 0;
            for (ItemStack stack : w.items.carriedItems()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if (id.equals(type + "_planks")) amount += stack.getCount();
                else if (stack.is(ItemTags.LOGS) && (id.startsWith(type + "_") || id.startsWith("stripped_" + type + "_"))) amount += stack.getCount() * 4;
                else if (id.equals(type + "_fence")) amount += stack.getCount() * 2;
                else if (id.equals(type + "_fence_gate")) amount += stack.getCount() * 3;
            }
            if (amount > most || amount == most && type.equals(wood)) { most = amount; best = type; }
        }
        wood = best;
    }

    /** Logs to cut for what is still to make: four planks and two sticks a craft of three fences, and a bench if none is near. */
    private int logsToCut(int fenceShort, int gateShort) {
        int crafts = (Math.max(0, fenceShort) + 2) / 3, gates = Math.max(0, gateShort);
        int sticks = 2 * crafts + 4 * gates - w.items.countCarried(Items.STICK);
        int planks = 4 * crafts + 2 * gates + 2 * Math.max(0, (sticks + 3) / 4) + (w.owner.hasCraftingTableNearby() ? 0 : 4);
        int have = 0;
        for (ItemStack stack : w.items.carriedItems()) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (id.equals(wood + "_planks")) have += stack.getCount();
            else if (stack.is(ItemTags.LOGS) && (id.startsWith(wood + "_") || id.startsWith("stripped_" + wood + "_"))) have += stack.getCount() * 4;
        }
        return Math.clamp((planks - have + 3) / 4 + 1, 2, 24);
    }

    // ---- the blocks ----

    private void place(ServerLevel level, List<BlockPos> missing) {
        if (target == null || !missing.contains(target)) {
            target = null; path = null;
            // The gate goes in last, once the fences stand.
            List<BlockPos> candidates = missing.stream().filter(cell -> missing.size() == 1 || !cell.equals(gateCell())).filter(cell -> !unreachable.contains(cell))
                    .sorted(java.util.Comparator.comparingDouble(cell -> w.owner.distanceToSqr(cell.getCenter()))).toList();
            boolean blocked = false;
            for (BlockPos cell : candidates) {
                if (!clear(level, cell)) { blocked = true; continue; }
                if (canPlaceFromHere(level, cell)) { select(level, cell); break; }
                Path route = route(level, cell);
                if (route != null && route.canReach()) { select(level, cell); path = route; break; }
                unreachable.add(cell);
            }
            if (target == null) {
                if (blocked) {
                    // Animals or the player in the way of a fence: given a little time to move on.
                    if (waitingSince == 0) waitingSince = level.getGameTime();
                    if (level.getGameTime() - waitingSince <= WAIT_TICKS) { w.owner.workStatus("Жду, пока освободится место для ограды загона."); return; }
                    w.finish(false, "Место для оставшихся оград занято, там стоят животные или кто-то из нас. Поставил оград: " + fencesNow + ". Достроить можно по новой просьбе.");
                } else w.finish(false, "Не могу подойти к оставшимся оградам загона. Поставил оград: " + fencesNow + ". Освободи проход и попроси достроить.");
                return;
            }
            waitingSince = 0;
        }
        BlockPos cell = target;
        w.owner.getLookControl().setLookAt(cell.getX() + .5, cell.getY() + .5, cell.getZ() + .5);
        double distance = w.owner.distanceToSqr(cell.getCenter());
        if (distance + .05 < bestDistance) { bestDistance = distance; progressTime = level.getGameTime(); }
        if (!clear(level, cell)) { target = null; path = null; return; }
        if (canPlaceFromHere(level, cell)) { put(level, cell, missing.size()); return; }
        if (level.getGameTime() - progressTime > 160) { unreachable.add(cell); target = null; path = null; w.owner.getNavigation().stop(); return; }
        if (path == null || w.owner.getNavigation().isDone() || w.owner.getNavigation().isStuck()) path = route(level, cell);
        if (path != null) CompanionMiningNavigation.walk(w.owner, path);
        w.owner.workStatus("Подхожу, чтобы поставить ограду загона: осталось " + missing.size() + " из " + RING + ".");
    }

    private void select(ServerLevel level, BlockPos cell) { target = cell; path = null; progressTime = level.getGameTime(); bestDistance = Double.MAX_VALUE; }

    /** Within reach and in sight, and not from inside the ring: he would shut himself in. */
    private boolean canPlaceFromHere(ServerLevel level, BlockPos cell) {
        return w.owner.onGround() && !insideRing(w.owner.blockPosition()) && visible(level, w.owner.getEyePosition(), cell);
    }

    private void put(ServerLevel level, BlockPos cell, int missing) {
        w.owner.getNavigation().stop();
        boolean gate = cell.equals(gateCell());
        Block block = gate ? gate() : fence();
        ItemStack material = held(block.asItem());
        if (material.isEmpty()) {
            if (w.items.countCarried(block.asItem()) > 0) { w.finish(false, "В сумке нет места, чтобы взять ограду в руку. Освободи ячейку."); return; }
            prepared = false; cancel(); return;
        }
        BlockState state = gate ? block.defaultBlockState().setValue(FenceGateBlock.FACING, gateSide.getAxis() == Direction.Axis.Z ? Direction.NORTH : Direction.WEST)
                : Block.updateFromNeighbourShapes(block.defaultBlockState(), level, cell);
        if (!state.canSurvive(level, cell) || !level.setBlock(cell, state, 3)) { w.finish(false, "Не получилось поставить ограду загона. Материалы сохранил."); return; }
        w.owner.animateAction(1, cell, material);
        if (gate) w.owner.rememberPlaced(cell);
        material.shrink(1);
        w.owner.setChanged();
        w.owner.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, cell, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
        if (gate) gateNow = true; else fencesNow++;
        target = null; path = null; cooldown = 4; unreachable.clear();
        w.owner.workStatus("Строю загон: " + (RING - missing + 1) + " из " + RING + ".");
    }

    private ItemStack held(Item item) {
        if (!w.owner.getMainHandItem().is(item) && !w.owner.equipForWork(BuiltInRegistries.ITEM.getKey(item).toString()).success()) return ItemStack.EMPTY;
        return w.owner.getMainHandItem();
    }

    /** Like the house builder: any face of the cell in sight, as a player aims. */
    private boolean visible(ServerLevel level, Vec3 eye, BlockPos pos) {
        double x = Math.clamp(eye.x, pos.getX() + .01, pos.getX() + .99), z = Math.clamp(eye.z, pos.getZ() + .01, pos.getZ() + .99);
        double y = Math.clamp(eye.y, pos.getY() + .01, pos.getY() + .99);
        for (Vec3 aim : new Vec3[]{new Vec3(x, y, z), new Vec3(x, pos.getY() + .49, z), pos.getCenter()}) {
            if (eye.distanceToSqr(aim) > 4.5 * 4.5) continue;
            var hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w.owner));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }

    /** Cells outside the ring from which the cell can be reached with the hand. */
    private Path route(ServerLevel level, BlockPos cell) {
        Set<BlockPos> feet = new HashSet<>();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos pos = cell.offset(dx, dy, dz);
            if (insideRing(pos) || !CompanionMiningNavigation.standable(w.owner, pos)) continue;
            if (visible(level, Vec3.atBottomCenterOf(pos).add(0, w.owner.getEyeHeight(), 0), cell)) feet.add(pos);
        }
        return feet.isEmpty() ? null : w.owner.getNavigation().createPath(feet, 0);
    }

    // ---- the result ----

    private void complete(ServerLevel level) {
        done = true;
        AABB inside = interior(base);
        java.util.function.Predicate<Animal> ours = animal -> animal.isAlive() && CompanionGame.Kind.of(animal) != null && (kind == null || CompanionGame.Kind.of(animal) == kind);
        List<Animal> in = level.getEntitiesOfClass(Animal.class, inside, ours.and(animal -> inside.contains(animal.position())));
        List<Animal> out = level.getEntitiesOfClass(Animal.class, new AABB(base).inflate(14, 4, 14), ours.and(animal -> !inside.contains(animal.position())));
        String made = fencesNow == 0 && !gateNow ? "Загон уже стоял целым" : "Поставил" + (fencesNow > 0 ? " оград: " + fencesNow : "") + (gateNow ? (fencesNow > 0 ? " и калитку" : " калитку") : "")
                + (fencesNow < RING - 1 && fencesNow > 0 ? " (остальные " + (RING - 1 - fencesNow) + " стояли раньше)" : "");
        String said = !in.isEmpty() ? "Внутри " + describe(in) + (in.size() < wanted ? " (просили держать до " + wanted + ")." : ".")
                : out.isEmpty() ? "Внутри пока никого: " + (kind == null ? "животных" : kind.many) + " рядом не нашёл." : "Внутри никого нет, животные остались снаружи.";
        w.finish(true, "Загон готов на " + base.toShortString() + ": ограда 6×6, внутри 4×4, калитка с " + side() + " стороны. " + made + ". " + said
                + (in.isEmpty() || out.isEmpty() ? "" : " Снаружи рядом осталось ещё " + describe(out) + "."));
        dev.aicompanion.CompanionDiagnostics.event(w.owner.diagnosticTrace(), "pen_finished", "entity", w.owner.getUUID(), "base", base,
                "fences", fencesNow, "gate", gateNow, "inside", in.size(), "outside", out.size());
    }

    private String side() {
        return switch (gateSide) { case NORTH -> "северной"; case SOUTH -> "южной"; case WEST -> "западной"; default -> "восточной"; };
    }

    /** «3 коровы, 1 свинья». */
    static String describe(List<Animal> animals) {
        Map<CompanionGame.Kind, Integer> kinds = new LinkedHashMap<>();
        for (Animal animal : animals) kinds.merge(CompanionGame.Kind.of(animal), 1, Integer::sum);
        return kinds.entrySet().stream().map(entry -> entry.getKey().are(entry.getValue())).collect(Collectors.joining(", "));
    }

    // ---- saved with the companion: where an unfinished pen stands ----

    void save(CompoundTag tag) {
        if (base == null) return;
        CompoundTag data = new CompoundTag();
        data.putLong("Base", base.asLong());
        data.putInt("Gate", gateSide.get2DDataValue());
        data.putString("Wood", wood);
        data.putString("Kind", kind == null ? "" : kind.key);
        data.putBoolean("Done", done);
        data.putString("Dimension", w.owner.level().dimension().location().toString());
        tag.put("AnimalPen", data);
    }

    void load(CompoundTag tag) {
        base = null; done = false; cancel();
        CompoundTag data = tag.getCompound("AnimalPen");
        if (!data.contains("Base") || !WOODS.contains(data.getString("Wood")) || !w.owner.level().dimension().location().toString().equals(data.getString("Dimension"))) return;
        base = BlockPos.of(data.getLong("Base"));
        gateSide = Direction.from2DDataValue(data.getInt("Gate"));
        wood = data.getString("Wood");
        kind = CompanionGame.Kind.of(data.getString("Kind"));
        done = data.getBoolean("Done");
    }
}
