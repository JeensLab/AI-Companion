package dev.aicompanion.entity;

import dev.aicompanion.AICompanionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * «Веди к крепости» (walk_heading stronghold): he throws an eye of ender of his own, as a player does, and leads the player where
 * it flew. Here beside CompanionCourse, not in gametest/: the flat world of a GameTest has no stronghold, so the tests put a
 * point in the course's own search, and the second throw comes sooner than in a game (the arena is 128 blocks).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionEyeCourseTests {
    private static final int START_X = 20, LINE = 64, FAR = 80;

    /** Three eyes in his bag: one for the heading, one forty blocks on, one over the place, where it goes down; he stands over it. */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 3000)
    public static void theEyeLeadsHimToTheStrongholdInThreeThrows(GameTestHelper h) {
        CompanionEntity c = arena(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        c.setItem(0, new ItemStack(Items.ENDER_EYE, 3));
        Set<UUID> thrown = new HashSet<>();
        h.onEachTick(() -> {
            player.teleportTo(c.getX() - 3, c.getY(), c.getZ());
            for (EyeOfEnder eye : h.getLevel().getEntitiesOfClass(EyeOfEnder.class, c.getBoundingBox().inflate(40))) thrown.add(eye.getUUID());
        });
        var accepted = CompanionEvents.executeAction(player, "walk_heading", "stronghold", 0);
        h.assertTrue(accepted.success() && accepted.message().contains("к крепости"), "Not accepted: " + accepted.message());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            BlockPos at = c.blockPosition().subtract(h.absolutePos(BlockPos.ZERO));
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && !c.isWorking(), "Still walking: " + c.getLastTaskResult() + " at " + at.toShortString() + ", thrown " + thrown.size());
            h.assertTrue(result.success() && result.message().contains("под нами"), result.message());
            h.assertTrue(Math.hypot(at.getX() - (START_X + FAR), at.getZ() - LINE) <= 8, "Not over the place: " + at.toShortString());
            h.assertTrue(thrown.size() == 3, "Eyes thrown: " + thrown.size());
            // Four in five fall back: every one that did is in his bag again, none left lying.
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, c.getBoundingBox().inflate(100), item -> item.getItem().is(Items.ENDER_EYE)).isEmpty(),
                    "An eye of his was left lying; he carries " + c.work.items.countCarried(Items.ENDER_EYE));
        });
    }

    /** With no eye he does not start. With one that breaks in the air (one in five does) he stops at once and says where it flew. */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 600)
    public static void hisOnlyEyeBrokenHeStopsAndSaysWhereItFlew(GameTestHelper h) {
        CompanionEntity c = arena(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        var none = CompanionEvents.executeAction(player, "walk_heading", "stronghold", 0);
        h.assertTrue(!none.success() && none.message().contains("Нет ока") && !c.isWorking(), "With no eye: " + none.message());
        c.setItem(0, new ItemStack(Items.ENDER_EYE));
        h.onEachTick(() -> h.getLevel().getEntitiesOfClass(EyeOfEnder.class, c.getBoundingBox().inflate(40), eye -> eye.tickCount > 20).forEach(Entity::discard));
        h.assertTrue(CompanionEvents.executeAction(player, "walk_heading", "stronghold", 0).success(), "Not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && !c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(!result.success() && result.message().contains("разбилось") && result.message().contains("восток"), result.message());
        });
    }

    /** «Стоп» while his eye is in the air: it is still his, and he picks it up where it falls (four in five do). */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 800)
    public static void hisEyeInTheAirAtStopIsPickedUpAllTheSame(GameTestHelper h) {
        CompanionEntity c = arena(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        c.setItem(0, new ItemStack(Items.ENDER_EYE));
        h.assertTrue(CompanionEvents.executeAction(player, "walk_heading", "stronghold", 0).success(), "Not accepted: " + c.getLastTaskResult());
        boolean[] stopped = {false};
        h.onEachTick(() -> {
            if (stopped[0] || h.getLevel().getEntitiesOfClass(EyeOfEnder.class, c.getBoundingBox().inflate(40), eye -> eye.tickCount > 20).isEmpty()) return;
            stopped[0] = true;
            CompanionEvents.executeAction(player, "stop", "", 1);
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopped[0] && !c.isWorking(), "Not stopped yet: " + c.getLastTaskResult());
            h.assertTrue(h.getLevel().getEntitiesOfClass(EyeOfEnder.class, c.getBoundingBox().inflate(40)).isEmpty(), "The eye is still in the air");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, c.getBoundingBox().inflate(100), item -> item.getItem().is(Items.ENDER_EYE)).isEmpty(),
                    "His eye was left lying after «стоп»");
        });
    }

    /** «Стоп» while his eye is in the air and another order at once (he walks north, away from it): the eye is his all the same. */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 800)
    public static void hisEyeInTheAirAtStopAndANewOrderIsPickedUpAllTheSame(GameTestHelper h) {
        CompanionEntity c = arena(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        c.setItem(0, new ItemStack(Items.ENDER_EYE));
        h.assertTrue(CompanionEvents.executeAction(player, "walk_heading", "stronghold", 0).success(), "Not accepted: " + c.getLastTaskResult());
        long[] walk = {0};
        h.onEachTick(() -> {
            player.teleportTo(c.getX() - 3, c.getY(), c.getZ());
            if (walk[0] != 0 || h.getLevel().getEntitiesOfClass(EyeOfEnder.class, c.getBoundingBox().inflate(40), eye -> eye.tickCount > 20).isEmpty()) return;
            CompanionEvents.executeAction(player, "stop", "", 1);
            var north = CompanionEvents.executeAction(player, "walk_heading", "north", 30);
            h.assertTrue(north.success(), "The walk north is not accepted: " + north.message());
            walk[0] = c.activeWorkId();
        });
        h.succeedWhen(() -> {
            h.assertTrue(walk[0] != 0 && c.workResult(walk[0]) != null && !c.isWorking(), "Still walking: " + c.getLastTaskResult());
            h.assertTrue(h.getLevel().getEntitiesOfClass(EyeOfEnder.class, c.getBoundingBox().inflate(60)).isEmpty(), "The eye is still in the air");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, c.getBoundingBox().inflate(100), item -> item.getItem().is(Items.ENDER_EYE)).isEmpty(),
                    "His eye was left lying after «стоп» and a new order");
        });
    }

    /**
     * His only eye falls on the leaves of a tree, six blocks over his head: he does not go up for it (nor into water, nor down a pit:
     * in game he went down a cave after one), it is not claimed as his pickup, and he says he could not take it.
     */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 800)
    public static void anEyeFallenOutOfHandIsLeftWhereItLies(GameTestHelper h) {
        CompanionEntity c = arena(h);
        ServerPlayer player = h.getLevel().getServer().getPlayerList().getPlayer(c.getOwnerId());
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int x = START_X + 8; x <= START_X + 16; x++) for (int z = LINE - 4; z <= LINE + 4; z++) h.setBlock(new BlockPos(x, 8, z), leaves);
        c.setItem(0, new ItemStack(Items.ENDER_EYE));
        h.assertTrue(CompanionEvents.executeAction(player, "walk_heading", "stronghold", 0).success(), "Not accepted: " + c.getLastTaskResult());
        long id = c.activeWorkId();
        h.succeedWhen(() -> {
            CompanionActionResult result = c.workResult(id);
            h.assertTrue(result != null && !c.isWorking(), "Still walking: " + c.getLastTaskResult());
            var lying = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, c.getBoundingBox().inflate(40), item -> item.getItem().is(Items.ENDER_EYE));
            h.assertTrue(lying.stream().noneMatch(item -> item.getPersistentData().contains("aicompanion:harvester")), "The eye on the leaves was claimed for his pickup");
            h.assertTrue(!result.success() && result.message().contains(lying.isEmpty() ? "разбилось" : "подобрать не смог") && result.message().contains("восток"), result.message());
        });
    }

    /** A stone floor over bedrock with a bedrock border; he stands at (START_X, 2, LINE), the player behind him; the stronghold is FAR blocks east of where it is asked for. */
    private static CompanionEntity arena(GameTestHelper h) {
        CompanionCourse.stronghold = (level, from) -> level.dimension() == Level.OVERWORLD ? from.offset(FAR, 0, 0) : null;
        CompanionCourse.secondThrow = 40;
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            boolean border = x == 0 || x == 127 || z == 0 || z == 127;
            h.setBlock(new BlockPos(x, 1, z), border ? Blocks.BEDROCK : Blocks.STONE);
            if (border) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK);
        }
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), START_X, 2, LINE);
        var owner = h.makeMockServerPlayerInLevel();
        BlockPos stand = h.absolutePos(new BlockPos(START_X - 3, 2, LINE));
        owner.teleportTo(stand.getX() + .5, stand.getY(), stand.getZ() + .5);
        companion.setOwnerId(owner.getUUID());
        owner.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(owner);
        companion.setSearchRadius(60);
        return companion;
    }
}
