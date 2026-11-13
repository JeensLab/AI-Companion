package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What a player gets with a tool in hand rather than by breaking or killing, as steps of «добудь»: shear_sheep (shears on a grown
 * sheep of the colour: it lives, one to three wool fall), milk_animal (an empty bucket of his under a cow, a mooshroom or a goat),
 * collect_eggs (the eggs that lie about the hens; none is waited for) and strip_log (a log of his own set down, stripped with his
 * axe, broken and picked up: what stands in the world is never stripped, and his log is not left standing when the work ends).
 */
final class CompanionChores {
    private enum Mode { SHEAR, MILK, EGGS, STRIP }
    /** Blocks above and below him the animals are looked for: the meadow he stands on. */
    private static final int DEPTH = 12;
    /** From his eye to the animal: a hand's reach, over a fence too. */
    private static final double REACH = 3.0;
    /** Ticks he gets no nearer to an animal before it is passed over. */
    private static final int STALL = 300;
    private static final String EGG = "minecraft:egg";
    private static final String STRIPPED = "stripped_";

    private final CompanionWork w;
    /** The work is made before it knows its owner: taken at the order. */
    private CompanionEntity c;
    private Mode mode;
    private Item item;
    private int wanted, before;
    private DyeColor colour;
    /** strip_log: the block as it grows and as it is asked for, and his log that stands to be stripped. */
    private Block raw, bare;
    private BlockPos placed;
    private boolean stripped;
    /** The animal he walks to, since when he got no nearer, and his way. */
    private UUID target;
    private long since, nextPath;
    private double best;
    private Path path;
    private Vec3 pathAt = Vec3.ZERO;
    /** Animals he found no way to, eggs he has gone for once. */
    private final Set<UUID> passed = new HashSet<>();

    CompanionChores(CompanionWork w) { this.w = w; }

    /** The colour of a wool block, null for anything else. */
    static DyeColor fleece(Item item) {
        if (!new ItemStack(item).is(ItemTags.WOOL)) return null;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        for (DyeColor colour : DyeColor.values()) if (path.equals(colour.getName() + "_wool")) return colour;
        return null;
    }

