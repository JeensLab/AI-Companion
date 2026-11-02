package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What the one who comes back does about his things at the death place: only a healed body goes for them. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionAgendaDeathTests {
    /** Above the health at which a goal pauses for danger (6), below the fit health he goes for his things with. */
    private static final float HURT = 10;

    private CompanionAgendaDeathTests() { }

    @AfterBatch(batch = "agenda_recover_hurt") public static void afterHurt(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_no_wood") public static void afterNoWood(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_recover_mend") public static void afterMend(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_recover_cave") public static void afterCave(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_descend") public static void afterDescend(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_recover_foe_bare") public static void afterFoeBare(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "agenda_recover_foe_armed") public static void afterFoeArmed(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Hurt, with nothing to eat and nothing to heal him: the try for the things is spent at once, with the reason, and he does not set out. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_hurt", timeoutTicks = 600)
    public static void aHurtBodyWithNothingToMendHimDoesNotGoForHisThings(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setHealth(HURT);
        BlockPos place = lieAt(h, player);
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.deathPlace() == null, "The try for the things was not made");
            h.assertTrue(c.autonomy().said().stream().noneMatch(line -> line.contains("Иду за своими вещами")),
                    "He set out for his things hurt: " + c.autonomy().said());
            h.assertTrue(c.autonomy().said().stream().anyMatch(line -> line.contains("ранен")), "The reason was not said: " + c.autonomy().said());
        });
    }

    /** Hurt, with food: he waits and mends, and goes for his things only at fit health. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_mend", timeoutTicks = 3000)
    public static void aHurtBodyWithFoodMendsBeforeGoingForHisThings(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setItem(0, new ItemStack(Items.COOKED_BEEF, 16));
        c.setHealth(HURT);
        BlockPos place = lieAt(h, player);
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        float[] healthAtTheTry = {-1};
        h.onEachTick(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            if (healthAtTheTry[0] < 0 && goal != null && goal.deathPlace() == null) healthAtTheTry[0] = c.getHealth();
        });
        h.succeedWhen(() -> {
            h.assertTrue(healthAtTheTry[0] >= 0, "The try for the things was never made: " + c.getHealth());
            h.assertTrue(healthAtTheTry[0] >= 18, "He went for his things at health " + healthAtTheTry[0]);
        });
    }

    /** His things lie in a cave the mine remembers as dangerous: he does not walk into it, the try is spent and the place is said, so the things are not lost silently. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_cave", timeoutTicks = 600)
    public static void hisThingsInACaveTheMineShunsAreNotFetched(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setHealth(c.getMaxHealth());
        BlockPos place = lieAt(h, player);
        c.mineSite().start(place, net.minecraft.core.Direction.NORTH);
        c.mineSite().shun(java.util.List.of(place));
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.deathPlace() == null, "The try for the things was not made");
            h.assertTrue(c.autonomy().said().stream().noneMatch(line -> line.contains("Иду за своими вещами")),
                    "He set out into the cave for his things: " + c.autonomy().said());
            h.assertTrue(c.autonomy().said().stream().anyMatch(line -> line.contains("пещер") && line.contains(place.toShortString())), "The reason was not said: " + c.autonomy().said());
        });
    }

    /** A zombie stands by his things and he has no weapon: going there is a second death, so the try is spent and the reason is said. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_foe_bare", timeoutTicks = 600)
    public static void withoutAWeaponHeDoesNotGoForThingsGuardedByAZombie(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setHealth(c.getMaxHealth());
        BlockPos place = lieAt(h, player);
        guardWithAZombie(h);
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.deathPlace() == null, "The try for the things was not made");
            h.assertTrue(c.autonomy().said().stream().noneMatch(line -> line.contains("Иду за своими вещами")),
                    "He set out for his things with no weapon: " + c.autonomy().said());
            h.assertTrue(c.autonomy().said().stream().anyMatch(line -> line.contains("мобы") && line.contains(place.toShortString())), "The reason was not said: " + c.autonomy().said());
        });
    }

    /** The same zombie and a sword in the bag: he goes, the sword is what the fight is done with. */
    @GameTest(template = "tall_tree", batch = "agenda_recover_foe_armed", timeoutTicks = 600)
    public static void withASwordHeGoesForThingsGuardedByAZombie(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        c.setItem(0, new ItemStack(Items.IRON_SWORD));
        c.setHealth(c.getMaxHealth());
        BlockPos place = lieAt(h, player);
        guardWithAZombie(h);
        start(h, player);
        CompanionAgenda.died(h.getLevel().getServer(), player.getUUID(), h.getLevel().dimension(), place);
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.deathPlace() == null, "The try for the things was not made");
            h.assertTrue(c.autonomy().said().stream().anyMatch(line -> line.contains("Иду за своими вещами")), "He did not set out: " + c.autonomy().said());
        });
    }

    /** No bench in the bag and no tree in reach: the mine goal stops at the first search that finds no tree and says so (about 600 ticks here; one more search for each part and each step took about 1700). */
    @GameTest(template = "tall_tree", batch = "agenda_no_wood", timeoutTicks = 1200)
    public static void aGoalWithNoBenchAndNoTreeStopsWithTheReason(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 1; y <= 40; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        c.setHealth(c.getMaxHealth());
        // The only tree is walled up in bedrock: he sees it and has no way to it.
        for (int x = 22; x <= 26; x++) for (int z = 22; z <= 26; z++) for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        for (int y = 2; y <= 4; y++) { h.setBlock(new BlockPos(24, y, 24), Blocks.OAK_LOG); h.setBlock(new BlockPos(24, y, 23), Blocks.AIR); }
        h.setBlock(new BlockPos(24, 5, 24), Blocks.OAK_LEAVES);
        var result = CompanionEvents.executeAction(player, "pursue_goal", "find_diamonds", 1);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.state() == CompanionAgenda.State.BLOCKED, "The goal did not stop by tick " + h.getTick() + ": " + (goal == null ? null : goal.state()));
            h.assertTrue(goal.reason().contains("дерев"), "Stopped for another reason: " + goal.reason());
        });
    }

    /** The iron lies below the reach of the ore search and the iron pickaxe is gone (a death): he digs down with the stone one to where the search sees it, instead of standing and stopping the goal. */
    @GameTest(template = "tall_tree", batch = "agenda_descend", timeoutTicks = 6000)
    public static void ironBelowTheOreSearchIsSoughtByDiggingDown(GameTestHelper h) {
        var player = survivalPlayer(h);
        var c = body(h, player);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = 1; y <= 14; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        for (int x = 15; x <= 17; x++) for (int z = 15; z <= 17; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.IRON_ORE);
        BlockPos stand = h.absolutePos(new BlockPos(16, 15, 16));
        c.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        c.setHealth(c.getMaxHealth());
        c.setItem(0, new ItemStack(Items.STONE_PICKAXE, 2)); c.setItem(1, new ItemStack(Items.COBBLESTONE, 40)); c.setItem(2, new ItemStack(Items.TORCH, 24));
        c.setItem(3, new ItemStack(Items.STONE_SWORD)); c.setItem(4, new ItemStack(Items.CRAFTING_TABLE)); c.setItem(5, new ItemStack(Items.STICK, 8));
        c.setItem(6, new ItemStack(Items.BREAD, 8)); c.setItem(7, new ItemStack(Items.COAL, 8));
        var result = CompanionEvents.executeAction(player, "pursue_goal", "find_diamonds", 1);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
        double[] lowest = {c.getY()};
        h.onEachTick(() -> lowest[0] = Math.min(lowest[0], c.getY()));
        h.succeedWhen(() -> {
            var goal = CompanionAgenda.goal(h.getLevel().getServer(), player.getUUID());
            h.assertTrue(goal != null && goal.state() != CompanionAgenda.State.BLOCKED, "The goal stopped on the surface: " + (goal == null ? null : goal.reason()));
            h.assertTrue(lowest[0] <= stand.getY() - 6, "He did not go down: lowest " + lowest[0] + ", started at " + stand.getY());
        });
    }

    private static void start(GameTestHelper h, ServerPlayer player) {
        var result = CompanionEvents.executeAction(player, "pursue_goal", "gear_up", 1);
        h.assertTrue(result.status() == CompanionActionResult.Status.ACCEPTED, "Goal not accepted: " + result.message());
    }

    /** His things at the death place, tagged as the owner's, as they fall from a dead body. */
    private static BlockPos lieAt(GameTestHelper h, ServerPlayer player) {
        BlockPos place = h.absolutePos(new BlockPos(3, 1, 3));
        ItemEntity item = new ItemEntity(h.getLevel(), place.getX() + .5, place.getY(), place.getZ() + .5, new ItemStack(Items.IRON_INGOT, 3));
        item.getPersistentData().putUUID("aicompanion:belongings", player.getUUID());
        h.getLevel().addFreshEntity(item);
        return place;
    }

    /** A helmeted zombie beside the place, so the sun does not take it before the try is made. */
    private static void guardWithAZombie(GameTestHelper h) {
        var zombie = h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 4, 1, 3);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.setPersistenceRequired();
    }

    private static CompanionEntity body(GameTestHelper h, ServerPlayer player) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        var c = h.spawn(AICompanionMod.COMPANION.get(), 16, 1, 16);
        player.teleportTo(c.getX() - 5, c.getY(), c.getZ() - 5);
        c.setOwnerId(player.getUUID()); c.setSearchRadius(16); c.setLifestyle(false, false);
        player.getPersistentData().putUUID("aicompanion:canonical_companion", c.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        return c;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setInvulnerable(true);
        return p;
    }
}
