package dev.aicompanion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Notices a missing, broken or outclassed work tool and makes a better one from the bag, like a player would:
 * a lost tool is always replaced (placing a workbench from own wood if none is known nearby);
 * upgrades use existing stone, ingots or diamonds and a workbench within a short walk.
 * Everything is a real recipe: materials are spent, the bench is walked to, nothing is conjured.
 */
final class CompanionToolSense {
    /** A known workbench within this many blocks is walked to instead of placing another one. */
    static final int BENCH_WALK = 16;
    private static final int WALK_TICKS = 20 * 20;
    private static final double BENCH_REACH = 2.8D;
    private final CompanionEntity owner;
    private final String kind;
    private final TagKey<Item> tag;
    private final BlockState sample;
    private int evaluated = Integer.MIN_VALUE;
    private int reservedStone;
    private BlockPos bench;
    private Path path;
    private long giveUpAt;
    private String wanted = "";
    /** A recipe that failed is retried only once more materials are carried; walks to a bench are bounded too. */
    private final java.util.Map<String, Integer> failed = new java.util.HashMap<>();
    private int walks;
    private static final int MAX_WALKS = 2;

    CompanionToolSense(CompanionEntity owner, String kind, TagKey<Item> tag, BlockState sample) {
        this.owner = owner; this.kind = kind; this.tag = tag; this.sample = sample;
    }

    /** A new task: forget the bench walk and earlier failed recipes, re-evaluate the current bag. */
    void reset() { bench = null; path = null; wanted = ""; evaluated = Integer.MIN_VALUE; failed.clear(); walks = 0; }

    /** True when walking to a workbench or crafting; the caller skips its own movement and mining. */
    boolean tick() {
        return tick(0);
    }
    /** Already collected order materials are not spent on optional tool improvements. */
    boolean tick(int reservedStone) {
        this.reservedStone = Math.max(0, reservedStone);
        if (!(owner.level() instanceof ServerLevel level) || !owner.onGround()) return bench != null;
        if (bench != null) return walkToBench(level);
        int hash = carriedHash();
        if (hash == evaluated) return false;
        evaluated = hash;
        String target = upgradeTarget();
        if (target == null) { equipBest(); return false; }
        boolean replacing = bestSpeed() <= 0;
        if (owner.hasCraftingTableNearby()) return craft(target);
        BlockPos known = walks < MAX_WALKS ? reachableBench() : null;
        if (known != null) {
            bench = known; wanted = target; giveUpAt = level.getGameTime() + WALK_TICKS; path = null; walks++;
            owner.workStatus("Схожу к верстаку на " + known.toShortString() + ", сделаю " + label(target) + ".");
            return true;
        }
        // Reuse a reachable bench. An optional new one also needs a comfortable wood reserve.
        if ((replacing || woodReserve() >= 12) && !owner.hasKnownCraftingTableWithin(BENCH_WALK)) return craft(target);
        else equipBest();
        return false;
    }

    private boolean walkToBench(ServerLevel level) {
        if (!level.hasChunkAt(bench) || !level.getBlockState(bench).is(Blocks.CRAFTING_TABLE) || level.getGameTime() > giveUpAt) {
            bench = null; path = null; equipBest(); return false;
        }
        if (owner.hasCraftingTableNearby()) {
            owner.getNavigation().stop();
            String target = wanted; bench = null; path = null;
            return craft(target);
        }
        owner.movementIntent(bench);
        owner.getLookControl().setLookAt(bench.getX() + .5D, bench.getY() + .5D, bench.getZ() + .5D);
        if (path == null || owner.getNavigation().isDone() || owner.getNavigation().isStuck()) {
            path = CompanionMiningNavigation.pathTo(owner, bench, BENCH_REACH, false);
            if (path == null || !path.canReach()) { bench = null; path = null; equipBest(); return false; }
        }
        CompanionMiningNavigation.walk(owner, path);
        return true;
    }

