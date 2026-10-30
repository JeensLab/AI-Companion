package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionAgendaData;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionMineSite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The long goal find_diamonds (count N in the tier slot of the goal) run by the agenda. Arena as in the retreat tests: origin at
 * Y -60, the mountain of 48x48 with the grass at Y 21, Oleg picks NORTH from (24,22,24), the first tunnel runs north at feet level
 * Y 2 and the strip of diamond ore lies under it at z=1, x 22..25.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionDiamondGoalTests {
    private static final String GOAL = "find_diamonds", RETREATED = "отступил";
    static final int SIZE = 48, CENTER = 24, GROUND = 21, DEEPSLATE_TOP = 8, DIAMOND_FEET = 2;
    private static final int STRIP_Z = 1, STRIP_X0 = 22, STRIP_X1 = 25;
    private static final int TIMEOUT = 24000, SAVE_TIMEOUT = 100, FIGHT_DEADLINE = 600, RETARGET_EVERY = 20, FORCED_RETREATS = 3, NEW_CELLS = 3;
    private static final float LOW = 7;

    @AfterBatch(batch = "diamond_goal_ends") public static void afterEnds(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "diamond_goal_top_up") public static void afterTopUp(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "diamond_goal_progress") public static void afterProgress(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "diamond_goal_save") public static void afterSave(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "diamond_goal_fight") public static void afterFight(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    record Rig(CompanionEntity c, ServerPlayer p) { }

    /** Everything the goal needs is in the bag: the goal has nothing to prepare and goes straight down; two diamonds come from the strip. */
    @GameTest(template = "marathon_cave", batch = "diamond_goal_ends", timeoutTicks = TIMEOUT)
    public static void findDiamondsGoalEndsWithTheDiamondsInTheBag(GameTestHelper h) {
        Rig r = rig(h, true, supplies(32, 4));
        start(h, r, 2);
        boolean[] dug = {false};
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            dug[0] |= c.mineSite().exists() && advanced(c) >= 1;
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
        });
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time | " + report(h, r)));
        h.succeedWhen(() -> {
            CompanionEntity c = CompanionEvents.getOwnedCompanion(r.p());
            h.assertTrue(state(h, r) == CompanionAgenda.State.COMPLETED, "Goal incomplete | " + report(h, r));
            h.assertTrue(count(c, Items.DIAMOND) >= 2, "The goal is done but the bag has " + count(c, Items.DIAMOND) + " of 2 diamonds | " + report(h, r));
            h.assertTrue(dug[0] && goal(h, r).resultCount() >= 1,
                    "No dig_for_diamonds step behind the goal: dug " + dug[0] + " | " + report(h, r));
        });
    }

    /** Two torches, coal and sticks: the 32 torches are made on the surface, and only then does the first step go down. */
    @GameTest(template = "marathon_cave", batch = "diamond_goal_top_up", timeoutTicks = TIMEOUT)
    public static void topsUpTorchesOnTheSurfaceBeforeTheNextStep(GameTestHelper h) {
        ItemStack[] bag = {new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, 2), new ItemStack(Items.COAL, 8), new ItemStack(Items.STICK, 8),
                new ItemStack(Items.COBBLESTONE, 8)};
        Rig r = rig(h, true, bag);
        h.setBlock(new BlockPos(CENTER + 3, GROUND + 1, CENTER), Blocks.CRAFTING_TABLE);
        start(h, r, 1);
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Never went down | " + report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
            if (rel(h, c.blockPosition()).getY() >= GROUND) return;
            int torches = count(c, Items.TORCH);
            h.assertTrue(torches >= 16, "Went down with " + torches + " torches, nothing topped up on the surface | " + report(h, r));
            h.assertTrue(c.mineSite().exists(), "Below the surface without a mine | " + report(h, r));
            h.succeed();
        });
    }

    /**
     * Each step is cut short in the tunnel (health falls to 7, he retreats, rests, says so) after it opened new cells and found no
     * diamonds: three such steps in a row are progress, not three failures, and a fourth step starts.
     */
    @GameTest(template = "marathon_cave", batch = "diamond_goal_progress", timeoutTicks = TIMEOUT)
    public static void aStepWithNewCellsButNoDiamondsIsProgress(GameTestHelper h) {
        Rig r = rig(h, false, supplies(32, 4));
        start(h, r, 16);
        long[] step = {0};
        int[] base = {0}, retreats = {0};
        boolean[] hurt = {false};
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time: " + retreats[0] + " retreated steps | " + report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "Blocked after " + retreats[0] + " retreated steps with new cells | " + report(h, r));
            long id = c.activeWorkId();
            if (id != 0 && id != step[0]) {
                if (retreats[0] > 0) {
                    var goal = CompanionAgenda.goal(h.getLevel().getServer(), r.p().getUUID());
                    int[] counter = goal.counter(goal.book().milestones().getLast().key());
                    h.assertTrue(counter[1] == 0, "A step with new cells was counted as a failure (" + counter[1] + ") | " + report(h, r));
                    if (retreats[0] >= FORCED_RETREATS) { h.succeed(); return; }
                }
                step[0] = id; base[0] = c.mineSite().opened(); hurt[0] = false;
            }
            if (id == step[0] && !hurt[0] && c.mineSite().exists() && c.mineSite().opened() >= base[0] + NEW_CELLS) { c.setHealth(LOW); hurt[0] = true; }
            CompanionActionResult result = hurt[0] ? c.workResult(step[0]) : null;
            if (result != null && !result.message().contains(RETREATED)) h.fail("The cut step did not end as a retreat: " + result.message() + " | " + report(h, r));
            if (result != null && hurt[0]) { retreats[0]++; hurt[0] = false; step[0] = -1; }
        });
    }

    /** A save and a load keep the diamond count; an old record of another playbook loads as before and a count out of range does not. */
    @GameTest(template = "tall_tree", batch = "diamond_goal_save", timeoutTicks = SAVE_TIMEOUT)
    public static void diamondGoalSurvivesSaveAndLoad(GameTestHelper h) {
        Rig r = bare(h);
        start(h, r, 5);
        var access = h.getLevel().registryAccess();
        CompoundTag saved = CompanionAgendaData.get(h.getLevel().getServer()).save(new CompoundTag(), access);
        var loaded = CompanionAgendaData.load(saved, access).active(r.p().getUUID());
        h.assertTrue(loaded != null && loaded.id().equals(goal(h, r).id()) && loaded.playbook().equals(GOAL) && loaded.tier() == 5 && loaded.state() == CompanionAgenda.State.ACTIVE,
                "The goal did not survive a save: " + (loaded == null ? "dropped" : loaded.playbook() + " " + loaded.tier() + " " + loaded.state()));
        h.assertTrue(loaded.book() != null && loaded.book().id().equals(GOAL) && loaded.book().milestones().size() == 2, "The loaded goal lost its book");
        // An old save: the same record under another playbook and its own tier, as the other goals were written before.
        for (int i = 0; i < saved.getList("Active", 10).size(); i++) { saved.getList("Active", 10).getCompound(i).putString("Playbook", "gear_up"); saved.getList("Active", 10).getCompound(i).putInt("Tier", 2); }
        var old = CompanionAgendaData.load(saved, access).active(r.p().getUUID());
        h.assertTrue(old != null && old.playbook().equals("gear_up") && old.tier() == 2, "An old gear_up record changed: " + (old == null ? "dropped" : old.playbook() + " " + old.tier()));
        for (int i = 0; i < saved.getList("Active", 10).size(); i++) { saved.getList("Active", 10).getCompound(i).putString("Playbook", GOAL); saved.getList("Active", 10).getCompound(i).putInt("Tier", 17); }
        h.assertTrue(CompanionAgendaData.load(saved, access).active(r.p().getUUID()) == null, "A diamond count of 17 was loaded");
        h.succeed();
    }

    /** A zombie comes at him in the tunnel far from the player: the agenda does not cancel the step, he fights it off, and the goal goes on to the diamonds. */
    @GameTest(template = "marathon_cave", batch = "diamond_goal_fight", timeoutTicks = TIMEOUT)
    public static void aFightInHisOwnExpeditionDoesNotCancelTheGoalStep(GameTestHelper h) {
        Rig r = rig(h, true, supplies(32, 4));
        start(h, r, 2);
        Zombie[] zombie = {null};
        long[] spawnedAt = {-1}, step = {0};
        h.runAtTickTime(TIMEOUT - 5, () -> h.fail("Out of time | zombie " + (zombie[0] == null ? "never spawned" : "hp " + zombie[0].getHealth()) + " | " + report(h, r)));
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            h.assertTrue(c.isAlive(), "Died at " + rel(h, c.blockPosition()).toShortString() + " | " + report(h, r));
            if (zombie[0] == null) {
                if (rel(h, c.blockPosition()).getY() != DIAMOND_FEET || advanced(c) < 1) return;
                BlockPos behind = rel(h, c.mineSite().foot());
                zombie[0] = h.spawn(EntityType.ZOMBIE, behind.getX(), DIAMOND_FEET, behind.getZ());
                zombie[0].setPersistenceRequired();
                spawnedAt[0] = h.getTick(); step[0] = c.activeWorkId();
                return;
            }
            if (!zombie[0].isAlive()) return;
            if (zombie[0].getTarget() == null && h.getTick() % RETARGET_EVERY == 0) zombie[0].setTarget(c);
            h.assertTrue(c.workResult(step[0]) == null && c.activeWorkId() == step[0], "The step " + step[0] + " ended during the fight, now work " + c.activeWorkId() + " | " + report(h, r));
            h.assertTrue(state(h, r) != CompanionAgenda.State.PAUSED_DANGER, "The agenda paused the goal for the fight | " + report(h, r));
            h.assertTrue(h.getTick() - spawnedAt[0] <= FIGHT_DEADLINE, "The zombie was not dead in " + FIGHT_DEADLINE + " ticks (hp " + zombie[0].getHealth() + ") | " + report(h, r));
        });
        h.succeedWhen(() -> {
            h.assertTrue(zombie[0] != null && !zombie[0].isAlive(), "No fight yet");
            h.assertTrue(state(h, r) == CompanionAgenda.State.COMPLETED && count(CompanionEvents.getOwnedCompanion(r.p()), Items.DIAMOND) >= 2, "The goal did not go on after the fight | " + report(h, r));
        });
    }

    static void start(GameTestHelper h, Rig r, int count) {
        var result = CompanionEvents.executeAction(r.p(), "pursue_goal", GOAL, count);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
    }

    static CompanionAgendaData.Goal goal(GameTestHelper h, Rig r) { return CompanionAgenda.goal(h.getLevel().getServer(), r.p().getUUID()); }

    static CompanionAgenda.State state(GameTestHelper h, Rig r) {
        var goal = goal(h, r);
        return goal == null ? null : goal.state();
    }

    /** Iron pickaxe, the given torches and stone pickaxes, bread, cobblestone for plugs. */
    static ItemStack[] supplies(int torches, int stonePickaxes) {
        List<ItemStack> bag = new ArrayList<>(List.of(new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BREAD, 8), new ItemStack(Items.TORCH, torches), new ItemStack(Items.COBBLESTONE, 8)));
        for (int i = 0; i < stonePickaxes; i++) bag.add(new ItemStack(Items.STONE_PICKAXE));
        return bag.toArray(new ItemStack[0]);
    }

    static Rig rig(GameTestHelper h, boolean strip, ItemStack... bag) {
        mountain(h, strip);
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), CENTER, GROUND + 1, CENTER);
        c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
        for (int slot = 0; slot < bag.length; slot++) c.setItem(slot, bag[slot]);
        return bind(h, c, 3);
    }

    /** A flat bedrock floor and a bench, as the other goal tests have. */
    static Rig bare(GameTestHelper h) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        h.setBlock(new BlockPos(17, 1, 16), Blocks.CRAFTING_TABLE);
        return bind(h, h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16), 5);
    }

    /** A real survival player (the agenda looks its owner up in the player list), off by this many blocks along both axes. */
    static Rig bind(GameTestHelper h, CompanionEntity c, int distance) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setInvulnerable(true);
        p.teleportTo(c.getX() - distance, c.getY(), c.getZ() - distance);
        c.setOwnerId(p.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        p.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(p);
        return new Rig(c, p);
    }

    /** Bedrock floor and walls, deepslate up to Y -52, stone, dirt, grass; the strip of deepslate diamond ore under the tunnel when asked. */
    static void mountain(GameTestHelper h, boolean strip) {
        var level = h.getLevel();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y <= GROUND; y++) {
            boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
            boolean ore = strip && y == DIAMOND_FEET - 1 && z == STRIP_Z && x >= STRIP_X0 && x <= STRIP_X1;
            var block = y == 0 || edge ? Blocks.BEDROCK
                    : ore ? Blocks.DEEPSLATE_DIAMOND_ORE
                    : y <= DEEPSLATE_TOP ? Blocks.DEEPSLATE : y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK;
            level.setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 2 | 16);
        }
    }

    static int advanced(CompanionEntity c) { return c.mineSite().tunnels().stream().mapToInt(CompanionMineSite.Tunnel::length).sum(); }

    /** Position relative to the arena corner; GameTestHelper.relativePos mirrors X and Z in a rotated arena, so the frame is read from absolutePos. */
    static BlockPos rel(GameTestHelper h, BlockPos world) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO), dx = h.absolutePos(new BlockPos(1, 0, 0)).subtract(origin), dz = h.absolutePos(new BlockPos(0, 0, 1)).subtract(origin);
        BlockPos d = world.subtract(origin);
        return new BlockPos(d.getX() * dx.getX() + d.getZ() * dx.getZ(), d.getY(), d.getX() * dz.getX() + d.getZ() * dz.getZ());
    }

    static String report(GameTestHelper h, Rig r) {
        var goal = goal(h, r);
        CompanionEntity c = r.c();
        return "goal " + (goal == null ? "none" : goal.state() + " '" + goal.reason() + "' results " + goal.resultCount()) + ", at " + rel(h, c.blockPosition()).toShortString() + ", hp " + c.getHealth()
                + ", mine " + (c.mineSite().exists() ? c.mineSite().opened() + " cells" : "none") + " | " + c.getDeedsForAi() + " | " + c.getLastTaskResult() + " | bag: " + c.getStoredItemsSummary();
    }

    static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }
}
