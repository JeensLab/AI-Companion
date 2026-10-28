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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
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

/** One modest house, built from real items. The saved blueprint also allows interrupted work to resume. */
final class CompanionHouse {
    enum State { WORKING, NEED_WOOD, DONE, FAILED }
    record Result(State state, String message, int logs) {
        static Result working() { return new Result(State.WORKING, "", 0); }
        static Result failed(String message) { return new Result(State.FAILED, message, 0); }
    }
    private record Step(BlockPos pos, BlockState state, int stage) {
        boolean door() { return state.getBlock() instanceof DoorBlock; }
        Item item() { return state.getBlock().asItem(); }
    }
    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped");
    private final CompanionEntity owner;
    private BlockPos base;
    private String wood = "oak";
    private final BitSet placed = new BitSet();
    private List<Step> steps = List.of();
    private boolean prepared;
    private int target = -1;
    private int cooldown;
    private Path path;
    private BlockPos workbench;
    private long progressTime;
    private double bestDistance;
    private final java.util.HashSet<Integer> unreachable = new java.util.HashSet<>();

    CompanionHouse(CompanionEntity owner) { this.owner = owner; }

    void begin() {
        prepared = false;
        cancel();
        unreachable.clear();
    }

    void cancel() {
        target = -1;
        path = null;
        cooldown = 0;
        // Placed blocks and the blueprint belong to the world, not the active command.
    }

    boolean needsPreparation() { return !prepared; }
    BlockPos interiorAnchor() { return base == null ? null : base.offset(2, 1, 2); }
    boolean inside(BlockPos pos) {
        return base != null && pos.getY() == base.getY() + 1
                && pos.getX() > base.getX() && pos.getX() < base.getX() + 4
                && pos.getZ() > base.getZ() && pos.getZ() < base.getZ() + 4;
    }
    boolean interiorAvailable(ServerLevel level) {
        if (base == null) return false;
        BlockPos center = interiorAnchor();
        // A saved blueprint alone does not prove there is still a house in the world.
        return level.hasChunkAt(center) && level.hasChunkAt(center.above(3))
                && level.getBlockState(center.below()).isFaceSturdy(level, center.below(), Direction.UP)
                && !level.getBlockState(center.above(3)).isAir()
                && !level.getBlockState(base.offset(0, 1, 2)).isAir()
                && !level.getBlockState(base.offset(4, 1, 2)).isAir()
                && !level.getBlockState(base.offset(2, 1, 4)).isAir();
    }
    Result ensureSite(ServerLevel level) {
        if (base != null) return null;
        base = findSite(level);
        return base == null ? Result.failed("Не нашёл ровную свободную площадку 7×7 рядом. Давай выберем место попросторнее.") : null;
    }

