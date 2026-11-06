package dev.aicompanion.gametest.support;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/** The goal fixture and the checks the agenda GameTest classes share. */
public final class AgendaKit {
    private AgendaKit() { }

    private static final Item[] IRON_ARMOR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** The end state of the gear_up probe: the iron set worn, every ingot spent exactly, no failure counted on the way. */
    public static void gearDone(Fixture f) {
        f.completed();
        ironSetWorn(f.h, f.c);
        f.h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
        for (var milestone : f.goal().book().milestones())
            f.h.assertTrue(f.goal().counter(milestone.key())[1] == 0, "A failure was counted for " + milestone.key());
    }

    /** Diamond tools carried and diamond armour worn: everything of prepare_nether before the food is already there. */
    public static void diamondsDone(Fixture f) {
        f.c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE)); f.c.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        Item[] armor = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) f.c.setItemSlot(ARMOR_SLOTS[i], new ItemStack(armor[i]));
    }

    public static void ironSetWorn(GameTestHelper h, CompanionEntity c) {
        for (int i = 0; i < ARMOR_SLOTS.length; i++)
            h.assertTrue(c.getItemBySlot(ARMOR_SLOTS[i]).is(IRON_ARMOR[i]), "Not worn: " + IRON_ARMOR[i] + " in " + ARMOR_SLOTS[i]);
        h.assertTrue(count(c, Items.IRON_PICKAXE) == 1 && count(c, Items.IRON_SWORD) == 1, "Tools: " + c.getStoredItemsSummary());
        for (Item piece : IRON_ARMOR) h.assertTrue(count(c, piece) == 0, "A second piece in the bag: " + piece);
    }

    public static void ingots(Fixture f, GameTestHelper h) {
        f.c.setItem(0, new ItemStack(Items.IRON_INGOT, 29)); f.c.setItem(1, new ItemStack(Items.STICK, 3));
    }

    public static void furnaces(GameTestHelper h) {
        for (int z = 14; z <= 20; z += 2) h.setBlock(new BlockPos(19, 1, z), Blocks.FURNACE);
    }

    public static Fixture fixture(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(17, 1, 16), Blocks.CRAFTING_TABLE);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        var p = survivalPlayer(h);
        p.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5); c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID()); CompanionEvents.getOwnedCompanion(p);
        return new Fixture(h, c, p);
    }

    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setInvulnerable(true);
        return p;
    }

    public static int count(CompanionEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) n += c.getItemBySlot(slot).getCount();
        return n;
    }

    public static final class Fixture {
        public final GameTestHelper h; public CompanionEntity c; public final ServerPlayer p;
        public Fixture(GameTestHelper h, CompanionEntity c, ServerPlayer p) { this.h = h; this.c = c; this.p = p; }
        public void start(String goal, int tier) {
            var result = CompanionEvents.executeAction(p, "pursue_goal", goal, tier);
            h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        }
        public CompanionAgendaData.Goal goal() { return CompanionAgenda.goal(h.getLevel().getServer(), p.getUUID()); }
        public boolean working() { return CompanionAgenda.running(p.getUUID()) && c.hasActiveWorkOrder(); }
        public CompanionActionResult order(String action) { return order(action, ""); }
        public CompanionActionResult order(String action, String item) { return CompanionEvents.executeAction(p, action, item, 1); }
        public void completed() {
            c = CompanionEvents.getOwnedCompanion(p);
            h.assertTrue(goal().state() == CompanionAgenda.State.COMPLETED, "Goal incomplete: " + goal().state() + " " + goal().reason() + " / " + c.getLastTaskResult());
        }
        public void reload() {
            var saved = new CompoundTag(); c.saveWithoutId(saved); UUID uuid = c.getUUID();
            c.discard(); c = AICompanionMod.COMPANION.get().create(h.getLevel()); c.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(c) && c.getUUID().equals(uuid), "Reload duplicated the companion");
        }
    }
}