    /** The block an axe makes the stripped one of (the game names them so: stripped_oak_log of oak_log), null for anything else. */
    static Block unstripped(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!(item instanceof BlockItem) || !id.getPath().startsWith(STRIPPED)) return null;
        return BuiltInRegistries.BLOCK.getOptional(id.withPath(id.getPath().substring(STRIPPED.length()))).orElse(null);
    }

    /** Whether the item is to be had about him by one of these chores: a sheep of the colour to shear, a cow to milk, an egg that lies. */
    static boolean about(CompanionEntity c, ServerLevel level, Item wanted) {
        DyeColor colour = fleece(wanted);
        if (colour != null) return !herd(c, level, shorn(colour), Set.of()).isEmpty();
        if (wanted == Items.MILK_BUCKET) return !herd(c, level, CompanionChores::milked, Set.of()).isEmpty();
        return wanted == Items.EGG && !lying(c, level, Set.of()).isEmpty();
    }

    private static Predicate<Animal> shorn(DyeColor colour) {
        return animal -> animal instanceof Sheep sheep && sheep.readyForShearing() && sheep.getColor() == colour;
    }

    private static boolean milked(Animal animal) { return animal instanceof Cow || animal instanceof Goat; }

    /** Grown animals of the kind within his search radius, where his leash lets him go. */
    private static List<Animal> herd(CompanionEntity c, ServerLevel level, Predicate<Animal> fits, Set<UUID> passed) {
        int radius = c.getSearchRadius();
        return level.getEntitiesOfClass(Animal.class, new AABB(c.blockPosition()).inflate(radius, DEPTH, radius),
                animal -> animal.isAlive() && !animal.isBaby() && !passed.contains(animal.getUUID()) && c.leashAllows(animal.blockPosition()) && fits.test(animal));
    }

    private static List<ItemEntity> lying(CompanionEntity c, ServerLevel level, Set<UUID> passed) {
        List<ItemEntity> eggs = CompanionPickUp.find(level, c.blockPosition(), c.getSearchRadius(), CompanionPickUp.of(EGG), CompanionPickUp.ALL_AT_ONCE, c.getOwnerId());
        eggs.removeIf(egg -> passed.contains(egg.getUUID()));
        return eggs;
    }

    /** {@code count} more of the item in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(String action, String id, int count) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        Mode chosen = switch (action) { case "shear_sheep" -> Mode.SHEAR; case "milk_animal" -> Mode.MILK; case "collect_eggs" -> Mode.EGGS; default -> Mode.STRIP; };
        DyeColor fleece = asked == null ? null : fleece(asked);
        Block grown = asked == null ? null : unstripped(asked);
        boolean fits = switch (chosen) {
            case SHEAR -> fleece != null;
            case MILK -> asked == Items.MILK_BUCKET;
            case EGGS -> asked == Items.EGG;
            case STRIP -> grown != null;
        };
        if (!fits) return CompanionActionResult.failed("Такого я не умею: " + action + " " + id + ".");
        mode = chosen; item = asked; wanted = count; before = w.items.countCarried(item);
        colour = fleece; raw = grown; bare = grown == null ? null : ((BlockItem) asked).getBlock();
        placed = null; stripped = false;
        passed.clear();
        release();
        return null;
    }

    String jobName() {
        String what = switch (mode) {
            case SHEAR -> "стрижка овец";
            case MILK -> "дойка";
            case EGGS -> "сбор яиц";
            case STRIP -> "обтёсывание брёвен";
        };
        return what + " (" + got() + " из " + wanted + ")";
    }

    private String name() { return new ItemStack(item).getHoverName().getString(); }

    private int got() { return Math.max(0, w.items.countCarried(item) - before); }

    void tick(ServerLevel level) {
        int got = got();
        if (got >= wanted) { end(CompanionActionResult.ok("Добыл: «" + name() + "», " + got + " шт.")); return; }
        // ponytail: room is asked for before the bucket or the log leaves its slot, so a bag full but for that one slot refuses.
        if (!w.items.canStore(List.of(new ItemStack(item)))) { end(CompanionActionResult.blocked(CompanionPicking.FULL_BAG + part(got))); return; }
        switch (mode) {
            case SHEAR, MILK -> tend(level, got);
            case EGGS -> eggs(level, got);
            case STRIP -> strip(level, got);
        }
    }

    private String part(int got) { return got == 0 ? "" : " Добыл «" + name() + "»: " + got + " из " + wanted + "."; }

    /** Some got: obtain_item plans the rest again and says what is missing. None: it ends here with what he says. */
    private void out(int got, String none) {
        end(got > 0 ? CompanionActionResult.failed("Добыл «" + name() + "»: " + got + " из " + wanted + ", больше рядом нет.") : CompanionActionResult.blocked(none));
    }

    /** The nearest sheep to shear or cow to milk: walked up to within a hand's reach (over a fence too) and worked with the tool. */
    private void tend(ServerLevel level, int got) {
        boolean shear = mode == Mode.SHEAR;
        if (w.items.countCarried(shear ? Items.SHEARS : Items.BUCKET) == 0) { end(CompanionActionResult.blocked((shear ? "Ножниц нет: стричь нечем." : "Пустого ведра нет: доить не во что.") + part(got))); return; }
        Predicate<Animal> fits = shear ? shorn(colour) : CompanionChores::milked;
        long now = level.getGameTime();
        Animal animal = target != null && level.getEntity(target) instanceof Animal found && found.isAlive() && fits.test(found) ? found : null;
        if (animal == null) {
            release();
            animal = herd(c, level, fits, passed).stream().min(Comparator.comparingDouble(c::distanceToSqr)).orElse(null);
            if (animal == null) {
                String whom = shear ? "овцы, которую можно постричь" : "коровы, которую можно подоить";
                out(got, passed.isEmpty() ? "Рядом нет " + whom + "." : "Вижу, кого " + (shear ? "стричь" : "доить") + ", но подойти не смог.");
                return;
            }
            target = animal.getUUID(); since = now; best = Double.MAX_VALUE;
        }
        double distance = animal.getBoundingBox().distanceToSqr(c.getEyePosition());
        if (distance > REACH * REACH) {
            if (distance + .05 < best) { best = distance; since = now; }
            // In the middle of a pen no hand reaches it, or no way to it is found: the next one, this one is not waited for.
            if (now - since > STALL || !walk(animal, now)) { passed.add(target); release(); return; }
            c.workStatus(shear ? "Иду стричь овцу." : "Иду доить.");
            return;
        }
        c.getNavigation().stop();
        c.getLookControl().setLookAt(animal, 30, 30);
        if (shear) {
            if (!c.getMainHandItem().is(Items.SHEARS) && !c.equipForWork("minecraft:shears").success()) { end(CompanionActionResult.blocked("Не могу взять ножницы в руку: в сумке нет места." + part(got))); return; }
            ItemStack shears = c.getMainHandItem();
            c.animateAction(3, animal.blockPosition(), shears);
            // The game's own shearing: the sound, the bare sheep and its one to three wool. They fall at his feet, not at the sheep's:
            // shorn over a fence, the wool inside the pen would lie where he does not get.
            List<ItemStack> wool = ((Sheep) animal).onSheared(null, shears, level, animal.blockPosition());
            shears.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
            c.dropHarvest(c.blockPosition(), wool);
        } else {
            ItemStack bucket = w.items.takeCarried(Items.BUCKET, 1);
            c.animateAction(3, animal.blockPosition(), bucket);
            level.playSound(null, animal, animal instanceof Goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK, SoundSource.PLAYERS, 1F, 1F);
            c.storeLoot(new ItemStack(Items.MILK_BUCKET));
        }
        c.setChanged();
        w.cooldown = 8;
        release();
    }

    /** One tick of the walk to where a hand reaches the animal (it drifts: the way is found again once it has gone two blocks). False when there is no way. */
    private boolean walk(Animal animal, long now) {
        CompanionRoute route = w.route;
        if (path != null && !route.legOver() && pathAt.distanceToSqr(animal.position()) <= 4 || now < nextPath) return true;
        Set<BlockPos> stands = new HashSet<>();
        BlockPos at = animal.blockPosition();
        for (int dy = -2; dy <= 1; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos cell = at.offset(dx, dy, dz);
            if (!CompanionMiningNavigation.standable(c, cell)) continue;
            Vec3 eye = Vec3.atBottomCenterOf(cell).add(0, c.getEyeHeight(), 0);
            if (animal.getBoundingBox().distanceToSqr(eye) <= (REACH - .2) * (REACH - .2)) stands.add(cell);
        }
        nextPath = now + 5;
        // No cell to stand on yet (it is in the water, in the air), or the search has not come about: asked again, the stall bounds it.
        if (stands.isEmpty()) return true;
        CompanionRoute.Reach reach = route.cells(stands, 0);
        if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) return true;
        if (!reach.found()) return false;
        path = reach.path(); pathAt = animal.position();
        route.walk(path);
        return true;
    }

    /** The eggs that lie about are his to walk to and pick up, each gone for once; a hen lays in five to ten minutes, he does not wait. */
    private void eggs(ServerLevel level, int got) {
        List<ItemEntity> eggs = lying(c, level, passed);
        if (eggs.isEmpty()) { out(got, lying(c, level, Set.of()).isEmpty() ? "Яиц рядом не лежит: куры несутся раз в 5–10 минут." : "Яйца вижу, но подобрать не смог."); return; }
        if (eggs.size() > wanted - got) eggs = eggs.subList(0, wanted - got);
        for (ItemEntity egg : eggs) passed.add(egg.getUUID());
        c.workStatus("Подбираю яйца.");
        c.claimDrops(eggs);
    }

    /** One log of his at a time: set down beside him, stripped with the axe, broken, its drop picked up. */
    private void strip(ServerLevel level, int got) {
        if (placed != null && !level.getBlockState(placed).is(stripped ? bare : raw)) { placed = null; stripped = false; w.breaking.reset(); }
        if (placed == null && w.items.countCarried(raw.asItem()) == 0) { out(got, "Нет брёвен, чтобы обтесать: «" + name() + "»."); return; }
        if (!CompanionTools.equip(c, ItemTags.AXES, raw.defaultBlockState())) { end(CompanionActionResult.blocked("Топора нет: обтёсывать нечем." + part(got))); return; }
        ItemStack axe = c.getMainHandItem();
        if (placed == null) {
            // Set down by this work itself, as his own composter is: a log a time through place_block would fill his journal of deeds.
            BlockPos at = c.placement.findPlacementPosition(level, raw, c.blockPosition(), pos -> true);
            ItemStack log = c.companionInventory.stream().filter(stack -> stack.is(raw.asItem())).findFirst().orElse(ItemStack.EMPTY);
            if (at == null || log.isEmpty()) { end(CompanionActionResult.blocked("Негде поставить бревно, чтобы обтесать." + part(got))); return; }
            level.setBlock(at, raw.defaultBlockState(), 3);
            c.animateAction(1, at, log);
            log.shrink(1);
            c.refreshInventorySummary();
            level.playSound(null, at, raw.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
            placed = at.immutable();
            w.cooldown = 6;
            return;
        }
        BlockState state = level.getBlockState(placed);
        if (!stripped) {
            BlockState peeled = bare.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.AXIS) && peeled.hasProperty(BlockStateProperties.AXIS)) peeled = peeled.setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
            c.animateAction(1, placed, axe);
            level.setBlock(placed, peeled, 3);
            level.playSound(null, placed, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1F, 1F);
            axe.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
            stripped = true;
            w.cooldown = 6;
            return;
        }
        c.workStatus("Обтёсываю: " + name() + ".");
        if (!w.breaking.tick(level, placed)) return;
        var drops = CompanionTools.drops(state, level, placed, null, c, axe);
        if (level.destroyBlock(placed, false, c, 512)) {
            c.dropHarvest(placed, drops);
            axe.getItem().mineBlock(axe, level, state, placed, c);
        }
        placed = null; stripped = false;
        w.breaking.reset();
    }

    private void release() { target = null; path = null; nextPath = 0; }

    /** The order is called off: his log is not left standing. */
    void cancel() { takeBack(); }

    /** His log still stands (the axe broke, the bag is full, the order was called off): broken by hand at once, its drop his to pick up. */
    private void takeBack() {
        if (placed != null && c.level() instanceof ServerLevel level) {
            BlockState state = level.getBlockState(placed);
            if (state.is(raw) || state.is(bare)) {
                var drops = Block.getDrops(state, level, placed, null, c, ItemStack.EMPTY);
                if (level.destroyBlock(placed, false, c, 512)) c.dropHarvest(placed, drops);
            }
        }
        placed = null; stripped = false;
    }

    private void end(CompanionActionResult result) {
        release();
        takeBack();
        w.finish(result);
    }
}