    Result tick(ServerLevel level) {
        if (!owner.onGround()) return Result.working();
        if (base == null) {
            base = findSite(level);
            if (base == null) return Result.failed("Не нашёл ровную свободную площадку 7×7 рядом. Давай выберем место попросторнее.");
        }
        if (!prepared) {
            if (placed.isEmpty()) wood = chooseWood();
            steps = blueprint();
            Result checked = checkBlueprint(level);
            if (checked != null) return checked;
            if (placed.cardinality() == steps.size()) return finished();
            Result materials = prepareMaterials(level);
            if (materials != null) return materials;
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
            int stage = Integer.MAX_VALUE;
            for (int index = 0; index < steps.size(); index++) if (!placed.get(index)) stage = Math.min(stage, steps.get(index).stage);
            List<Integer> candidates = new ArrayList<>();
            for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && steps.get(index).stage == stage && !unreachable.contains(index)) candidates.add(index);
            candidates.sort(Comparator.comparingDouble(index -> owner.distanceToSqr(steps.get(index).pos.getCenter())));
            for (int index : candidates) {
                Step step = steps.get(index);
                if (!free(level, step)) return Result.failed("На месте стройки появился другой блок. Не буду его ломать. Освободи место и попроси достроить дом.");
                if (!supported(level, step)) continue;
                if (canPlaceFromHere(level, step)) { select(index, level); break; }
                Path route = route(level, step);
                if (route != null && route.canReach()) { select(index, level); path = route; break; }
                // The tick's cells went elsewhere: the rest of the steps are asked next tick, none is written off.
                if (route == null && routeBusy) return Result.working();
                unreachable.add(index);
            }
            if (target < 0) return Result.failed("Не могу подойти к оставшимся блокам дома. Освободи проход и скажи «дострой дом».");
        }
        Step step = steps.get(target);
        if (!free(level, step)) return Result.failed("Место для следующего блока занято. Сохранил стройку; продолжу, когда освободишь место.");
        owner.getLookControl().setLookAt(step.pos.getX() + .5, step.pos.getY() + .5, step.pos.getZ() + .5);
        double distance = owner.distanceToSqr(step.pos.getCenter());
        if (distance + .05 < bestDistance) { bestDistance = distance; progressTime = level.getGameTime(); }
        if (canPlaceFromHere(level, step)) {
            owner.getNavigation().stop();
            ItemStack material = heldMaterial(step.item());
            if (material.isEmpty()) { prepared = false; cancel(); return Result.working(); }
            if (!supported(level, step) || !step.state.canSurvive(level, step.pos)) return Result.failed("Опора дома изменилась. Сначала нужно восстановить площадку.");
            BlockState previous = level.getBlockState(step.pos);
            BlockState above = step.door() ? level.getBlockState(step.pos.above()) : null;
            if (!level.setBlock(step.pos, step.state, 3)) return Result.failed("Не получилось поставить блок дома. Материалы сохранил.");
            if (step.door() && !level.setBlock(step.pos.above(), step.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3)) {
                level.setBlock(step.pos, previous, 3);
                level.setBlock(step.pos.above(), above, 3);
                return Result.failed("Не получилось установить дверь. Материалы сохранил.");
            }
            owner.animateAction(1, step.pos, material);
            owner.rememberPlaced(step.pos);
            material.shrink(1);
            owner.setChanged();
            owner.swing(InteractionHand.MAIN_HAND);
            var sound = step.state.getSoundType();
            level.playSound(null, step.pos, sound.getPlaceSound(), SoundSource.BLOCKS, .65F, 1F);
            placed.set(target);
            owner.workStatus("Строю дом: " + placed.cardinality() + " из " + steps.size() + " блоков. Место: " + base.toShortString() + ".");
            target = -1; path = null; cooldown = 5; unreachable.clear();
        } else {
            if (level.getGameTime() - progressTime > 160) {
                unreachable.add(target); target = -1; path = null; owner.getNavigation().stop();
                return Result.working();
            }
            if (path == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck()) path = route(level, step);
            if (path != null) {
                owner.work.route.walk(path);
            }
        }
        return Result.working();
    }

    private void select(int index, ServerLevel level) {
        target = index; path = null; progressTime = level.getGameTime(); bestDistance = Double.MAX_VALUE;
    }

    private Result finished() { return new Result(State.DONE, "Дом готов. Вход с северной стороны, координаты: " + base.offset(2, 1, 0).toShortString() + ".", 0); }

    private List<Step> blueprint() {
        List<Step> result = new ArrayList<>();
        BlockState planks = block("planks").defaultBlockState();
        for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) result.add(new Step(base.offset(x, 0, z), planks, 0));
        result.add(new Step(base.offset(2, 0, -1), block("stairs").defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), 0));
        for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) {
            if (x != 0 && x != 4 && z != 0 && z != 4) continue;
            if (z == 0 && x == 2 && y < 3) continue;
            if (y == 2 && (z == 4 && x == 2 || z == 2 && (x == 0 || x == 4))) continue;
            result.add(new Step(base.offset(x, y, z), planks, y));
        }
        // A shallow roof with a one-block overhang. Low enough to build from the ground/interior.
        for (int z = -1; z <= 5; z++) for (int x = -1; x <= 5; x++) {
            // Cap the walls before enclosing their tops with the overhang. Otherwise a final
            // tile can become invisible from every ground-level standing position.
            boolean overhang = x < 0 || x > 4 || z < 0 || z > 4;
            int stage = overhang ? 6 : x == 0 || x == 4 || z == 0 || z == 4 ? 4 : 5;
            result.add(new Step(base.offset(x, 4, z), block("slab").defaultBlockState(), stage));
        }
        result.add(new Step(base.offset(2, 1, 0), block("door").defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH), 7));
        return List.copyOf(result);
    }

    private Block block(String suffix) { return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(wood + "_" + suffix)); }

    private String chooseWood() {
        String best = wood;
        int most = -1;
        List<ItemStack> carried = inventory();
        carried.add(owner.getMainHandItem()); carried.add(owner.getOffhandItem());
        for (String type : WOODS) {
            int amount = 0;
            for (ItemStack stack : carried) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if (id.equals(type + "_planks")) amount += stack.getCount();
                if (stack.is(ItemTags.LOGS) && (id.startsWith(type + "_") || id.startsWith("stripped_" + type + "_"))) amount += stack.getCount() * 4;
                if (id.equals(type + "_slab")) amount += stack.getCount() / 2;
                if (id.equals(type + "_stairs")) amount += stack.getCount();
                if (id.equals(type + "_door")) amount += stack.getCount() * 2;
            }
            if (amount > most) { most = amount; best = type; }
        }
        return best;
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
        for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && !steps.get(index).state.is(block("planks"))) required.merge(steps.get(index).item(), 1, Integer::sum);
        for (int index = 0; index < steps.size(); index++) if (!placed.get(index) && steps.get(index).state.is(block("planks"))) required.merge(steps.get(index).item(), 1, Integer::sum);
        List<ItemStack> trial = inventory();
        boolean tableNearby = owner.hasCraftingTableNearby();
        boolean needsStation = false;
        for (var entry : required.entrySet()) if (entry.getKey() != block("planks").asItem() && count(trial, entry.getKey()) < entry.getValue()) needsStation = true;
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
                var plan = CompanionCrafting.plan(level, trial, BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(),
                        Math.min(64, entry.getValue() - count(trial, entry.getKey())), tableNearby || placeTable);
                if (!plan.success()) {
                    if (plan.message().contains("места") || plan.message().contains("сложная")) return Result.failed(plan.message());
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
            BlockPos table = tableSite(level);
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

    private BlockPos tableSite(ServerLevel level) {
        for (int radius = 1; radius <= 4; radius++) for (int dy : new int[]{0, -1, 1}) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos pos = owner.blockPosition().offset(dx, dy, dz);
            if (reserved(pos) || !replaceable(level, pos) || !level.hasChunkAt(pos.below())) continue;
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && level.noCollision(new AABB(pos))) return pos;
        }
        return null;
    }
    private boolean reserved(BlockPos pos) { return pos.getX() >= base.getX() - 1 && pos.getX() <= base.getX() + 5 && pos.getZ() >= base.getZ() - 1 && pos.getZ() <= base.getZ() + 5; }

    private BlockPos findSite(ServerLevel level) {
        BlockPos center = owner.blockPosition();
        for (int radius = 6; radius <= Math.min(24, owner.getSearchRadius()); radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int dy : new int[]{0, -1, 1, -2, 2, -3}) {
                    BlockPos corner = center.offset(dx - 2, dy, dz - 2);
                    if (siteClear(level, corner)) return corner;
                }
            }
        }
        return null;
    }
    private boolean siteClear(ServerLevel level, BlockPos corner) {
        for (int x = -1; x <= 5; x++) for (int z = -1; z <= 5; z++) {
            BlockPos floor = corner.offset(x, -1, z);
            if (!level.hasChunkAt(floor) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) || !level.getFluidState(floor).isEmpty()) return false;
            for (int y = 0; y <= 5; y++) if (!replaceable(level, corner.offset(x, y, z))) return false;
        }
        if (!level.getEntities(owner, new AABB(Vec3.atLowerCornerOf(corner.offset(-1, 0, -1)), Vec3.atLowerCornerOf(corner.offset(6, 6, 6))), entity -> entity.isAlive()).isEmpty()) return false;
        return owner.work.route.toCells(Set.of(corner.offset(2, 0, -1), corner.offset(-1, 0, 2), corner.offset(5, 0, 2))) != null;
    }

    private Result checkBlueprint(ServerLevel level) {
        for (int index = 0; index < steps.size(); index++) {
            Step step = steps.get(index);
            if (!level.hasChunkAt(step.pos)) return Result.failed("Стройка сейчас за пределами загруженной области. Вернёмся поближе и продолжим.");
            if (placed.get(index) && !matches(level, step)) placed.clear(index);
            if (!placed.get(index) && !free(level, step)) return Result.failed("На месте дома появились чужие блоки. Не буду их ломать; освободи площадку и попроси достроить дом.");
        }
        return null;
    }
    private boolean matches(ServerLevel level, Step step) {
        BlockState actual = level.getBlockState(step.pos);
        if (!step.door()) return actual.equals(step.state);
        BlockState top = level.getBlockState(step.pos.above());
        return actual.is(step.state.getBlock()) && actual.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && top.is(step.state.getBlock()) && top.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }
    private boolean free(ServerLevel level, Step step) { return replaceable(level, step.pos) && (!step.door() || replaceable(level, step.pos.above())); }
    private boolean replaceable(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && !level.isOutsideBuildHeight(pos)
                && level.getBlockEntity(pos) == null && level.getFluidState(pos).isEmpty() && level.getBlockState(pos).canBeReplaced();
    }
    private boolean supported(ServerLevel level, Step step) {
        for (Direction direction : Direction.values()) {
            BlockPos pos = step.pos.relative(direction);
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return true;
        }
        return false;
    }
    private boolean canPlaceFromHere(ServerLevel level, Step step) {
        if (!owner.onGround() || step.door() && owner.getZ() >= base.getZ()) return false;
        AABB box = new AABB(step.pos);
        if (step.door()) box = box.expandTowards(0, 1, 0);
        return level.noCollision(box) && visible(level, owner.getEyePosition(), step.pos);
    }
    private boolean visible(ServerLevel level, Vec3 eye, BlockPos pos) {
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
    private boolean routeBusy;
    private Path route(ServerLevel level, Step step) {
        Set<BlockPos> feet = new java.util.HashSet<>();
        for (int y = 0; y <= 1; y++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos pos = new BlockPos(step.pos.getX() + dx, base.getY() + y, step.pos.getZ() + dz);
            if (step.door() && pos.getZ() >= base.getZ()) continue;
            if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below()) || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
            AABB body = new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2, pos.getX() + .8, pos.getY() + owner.getBbHeight(), pos.getZ() + .8);
            if (body.intersects(new AABB(step.pos)) || step.door() && body.intersects(new AABB(step.pos.above()))) continue;
            if (!level.noCollision(owner, body) || !visible(level, Vec3.atBottomCenterOf(pos).add(0, owner.getEyeHeight(), 0), step.pos)) continue;
            feet.add(pos);
        }
        var reach = feet.isEmpty() ? null : owner.work.route.cells(feet, 0);
        routeBusy = reach != null && reach.busy();
        return reach == null ? null : reach.path();
    }

    void save(CompoundTag tag) {
        if (base == null) return;
        CompoundTag data = new CompoundTag();
        data.putLong("Base", base.asLong());
        data.putString("Wood", wood);
        data.putString("Dimension", owner.level().dimension().location().toString());
        data.putLongArray("Placed", placed.toLongArray());
        if (workbench != null) data.putLong("Workbench", workbench.asLong());
        tag.put("SmallHouse", data);
    }
    void load(CompoundTag tag) {
        base = null; workbench = null; placed.clear(); begin();
        CompoundTag data = tag.getCompound("SmallHouse");
        if (!data.contains("Base") || !WOODS.contains(data.getString("Wood")) || !owner.level().dimension().location().toString().equals(data.getString("Dimension"))) return;
        base = BlockPos.of(data.getLong("Base")); wood = data.getString("Wood"); steps = blueprint();
        if (data.contains("Workbench")) workbench = BlockPos.of(data.getLong("Workbench"));
        placed.or(BitSet.valueOf(data.getLongArray("Placed")));
        placed.clear(steps.size(), Math.max(steps.size(), placed.length()));
    }
}
