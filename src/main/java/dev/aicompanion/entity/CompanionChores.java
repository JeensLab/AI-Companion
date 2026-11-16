package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What a player gets with a tool in hand rather than by breaking or killing, as steps of «добудь»: shear_sheep (shears on a grown
 * sheep of the colour: it lives, one to three wool fall), milk_animal (an empty bucket of his under a cow, a mooshroom or a goat),
 * collect_eggs (the eggs that lie about the hens; none is waited for), strip_log (a log of his own set down, stripped with his
 * axe, broken and picked up: what stands in the world is never stripped, and his log is not left standing when the work ends) and
 * harvest_hive (a full hive or nest: three combs to his shears or honey into his bottle, only with a campfire burning under it, so
 * the bees stay calm; none burns there: his own is set under the hive first and left, a broken one gives but charcoal) and
 * scoop_mob (a fish under open water scooped alive into his bucket of water, as a player does; one let out of somebody's
 * bucket or named is not his, a pufferfish only while he is well enough for its sting) and scoop_block (lava's own source or
 * powder snow into an empty bucket of his, from the firm ground beside it: he never steps into either; in the Nether too).
 */
final class CompanionChores {
    private enum Mode { SHEAR, MILK, EGGS, STRIP, HIVE, SCOOP, FILL }
    /**
     * scoop_mob: what swims into the bucket of each kind, and who is not seen about (as «Рядом нет …» says it). Fish only (the
     * player 15.11): a tadpole is always a bucket's to the game (it hatches of frogs a player bred), and an axolotl under the sky
     * is somebody's pet or its young, the wild ones keep to the caves.
     */
    private record Catch(EntityType<?> kind, String whom) { }
    private static final Map<Item, Catch> CATCHES = Map.of(Items.COD_BUCKET, new Catch(EntityType.COD, "трески"),
            Items.SALMON_BUCKET, new Catch(EntityType.SALMON, "лосося"), Items.TROPICAL_FISH_BUCKET, new Catch(EntityType.TROPICAL_FISH, "тропической рыбы"),
            Items.PUFFERFISH_BUCKET, new Catch(EntityType.PUFFERFISH, "иглобрюха"));
    /** Blocks above and below him the animals are looked for: the meadow he stands on. */
    private static final int DEPTH = 12;
    /** From his eye to the animal: a hand's reach, over a fence too. */
    private static final double REACH = 3.0;
    /** Ticks he gets no nearer to an animal before it is passed over. */
    private static final int STALL = 300;
    /** From where he stands to the hive: a nest hangs on a trunk over his head. */
    private static final double HIVE_REACH = 4.0;
    /** Blocks under a hive the smoke of a campfire rises through (CampfireBlock.isSmokeyPos). */
    private static final int SMOKE = 5;
    /** Ticks the combs at his feet are given to be picked up, walks to one hive and ticks no search for the way comes about before it is passed over. */
    private static final int SETTLE = 40, LEGS = 8, WAIT = 100;
    /** Lava and powder snow passed over in one order before he says he cannot get to any: a lake of lava has many cells. */
    private static final int SKIPS = 64;
    private static final String LEFT = " Костёр под ульем оставил: с дымом пчёлы не злятся.";
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
    /** harvest_hive: the full hive he works, the combs cut so far and until when those at his feet are waited for, whether a campfire of his was set. */
    private BlockPos hive;
    private int due;
    private long settle;
    private boolean lit;
    /** He has stood where his hand reaches this hive: only then is a campfire of his spent under it. */
    private boolean reached;
    /** The cells a block is worked from, the block they are for, his walks to them. */
    private Set<BlockPos> stands = Set.of();
    private BlockPos standsOf;
    private int legs, waited, crowded;
    /** scoop_block: the lava or the powder snow he goes to fill his bucket from. */
    private BlockPos pool;
    /** Hives, lava and powder snow he found no way to, and what he says of them. */
    private final Set<BlockPos> skipped = new HashSet<>();
    private String missed;

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

    /** Who of what swims into the bucket is not seen about, null for an item that is no such bucket. */
    static String whom(Item bucket) {
        Catch caught = CATCHES.get(bucket);
        return caught == null ? null : caught.whom();
    }

    /** scoop_block: the block an empty bucket takes for the item, null for any other. */
    private static Block poured(Item bucket) {
        return bucket == Items.LAVA_BUCKET ? Blocks.LAVA : bucket == Items.POWDER_SNOW_BUCKET ? Blocks.POWDER_SNOW : null;
    }

