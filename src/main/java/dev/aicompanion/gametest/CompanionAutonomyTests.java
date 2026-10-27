package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.entity.CompanionAutonomy;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * «Порядок и запас» (the player 23.10): with the mode on and nothing asked, Oleg takes up a deed of his own by the player — his bag
 * in order, wood and stone up to his stock, the raw meat cooked. Arena «долина» (a recorded terrain fixture), the player in the
 * forest by the quarry (x 48, z 40): trees and stone both within 24 blocks of him.
 * The clock: the whole test server shares it and in GameTests the day goes on (only mob spawning and weather are off). These
 * tests take up to thirteen minutes, and the marathon batch after them started at night (its hunt-and-cook test failed). Each
 * test starts by day and sets the clock back at its end; the night test has a batch of its own.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAutonomyTests {
    static final int X = CompanionMarathonValleyTests.X, Y = CompanionMarathonValleyTests.Y, Z = CompanionMarathonValleyTests.Z;
    static final int PLAYER_X = 48, PLAYER_Z = 40;

    /** The clock when each test began, set back at its end (all tests of a batch begin on the same tick). */
    private static final java.util.Map<MarathonArena, Long> CLOCK = new java.util.concurrent.ConcurrentHashMap<>();

    private static MarathonArena arena(GameTestHelper h) {
        CompanionMarathonValleyTests.valley(h);
        long clock = h.getLevel().getDayTime();
        // Morning for the long day tests: the day goes on in GameTests, and night is a rule of its own.
        h.getLevel().setDayTime(clock - Math.floorMod(clock, 24000L) + 1000);
        var a = new MarathonArena(h, X, Y, Z, PLAYER_X - 2, PLAYER_Z + 1, PLAYER_X, PLAYER_Z, 40);
        a.oleg.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        a.oleg.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        CLOCK.put(a, clock);
        return a;
    }

    private static void finish(MarathonArena a, String name) {
        // These companions used to stop at a tiny bag stock. Continuous mode must be shut down after its assertions,
        // otherwise completed arenas keep running autonomous mining throughout the thirty-minute session tests.
        a.script.check("end the test's autonomy session", () -> a.oleg.autonomy().setEnabled(false));
        a.script.check("the clock as it was", () -> a.h.getLevel().setDayTime(CLOCK.remove(a)));
        a.script.run(name, a.watch::summary);
    }

    private static int stone(MarathonArena a) { return a.countTag(ItemTags.STONE_TOOL_MATERIALS); }
    private static int logs(MarathonArena a) { return a.countTag(ItemTags.LOGS); }
    private static boolean saidSomething(MarathonArena a, String part) { return a.oleg.autonomy().said().stream().anyMatch(line -> line.contains(part)); }
    private static int freeSlots(CompanionEntity oleg) {
        int free = 0;
        for (int slot = 0; slot < oleg.getContainerSize(); slot++) if (oleg.getItem(slot).isEmpty()) free++;
        return free;
    }

    /** On, the player stands by for ten minutes: wood and stone up to his stock, within 24 blocks, nothing left lying. */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 13)
    public static void standingByHeKeepsWoodAndStone(GameTestHelper h) {
        var a = arena(h);
        var watch = a.watch;
        a.script
                .order("Будь самостоятельным")
                .check("the mode is on", () -> a.expect(a.oleg.autonomy().enabled(), "Mode not on: " + a.chat.history()))
                .waitUntil("wood up to the stock", () -> logs(a) >= CompanionAutonomy.WOOD_STOCK && !a.oleg.autonomy().running(), 20 * 60 * 5)
                .waitUntil("stone up to the stock", () -> stone(a) >= CompanionAutonomy.STONE_STOCK && !a.oleg.autonomy().running(), 20 * 60 * 6)
                .check("like a friend who keeps stock", () -> {
                    a.expect(a.oleg.autonomy().said().size() <= 1, "Routine work is too chatty: " + a.oleg.autonomy().said());
                    a.expect(a.oleg.getDeedsForAi().contains("Сам, без просьбы"), "Deeds not in the journal as his own: " + a.oleg.getDeedsForAi());
                    a.expect(freeSlots(a.oleg) > 0, "Bag overfull: " + a.oleg.getStoredItemsSummary());
                    // Targets within 24 of the player; standing by one at 24, he is a few blocks farther at most.
                    watch.assertStayedNear(CompanionAutonomy.LEASH + 4);
                    watch.assertNoLongIdle(15);
                    watch.assertNoCircling(40);
                    watch.assertNoRescue();
                    watch.assertNoTeleport();
                    watch.assertNothingLeft();
                })
                .order("Стоп")
                .pause(20 * 90)
                .check("the session stops when asked", () -> {
                    a.expect(!a.oleg.autonomy().running() && !a.oleg.hasActiveWorkOrder(), "Took up a deed after stop: " + a.oleg.getLastTaskResult());
                    watch.assertNoRescue();
                    watch.assertNothingLeft();
                });
        finish(a, "autonomy-stock");
    }

    /**
     * Game 23.10: «делай что хочешь» sent him for a walk, then «займись чем нибудь» twice — and he only ran about: walking, he never
     * took up a deed, and every «займись» started the minute of waiting anew. Asked in words, he comes back and starts at once.
     */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 4)
    public static void askedAfterAWalkHeTakesUpADeedAtOnce(GameTestHelper h) {
        var a = arena(h);
        long[] asked = {0};
        a.script
                .order("Погуляй")
                .check("out for a walk", () -> a.expect(a.oleg.isRoaming(), "Not roaming after «погуляй»"))
                .pause(20 * 20)
                .then("«займись чем нибудь», twice", () -> { asked[0] = h.getTick(); a.chat.say("Займись чем нибудь"); h.runAfterDelay(60, () -> a.chat.say("Займись чем нибудь, че ты просто бегаешь?")); },
                        () -> a.oleg.autonomy().running(), 20 * 45)
                .check("at once, not a minute later", () -> {
                    a.expect(h.getTick() - asked[0] <= 20 * 30, "His deed came " + (h.getTick() - asked[0]) / 20 + " s after the request");
                    a.expect(!a.oleg.isRoaming(), "Still out for a walk");
                    a.expect(a.oleg.autonomy().staysAtWork(), "No independent work area");
                });
        finish(a, "autonomy-after-walk");
    }

    /** Off (as by default): three minutes by the player, he only keeps near. */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 5)
    public static void offHeOnlyKeepsNear(GameTestHelper h) {
        var a = arena(h);
        boolean[] worked = {false};
        h.onEachTick(() -> worked[0] |= a.oleg.hasActiveWorkOrder() || a.oleg.autonomy().running());
        int logsBefore = logs(a);
        a.script
                .pause(20 * 180)
                .check("nothing on his own", () -> {
                    a.expect(!a.oleg.autonomy().enabled(), "The mode is on by default");
                    a.expect(!worked[0], "Worked on his own with the mode off: " + a.oleg.getLastTaskResult());
                    a.expect(logs(a) == logsBefore, "Logs changed: " + logsBefore + " -> " + logs(a));
                    a.watch.assertWithPlayer(8);
                });
        finish(a, "autonomy-off");
    }

    /** A request in the middle of his deed: the deed ends at once, the request is done, the logs stay with him. */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 8)
    public static void aRequestInTheMiddleComesFirst(GameTestHelper h) {
        var a = arena(h);
        int[] logsAtOrder = {0}, stoneAtOrder = {0};
        long[] orderTick = {0};
        MarathonChat.Said[] said = {null};
        a.script
                .order("Займись чем-нибудь")
                .waitUntil("his deed: two logs in", () -> a.oleg.autonomy().running() && logs(a) >= 2, 20 * 60 * 3)
                .then("«добудь 6 камня»", () -> {
                    logsAtOrder[0] = logs(a); stoneAtOrder[0] = stone(a); orderTick[0] = h.getTick();
                    said[0] = a.chat.say("Олег, добудь 6 камня");
                }, () -> !a.oleg.autonomy().running(), 20 * 3)
                .check("dropped at once", () -> a.expect(h.getTick() - orderTick[0] <= 20, "His deed went on " + (h.getTick() - orderTick[0]) + " ticks after the request"))
                .then("the request done", () -> { }, () -> said[0].done(), 20 * 60 * 4)
                .check("the request done, nothing lost", () -> {
                    a.expect(stone(a) >= stoneAtOrder[0] + 6, "Stone not got: " + stoneAtOrder[0] + " -> " + stone(a) + " || " + a.chat.history());
                    a.expect(logs(a) >= logsAtOrder[0], "Logs lost: " + logsAtOrder[0] + " -> " + logs(a));
                    a.expect(!a.chat.said.getLast().report().contains("Сначала нужно завершить"), "The request waited for his deed: " + a.chat.history());
                })
                // Continuous mode now resumes after five seconds. Check the interrupted job's drops, not a new harvest in flight.
                .order("Выключи самостоятельность")
                .pause(20 * 20)
                .check("nothing left lying", () -> a.watch.assertNothingLeft());
        finish(a, "autonomy-request-first");
    }

    /** «Стоп», then «стой», in the middle of his deed: he stops, and does not take up anything by himself afterwards. */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 12)
    public static void stopAndStayKeepHimFromStartingAgain(GameTestHelper h) {
        var a = arena(h);
        boolean[] started = {false};
        h.onEachTick(() -> started[0] |= a.oleg.autonomy().running());
        a.script
                .order("Будь самостоятельным")
                .waitUntil("his deed", () -> a.oleg.autonomy().running() && logs(a) >= 1, 20 * 60 * 3)
                .then("«стоп»", () -> a.chat.say("Стоп"), () -> !a.oleg.autonomy().running() && !a.oleg.isWorking(), 20 * 3)
                .check("from here on", () -> started[0] = false)
                .pause(20 * 150)
                .check("nothing new after «стоп»", () -> {
                    a.expect(!started[0], "Took up a deed after «стоп»: " + a.oleg.autonomy().said());
                    a.expect(a.oleg.autonomy().held(), "Not held after «стоп»");
                })
                .order("Иди за мной")
                .pause(20 * 10)
                .check("following remains the player's order", () -> a.expect(a.oleg.autonomy().held() && !a.oleg.autonomy().running(), "Autonomy overrode following"))
                .order("Будь самостоятельным")
                .waitUntil("after an order he may again", () -> a.oleg.autonomy().running(), 20 * 60 * 3)
                .then("«стой»", () -> a.chat.say("Стой"), () -> !a.oleg.autonomy().running() && !a.oleg.isWorking(), 20 * 3)
                .check("from here on", () -> started[0] = false)
                .pause(20 * 150)
                .check("nothing new after «стой»", () -> {
                    a.expect(!started[0], "Took up a deed after «стой»: " + a.oleg.autonomy().said());
                    a.expect(!a.oleg.isFollowing(), "«Стой» did not keep him in place");
                    a.watch.assertNothingLeft();
                });
        finish(a, "autonomy-stop-stay");
    }

    /** The player leaves: finish the batch and keep working in the original area, until explicitly called. */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 7)
    public static void thePlayerWalksAwayInTheMiddleOfChopping(GameTestHelper h) {
        var a = arena(h);
        var watch = a.watch;
        long[] workAtLeave = {0};
        BlockPos[] area = {null};
        a.script
                .order("Будь самостоятельным")
                .waitUntil("his chopping", () -> a.oleg.autonomy().running() && a.oleg.autonomy().deedName().contains("дров") && logs(a) >= 1, 20 * 60 * 3)
                .then("the player walks off to the east", () -> { workAtLeave[0] = a.oleg.activeWorkId(); area[0] = a.oleg.autonomy().workArea(); a.walkPlayer(.2, 60, 44, 74, 50, 86, 60); }, a::playerArrived, 20 * 60)
                .waitUntil("the original batch actually succeeds", () -> a.oleg.workResult(workAtLeave[0]) != null, 20 * 180)
                .check("work succeeds while the owner is far away", () -> {
                    a.expect(a.oleg.workResult(workAtLeave[0]) != null && a.oleg.workResult(workAtLeave[0]).success(), "Batch abandoned: " + a.oleg.getLastTaskResult());
                    a.expect(a.toPlayer() > 24, "Chased the owner: " + a.toPlayer());
                    a.expect(area[0].equals(a.oleg.autonomy().workArea()), "Work area moved with the owner");
                    a.expect(!saidSomething(a, "ушёл далеко"), "Still announces abandoning work");
                    watch.assertNoTeleport();
                })
                .waitUntil("another job without being asked", () -> a.oleg.activeWorkId() > workAtLeave[0], 20 * 90)
                .order("Иди за мной")
                .waitUntil("comes when explicitly called", () -> a.toPlayer() < 8, 20 * 120)
                .check("the call pauses independent work", () -> a.expect(a.oleg.autonomy().held(), "Did not keep the follow order"));
        finish(a, "autonomy-player-leaves");
    }

    /**
     * The player's log hut and his chest by him: the bag almost full, he puts the surplus in a chest of his own (not the
     * player's), then chops for wood — not the hut. Persistent leaves on a log the player set are left alone too.
     */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 12)
    public static void thePlayersHutAndChestAreLeftAlone(GameTestHelper h) {
        var a = arena(h);
        List<BlockPos> hut = hut(a, 51, 43);
        BlockPos planted = planted(a, 45, 36);
        BlockPos playerChest = a.cell(50, a.ground(50, 38), 38);
        h.setBlock(playerChest, Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getBlockEntity(playerChest);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        // Almost full (four slots free): resources over his stock, 4 logs for a chest of his own, his tools.
        a.oleg.setItem(1, new ItemStack(Items.OAK_LOG, 4));
        Item[] junk = {Items.COBBLESTONE, Items.DIRT, Items.GRAVEL, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.FLINT, Items.SAND};
        for (int slot = 2; slot < 27 - CompanionAutonomy.BAG_FREE_SLOTS; slot++) a.oleg.setItem(slot, new ItemStack(junk[slot % junk.length], 64));
        a.script
                .order("Будь самостоятельным")
                .waitUntil("the surplus put away", () -> freeSlots(a.oleg) > CompanionAutonomy.BAG_FREE_SLOTS && !a.oleg.autonomy().running(), 20 * 60 * 5)
                .check("in a chest of his own, not the player's", () -> {
                    a.expect(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3, "The player's chest changed: " + chest.getItem(0));
                    for (int slot = 1; slot < chest.getContainerSize(); slot++) a.expect(chest.getItem(slot).isEmpty(), "Put into the player's chest: " + chest.getItem(slot));
                    a.expect(freeSlots(a.oleg) > CompanionAutonomy.BAG_FREE_SLOTS, "Bag still full: " + a.oleg.getStoredItemsSummary());
                    a.expect(stone(a) >= CompanionAutonomy.STONE_STOCK, "Kept stone not kept: " + stone(a));
                })
                .waitUntil("wood chopped", () -> logs(a) >= 16 && !a.oleg.autonomy().running(), 20 * 60 * 5)
                .check("the hut and the planted log stand", () -> {
                    for (BlockPos pos : hut) a.expect(!h.getBlockState(pos).isAir(), "The player's hut was broken at " + pos);
                    a.expect(h.getBlockState(planted).is(net.minecraft.tags.BlockTags.LOGS), "The player's planted log was chopped");
                    a.watch.assertNothingLeft();
                });
        finish(a, "autonomy-players-things");
    }

    /** A log hut of the player: oak log corners three high, planks between, a plank roof (on a levelled floor). */
    private static List<BlockPos> hut(MarathonArena a, int x0, int z0) {
        int y = a.ground(x0 + 1, z0 + 1);
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = x0; x <= x0 + 3; x++) for (int z = z0; z <= z0 + 3; z++) {
            for (int dy = -3; dy < 0; dy++) a.h.setBlock(a.cell(x, y + dy, z), Blocks.DIRT);
            for (int dy = 0; dy <= 5; dy++) a.h.setBlock(a.cell(x, y + dy, z), Blocks.AIR);
            boolean corner = (x == x0 || x == x0 + 3) && (z == z0 || z == z0 + 3), wall = x == x0 || x == x0 + 3 || z == z0 || z == z0 + 3;
            for (int dy = 0; dy < 3; dy++) if (wall) { BlockPos pos = a.cell(x, y + dy, z); a.h.setBlock(pos, corner ? Blocks.OAK_LOG : Blocks.OAK_PLANKS); blocks.add(pos); }
            BlockPos roof = a.cell(x, y + 3, z); a.h.setBlock(roof, Blocks.OAK_PLANKS); blocks.add(roof);
        }
        return blocks;
    }

    /** A log the player set among leaves he placed (persistent), nothing crafted by it. */
    private static BlockPos planted(MarathonArena a, int x, int z) {
        int y = a.ground(x, z);
        BlockPos log = a.cell(x, y, z);
        a.h.setBlock(log, Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        a.h.setBlock(a.cell(x, y + 1, z), leaves);
        a.h.setBlock(a.cell(x + 1, y, z), leaves);
        return log;
    }

    /** Safe work can continue through the night; an actual nearby monster pauses it. */
    @GameTest(template = "marathon_valley", batch = "autonomy_night", timeoutTicks = 20 * 60 * 8)
    public static void nightOrAMonsterKeepsHimByThePlayer(GameTestHelper h) {
        var a = arena(h);
        boolean[] started = {false};
        h.onEachTick(() -> started[0] |= a.oleg.autonomy().running());
        net.minecraft.world.entity.monster.Zombie[] zombie = {null};
        // The whole test server shares the clock: set back at the end, as the marathon's night test does.
        long clock = h.getLevel().getDayTime();
        a.script
                .then("night, a zombie in a glass cage by the player", () -> {
                    h.getLevel().setDayTime(18000);
                    int x = PLAYER_X + 9, z = PLAYER_Z, y = a.ground(x, z);
                    zombie[0] = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, a.at(x, z));
                    zombie[0].setNoAi(true); zombie[0].setPersistenceRequired();
                    zombie[0].setInvulnerable(true); zombie[0].setTarget(a.player);
                    zombie[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                }, () -> true, 20)
                .order("Будь самостоятельным")
                .waitUntil("he explains the actual danger", () -> saidSomething(a, "Рядом монстры"), 20 * 10)
                .pause(20 * 100)
                .check("no deed with a monster by the player", () -> a.expect(!started[0], "Took up a deed with a zombie by the player: " + a.oleg.autonomy().said()))
                .then("the zombie gone", () -> zombie[0].discard(), () -> true, 20)
                .waitUntil("then his deed, still at night", () -> started[0], 20 * 60 * 2)
                .then("another monster interrupts his deed", () -> {
                    zombie[0] = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, a.at(PLAYER_X + 9, PLAYER_Z));
                    zombie[0].setNoAi(true); zombie[0].setPersistenceRequired();
                    zombie[0].setInvulnerable(true); zombie[0].setTarget(a.player);
                }, () -> !a.oleg.autonomy().running(), 20 * 3)
                .check("protection takes priority", () -> a.expect(saidSomething(a, "Рядом монстры"), "Did not explain the interruption"))
                .then("safe again", () -> zombie[0].discard(), () -> true, 20)
                .waitUntil("work resumes without a request", () -> a.oleg.autonomy().running(), 20 * 90)
                .order("Стоп")
                .check("all the while by the player", () -> { a.watch.assertNothingLeft(); h.getLevel().setDayTime(clock); });
        finish(a, "autonomy-night-monster");
    }

    /** Leaving the world in the middle of his deed: the mode is remembered, the same Oleg with the logs, no second one. */
    @GameTest(template = "marathon_valley", batch = "autonomy", timeoutTicks = 20 * 60 * 9)
    public static void leavingTheWorldInTheMiddleOfHisDeed(GameTestHelper h) {
        var a = arena(h);
        int[] logsBefore = {0};
        java.util.UUID id = a.oleg.getUUID();
        a.script
                .order("Будь самостоятельным")
                .waitUntil("his deed: three logs in", () -> a.oleg.autonomy().running() && logs(a) >= 3, 20 * 60 * 3)
                .then("leave and come back", () -> { logsBefore[0] = logs(a); a.reload(() -> { }); },
                        () -> a.oleg.getUUID().equals(id) && !a.oleg.isRemoved() && a.oleg.tickCount > 5, 20 * 5)
                .check("the same Oleg, the mode on", () -> {
                    a.expect(a.oleg.autonomy().enabled(), "The mode was forgotten");
                    a.expect(logs(a) >= logsBefore[0], "Logs after reload: " + logsBefore[0] + " -> " + logs(a));
                    long companions = h.getLevel().getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(200)).stream()
                            .filter(c -> c.isAlive() && a.player.getUUID().equals(c.getOwnerId())).count();
                    a.expect(companions == 1, "Companions after reload: " + companions);
                })
                .waitUntil("his deed again after a while", () -> a.oleg.autonomy().running(), 20 * 60 * 3)
                .waitUntil("wood up to the stock", () -> logs(a) >= CompanionAutonomy.WOOD_STOCK && !a.oleg.autonomy().running(), 20 * 60 * 3)
                .check("nothing of it lying", () -> a.watch.assertNothingLeft());
        finish(a, "autonomy-reload");
    }

    /** A world from before the mode: no field in his save — he loads, everything with him, the mode off. The phrases switch it. */
    @GameTest(template = "empty")
    public static void anOldWorldLoadsWithTheModeOff(GameTestHelper h) {
        CompanionEntity oleg = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        oleg.setItem(0, new ItemStack(Items.OAK_LOG, 5));
        oleg.autonomy().setEnabled(true);
        CompoundTag saved = new CompoundTag();
        oleg.saveWithoutId(saved);
        h.assertTrue(saved.getBoolean("Autonomy"), "The mode is not saved");
        BlockPos workArea = oleg.autonomy().workArea();
        saved.remove("Autonomy"); saved.remove("AutonomyHeld");
        saved.remove("AutonomyArea"); saved.remove("AutonomyDimension");
        CompanionEntity old = AICompanionMod.COMPANION.get().create(h.getLevel());
        old.load(saved);
        h.assertTrue(!old.autonomy().enabled() && !old.autonomy().held(), "An old world without the field is not off");
        h.assertTrue(MarathonArena.count(old, Items.OAK_LOG) == 5, "The bag of an old world not loaded");
        CompoundTag on = new CompoundTag();
        oleg.saveWithoutId(on);
        CompanionEntity again = AICompanionMod.COMPANION.get().create(h.getLevel());
        again.load(on);
        h.assertTrue(again.autonomy().enabled(), "The mode is not loaded");
        h.assertTrue(workArea.equals(again.autonomy().workArea()), "Workplace not loaded");
        on.remove("AutonomyArea"); on.remove("AutonomyDimension");
        again.load(on);
        h.assertTrue(again.autonomy().enabled() && again.autonomy().workArea() == null, "Previous autonomy save is incompatible");
        // The phrases as said in the game: a local command, not a request for the model.
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Будь самостоятельным", "Олег")), "«Будь самостоятельным»");
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Олег, займись чем-нибудь", "Олег")), "«займись чем-нибудь»");
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Делай что хочешь", "Олег")), "«делай что хочешь»");
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Займись пока чем нибудь", "Олег")), "«займись пока чем нибудь»");
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Делай пока что хочешь", "Олег")), "«делай пока что хочешь»");
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Займись делом", "Олег")), "«займись делом»");
        h.assertTrue("companion autonomy on".equals(CompanionLocalCommands.route("Займись чем нибудь, че ты просто бегаешь?", "Олег")), "«займись чем нибудь, че ты просто бегаешь?»");
        h.assertTrue(CompanionLocalCommands.route("Займись чем-нибудь, но сначала добудь камня", "Олег") == null, "A request with steps goes to the model");
        h.assertTrue("companion autonomy off".equals(CompanionLocalCommands.route("Хватит, просто будь рядом", "Олег")), "«хватит, просто будь рядом»");
        h.assertTrue("companion autonomy off".equals(CompanionLocalCommands.route("Не будь самостоятельным", "Олег")), "«не будь самостоятельным»");
        h.assertTrue("companion stop".equals(CompanionLocalCommands.route("Стоп", "Олег")), "«стоп» still stops");
        h.assertTrue(!CompanionLocalCommands.cancelsPlans("companion autonomy on"), "The mode switch drops the player's plans");
        h.assertTrue(!dev.aicompanion.client.CompanionPlanCodec.ACTIONS.contains("autonomy"), "The model's plan got a new action");
        h.succeed();
    }
}
