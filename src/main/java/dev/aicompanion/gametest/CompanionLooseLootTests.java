package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The owner's request of 31.10: items thrown at his feet or lying near him go into his bag, as a player takes them.
 * A gift handed to the player, another companion's drop and his own leftover drop stay where they are; what fell out
 * of the dead player is kept apart for the player and given back.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionLooseLootTests {
    @GameTest(template = "empty")
    public static void anItemThrownAtHisFeetGoesIntoTheBag(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity item = drop(h, companion, Items.COBBLESTONE, 5);
        h.succeedWhen(() -> {
            h.assertTrue(item.isRemoved(), "The item is still lying on the floor");
            h.assertTrue(stored(companion, Items.COBBLESTONE) >= 5,
                    "Only " + stored(companion, Items.COBBLESTONE) + " of 5 reached his bag");
        });
    }

    /** A gift he threw to the player is aimed at the player and flagged: it must not come back. */
    @GameTest(template = "empty")
    public static void aGiftHandedToThePlayerStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity gift = drop(h, companion, Items.IRON_INGOT, 2);
        gift.getPersistentData().putBoolean("aicompanion:gift", true);
        gift.setTarget(companion.getOwnerId());
        // A plain drop lies next to it, so the pickup really runs and only the gift is refused.
        ItemEntity plain = drop(h, companion, Items.COBBLESTONE, 3);
        h.succeedWhen(() -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!gift.isRemoved(), "He took back a gift he had handed to the player");
            h.assertTrue(stored(companion, Items.IRON_INGOT) == 0, "The gift reached his bag");
        });
    }

    /** A drop aimed at somebody else (another player's share) is theirs, not his. */
    @GameTest(template = "empty")
    public static void anItemAimedAtSomebodyElseStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity aimed = drop(h, companion, Items.IRON_INGOT, 2);
        aimed.setTarget(UUID.randomUUID());
        ItemEntity plain = drop(h, companion, Items.COBBLESTONE, 3);
        h.succeedWhen(() -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!aimed.isRemoved(), "He took an item aimed at somebody else");
        });
    }

    /** Tagged drops are not loose loot: another companion's harvest, or his own stack left on purpose. */
    @GameTest(template = "empty")
    public static void aTaggedDropStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity foreign = drop(h, companion, Items.OAK_LOG, 3);
        foreign.getPersistentData().putUUID("aicompanion:harvester", UUID.randomUUID());
        ItemEntity own = drop(h, companion, Items.DIRT, 2);
        own.getPersistentData().putUUID("aicompanion:harvester", companion.getUUID());
        ItemEntity plain = drop(h, companion, Items.COBBLESTONE, 3);
        h.succeedWhen(() -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!foreign.isRemoved(), "He took another companion's harvest");
            h.assertTrue(!own.isRemoved(), "He took back his own leftover drop");
        });
    }

    /** What falls out of the dead player is kept for him apart from the bag: a hungry, hurt Oleg neither eats it nor arms himself with it. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void whatFallsOutOfTheDeadPlayerIsKeptForHim(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        player.getInventory().add(new ItemStack(Items.BREAD, 3));
        player.getInventory().add(new ItemStack(Items.IRON_SWORD));
        player.teleportTo(companion.getX() + 0.5D, companion.getY(), companion.getZ());
        player.hurt(h.getLevel().damageSources().genericKill(), 100.0F);
        companion.setHealth(companion.getMaxHealth() / 3);
        // The series that hunts the flake reads these lines: the tick the things were taken, and the fate of every drop of the pile.
        var pile = new net.minecraft.world.phys.Vec3[1];
        long[] keptAt = {-1};
        var piled = new java.util.LinkedHashMap<ItemEntity, String>();
        var trail = new java.util.ArrayList<String>();
        java.util.function.Supplier<String> around = () -> {
            var sb = new StringBuilder();
            for (var e : h.getLevel().getEntities(companion, companion.getBoundingBox().inflate(16.0D), e -> !(e instanceof ItemEntity)))
                sb.append(' ').append(e.getType().toShortString()).append('@').append(String.format("%.1f", e.position().distanceTo(pile[0] == null ? companion.position() : pile[0])))
                  .append(e instanceof CompanionEntity other ? "{kept bread " + other.keptForPlayer(Items.BREAD) + ", bag " + stored(other, Items.BREAD) + ", hp " + other.getHealth() + "}"
                          : e instanceof net.minecraft.world.entity.player.Player p ? "{alive " + p.isAlive() + ", bread " + p.getInventory().countItem(Items.BREAD) + "}" : "");
            for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(16.0D)))
                sb.append(' ').append(e.getItem().getCount()).append('x').append(e.getItem().getItem()).append('@').append(String.format("%.1f", e.position().distanceTo(pile[0] == null ? companion.position() : pile[0])))
                  .append(e.getPersistentData().getBoolean("aicompanion:players") ? "[players]" : "").append(e.getTarget() != null ? "[aimed]" : "").append(e.hasPickUpDelay() ? "[delay]" : "");
            return sb.toString();
        };
        h.onEachTick(() -> {
            for (var entry : piled.entrySet()) {
                var drop = entry.getKey();
                String now = drop.isRemoved() ? "removed" : drop.getItem().getCount() + "x" + drop.getItem().getItem() + (drop.hasPickUpDelay() ? "[delay]" : "") + String.format("@%.2f", drop.position().distanceTo(pile[0]));
                if (now.equals(entry.getValue())) continue;
                entry.setValue(now);
                trail.add("t" + h.getTick() + " " + now + " | kept " + companion.keptForPlayer(Items.IRON_INGOT) + "/" + companion.keptForPlayer(Items.BREAD) + "/" + companion.keptForPlayer(Items.IRON_SWORD) + " | bag " + companion.getStoredItemsSummary() + " | oleg " + String.format("%.2f", companion.position().distanceTo(pile[0])) + " | " + around.get());
            }
            if (keptAt[0] >= 0 || pile[0] == null || companion.keptForPlayer(Items.IRON_INGOT) != 4 || companion.keptForPlayer(Items.BREAD) != 3 || companion.keptForPlayer(Items.IRON_SWORD) != 1) return;
            keptAt[0] = h.getTick();
            AICompanionMod.LOGGER.info("whatFallsOutOfTheDeadPlayerIsKeptForHim: kept for the player at tick {} , {} blocks from the pile, phase {}", keptAt[0], String.format("%.2f", companion.position().distanceTo(pile[0])), companion.workPhase());
        });
        h.startSequence().thenExecuteAfter(2, () -> {
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(8.0D));
            h.assertTrue(!drops.isEmpty(), "The dead player dropped nothing");
            // The drops scatter over a few blocks; he walks over them, so they are put at his feet, without the toss that rolls them away.
            for (ItemEntity drop : drops) { drop.setPos(companion.getX(), companion.getY() + 0.2D, companion.getZ()); drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); }
            pile[0] = companion.position();
            for (ItemEntity drop : drops) piled.put(drop, "");
            AICompanionMod.LOGGER.info("whatFallsOutOfTheDeadPlayerIsKeptForHim: pile of {} at tick {}:{}", drops.size(), h.getTick(), around.get());
        }).thenWaitUntil(() -> h.assertTrue(companion.keptForPlayer(Items.IRON_INGOT) == 4 && companion.keptForPlayer(Items.BREAD) == 3
                && companion.keptForPlayer(Items.IRON_SWORD) == 1, "What fell out of the dead player is not kept for him: " + companion.keptForPlayer(Items.IRON_INGOT)
                + " ingots, " + companion.keptForPlayer(Items.BREAD) + " bread, " + companion.keptForPlayer(Items.IRON_SWORD) + " swords; bag " + companion.getStoredItemsSummary()
                + "; tick " + h.getTick() + ", " + (pile[0] == null ? "no pile" : String.format("%.2f", companion.position().distanceTo(pile[0])) + " blocks from the pile") + ", phase " + companion.workPhase()
                + "; player bread " + player.getInventory().countItem(Items.BREAD) + " alive " + player.isAlive() + "; around:" + around.get() + " || trail: " + String.join(" ;; ", trail)))
          .thenIdle(200)
          .thenExecute(() -> {
            h.assertTrue(companion.keptForPlayer(Items.BREAD) == 3, "He ate the player's bread");
            h.assertTrue(stored(companion, Items.IRON_INGOT) == 0 && stored(companion, Items.BREAD) == 0, "The player's things went into his bag");
            h.assertTrue(!companion.getMainHandItem().is(Items.IRON_SWORD), "He took up the player's sword");
        }).thenSucceed();
    }

    /** Once the player is beside him again, the kept things are tossed at the player's feet, aimed at the player, and nothing is left with him. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void thePlayersThingsGoBackWhenHeComes(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos far = h.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
        ItemEntity lost = drop(h, companion, Items.IRON_INGOT, 5);
        lost.getPersistentData().putBoolean("aicompanion:players", true);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(lost.isRemoved() && companion.keptForPlayer(Items.IRON_INGOT) == 5, "The player's ingots were not kept"))
          .thenExecute(() -> player.teleportTo(companion.getX() + 1.5D, companion.getY(), companion.getZ()))
          .thenWaitUntil(() -> {
            h.assertTrue(companion.keptForPlayer(Items.IRON_INGOT) == 0 && stored(companion, Items.IRON_INGOT) == 0, "The ingots stayed with him");
            int aimed = 0;
            for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(4.0D)))
                if (item.getItem().is(Items.IRON_INGOT) && player.getUUID().equals(item.getTarget())) aimed += item.getItem().getCount();
            h.assertTrue(aimed + player.getInventory().countItem(Items.IRON_INGOT) == 5, "The ingots were not given back to the player: " + aimed);
        }).thenSucceed();
    }

    /** The kept things are saved with him; a save from before them loads with nothing kept. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void thePlayersThingsSurviveASave(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        var player = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        BlockPos far = h.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(far.getX() + .5, far.getY(), far.getZ() + .5);
        ItemEntity lost = drop(h, companion, Items.IRON_INGOT, 5);
        lost.getPersistentData().putBoolean("aicompanion:players", true);
        h.succeedWhen(() -> {
            h.assertTrue(companion.keptForPlayer(Items.IRON_INGOT) == 5, "The player's ingots were not kept");
            var saved = new net.minecraft.nbt.CompoundTag();
            companion.addAdditionalSaveData(saved);
            var older = saved.copy(); older.remove("PlayersThings");
            companion.readAdditionalSaveData(older);
            h.assertTrue(companion.keptForPlayer(Items.IRON_INGOT) == 0, "An older save kept something");
            companion.readAdditionalSaveData(saved);
            h.assertTrue(companion.keptForPlayer(Items.IRON_INGOT) == 5, "The kept ingots were lost in the save");
        });
    }

    /** A stack he threw himself stays on the floor: the mine leaves its excess stone that way (PR #48). */
    @GameTest(template = "empty")
    public static void aStackHeThrewHimselfStaysOnTheFloor(GameTestHelper h) {
        arena(h);
        CompanionEntity companion = spawn(h);
        ItemEntity own = drop(h, companion, Items.COBBLESTONE, 7);
        own.setThrower(companion);
        ItemEntity plain = drop(h, companion, Items.OAK_LOG, 2);
        h.succeedWhen(() -> {
            h.assertTrue(plain.isRemoved(), "The plain drop was not taken");
            h.assertTrue(!own.isRemoved(), "He took back a stack he threw himself");
        });
    }

    /** A stack on the ground next to him, thrown by the player — the very case the old rule refused. */
    private static ItemEntity drop(GameTestHelper h, CompanionEntity companion, Item item, int count) {
        ItemEntity drop = new ItemEntity(h.getLevel(), companion.getX() + 0.3D, companion.getY() + 0.1D, companion.getZ(),
                new ItemStack(item, count));
        var thrower = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        if (thrower != null) drop.setThrower(thrower);
        drop.setPickUpDelay(0);
        h.getLevel().addFreshEntity(drop);
        return drop;
    }

    private static int stored(CompanionEntity companion, Item item) {
        int total = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            ItemStack stack = companion.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
    }

    private static CompanionEntity spawn(GameTestHelper h) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 8, 1, 8);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(5, 1, 8));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        // Idle, so loose pickup runs: while a work order runs the ground belongs to the player.
        companion.setFollowing(false);
        return companion;
    }
}