    /** What is not seen about when none is (as «Рядом не вижу …» says it). */
    static String pouredName(Item bucket) { return bucket == Items.LAVA_BUCKET ? "лавы" : "рыхлого снега"; }

    /** Whether the item is to be had about him by one of these chores: a sheep of the colour to shear, a cow to milk, an egg that lies. */
    static boolean about(CompanionEntity c, ServerLevel level, Item wanted) {
        if (poured(wanted) != null) return pool(c, level, wanted, Set.of()) != null;
        Catch caught = CATCHES.get(wanted);
        if (caught != null) return !swimmers(c, level, caught.kind(), Set.of()).isEmpty();
        DyeColor colour = fleece(wanted);
        if (colour != null) return !herd(c, level, shorn(colour), Set.of()).isEmpty();
        if (wanted == Items.MILK_BUCKET) return !herd(c, level, CompanionChores::milked, Set.of()).isEmpty();
        if (wanted == Items.HONEYCOMB || wanted == Items.HONEY_BOTTLE) return fullHive(c, level, Set.of(), false) != null;
        // Asked of a campfire: whether one burns under a full hive already, so none of his is needed for it.
        if (wanted == Items.CAMPFIRE) return fullHive(c, level, Set.of(), true) != null;
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

    /** Of the kind under open water about him (or on the bank by it), down to the floor of the sea, where his leash lets him go. */
    private static List<Mob> swimmers(CompanionEntity c, ServerLevel level, EntityType<?> kind, Set<UUID> passed) {
        return level.getEntitiesOfClass(Mob.class, CompanionMobDrops.around(c), mob -> !passed.contains(mob.getUUID()) && scoopable(c, level, kind, mob));
    }

    /** Alive, nobody's (no name, not let out of a bucket, not on a lead), under open water (not a lake in a cave). */
    private static boolean scoopable(CompanionEntity c, ServerLevel level, EntityType<?> kind, Mob mob) {
        return mob.getType() == kind && mob.isAlive() && !mob.hasCustomName() && mob instanceof Bucketable kept && !kept.fromBucket() && !mob.isPassenger()
                && !mob.isLeashed() && c.leashAllows(mob.blockPosition()) && CompanionMobDrops.open(level, mob);
    }

    /**
     * The nearest lava source (a flow fills no bucket) or powder snow, nowhere the player builds; a cauldron is a block of its own.
     * Only one with air over it or beside it: a pocket shut in the rock is no player's to fill from either.
     */
    private static BlockPos pool(CompanionEntity c, ServerLevel level, Item bucket, Set<BlockPos> skipped) {
        Block block = poured(bucket);
        Predicate<BlockPos> site = CompanionPicking.sites(c, level);
        return CompanionObsidian.nearest(c, level, DEPTH, state -> state.is(block), pos -> !skipped.contains(pos) && c.leashAllows(pos)
                && (block != Blocks.LAVA || level.getFluidState(pos).isSource()) && !site.test(pos) && open(level, pos));
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) if (side != Direction.DOWN && level.getBlockState(pos.relative(side)).isAir()) return true;
        return false;
    }

    /**
     * Where it is filled from: firm ground within his reach and sight of it; never in powder snow nor with his head in it (he sinks
     * and freezes) or on magma (it burns, the shore of the Nether's lava has much of it). By lava still running he does not stand
     * either: fill() waits for it.
     */
    private static Set<BlockPos> banks(CompanionEntity c, ServerLevel level, BlockPos pool) {
        Set<BlockPos> cells = CompanionWaterFill.standsFor(c, level, List.of(pool));
        cells.removeIf(cell -> level.getBlockState(cell).is(Blocks.POWDER_SNOW) || level.getBlockState(cell.above()).is(Blocks.POWDER_SNOW)
                || level.getBlockState(cell.below()).is(Blocks.POWDER_SNOW) || level.getBlockState(cell.below()).is(Blocks.MAGMA_BLOCK));
        return cells;
    }

    private static boolean full(BlockState state) {
        return state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS;
    }