    /** The best tool Oleg can make right now that is faster than everything he carries, or null. */
    private String upgradeTarget() {
        float best = bestSpeed();
        if (!(owner.level() instanceof ServerLevel level)) return null;
        // Use the real detached recipe planner, including existing sticks and hand-held materials.
        // Optional upgrades never start another gathering/smelting order.
        for (String material : new String[]{"diamond", "iron", "stone", "wooden"}) {
            String id = "minecraft:" + material + "_" + kind;
            if (speed(id) <= best || blocked(id) || material.equals("wooden") && best > 0) continue;
            boolean available = switch (material) {
                case "diamond" -> count(net.minecraft.world.item.Items.DIAMOND) >= stoneCost();
                case "iron" -> count(net.minecraft.world.item.Items.IRON_INGOT) >= stoneCost();
                case "stone" -> CompanionTools.count(owner, ItemTags.STONE_TOOL_MATERIALS) - this.reservedStone >= stoneCost();
                default -> woodReserve() >= stoneCost();
            };
            if (available && CompanionCrafting.plan(level, carried(), id, 1, true).success()) return id;
        }
        return null;
    }

    private boolean craft(String target) {
        evaluated = Integer.MIN_VALUE;
        // The planner sees both hands; the actual craft uses the bag. Put only ingredients away.
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND}) {
            ItemStack held = owner.getItemBySlot(slot);
            if ((held.is(ItemTags.LOGS) || held.is(ItemTags.PLANKS) || held.is(ItemTags.STONE_TOOL_MATERIALS)
                    || held.is(net.minecraft.world.item.Items.STICK) || held.is(net.minecraft.world.item.Items.IRON_INGOT)
                    || held.is(net.minecraft.world.item.Items.DIAMOND)) && !owner.unequipForSupport(slot.getName()).success()) {
                failed.put(target, materials()); evaluated = carriedHash(); return false;
            }
        }
        // A stone recipe can still miss a stick or a slot; a lost tool then falls back to wood.
        boolean replacing = bestSpeed() <= 0;
        String made = owner.craftFromBag(target, 1).success() ? target : null;
        if (made == null) failed.put(target, materials());
        if (made == null && replacing && target.startsWith("minecraft:stone_") && !blocked("minecraft:wooden_" + kind)) {
            made = owner.craftFromBag("minecraft:wooden_" + kind, 1).success() ? "minecraft:wooden_" + kind : null;
            if (made == null) failed.put("minecraft:wooden_" + kind, materials());
        }
        if (made != null) dev.aicompanion.CompanionDiagnostics.event(owner.diagnosticTrace(), "tool_upgraded", "entity", owner.getUUID(),
                "kind", kind, "item", made, "replacedLostTool", replacing);
        evaluated = carriedHash();
        equipBest();
        return made != null;
    }

    private boolean blocked(String recipe) { return failed.containsKey(recipe) && materials() == failed.get(recipe); }

    /** Retry only when the carried contents change; replacing one ingredient with another matters too. */
    private int materials() {
        return carriedHash();
    }
    private int woodReserve() { return CompanionTools.count(owner, ItemTags.LOGS) * 4 + CompanionTools.count(owner, ItemTags.PLANKS); }
    private int count(Item item) { return carried().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }

    private void equipBest() { CompanionTools.equip(owner, tag, sample); }

    private BlockPos reachableBench() {
        for (BlockPos pos : owner.knownCraftingTables()) {
            if (pos.distSqr(owner.blockPosition()) > BENCH_WALK * BENCH_WALK) continue;
            Path route = CompanionMiningNavigation.pathTo(owner, pos, BENCH_REACH, false);
            if (route != null && route.canReach()) return pos;
        }
        return null;
    }

    private float bestSpeed() {
        float best = 0;
        for (ItemStack stack : carried()) if (stack.is(tag)) best = Math.max(best, stack.getDestroySpeed(sample));
        return best;
    }

    private float speed(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id))).getDestroySpeed(sample);
    }

    private int stoneCost() { return kind.equals("shovel") ? 1 : kind.equals("sword") || kind.equals("hoe") ? 2 : 3; }

    private java.util.List<ItemStack> carried() {
        var stacks = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < owner.getContainerSize(); slot++) stacks.add(owner.getItem(slot));
        stacks.add(owner.getMainHandItem()); stacks.add(owner.getOffhandItem());
        return stacks;
    }

    /** What is carried, wherever it sits: swapping dirt and axe between hand and bag is not a change, a broken tool is. */
    private int carriedHash() {
        int hash = 0;
        for (ItemStack stack : carried()) if (!stack.isEmpty())
            hash += (BuiltInRegistries.ITEM.getId(stack.getItem()) * 31 + 7) * (stack.getCount() + 13);
        return hash;
    }

    private static String label(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id))).getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
    }
}
