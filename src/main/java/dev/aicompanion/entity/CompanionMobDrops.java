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
 * Only what falls often enough to count on (a witch's odds and ends are not), his kill being his player's (CompanionKillCredit:
 * a spider's eye, a blaze's rod); on land; never a named mob (somebody's), a piglin (the whole group rises), a ghast or
 * the warden. Under the Nether's roof no surface is known, so there only one in his sight is about. Of what lives in the water only
 * a fish and a squid (cod, salmon, ink): fought in the water, as for a drowned (CompanionSwimming dives with a blade in hand); a
 * dolphin, an axolotl, a turtle and a guardian never.
 */
final class CompanionMobDrops {
    /** Kills tried with fixed seeds, so the list is the same every time; an item is counted on when a third of them give it. */
    private static final int ROLLS = 48;
    /** Blocks above and below his feet that are looked at: the hill he stands on, not the cave under it. */
    private static final int DEPTH = 12;
    /**
     * Blocks of water over a fish or a squid looked up through for the sky (open), and as many more under the hill it is looked for
     * at: a cod swims down to the floor of the sea, 20 to 30 blocks under it (de-flandres #178: deeper than 12 it was «not about»).
     */
    private static final int SEA = 32;
    /** Ticks with no step and no blow landed before the monster is passed over. */
    private static final int STALL = 300;
    /**
     * The same for a fish or a squid while he is in the water: one way up for air and back down is in it. A cod 18 down, gone for
     * with his breath short after the one before, was passed over on the way back (CompanionBreath, some 340 ticks).
     */
    static final int SWIM_STALL = 2 * STALL;
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
    /** The one he is after now, how near he got and how much life it had when he last gained on it. */
    private UUID chased;
    private double best;
    private float health;
    private long since;

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
            var category = type.getCategory();
            if (category != MobCategory.MONSTER && category != MobCategory.WATER_AMBIENT && category != MobCategory.WATER_CREATURE
                    && category != MobCategory.UNDERGROUND_WATER_CREATURE || !(type.create(level) instanceof Mob mob)) continue;
            if (!swims(mob) && (category != MobCategory.MONSTER || !(mob instanceof Enemy) || spared(mob))) continue;
            var table = level.getServer().reloadableRegistries().getLootTable(mob.getLootTable());
            // A player in the kill, as his own is counted for his player (CompanionKillCredit): a spider's eye, a phantom's membrane.
            // ponytail: with his player in another dimension these do not fall, and he says so after the kills; planned as if at hand.
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level)).create(LootContextParamSets.ENTITY);
            Map<Item, Integer> seen = new HashMap<>();
            // From one: a seed of nought means «none», and the roll would come from the world's own sequence.
            for (int seed = 1; seed <= ROLLS; seed++) {
                Set<Item> once = new HashSet<>();
                for (ItemStack drop : table.getRandomItems(params, seed)) if (!drop.isEmpty()) once.add(drop.getItem());
                for (Item drop : once) seen.merge(drop, 1, Integer::sum);
            }
            seen.forEach((drop, times) -> { if (times * 3 >= ROLLS) found.computeIfAbsent(drop, key -> new ArrayList<>()).add(type); });
        }
        return found;
    }

    /**
     * Kinds never hunted for a drop: the warden and the wither are no game, a piglin struck raises its group, a guardian lives under
     * water, a breeze turns his arrows back at him, and a ghast hangs over the lava its drops fall into.
     */
    private static boolean spared(Mob mob) {
        return mob instanceof net.minecraft.world.entity.monster.warden.Warden || mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin || mob instanceof net.minecraft.world.entity.monster.ZombifiedPiglin
                || mob instanceof net.minecraft.world.entity.monster.Guardian || mob instanceof net.minecraft.world.entity.monster.breeze.Breeze
                || mob instanceof net.minecraft.world.entity.monster.Ghast;
    }

    /** What is fished for its drop: a fish (a tadpole drops nothing) or a squid, glowing or not. */
    private static boolean swims(Mob mob) {
        return mob instanceof net.minecraft.world.entity.animal.AbstractFish || mob instanceof net.minecraft.world.entity.animal.Squid;
    }

    /** «Spider, Cave Spider»: who is looked for, said when none is about. */
    static List<String> names(ServerLevel level, Item item) {
        return sources(level, item).stream().map(type -> type.getDescription().getString()).distinct().toList();
    }

    /** Whether a monster that drops the item is within his search radius where he may fight it. */
    static boolean near(CompanionEntity c, ServerLevel level, Item item) {
        return !targets(c, level, sources(level, item), Set.of(), Set.of()).isEmpty();
    }

    /**
     * Those he may go for. One he has not struck yet walks the surface (behind a trunk or a rise it is still about: in game 12.11 the
     * spiders had gone round the trees while he made his sword) or is in his sight: not the cave under the hill.
     */
    private static List<Mob> targets(CompanionEntity c, ServerLevel level, List<EntityType<?>> kinds, Set<UUID> passed, Set<UUID> engaged) {
        if (kinds.isEmpty()) return List.of();
        return level.getEntitiesOfClass(Mob.class, around(c), mob -> !passed.contains(mob.getUUID()) && fought(c, kinds, mob)
                // Under the hill only a fish or a squid of the sea about him (or one struck, gone under a ledge), never the cave.
                && (mob.getBlockY() < c.getBlockY() - DEPTH ? swims(mob) && (engaged.contains(mob.getUUID()) || open(level, mob))
                : engaged.contains(mob.getUUID()) || c.hasLineOfSight(mob) || swims(mob) && open(level, mob)
                || mob.getBlockY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mob.getBlockX(), mob.getBlockZ()) - 1));
    }

    /** Where a mob is looked for: within his search radius, DEPTH above and below his feet and SEA deeper for what swims (open). */
    static AABB around(CompanionEntity c) {
        int radius = c.getSearchRadius();
        return new AABB(c.blockPosition()).inflate(radius, DEPTH, radius).expandTowards(0, -SEA, 0);
    }

    /**
     * A fish or a squid under open water: the sea is about him, though the bank hides what swims at the bottom by it from his
     * eyes on the shore (a squid at the foot of a pond's bank was «no one about» in a test); not a lake in a cave.
     */
    static boolean open(ServerLevel level, Mob mob) {
        net.minecraft.core.BlockPos cell = surface(level, mob);
        return level.getFluidState(cell).isEmpty() && level.canSeeSky(cell);
    }

    /** The first cell over the water a fish or a squid is in (its own one out of the water), looked up through SEA blocks at most. */
    private static net.minecraft.core.BlockPos surface(ServerLevel level, Mob mob) {
        net.minecraft.core.BlockPos.MutableBlockPos cell = mob.blockPosition().mutable();
        for (int up = 0; up < SEA && level.getFluidState(cell).is(net.minecraft.tags.FluidTags.WATER); up++) cell.move(0, 1, 0);
        return cell.immutable();
    }

    /**
     * Where he is walked to for a fish or a squid in the water while he is out of it: into the top of the water over it, not near it
     * (no route goes down under the surface: 18 blocks down «no leg found», he stood on the bank). In the water he dives for it.
     * One whose own cell holds no water (on the bank, at its edge) is walked up to, as anything else.
     */
    static CompanionTravel.Step wade(CompanionWork w, ServerLevel level, Mob mob) {
        net.minecraft.core.BlockPos top = surface(level, mob);
        return top.equals(mob.blockPosition()) ? w.route.follow(top, 2) : w.route.follow(top.below(), 0);
    }

    /**
     * He swims at a fish or a squid in the water with him; straight over it he only goes down (the dive, CompanionSwimming). Steered
     * at a point a hair across, the game turned his body up to a quarter round a tick, and he spun all the way down to a cod
     * under him (the player 17.11, «крутится как больной»).
     */
    static void swimAt(CompanionEntity c, Mob mob) {
        double dx = mob.getX() - c.getX(), dz = mob.getZ() - c.getZ();
        // His own place: no step and no turn (the game's MoveControl does not turn a body that is already where it is wanted).
        if (dx * dx + dz * dz > .5 * .5) c.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 1.15);
        else c.getMoveControl().setWantedPosition(c.getX(), c.getY(), c.getZ(), 1);
    }

    /** Of the kind, alive, nobody's (no name, not let out of a bucket), on land (a fish anywhere), on its own feet, and one he may fight by this order. */
    private static boolean fought(CompanionEntity c, List<EntityType<?>> kinds, Mob mob) {
        return kinds.contains(mob.getType()) && mob.isAlive() && !mob.hasCustomName() && !(mob instanceof net.minecraft.world.entity.animal.Bucketable kept && kept.fromBucket())
                && (swims(mob) || !mob.isInWater()) && !mob.isPassenger() && !mob.isVehicle()
                && c.leashAllows(mob.blockPosition()) && c.fairTarget(mob, true);
    }

    /** hunt_drop: {@code count} more of the item in his bag. Null when the work may start, else why not. */
    CompanionActionResult begin(String id, int count) {
        c = w.owner;
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        Item asked = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        kinds = asked == null || !(c.level() instanceof ServerLevel level) ? List.of() : sources(level, asked);
        if (kinds.isEmpty()) return CompanionActionResult.failed("Такое я с монстров не добываю: " + id + ".");
        if (c.getHealth() <= CompanionCreeperGuard.FIGHT_HEALTH) return CompanionActionResult.blocked("Я ранен: в бой за «" + new ItemStack(asked).getHoverName().getString() + "» сейчас не пойду. Поем — тогда.");
        item = asked; wanted = count; before = w.items.countCarried(item);
        passed.clear(); chased = null;
        return null;
    }

    private String name() { return new ItemStack(item).getHoverName().getString(); }

    private int got() { return Math.max(0, w.items.countCarried(item) - before); }

    void tick(ServerLevel level) {
        int got = got();
        if (got >= wanted) { end(CompanionActionResult.ok("Добыл: «" + name() + "», " + got + " шт.")); return; }
        // Hurt, he does not go on striking first: what comes for him he still meets, as always.
        if (c.getHealth() <= CompanionCreeperGuard.FIGHT_HEALTH) { end(CompanionActionResult.blocked("Меня сильно побили, дальше в бой не лезу. Добыл «" + name() + "»: " + got + " из " + wanted + ".")); return; }
        // What has fallen is picked up before the next one is struck: one pearl ordered is one enderman.
        if (c.hasPendingLoot()) { c.setTarget(null); return; }
        if (!w.items.canStore(List.of(new ItemStack(item)))) { end(CompanionActionResult.blocked(CompanionPicking.FULL_BAG + (got == 0 ? "" : " Добыл " + got + " из " + wanted + "."))); return; }
        List<Mob> alive = targets(c, level, kinds, passed, w.huntTargets);
        if (alive.isEmpty()) {
            String rest = passed.isEmpty() ? "больше рядом не с кого" : "до остальных не добрался";
            // Some got: obtain_item plans the rest again and says what is missing. None: it ends here.
            if (got > 0) end(CompanionActionResult.failed("Добыл «" + name() + "»: " + got + " из " + wanted + ", " + rest + "."));
            else if (w.huntKills > 0) end(CompanionActionResult.blocked("Убил " + w.huntKills + ", но «" + name() + "» не выпало, а " + rest + "."));
            else end(CompanionActionResult.blocked(passed.isEmpty() ? "Рядом не нашёл, с кого добыть «" + name() + "»." : "Вижу, с кого добыть «" + name() + "», но добраться не смог."));
            return;
        }
        Mob target = alive.stream().min(Comparator.comparingDouble(c::distanceToSqr)).orElseThrow();
        // The order's own targets: the blows on them are counted, and one struck is his until it is dead or passed over.
        w.huntTargets.add(target.getUUID());
        c.selectWeaponForCombat(); c.setTarget(target); c.movementIntent(target.blockPosition());
        c.workStatus("Добываю «" + name() + "»: " + got + " из " + wanted + ".");
        // In the water with it he swims at it (watch); a route there would pull him back to the surface over it. The work's own
        // route does not wade him onto one he strikes from where he stands (a pufferfish by the bank stings who comes to it).
        if (swims(target) && (target.isInWater() && c.isInWater() || c.isWithinMeleeAttackRange(target) && c.hasLineOfSight(target))) c.getNavigation().stop();
        else if ((swims(target) ? wade(w, level, target) : w.route.follow(target.blockPosition(), 2)) != CompanionTravel.Step.WALKING) c.travel().reset();
    }

    /**
     * Every tick, also while the melee goal has taken the legs (as CompanionHunting.watchChase for game): a monster he gets no
     * nearer to and lands no blow on for {@link #STALL} ticks (up a tree, behind a fence), or one gone from where he may fight it
     * (an enderman that teleported away), is passed over.
     */
    void watch() {
        if (w.job != CompanionWork.Job.HUNT || w.phase != CompanionWork.Phase.DROPS || !(c.level() instanceof ServerLevel level)) return;
        // One he has killed is not one he failed to get to.
        if (!(c.getTarget() instanceof Mob mob) || !mob.isAlive() || !w.huntTargets.contains(mob.getUUID())) { chased = null; return; }
        // Hurt in the middle of it: he lets go, and the work says so when its turn comes.
        if (c.getHealth() <= CompanionCreeperGuard.FIGHT_HEALTH) { c.setTarget(null); return; }
        long now = level.getGameTime();
        double distance = c.distanceToSqr(mob);
        if (!mob.getUUID().equals(chased) || distance + .5 < best || mob.getHealth() < health) { chased = mob.getUUID(); best = distance; health = mob.getHealth(); since = now; }
        // A squid or a fish under the surface: no route comes to it (the search starts at the top of the water) and the melee goal
        // starts only within a blow, so he swims straight at it, as a player does; down to it is CompanionSwimming's dive
        // (in game 13.11 two squids in the sea were passed over, not one blow landed).
        // Not against the way up: with his air short the swim for air (CompanionBreath) and the float have him, not the fish.
        // In the water with it the legs are this watch's, not the melee goal's: its route to the fish ends at the surface over it,
        // pulled him back up and turned him round on the spot (the player 17.11). This runs after the route's step and before the
        // legs move (Mob.serverAiStep: navigation, customServerAiStep, moveControl), so its step is undone here.
        if (swims(mob) && mob.isInWater() && c.isInWater() && !c.breath.active()) {
            c.getNavigation().stop();
            // Out of sight too (round a pillar of the bed, behind a coral): he swims on to it, as for the scoop; held still he only waited
            // for the stall. Behind glass the stall gives it up.
            if (!c.isWithinMeleeAttackRange(mob) && c.getAirSupply() > c.getMaxAirSupply() / 2) swimAt(c, mob);
            else c.getMoveControl().setWantedPosition(c.getX(), c.getY(), c.getZ(), 1);
        }
        // Out of his search radius across, as it is looked for (around): a cod down by the floor of the sea is farther from his eyes.
        int radius = c.getSearchRadius();
        double dx = mob.getX() - c.getX(), dz = mob.getZ() - c.getZ();
        boolean gone = !fought(c, kinds, mob) || dx * dx + dz * dz > radius * radius;
        if (now - since <= (swims(mob) && c.isInWater() ? SWIM_STALL : STALL) && !gone) return;
        passed.add(mob.getUUID()); chased = null;
        c.setTarget(null);
        c.getNavigation().stop();
    }

    private void end(CompanionActionResult result) {
        c.setTarget(null);
        w.finish(result);
    }
}
