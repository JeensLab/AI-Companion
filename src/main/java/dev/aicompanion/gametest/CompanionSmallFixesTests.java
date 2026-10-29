package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

/** Review 22.10, М1 and М3; «/companion summon» from another dimension. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSmallFixesTests {
    /** Cobblestone the player drops by Oleg's dig is the player's, not Oleg's harvest. */
    @GameTest(template = "tall_tree", timeoutTicks = 800)
    public static void cobblestoneThePlayerDroppedIsNotTaken(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 0; y <= 1; y++)
            h.setBlock(new BlockPos(x, y, z), y == 0 ? Blocks.BEDROCK : Blocks.STONE);
        CompanionEntity companion = spawn(h, 2);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
        var dropped = new ItemEntity(h.getLevel(), companion.getX() + .6, companion.getY() + .2, companion.getZ() + .6, new ItemStack(Items.COBBLESTONE, 5));
        dropped.setThrower(owner);
        h.getLevel().addFreshEntity(dropped);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        companion.startWork("gather_stone", "", 1);
        h.succeedWhen(() -> {
            h.assertTrue(!companion.isWorking() && !companion.isGatheringTask(), "Still working: " + companion.getLastTaskResult());
            h.assertTrue(companion.didLastTaskSucceed(), companion.getLastTaskResult());
            h.assertTrue(dropped.isAlive() && dropped.getItem().getCount() == 5 && carried(companion, Items.COBBLESTONE) < 5, "He took the player's cobblestone");
        });
    }

    /** Reloaded in the middle of «наруби 4 бревна» with two already in the bag: two more, not four more. */
    @GameTest(template = "tall_tree", timeoutTicks = 1200)
    public static void afterReloadOnlyTheRestIsChopped(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        for (int x = 18; x <= 28; x += 2) h.setBlock(new BlockPos(x, 1, 16), Blocks.OAK_LOG);
        CompanionEntity first = spawn(h, 1);
        first.setItem(0, new ItemStack(Items.OAK_LOG, 2));
        first.startGatheringLogs(4);
        CompoundTag saved = new CompoundTag();
        first.saveWithoutId(saved);
        saved.putInt("LogsRemaining", 2);
        var owner = h.getLevel().getServer().getPlayerList().getPlayer(first.getOwnerId());
        first.discard();
        CompanionEntity loaded = AICompanionMod.COMPANION.get().create(h.getLevel());
        loaded.load(saved);
        h.getLevel().addFreshEntity(loaded);
        CompanionEvents.getOwnedCompanion(owner);
        h.succeedWhen(() -> {
            h.assertTrue(!loaded.isGatheringTask(), "Still gathering: " + loaded.getLastTaskResult());
            h.assertTrue(carried(loaded, Items.OAK_LOG) == 4, "Wanted 4 logs in all, has " + carried(loaded, Items.OAK_LOG));
        });
    }

    /**
     * Game 29.10: «/companion summon» typed in the Nether moved him to the same numbers in the Overworld, and he fell into a forest.
     * Here the other way round (a new Nether area takes too long to come up in a test): he waits in the Nether, the player calls him from
     * the Overworld. He comes over to the player's world, one of him, with his bag, and the chopping he was on is dropped.
     */
    @GameTest(template = "tall_tree", timeoutTicks = 2400)
    public static void summonFromAnotherDimensionBringsHimOver(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity first = spawn(h, 1);
        first.setItem(0, new ItemStack(Items.DIAMOND, 5));
        UUID id = first.getUUID();
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        h.assertTrue(nether != null, "No Nether to test with");
        var owner = server.getPlayerList().getPlayer(first.getOwnerId());
        // A closed room, so that neither netherrack nor lava is round him; a portal's ticket, renewed every tick, keeps it loaded. The entities
        // of a new Nether chunk take from 0 to some hundreds of ticks to load in a test server: he is waited for, not timed.
        BlockPos room = BlockPos.containing(first.getX(), 64, first.getZ());
        for (BlockPos pos : BlockPos.betweenClosed(room.offset(-2, -1, -2), room.offset(2, 3, 2)))
            nether.setBlockAndUpdate(pos, Math.abs(pos.getX() - room.getX()) == 2 || Math.abs(pos.getZ() - room.getZ()) == 2 || pos.getY() == room.getY() - 1
                    || pos.getY() == room.getY() + 3 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
        Runnable keepRoom = () -> nether.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL, new net.minecraft.world.level.ChunkPos(room), 1, room);
        keepRoom.run();
        h.onEachTick(keepRoom);
        first.teleportTo(nether, room.getX() + .5, room.getY(), room.getZ() + .5, Set.of(), 0, 0);
        long[] summonedAt = {-1};
        int[] answer = {0};
        h.succeedWhen(() -> {
            if (summonedAt[0] < 0) {
                h.assertTrue(nether.getEntity(id) instanceof CompanionEntity, "He is not in the Nether yet");
                ((CompanionEntity) nether.getEntity(id)).startGatheringLogs(4);
                summonedAt[0] = h.getTick();
                try { answer[0] = server.getCommands().getDispatcher().execute("companion summon", owner.createCommandSourceStack()); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { answer[0] = -1; }
            }
            h.assertTrue(answer[0] == 1, "Summon refused: " + answer[0]);
            h.assertTrue(h.getTick() - summonedAt[0] >= 40, "Watching him for a while");
            int owned = 0;
            for (var level : server.getAllLevels()) for (var entity : level.getAllEntities())
                if (entity instanceof CompanionEntity other && other.isAlive() && owner.getUUID().equals(other.getOwnerId())) owned++;
            h.assertTrue(owned == 1, "Companions of the player: " + owned);
            var left = nether.getEntity(id);
            h.assertTrue(h.getLevel().getEntity(id) instanceof CompanionEntity, "Not back in the Overworld: " + (left == null ? "nowhere" : "in the Nether at " + left.blockPosition().toShortString()));
            CompanionEntity back = (CompanionEntity) h.getLevel().getEntity(id);
            h.assertTrue(back.isAlive() && back.distanceToSqr(owner) < 4 * 4, "Not by the player: " + back.blockPosition().toShortString());
            h.assertTrue(carried(back, Items.DIAMOND) == 5 && nether.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(room).inflate(4)).isEmpty(),
                    "The bag was not kept: " + back.getStoredItemsSummary());
            h.assertTrue(!back.isGatheringTask() && !back.isWorking() && !back.getLastTaskResult().contains("выходом из мира"), "Still on the old work: " + back.getLastTaskResult());
        });
    }

    private static CompanionEntity spawn(GameTestHelper h, int y) {
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 16, y, 16);
        var owner = h.makeMockServerPlayerInLevel();
        owner.teleportTo(companion.getX() - 4, companion.getY(), companion.getZ() - 4);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(16);
        return companion;
    }

    private static int carried(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
