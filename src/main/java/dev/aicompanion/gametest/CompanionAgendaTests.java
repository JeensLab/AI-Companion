package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionFurnaceBatches;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Long goals (CompanionAgenda): milestones from facts, run as his own work, through reloads, deaths and fights. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaTests {
    private static final Item[] IRON_ARMOR = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static long gearClock;

    /** Night is where the probe is hardest: path torches are crafted from the bag when he is free in the dark, and the goal owns that wood. */
    @BeforeBatch(batch = "agenda_gear")
    public static void nightForGear(ServerLevel level) { gearClock = level.getDayTime(); level.setDayTime(18000); }
    @AfterBatch(batch = "agenda_gear")
    public static void endNightForGear(ServerLevel level) { level.setDayTime(gearClock); CompanionFixtureCleanup.afterAtomicTests(level); }

    // A goal left running after its test would go on working beside the batches that follow: clean up like the other suites.
    @AfterBatch(batch = "agenda_fallback") public static void afterFallback(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_reload") public static void afterReload(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_death") public static void afterDeath(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_danger") public static void afterDanger(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_park") public static void afterPark(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_hurt") public static void afterHurt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_siege") public static void afterSiege(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_stop") public static void afterStop(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_blocked") public static void afterBlocked(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_quick") public static void afterQuick(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** The probe of the plan: raw iron and coal in the bag, a bench and furnaces near; nothing is handed out. */
    @GameTest(template = "tall_tree", batch = "agenda_gear", timeoutTicks = 6000)
    public static void pursueGoalGearUpEquipsIronSetFromRawIron(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.RAW_IRON, 29)); f.c.setItem(1, new ItemStack(Items.COAL, 6)); f.c.setItem(2, new ItemStack(Items.STICK, 3));
        furnaces(h);
        f.start("gear_up", 1);
        boolean[] parkedFree = {false};
        h.onEachTick(() -> {
            // While the batch cooks he is free: no work order holds him, the batch is his own.
            for (var batch : f.c.batches().summaries())
                if (batch.orphan() && f.c.batches().state(batch.id()).status() == CompanionFurnaceBatches.Status.WAITING && !f.c.hasActiveWorkOrder()) parkedFree[0] = true;
        });
        h.succeedWhen(() -> {
            f.completed();
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0 && count(f.c, Items.RAW_IRON) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
            h.assertTrue(parkedFree[0], "The goal's smelt did not park as his own batch");
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_fallback", timeoutTicks = 4000)
    public static void diamondTierFallsBackToIronWhenNoDiamondsNear(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.IRON_PICKAXE)); f.c.setItem(1, new ItemStack(Items.IRON_INGOT, 26)); f.c.setItem(2, new ItemStack(Items.STICK, 1));
        f.start("gear_up", 2);
        h.succeedWhen(() -> {
            f.completed();
            h.assertTrue(f.goal().fallback(), "No fallback to iron");
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.DIAMOND) == 0 && count(f.c, Items.IRON_INGOT) == 0, "Wrong material use: " + f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_reload", timeoutTicks = 3000)
    public static void reloadMidGoalMakesNoSecondPiece(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        boolean[] loaded = {false};
        h.onEachTick(() -> {
            if (!loaded[0] && count(f.c, Items.IRON_PICKAXE) == 1) { loaded[0] = true; f.reload(); }
        });
        h.succeedWhen(() -> {
            f.completed();
            h.assertTrue(loaded[0], "No reload in the middle of the goal");
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots left or a piece made twice: " + f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_death", timeoutTicks = 1600)
    public static void goalSurvivesCompanionDeathAndReconciles(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        UUID[] oldId = {null}; long[] work = {0, 0};
        h.onEachTick(() -> {
            if (oldId[0] == null) work[0] = Math.max(work[0], f.c.activeWorkId());
            if (oldId[0] == null && count(f.c, Items.IRON_PICKAXE) == 1) { oldId[0] = f.c.getUUID(); f.c.kill(); }
            CompanionEntity back = CompanionEvents.getOwnedCompanion(f.p);
            if (oldId[0] != null && work[1] == 0 && back != null && !back.getUUID().equals(oldId[0])) work[1] = back.activeWorkId();
        });
        h.succeedWhen(() -> {
            h.assertTrue(oldId[0] != null, "No death in the middle of the goal");
            CompanionEntity back = CompanionEvents.getOwnedCompanion(f.p);
            h.assertTrue(back != null && !back.getUUID().equals(oldId[0]), "He did not come back");
            var goal = f.goal();
            h.assertTrue(goal.deaths() == 1, "The death was not seen by the goal: " + goal.deaths());
            h.assertTrue(goal.state().outstanding() && goal.state() != CompanionAgenda.State.BLOCKED, "The goal ended with the death: " + goal.state() + " " + goal.reason());
            for (String key : new String[]{"materials:iron", "piece:iron:pickaxe", "piece:iron:sword"})
                h.assertTrue(goal.counter(key)[1] == 0, "The death counted as a failure of " + key);
            // The goal took up its work on the companion who came back, with work numbers that go on from his memory.
            h.assertTrue(work[1] > work[0], "No new work after the death, or its number started over: " + work[1] + " <= " + work[0]);
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_danger", timeoutTicks = 3000)
    public static void dangerPausesAndResumesByItself(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var enemy = h.spawn(EntityType.ZOMBIE, 25, 1, 25); enemy.setNoAi(true); enemy.setInvulnerable(true);
        h.runAfterDelay(15, () -> f.c.setTarget(enemy));
        h.runAfterDelay(70, () -> {
            h.assertTrue(f.goal().state() == CompanionAgenda.State.PAUSED_DANGER, "The fight did not pause the goal: " + f.goal().state());
            enemy.discard(); f.c.setTarget(null);
        });
        h.succeedWhen(() -> { f.completed(); ironSetWorn(h, f.c); });
    }

    @GameTest(template = "tall_tree", batch = "agenda_quick", timeoutTicks = 200)
    public static void longGoalAndPickaxeOrderExcludeEachOther(GameTestHelper h) {
        var f = fixture(h);
        f.c.setItem(0, new ItemStack(Items.COBBLESTONE, 6)); f.c.setItem(1, new ItemStack(Items.STICK, 4));
        h.assertTrue(CompanionEvents.executeAction(f.p, "goal_pickaxes", "", 1).status() == CompanionActionResult.Status.ACCEPTED, "Pickaxe order refused");
        var refused = CompanionEvents.executeAction(f.p, "pursue_goal", "gear_up", 1);
        h.assertTrue(refused.status() == CompanionActionResult.Status.BLOCKED && f.goal() == null, "A long goal started over the pickaxe order: " + refused.message());
        h.assertTrue(f.c.goals().state() == dev.aicompanion.entity.CompanionGoals.State.ACTIVE, "Asking for a long goal paused the pickaxe order");
        h.assertTrue(!CompanionEvents.executeAction(f.p, "pursue_goal", "no_such_goal", 1).success(), "Unknown goal accepted");
        h.succeed();
    }

    @GameTest(template = "tall_tree", batch = "agenda_quick", timeoutTicks = 200)
    public static void activeGoalHoldsDeedsRefusesPickaxesAndNamesWhatHeCannot(GameTestHelper h) {
        var f = fixture(h); f.start("defeat_ender_dragon", 0);
        h.assertTrue(CompanionAgenda.suppressesDeeds(f.c), "An active goal lets his own deeds run");
        h.assertTrue(CompanionEvents.executeAction(f.p, "goal_pickaxes", "", 1).status() == CompanionActionResult.Status.BLOCKED, "Pickaxe order started over a long goal");
        var said = CompanionEvents.executeAction(f.p, "goal_status", "", 1).message();
        h.assertTrue(said.contains("победа над драконом") && said.contains("Пока не умею") && said.contains("портал"), "Status hides what he cannot do: " + said);
        h.succeed();
    }

    @GameTest(template = "tall_tree", batch = "agenda_quick", timeoutTicks = 100)
    public static void agendaRecordSurvivesSaveAndRejectsDamage(GameTestHelper h) {
        var f = fixture(h); f.start("gear_up", 2);
        var server = h.getLevel().getServer();
        CompoundTag saved = CompanionAgendaData.get(server).save(new CompoundTag(), h.getLevel().registryAccess());
        var loaded = CompanionAgendaData.load(saved, h.getLevel().registryAccess()).active(f.p.getUUID());
        h.assertTrue(loaded != null && loaded.id().equals(f.goal().id()) && loaded.playbook().equals("gear_up") && loaded.tier() == 2
                && loaded.state() == CompanionAgenda.State.ACTIVE, "The goal did not survive a save");
        for (int i = 0; i < saved.getList("Active", 10).size(); i++) saved.getList("Active", 10).getCompound(i).putString("Playbook", "summon_items");
        h.assertTrue(CompanionAgendaData.load(saved, h.getLevel().registryAccess()).active(f.p.getUUID()) == null, "A damaged record was loaded");
        h.assertTrue(CompanionAgendaData.load(new CompoundTag(), h.getLevel().registryAccess()).active(f.p.getUUID()) == null, "An old world got a goal");
        h.succeed();
    }

    /** Raw beef cooks as his own parked batch while the next milestone already runs; the food plan is not started twice. */
    @GameTest(template = "tall_tree", batch = "agenda_park", timeoutTicks = 2000)
    public static void goalSmeltParksAndNextMilestoneRuns(GameTestHelper h) {
        var f = fixture(h); diamondsDone(f);
        f.c.setItem(2, new ItemStack(Items.BEEF, 16)); f.c.setItem(3, new ItemStack(Items.COAL, 6)); f.c.setItem(4, new ItemStack(Items.STICK, 4));
        furnaces(h);
        f.start("prepare_nether", 0);
        Set<Object> batches = new HashSet<>(); long[] smeltWork = {0}; boolean[] seen = {false, false};
        h.onEachTick(() -> {
            for (var batch : f.c.batches().summaries()) {
                if (batches.add(batch.id())) smeltWork[0] = Math.max(smeltWork[0], f.c.activeWorkId());
                if (!batch.orphan() || f.c.batches().state(batch.id()).status() != CompanionFurnaceBatches.Status.WAITING) continue;
                if (f.goal().counter("food")[0] != 0) seen[0] = true;
                // A new work of the goal while the beef still cooks: the next milestone runs beside the parked batch.
                if (CompanionAgenda.running(f.p.getUUID()) && f.c.activeWorkId() > smeltWork[0]) seen[1] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!seen[0], "The food plan ran again while its batch cooked: " + f.goal().counter("food")[0]);
            h.assertTrue(batches.size() == 1, "Not exactly one batch: " + batches.size());
            h.assertTrue(seen[1], "The next milestone did not run beside the parked batch: " + f.goal().state() + " " + f.goal().reason());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_hurt", timeoutTicks = 200)
    public static void lowHealthWithoutFoodDoesNotPauseTheGoal(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        boolean[] seen = {false, false};
        h.onEachTick(() -> {
            if (f.c.getHealth() > 4) f.c.setHealth(4);
            seen[0] |= f.goal().state() == CompanionAgenda.State.PAUSED_DANGER;
            seen[1] |= CompanionAgenda.running(f.p.getUUID());
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(!seen[0] && f.goal().state().suppressesDeeds(), "Low health without food paused the goal: " + f.goal().state());
            h.assertTrue(seen[1], "The goal did not run");
            h.succeed();
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_siege", timeoutTicks = 2000)
    public static void dangerThatNeverPassesBlocksTheGoal(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        var enemy = h.spawn(EntityType.ZOMBIE, 25, 1, 25); enemy.setNoAi(true); enemy.setInvulnerable(true);
        h.onEachTick(() -> { if (f.c.getTarget() == null) f.c.setTarget(enemy); });
        h.succeedWhen(() -> {
            var goal = f.goal();
            h.assertTrue(goal.state() == CompanionAgenda.State.BLOCKED && !goal.reason().isBlank(), "An endless fight did not stop the goal: " + goal.state());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_stop", timeoutTicks = 3000)
    public static void neutralEndSpendsNoAttemptAndResumeClearsTheStop(GameTestHelper h) {
        var f = fixture(h); ingots(f, h); f.start("gear_up", 1);
        int[] phase = {0, 0};
        h.onEachTick(() -> {
            if (phase[0] == 0 && CompanionAgenda.running(f.p.getUUID())) {
                CompanionEvents.executeAction(f.p, "stop", "", 1);
                h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD && f.c.autonomy().playerStopped(), "Stop did not hold the goal: " + f.goal().state());
                phase[0] = 1;
            } else if (phase[0] == 1 && ++phase[1] >= 60) {
                h.assertTrue(f.goal().state() == CompanionAgenda.State.HELD, "The held goal moved: " + f.goal().state());
                for (var milestone : f.goal().book().milestones())
                    h.assertTrue(f.goal().counter(milestone.key())[0] == 0, "Stop spent an attempt of " + milestone.key());
                var resumed = CompanionEvents.executeAction(f.p, "goal_resume", "", 1);
                h.assertTrue(resumed.status() == CompanionActionResult.Status.ACCEPTED && f.goal().state() == CompanionAgenda.State.ACTIVE,
                        "Resume did not restart the goal: " + resumed.message());
                h.assertTrue(!f.c.autonomy().playerStopped(), "Resume left his stop in place");
                phase[0] = 2;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(phase[0] == 2, "No stop and resume");
            f.completed();
            ironSetWorn(h, f.c);
            h.assertTrue(count(f.c, Items.IRON_INGOT) == 0, "Ingots spent not exactly: " + f.c.getStoredItemsSummary());
        });
    }

    @GameTest(template = "tall_tree", batch = "agenda_blocked", timeoutTicks = 300)
    public static void blockedGoalStaysCancellableAndYieldsToPickaxes(GameTestHelper h) {
        var f = fixture(h); diamondsDone(f);
        f.c.setItem(2, new ItemStack(Items.COBBLESTONE, 64)); f.c.setItem(3, new ItemStack(Items.COOKED_BEEF, 16)); f.c.setItem(4, new ItemStack(Items.STICK, 4));
        f.start("prepare_nether", 0);
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || f.goal().state() != CompanionAgenda.State.BLOCKED) return;
            done[0] = true;
            h.assertTrue(f.goal().reason().contains("огниво"), "The stop does not name the flint: " + f.goal().reason());
            var pickaxes = CompanionEvents.executeAction(f.p, "goal_pickaxes", "", 1);
            h.assertTrue(pickaxes.status() == CompanionActionResult.Status.ACCEPTED, "A blocked goal held the pickaxe order: " + pickaxes.message());
            CompanionEvents.executeAction(f.p, "goal_resume", "", 1);
            h.assertTrue(f.goal().state() == CompanionAgenda.State.BLOCKED && f.c.goals().outstanding(), "«Продолжай» woke the long goal beside the pickaxe order");
            CompanionEvents.executeAction(f.p, "goal_cancel", "", 1);
            h.assertTrue(f.goal().state() == CompanionAgenda.State.CANCELLED && f.c.goals().outstanding(), "The first cancel did not end the long goal");
            CompanionEvents.executeAction(f.p, "goal_cancel", "", 1);
            h.assertTrue(!f.c.goals().outstanding(), "The second cancel did not end the pickaxe order");
            var none = CompanionEvents.executeAction(f.p, "goal_cancel", "", 1);
            h.assertTrue(!none.success() && none.message().contains("Нет цели"), "A third cancel found a goal: " + none.message());
            h.succeed();
        });
    }

    // ---- Fixture ----

    /** Diamond tools carried and diamond armour worn: everything of prepare_nether before the food is already there. */
    private static void diamondsDone(Fixture f) {
        f.c.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE)); f.c.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        Item[] armor = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        for (int i = 0; i < ARMOR_SLOTS.length; i++) f.c.setItemSlot(ARMOR_SLOTS[i], new ItemStack(armor[i]));
    }

    private static void ironSetWorn(GameTestHelper h, CompanionEntity c) {
        for (int i = 0; i < ARMOR_SLOTS.length; i++)
            h.assertTrue(c.getItemBySlot(ARMOR_SLOTS[i]).is(IRON_ARMOR[i]), "Not worn: " + IRON_ARMOR[i] + " in " + ARMOR_SLOTS[i]);
        h.assertTrue(count(c, Items.IRON_PICKAXE) == 1 && count(c, Items.IRON_SWORD) == 1, "Tools: " + c.getStoredItemsSummary());
        for (Item piece : IRON_ARMOR) h.assertTrue(count(c, piece) == 0, "A second piece in the bag: " + piece);
    }

    private static void ingots(Fixture f, GameTestHelper h) {
        f.c.setItem(0, new ItemStack(Items.IRON_INGOT, 29)); f.c.setItem(1, new ItemStack(Items.STICK, 3));
    }

    private static void furnaces(GameTestHelper h) {
        for (int z = 14; z <= 20; z += 2) h.setBlock(new BlockPos(19, 1, z), Blocks.FURNACE);
    }

    private static Fixture fixture(GameTestHelper h) {
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

    static int count(CompanionEntity c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (c.getItemBySlot(slot).is(item)) n += c.getItemBySlot(slot).getCount();
        return n;
    }

    private static final class Fixture {
        final GameTestHelper h; CompanionEntity c; final ServerPlayer p;
        Fixture(GameTestHelper h, CompanionEntity c, ServerPlayer p) { this.h = h; this.c = c; this.p = p; }
        void start(String goal, int tier) {
            var result = CompanionEvents.executeAction(p, "pursue_goal", goal, tier);
            h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        }
        CompanionAgendaData.Goal goal() { return CompanionAgenda.goal(h.getLevel().getServer(), p.getUUID()); }
        void completed() {
            c = CompanionEvents.getOwnedCompanion(p);
            h.assertTrue(goal().state() == CompanionAgenda.State.COMPLETED, "Goal incomplete: " + goal().state() + " " + goal().reason() + " / " + c.getLastTaskResult());
        }
        void reload() {
            var saved = new CompoundTag(); c.saveWithoutId(saved); UUID uuid = c.getUUID();
            c.discard(); c = AICompanionMod.COMPANION.get().create(h.getLevel()); c.load(saved);
            h.assertTrue(h.getLevel().addFreshEntity(c) && c.getUUID().equals(uuid), "Reload duplicated the companion");
        }
    }
}
