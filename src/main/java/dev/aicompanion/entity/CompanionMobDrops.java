package dev.aicompanion.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * hunt_drop: an item a monster drops (string of a spider, a bone of a skeleton, gunpowder of a creeper, an ender pearl, a slime
 * ball), from the game's own loot tables (the player 12.11: he should get everything a player gets). The monsters of that kind
 * within his search radius are fought one after another, their drops picked up, until the bag holds as many as ordered. By this
 * order he strikes first at a spider or an enderman he otherwise leaves alone. With none about he says so before anything starts:
 * he does not wait for the night nor go down a cave for them.
 *
 * Only what falls without a player's hand in the kill and often enough to count on (a witch's odds and ends are not); only the
 * overworld, on land; never a named mob (somebody's), a piglin (the whole group rises) or the warden.
 */
final class CompanionMobDrops {
    /** Kills tried with fixed seeds, so the list is the same every time; an item is counted on when a third of them give it. */
    private static final int ROLLS = 48;
    /** Blocks above and below his feet that are looked at: the hill he stands on, not the cave under it. */
    private static final int DEPTH = 12;
    /** Ticks with no step and no blow landed before the monster is passed over. */
    private static final int STALL = 300;
    // ponytail: built once for a server; a data pack reloaded in a running world keeps the old tables until the world is reopened.
    private static WeakReference<MinecraftServer> builtFor = new WeakReference<>(null);
    private static Map<Item, List<EntityType<?>>> sources = Map.of();

    private final CompanionWork w;
    /** The work is made before it knows its owner: taken at the order. */
    private CompanionEntity c;
    private Item item;
    private List<EntityType<?>> kinds = List.of();
    private int wanted, before;
    /** Monsters he could not get to. */
    private final Set<UUID> passed = new HashSet<>();

    CompanionMobDrops(CompanionWork w) { this.w = w; }

    /** The monsters that drop the item; empty for an item none drops so. */
    static synchronized List<EntityType<?>> sources(ServerLevel level, Item item) {
        if (builtFor.get() != level.getServer()) {
            sources = build(level);
            builtFor = new WeakReference<>(level.getServer());
        }
        return sources.getOrDefault(item, List.of());
    }

    private static Map<Item, List<EntityType<?>>> build(ServerLevel level) {
        Map<Item, List<EntityType<?>>> found = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != MobCategory.MONSTER || !(type.create(level) instanceof Mob mob)) continue;
            if (!(mob instanceof Enemy) || spared(mob)) continue;
            var table = level.getServer().reloadableRegistries().getLootTable(mob.getLootTable());
            // No player in the kill: what only a player's blow brings (a spider's eye, a blaze rod) does not fall for him.
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()).create(LootContextParamSets.ENTITY);
            Map<Item, Integer> seen = new HashMap<>();
            for (int seed = 0; seed < ROLLS; seed++) {
                Set<Item> once = new HashSet<>();
                for (ItemStack drop : table.getRandomItems(params, seed)) if (!drop.isEmpty()) once.add(drop.getItem());
                for (Item drop : once) seen.merge(drop, 1, Integer::sum);
            }
            seen.forEach((drop, times) -> { if (times * 3 >= ROLLS) found.computeIfAbsent(drop, key -> new ArrayList<>()).add(type); });
        }
        return found;
    }

    /** Kinds never hunted for a drop: the warden and the wither are no game, a piglin struck raises its group, a guardian lives under water. */
    private static boolean spared(Mob mob) {
        return mob instanceof net.minecraft.world.entity.monster.warden.Warden || mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin || mob instanceof net.minecraft.world.entity.monster.ZombifiedPiglin
                || mob instanceof net.minecraft.world.entity.monster.Guardian;
    }

    /** «Spider, Cave Spider»: who is looked for, said when none is about. */
    static List<String> names(ServerLevel level, Item item) {
        return sources(level, item).stream().map(type -> type.getDescription().getString()).distinct().toList();
    }

    /** Whether a monster that drops the item is within his search radius where he may fight it. */
    static boolean near(CompanionEntity c, ServerLevel level, Item item) {
        return !targets(c, level, sources(level, item), Set.of()).isEmpty();
    }

    private static List<Mob> targets(CompanionEntity c, ServerLevel level, List<EntityType<?>> kinds, Set<UUID> passed) {
        if (kinds.isEmpty() || level.dimension() != Level.OVERWORLD) return List.of();
        int radius = c.getSearchRadius();
        return level.getEntitiesOfClass(Mob.class, new AABB(c.blockPosition()).inflate(radius, DEPTH, radius), mob -> kinds.contains(mob.getType()) && mob.isAlive()
                && !passed.contains(mob.getUUID()) && !mob.hasCustomName() && !mob.isInWater() && !mob.isPassenger() && !mob.isVehicle()
                && c.leashAllows(mob.blockPosition()) && c.fairTarget(mob, true));
    }

    /** hunt_drop: {@code count} more of the item in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, int count) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        kinds = asked == null || !(c.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD ? List.of() : sources(level, asked);
        if (kinds.isEmpty()) return CompanionActionResult.failed("Такое я с монстров не добываю: " + id + ".");
        item = asked; wanted = count; before = w.items.countCarried(item);
        passed.clear();
        return null;
    }

    private String name() { return new ItemStack(item).getHoverName().getString(); }

    private int got() { return Math.max(0, w.items.countCarried(item) - before); }

    void tick(ServerLevel level) {
        int got = got();
        if (got >= wanted) { end(CompanionActionResult.ok("Добыл: «" + name() + "», " + got + " шт.")); return; }
        List<Mob> alive = targets(c, level, kinds, passed);
        if (alive.isEmpty()) {
            c.setTarget(null);
            // What fell from the last of them is picked up before he says how far he got.
            if (c.hasPendingLoot()) return;
            String rest = passed.isEmpty() ? "больше рядом не с кого" : "до остальных не добрался";
            // Some got: obtain_item plans the rest again and says what is missing. None: it ends here.
            if (got > 0) end(CompanionActionResult.failed("Добыл «" + name() + "»: " + got + " из " + wanted + ", " + rest + "."));
            else end(CompanionActionResult.blocked(passed.isEmpty() ? "Рядом не нашёл, с кого добыть «" + name() + "»." : "Вижу, с кого добыть «" + name() + "», но добраться не смог."));
            return;
        }
        Mob target = alive.stream().min(Comparator.comparingDouble(c::distanceToSqr)).orElseThrow();
        if (w.orderHasStalled(level, STALL)) {
            passed.add(target.getUUID());
            w.orderProgressTime = level.getGameTime();
            c.setTarget(null);
            return;
        }
        // The order's own targets: the blows on them count as progress, and nothing else is picked for a fight meanwhile.
        w.huntTargets.add(target.getUUID());
        c.selectWeaponForCombat(); c.setTarget(target); c.movementIntent(target.blockPosition());
        c.workStatus("Добываю «" + name() + "»: " + got + " из " + wanted + ".");
        if (w.route.follow(target.blockPosition(), 2) != CompanionTravel.Step.WALKING) c.travel().reset();
    }

    private void end(CompanionActionResult result) {
        c.setTarget(null);
        w.finish(result);
    }
}