    /**
     * The nearest full hive or nest he may work: his hand reaches it from the ground (a nest high on a birch is not his, and no
     * campfire is spent under it), and a campfire burns under it or ({@code smoked} false) there is room for his.
     */
    private static BlockPos fullHive(CompanionEntity c, ServerLevel level, Set<BlockPos> skipped, boolean smoked) {
        Predicate<BlockPos> site = CompanionPicking.sites(c, level);
        return CompanionObsidian.nearest(c, level, DEPTH, CompanionChores::full, pos -> !skipped.contains(pos) && c.leashAllows(pos)
                && (CampfireBlock.isSmokeyPos(level, pos) || !smoked && hearth(level, pos, site) != null) && !standsBy(c, pos).isEmpty());
    }

    /** The cells the hive is worked from: within his reach and in sight, never in its own column, where the campfire burns. */
    private static Set<BlockPos> standsBy(CompanionEntity c, BlockPos hive) {
        Set<BlockPos> cells = new HashSet<>(CompanionMiningNavigation.standCells(c, hive, HIVE_REACH, false, true));
        cells.removeIf(cell -> cell.getX() == hive.getX() && cell.getZ() == hive.getZ());
        return cells;
    }

    /**
     * Where his campfire goes: the first free cell under the hive that has a floor, with nothing between to stop the smoke. Null when
     * there is none (a hive that stands on the ground, a flower under the nest: the player's ground is not dug up for it), where
     * the player builds (a fire in the passage of his house burns him) and on leaves (a fire in the crown of a tree).
     */
    private static BlockPos hearth(ServerLevel level, BlockPos hive, Predicate<BlockPos> site) {
        for (int down = 1; down <= SMOKE; down++) {
            BlockPos cell = hive.below(down);
            BlockState state = level.getBlockState(cell), floor = level.getBlockState(cell.below());
            if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) return null;
            if (floor.isFaceSturdy(level, cell.below(), Direction.UP)) return floor.is(BlockTags.LEAVES) || site.test(cell) ? null : cell;
        }
        return null;
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
        Mode chosen = switch (action) { case "shear_sheep" -> Mode.SHEAR; case "milk_animal" -> Mode.MILK; case "collect_eggs" -> Mode.EGGS; case "harvest_hive" -> Mode.HIVE; case "scoop_mob" -> Mode.SCOOP; case "scoop_block" -> Mode.FILL; default -> Mode.STRIP; };
        DyeColor fleece = asked == null ? null : fleece(asked);
        Block grown = asked == null ? null : unstripped(asked);
        boolean fits = switch (chosen) {
            case SHEAR -> fleece != null;
            case MILK -> asked == Items.MILK_BUCKET;
            case EGGS -> asked == Items.EGG;
            case STRIP -> grown != null;
            case HIVE -> asked == Items.HONEYCOMB || asked == Items.HONEY_BOTTLE;
            case SCOOP -> CATCHES.containsKey(asked);
            case FILL -> poured(asked) != null;
        };
        if (!fits) return CompanionActionResult.failed("Такого я не умею: " + action + " " + id + ".");
        mode = chosen; item = asked; wanted = count; before = w.items.countCarried(item);
        colour = fleece; raw = grown; bare = grown == null ? null : ((BlockItem) asked).getBlock();
        placed = null; stripped = false;
        due = 0; settle = 0; lit = false; missed = null;
        passed.clear();
        skipped.clear();
        release();
        return null;
    }

    String jobName() {
        String what = switch (mode) {
            case SHEAR -> "стрижка овец";
            case MILK -> "дойка";
            case EGGS -> "сбор яиц";
            case STRIP -> "обтёсывание брёвен";
            case HIVE -> item == Items.HONEYCOMB ? "сбор сот" : "сбор мёда";
            case SCOOP -> "ловля в ведро";
            case FILL -> item == Items.LAVA_BUCKET ? "набор лавы" : "набор рыхлого снега";
        };
        return what + " (" + got() + " из " + wanted + ")";
    }

    private String name() { return new ItemStack(item).getHoverName().getString(); }

    private int got() { return Math.max(0, w.items.countCarried(item) - before); }

    void tick(ServerLevel level) {
        int got = got();
        if (got >= wanted) { end(CompanionActionResult.ok("Добыл: «" + name() + "», " + got + " шт." + (lit ? LEFT : ""))); return; }
        // ponytail: room is asked for before the bucket or the log leaves its slot, so a bag full but for that one slot refuses.
        if (!w.items.canStore(List.of(new ItemStack(item)))) { end(CompanionActionResult.blocked(CompanionPicking.FULL_BAG + part(got))); return; }
        switch (mode) {
            case SHEAR, MILK -> tend(level, got);
            case EGGS -> eggs(level, got);
            case STRIP -> strip(level, got);
            case HIVE -> harvest(level, got);
            case SCOOP -> scoop(level, got);
            case FILL -> fill(level, got);
        }
    }

    private String part(int got) { return got == 0 ? "" : " Добыл «" + name() + "»: " + got + " из " + wanted + "."; }

    /** Some got: obtain_item plans the rest again and says what is missing. None: it ends here with what he says. */
    private void out(int got, String none) {
        end(got > 0 ? CompanionActionResult.failed("Добыл «" + name() + "»: " + got + " из " + wanted + ", больше рядом нет." + (lit ? LEFT : "")) : CompanionActionResult.blocked(none + (lit ? LEFT : "")));
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

    /**
     * The nearest of the kind, swum up to (and dived for: CompanionSwimming) until his hand reaches it, scooped into his bucket of
     * water: the game's own Bucketable.bucketMobPickup, which wants a player, so its steps are taken here (the sound, the bucket
     * that keeps the looks and the age, the mob gone into it). One he gets no nearer to is passed over.
     */
    private void scoop(ServerLevel level, int got) {
        if (w.items.countCarried(Items.WATER_BUCKET) == 0) { end(CompanionActionResult.blocked("Ведра воды нет: черпать нечем." + part(got))); return; }
        Catch caught = CATCHES.get(item);
        // A pufferfish stings whoever comes near: hurt, he does not go (as he does not go striking first, CompanionMobDrops).
        if (caught.kind() == EntityType.PUFFERFISH && c.getHealth() <= CompanionCreeperGuard.FIGHT_HEALTH) { end(CompanionActionResult.blocked("Я ранен: к иглобрюху сейчас не полезу, он жалит. Поем — тогда." + part(got))); return; }
        long now = level.getGameTime();
        Mob mob = target != null && level.getEntity(target) instanceof Mob found && scoopable(c, level, caught.kind(), found) ? found : null;
        if (mob == null) {
            release();
            mob = swimmers(c, level, caught.kind(), passed).stream().min(Comparator.comparingDouble(c::distanceToSqr)).orElse(null);
            if (mob == null) { out(got, passed.isEmpty() ? "Рядом нет " + caught.whom() + " под открытой водой." : "Вижу, кого черпать, но добраться не смог."); return; }
            target = mob.getUUID(); since = now; best = Double.MAX_VALUE;
        }
        double distance = mob.getBoundingBox().distanceToSqr(c.getEyePosition());
        // A player's bucket reaches what his eye sees, not a fish behind the glass of a pond or a block of the bank.
        if (distance > REACH * REACH || !c.hasLineOfSight(mob)) {
            if (distance + .05 < best) { best = distance; since = now; }
            if (now - since > (c.isInWater() ? CompanionMobDrops.SWIM_STALL : STALL)) { passed.add(target); c.getNavigation().stop(); release(); return; }
            c.workStatus("Ловлю в ведро.");
            // In the water with it he swims straight at it (as for a fish to fight, CompanionMobDrops.watch): a route would pull him
            // back to the surface over it. Not against the way up: with his air short the swim for air has him. Also out of sight
            // (round a pillar of the bed): he swims on to it, and behind glass the stall gives it up.
            if (mob.isInWater() && c.isInWater()) {
                c.getNavigation().stop();
                if (c.getAirSupply() > c.getMaxAirSupply() / 2) c.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 1.15);
            } else if (CompanionMobDrops.wade(w, level, mob) != CompanionTravel.Step.WALKING) c.travel().reset();
            return;
        }
        c.getNavigation().stop();
        c.getLookControl().setLookAt(mob, 30, 30);
        ItemStack water = w.items.takeCarried(Items.WATER_BUCKET, 1);
        c.animateAction(3, mob.blockPosition(), water);
        Bucketable kept = (Bucketable) mob;
        ItemStack full = kept.getBucketItemStack();
        kept.saveToBucketTag(full);
        mob.playSound(kept.getPickupSound(), 1F, 1F);
        mob.discard();
        c.storeLoot(full);
        c.setChanged();
        w.cooldown = 8;
        release();
    }

    /** scoop_block: one source at a time, walked to the bank of and filled into an empty bucket of his by the game's own bucket. */
    private void fill(ServerLevel level, int got) {
        // The game's bucket fills one of his bag (CompanionWaterBucket.scoopInto): one in his hand is said, not walked round the lava with.
        if (!CompanionWaterBucket.roomToScoop(c)) {
            boolean bagged = false;
            for (int slot = 0; slot < c.getContainerSize(); slot++) bagged |= c.getItem(slot).is(Items.BUCKET);
            String why = w.items.countCarried(Items.BUCKET) == 0 ? "Пустого ведра нет: набрать не во что." : bagged ? CompanionPicking.FULL_BAG : "Пустое ведро у меня в руке, а набираю я из сумки.";
            end(CompanionActionResult.blocked(why + part(got)));
            return;
        }
        boolean lava = item == Items.LAVA_BUCKET;
        if (pool != null && !level.getBlockState(pool).is(poured(item))) release();
        if (pool == null) {
            BlockPos found = skipped.size() >= SKIPS ? null : pool(c, level, item, skipped);
            if (found == null) { out(got, skipped.isEmpty() ? "Рядом нет " + pouredName(item) + "." : "Вижу " + (lava ? "лаву" : "рыхлый снег") + ", но подойти не смог."); return; }
            Set<BlockPos> cells = banks(c, level, found), still = new HashSet<>(cells);
            still.removeIf(cell -> !CompanionLava.safeToFetch(level, cell));
            // Each bucket of his stirs the sources beside the hole (a flow tick for a second or two): waited for, not given up; the
            // wait is counted for the whole order, not anew for each source.
            if (still.isEmpty() && !cells.isEmpty() && ++waited < WAIT) { c.workStatus("Жду, пока лава уляжется."); return; }
            // The middle of a lake of lava, snow over his head all round: this one is passed over, the next looked for at the next tick.
            if (still.isEmpty()) { skipped.add(found); return; }
            pool = found; stands = still; path = null; legs = waited = 0;
        }
        if (!stands.contains(c.blockPosition())) {
            c.workStatus(lava ? "Иду за лавой." : "Иду за рыхлым снегом.");
            CompanionRoute route = w.route;
            if (path != null && !route.legOver()) return;
            if (++legs <= LEGS) {
                CompanionRoute.Reach reach = route.cells(stands, 0);
                // He is in the air or the search goes on: asked again at the next tick, though not for ever.
                if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; if (++waited < WAIT) return; }
                else if (reach.found()) { waited = 0; path = reach.path(); route.walk(path); return; }
            }
            skipped.add(pool); release();
            return;
        }
        c.getNavigation().stop();
        c.getLookControl().setLookAt(pool.getX() + .5, pool.getY() + .5, pool.getZ() + .5);
        // The game's own bucket, sound and all (CompanionWaterBucket.scoopInto, as for water); false with no room for the full one.
        if (!CompanionWaterBucket.scoopInto(c, level, pool)) skipped.add(pool);
        c.setChanged();
        w.cooldown = 8;
        release();
    }

    /** scoop_mob: who he swims for, so the float lets him down to it (CompanionSwimming). */
    Mob dive() {
        return mode == Mode.SCOOP && target != null && c.level() instanceof ServerLevel level && level.getEntity(target) instanceof Mob mob ? mob : null;
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

    /** One full hive at a time, those with a campfire under them first: his own set under the next, then the combs cut or the honey bottled. */
    private void harvest(ServerLevel level, int got) {
        long now = level.getGameTime();
        // The combs he cut lie at his feet: picked up before the next hive is looked for or the count is said.
        if (got < due && now < settle) return;
        boolean comb = item == Items.HONEYCOMB;
        if (w.items.countCarried(comb ? Items.SHEARS : Items.GLASS_BOTTLE) == 0) { end(CompanionActionResult.blocked((comb ? "Ножниц нет: срезать соты нечем." : "Пустой бутылочки нет: набрать мёд не во что.") + part(got))); return; }
        if (hive != null && !full(level.getBlockState(hive))) release();
        if (hive == null) {
            hive = fullHive(c, level, skipped, true);
            if (hive == null) hive = fullHive(c, level, skipped, false);
            if (hive == null) { out(got, missed == null ? "Рядом нет полного улья, под которым горит костёр или есть место для него. Пчёлы наполняют улей долго." : missed); return; }
        }
        if (!CampfireBlock.isSmokeyPos(level, hive)) {
            BlockPos fire = hearth(level, hive, CompanionPicking.sites(c, level));
            if (fire == null) { skip("Под ульем не осталось места для костра, а без дыма пчёлы нападут."); return; }
            // Some cut already: obtain_item plans the rest again, a campfire first.
            if (w.items.countCarried(Items.CAMPFIRE) == 0) { out(got, "Под ульем нет дыма, а костра у меня нет: без дыма пчёлы нападут."); return; }
            // To the hive first: one he finds no way to (a nest over a trunk he cannot climb) gets no campfire of his.
            if (!reached) { if (!at(level, hive)) return; reached = true; }
            if (!at(level, fire)) return;
            // Someone stands under the hive (the player, a cow): no fire is set under their feet. They are waited for, not for ever.
            if (!level.isUnobstructed(Blocks.CAMPFIRE.defaultBlockState(), fire, CollisionContext.empty())) {
                if (++crowded > WAIT) skip("Под ульем кто-то стоит: костёр поставить некуда.");
                return;
            }
            ItemStack kit = w.items.takeCarried(Items.CAMPFIRE, 1);
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
            dev.aicompanion.CompanionDiagnostics.event(c.diagnostics.trace(), "block_placed", "entity", c.getUUID(), "block", "minecraft:campfire", "position", fire, "reason", "hive_smoke");
            c.animateAction(1, fire, kit);
            level.playSound(null, fire, Blocks.CAMPFIRE.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, .7F, 1F);
            c.rememberPlaced(fire);
            c.setChanged();
            lit = true;
            w.cooldown = 6;
            return;
        }
        if (!at(level, hive)) return;
        BlockState state = level.getBlockState(hive);
        c.getLookControl().setLookAt(hive.getX() + .5, hive.getY() + .5, hive.getZ() + .5);
        if (comb) {
            if (!c.getMainHandItem().is(Items.SHEARS) && !c.equipForWork("minecraft:shears").success()) { end(CompanionActionResult.blocked("Не могу взять ножницы в руку: в сумке нет места." + part(got))); return; }
            ItemStack shears = c.getMainHandItem();
            c.animateAction(3, hive, shears);
            level.playSound(null, hive, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1F, 1F);
            // The game's own three combs, at his feet as the wool is: under the hive they would lie on the campfire.
            c.dropHarvest(c.blockPosition(), List.of(new ItemStack(Items.HONEYCOMB, 3)));
            shears.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
            due += 3; settle = now + SETTLE;
        } else {
            ItemStack bottle = w.items.takeCarried(Items.GLASS_BOTTLE, 1);
            c.animateAction(3, hive, bottle);
            level.playSound(null, hive, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1F, 1F);
            c.storeLoot(new ItemStack(Items.HONEY_BOTTLE));
        }
        // With the smoke under it the bees stay in and stay calm, as they do for a player.
        ((BeehiveBlock) state.getBlock()).resetHoneyLevel(level, state, hive);
        c.setChanged();
        w.cooldown = 8;
        release();
    }

    /** One tick of the walk to where the block is worked from: true once he stands there. A hive he finds no way to is passed over. */
    private boolean at(ServerLevel level, BlockPos block) {
        if (!block.equals(standsOf)) {
            standsOf = block; path = null; legs = waited = 0;
            // A hive has a body and is worked in sight; the cell of the campfire is empty yet: from the ground beside it.
            stands = block.equals(hive) ? standsBy(c, hive) : new HashSet<>();
            if (!block.equals(hive)) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && CompanionMiningNavigation.standable(c, block.offset(dx, dy, dz))) stands.add(block.offset(dx, dy, dz));
        }
        if (stands.contains(c.blockPosition())) { c.getNavigation().stop(); return true; }
        c.workStatus("Иду к улью.");
        CompanionRoute route = w.route;
        if (path != null && !route.legOver()) return false;
        if (!stands.isEmpty() && ++legs <= LEGS) {
            CompanionRoute.Reach reach = route.cells(stands, 0);
            // He is in the air or the search goes on: asked again at the next tick, though not for ever.
            if (reach.busy() || CompanionRoute.NOT_SEARCHED.equals(reach.reason())) { legs--; if (++waited < WAIT) return false; }
            else if (reach.found()) { waited = 0; path = reach.path(); route.walk(path); return false; }
        }
        skip("Вижу полный улей, но подойти не смог.");
        return false;
    }

    private void skip(String why) { missed = why; skipped.add(hive); release(); }

    private void release() { target = null; path = null; nextPath = 0; hive = null; pool = null; standsOf = null; stands = Set.of(); legs = waited = crowded = 0; reached = false; }

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
