package dev.aicompanion.gametest;

import com.google.gson.JsonParser;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * «Открой портал Края»: he puts eyes of ender of his own into the empty frames of an End portal near him, as a player does,
 * standing outside the ring. The ring is built here block by block (no stronghold in the flat world of a GameTest).
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionEndPortalTests {
    /**
     * The ring's corner of least x and z; the frames stand on the border of the five by five from here. In the middle of the
     * widest arena: he looks for frames 32 blocks round, and the rings of the tests beside this one are farther off than that.
     */
    private static final int X = 66, Z = 62;

    /** The room of a stronghold: the frames hang two blocks over a pool of lava. Twelve eyes: every frame gets one, the portal opens. */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 2400)
    public static void twelveEyesOfHisOpenThePortalOverTheLava(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 3, true);
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 12));
        CompanionActionResult started = CompanionEvents.executeAction(owner(h, companion), "open_end_portal", "", 1);
        h.assertTrue(started.success(), "Order refused: " + started.message());
        h.onEachTick(() -> h.assertTrue(outside(h, companion), "He stands in the ring: " + companion.blockPosition()));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Вставил 12 очей") && companion.getLastTaskResult().contains("Портал Края открыт"), companion.getLastTaskResult());
            h.assertTrue(eyes(h, frames) == 12 && portal(h, 3) == 9, "Eyes in the frames " + eyes(h, frames) + ", portal blocks " + portal(h, 3));
            h.assertTrue(count(companion) == 0, "Eyes left in the bag: " + count(companion));
        });
    }

    /**
     * A stronghold's spawner stands on the top step before the ring, two blocks off its frames: as long as a player is near, it lets
     * out silverfish. He breaks it with the pickaxe of his bag before the first eye, then opens the portal.
     */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 2400)
    public static void theSpawnerBeforeTheRingIsBrokenFirst(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 3, true);
        BlockPos spawner = spawner(h, 3);
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 12));
        companion.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "open_end_portal", "", 1).success(), "Order refused: " + companion.getLastTaskResult());
        h.onEachTick(() -> h.assertTrue(eyes(h, frames) == 0 || !h.getBlockState(spawner).is(Blocks.SPAWNER), "An eye was put in with the spawner standing"));
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Сломал спаунер") && companion.getLastTaskResult().contains("Портал Края открыт"), companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(spawner).isAir() && eyes(h, frames) == 12 && portal(h, 3) == 9, "Spawner " + h.getBlockState(spawner) + ", eyes in the frames " + eyes(h, frames));
        });
    }

    /** No pickaxe: the spawner is broken by hand, as slowly as a player breaks it (25 seconds). */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 2400)
    public static void withNoPickaxeTheSpawnerIsBrokenByHand(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 1, false);
        BlockPos spawner = spawner(h, 1);
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 12));
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "open_end_portal", "", 1).success(), "Order refused: " + companion.getLastTaskResult());
        h.succeedWhen(() -> {
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Сломал спаунер") && companion.getLastTaskResult().contains("Портал Края открыт"), companion.getLastTaskResult());
            h.assertTrue(h.getBlockState(spawner).isAir() && eyes(h, frames) == 12 && portal(h, 1) == 9, "Spawner " + h.getBlockState(spawner) + ", eyes in the frames " + eyes(h, frames));
        });
    }

    /**
     * Lava let out on the floor by the ring's south side: he stands on no cell with lava in it or beside it. Every frame he can come
     * to from a dry cell gets its eye, the east, west and north ones at least; the rest are left and he says why (in game 24.11 he
     * gave up at the first frame he could not come to, the others empty).
     */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 2400)
    public static void lavaOnTheFloorItsSideIsLeftAndTheOthersAreFilled(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 1, false);
        for (int x = 1; x <= 3; x++) h.setBlock(new BlockPos(X + x, 1, Z + 6), Blocks.LAVA);
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 12));
        int[] seen = {0};
        h.onEachTick(() -> {
            BlockPos feet = companion.blockPosition();
            boolean wet = companion.level().getFluidState(feet).is(net.minecraft.tags.FluidTags.LAVA);
            for (Direction side : Direction.Plane.HORIZONTAL) wet |= companion.level().getFluidState(feet.relative(side)).is(net.minecraft.tags.FluidTags.LAVA);
            h.assertTrue(eyes(h, frames) == seen[0] || !wet, "An eye was put in from a cell by lava: " + feet);
            seen[0] = eyes(h, frames);
        });
        // The lava runs its three blocks first.
        h.runAfterDelay(100, () -> h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "open_end_portal", "", 1).success(), "Order refused: " + companion.getLastTaskResult()));
        h.succeedWhen(() -> {
            h.assertTrue(seen[0] > 0, "Not begun");
            done(h, companion);
            int in = eyes(h, frames);
            h.assertTrue(in >= 9 && in < 12 && count(companion) == 12 - in, "Eyes in the frames " + in + ", in the bag " + count(companion));
            h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("У рамки лава — не подойти. Вставил " + in + " ")
                    && companion.getLastTaskResult().contains("пустых рамок ещё " + (12 - in)), companion.getLastTaskResult());
        });
    }

    /**
     * A ring a player set on the ground, two eyes in it already; five in his bag: he puts in the five and says how many are
     * missing. Given five more and told again he goes on with the empty ones. The order finds him standing in the ring: he
     * steps out of it first and puts no eye in from there, or the portal that opens in the ring would take him to the End.
     */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 2400)
    public static void tooFewEyesHeSaysHowManyAreMissingAndGoesOnWhenGivenMore(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 1, false);
        for (int i = 0; i < 2; i++) h.setBlock(frames.get(i), h.getBlockState(frames.get(i)).setValue(EndPortalFrameBlock.HAS_EYE, true));
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 5));
        BlockPos middle = h.absolutePos(new BlockPos(X + 2, 1, Z + 2));
        companion.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + .5);
        h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "open_end_portal", "", 1).success(), "Order refused: " + companion.getLastTaskResult());
        boolean[] again = {false};
        int[] seen = {2};
        h.onEachTick(() -> {
            h.assertTrue(eyes(h, frames) == seen[0] || outside(h, companion), "An eye was put in from inside the ring: he stands at " + companion.blockPosition());
            seen[0] = eyes(h, frames);
        });
        h.succeedWhen(() -> {
            done(h, companion);
            if (!again[0]) {
                h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Вставил 5 очей, не хватает ещё 5"), companion.getLastTaskResult());
                h.assertTrue(eyes(h, frames) == 7 && portal(h, 1) == 0 && count(companion) == 0, "Eyes in the frames " + eyes(h, frames));
                companion.setItem(0, new ItemStack(Items.ENDER_EYE, 6));
                again[0] = true;
                h.assertTrue(CompanionEvents.executeAction(owner(h, companion), "open_end_portal", "", 1).success(), "The second order refused: " + companion.getLastTaskResult());
            }
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Вставил 5 очей") && companion.getLastTaskResult().contains("Портал Края открыт"), companion.getLastTaskResult());
            h.assertTrue(eyes(h, frames) == 12 && portal(h, 1) == 9 && count(companion) == 1, "Eyes in the frames " + eyes(h, frames) + ", in the bag " + count(companion));
            h.assertTrue(companion.isAlive() && companion.level() == h.getLevel(), "The portal took him");
        });
    }

    /** «Стоп» with three eyes in: he stops, the eyes stay in the frames and the rest in his bag; told again he fills the empty ones. */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 2400)
    public static void stoppedHalfWayTheEyesStayAndTheNextOrderGoesOn(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 3, true);
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 12));
        ServerPlayer player = owner(h, companion);
        h.assertTrue(CompanionEvents.executeAction(player, "open_end_portal", "", 1).success(), "Order refused: " + companion.getLastTaskResult());
        int[] stoppedAt = {-1};
        h.onEachTick(() -> {
            if (stoppedAt[0] >= 0 || eyes(h, frames) < 3) return;
            CompanionEvents.executeAction(player, "stop", "", 1);
            stoppedAt[0] = eyes(h, frames);
            h.assertTrue(!companion.isWorking() && stoppedAt[0] < 12 && count(companion) == 12 - stoppedAt[0], "At «стоп»: in the frames " + stoppedAt[0] + ", in the bag " + count(companion));
            h.assertTrue(CompanionEvents.executeAction(player, "open_end_portal", "", 1).success(), "The second order refused: " + companion.getLastTaskResult());
        });
        h.succeedWhen(() -> {
            h.assertTrue(stoppedAt[0] >= 0, "Not stopped yet");
            done(h, companion);
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Портал Края открыт"), companion.getLastTaskResult());
            h.assertTrue(eyes(h, frames) == 12 && portal(h, 3) == 9 && count(companion) == 0, "Eyes in the frames " + eyes(h, frames) + ", in the bag " + count(companion));
        });
    }

    /**
     * One frame is empty and the player stands in the ring: the last eye would open the portal under him. He is told to step out,
     * the eye stays in the bag; with the player out of the ring the next order opens the portal.
     */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 1200)
    public static void theLastEyeWaitsForThePlayerToLeaveTheRing(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        List<BlockPos> frames = ring(h, 1, false);
        for (int i = 1; i < 12; i++) h.setBlock(frames.get(i), h.getBlockState(frames.get(i)).setValue(EndPortalFrameBlock.HAS_EYE, true));
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 2));
        ServerPlayer player = owner(h, companion);
        BlockPos middle = h.absolutePos(new BlockPos(X + 2, 1, Z + 2)), away = h.absolutePos(new BlockPos(X - 6, 1, Z - 6));
        player.teleportTo(middle.getX() + .5, middle.getY(), middle.getZ() + .5);
        h.assertTrue(CompanionEvents.executeAction(player, "open_end_portal", "", 1).success(), "Order refused: " + companion.getLastTaskResult());
        boolean[] again = {false};
        h.succeedWhen(() -> {
            done(h, companion);
            if (!again[0]) {
                h.assertTrue(!companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Выйди из кольца"), companion.getLastTaskResult());
                h.assertTrue(eyes(h, frames) == 11 && portal(h, 1) == 0 && count(companion) == 2, "Eyes in the frames " + eyes(h, frames) + ", in the bag " + count(companion));
                h.assertTrue(player.level() == h.getLevel(), "The player was sent to the End");
                player.teleportTo(away.getX() + .5, away.getY(), away.getZ() + .5);
                again[0] = true;
                h.assertTrue(CompanionEvents.executeAction(player, "open_end_portal", "", 1).success(), "The second order refused: " + companion.getLastTaskResult());
            }
            h.assertTrue(companion.didLastTaskSucceed() && companion.getLastTaskResult().contains("Портал Края открыт"), companion.getLastTaskResult());
            h.assertTrue(eyes(h, frames) == 12 && portal(h, 1) == 9 && count(companion) == 1, "Eyes in the frames " + eyes(h, frames) + ", in the bag " + count(companion));
        });
    }

    /** No frame near, no eye, a portal that is open already: said at the order, nothing is spent. The phrases and the plan's schema. */
    @GameTest(template = "large_search", skyAccess = true)
    public static void refusalsAndPhrases(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        ServerPlayer player = owner(h, companion);
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 12));
        CompanionActionResult none = CompanionEvents.executeAction(player, "open_end_portal", "", 1);
        h.assertTrue(!none.success() && none.message().contains("Не вижу рядом рамки портала Края"), "With no frame: " + none.message());
        List<BlockPos> frames = ring(h, 1, false);
        companion.setItem(0, ItemStack.EMPTY);
        CompanionActionResult noEye = CompanionEvents.executeAction(player, "open_end_portal", "", 1);
        h.assertTrue(!noEye.success() && noEye.message().contains("Нет ока Края") && !companion.isWorking(), "With no eye: " + noEye.message());
        // Every frame with an eye and no portal (a ring set wrong): no success is said, no eye is spent.
        for (BlockPos frame : frames) h.setBlock(frame, h.getBlockState(frame).setValue(EndPortalFrameBlock.HAS_EYE, true));
        companion.setItem(0, new ItemStack(Items.ENDER_EYE, 3));
        CompanionActionResult wrong = CompanionEvents.executeAction(player, "open_end_portal", "", 1);
        h.assertTrue(!wrong.success() && wrong.message().contains("рамка собрана не так") && !companion.isWorking() && count(companion) == 3, "With a ring full of eyes and no portal: " + wrong.message());
        // A portal of the End near and none of the Nether: «зайди в портал» is not an order to go to the End alone.
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) h.setBlock(new BlockPos(X + x, 1, Z + z), Blocks.END_PORTAL);
        CompanionActionResult open = CompanionEvents.executeAction(player, "open_end_portal", "", 1);
        h.assertTrue(open.success() && open.message().contains("уже открыт") && !companion.isWorking() && count(companion) == 3, "With the portal open: " + open.message());
        BlockPos beside = h.absolutePos(new BlockPos(X - 2, 1, Z + 2));
        companion.teleportTo(beside.getX() + .5, beside.getY(), beside.getZ() + .5);
        CompanionActionResult enter = CompanionEvents.executeAction(player, "enter_portal", "", 1);
        h.assertTrue(!enter.success() && enter.message().contains("В Край первым не пойду"), "«Зайди в портал» by the End portal: " + enter.message());

        for (String phrase : List.of("Олег, открой портал Края", "активируй портал в Энд", "вставь очи", "поставь очи в рамки", "вставь очи края в портал")) {
            String routed = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue(routed != null && routed.contains("open_end_portal"), "«" + phrase + "» → " + routed);
        }
        // A frame of the Nether's may stand by unlit: the bare phrase is the model's to read.
        for (String phrase : List.of("открой портал", "открой портал в незер", "вставь очи и иди за мной")) {
            String routed = CompanionLocalCommands.route(phrase, "Олег");
            h.assertTrue(routed == null || !routed.contains("open_end_portal"), "«" + phrase + "» → " + routed);
        }
        h.assertTrue("open_end_portal".equals(CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"open_end_portal\"}]")).getFirst().type()), "The plan's step is not read");
        boolean refused;
        try { CompanionPlanCodec.parsePlan(JsonParser.parseString("[{\"action\":\"open_end_portal\",\"item\":\"minecraft:ender_eye\",\"count\":3}]")); refused = false; } catch (IllegalStateException invalid) { refused = true; }
        h.assertTrue(refused, "A count and an item are taken");
        // What the player hears of the eyes that are missing is the number, not a general «не хватает материалов».
        String heard = dev.aicompanion.client.CompanionDialogue.summarize(List.of(new dev.aicompanion.client.PlannedAction("open_end_portal", 0, 1)),
                "План остановлен: Вставил 5 очей, не хватает ещё 5 — дай мне или скажи сделать.");
        h.assertTrue(heard.contains("Вставил 5 очей") && heard.contains("ещё 5"), "The player hears: " + heard);
        h.succeed();
    }

    /**
     * A hit silverfish wakes the infested blocks round it (ten blocks across, five up and down) and they crumble: in a stronghold's
     * portal room that lets the pool's lava out on the floor (twice his death in game, 24.11). By the player, with lava six blocks
     * off, he leaves it alone (it goes for no one who did not hit it); the lava gone, he meets it as any monster at the player's side.
     */
    @GameTest(template = "large_search", skyAccess = true, timeoutTicks = 600)
    public static void aSilverfishByLavaIsLeftAloneAndOneAwayFromItIsFought(GameTestHelper h) {
        CompanionEntity companion = setup(h);
        var silverfish = h.spawn(net.minecraft.world.entity.EntityType.SILVERFISH, 54, 1, 62);
        silverfish.setNoAi(true);
        BlockPos lava = new BlockPos(60, 1, 62);
        h.setBlock(lava, Blocks.LAVA);
        for (Direction side : Direction.Plane.HORIZONTAL) h.setBlock(lava.relative(side), Blocks.BEDROCK);
        float whole = silverfish.getMaxHealth();
        h.onEachTick(() -> {
            if (h.getBlockState(lava).is(Blocks.LAVA))
                h.assertTrue(companion.getTarget() != silverfish && silverfish.getHealth() == whole, "A silverfish by lava was fought: health " + silverfish.getHealth());
        });
        h.runAfterDelay(120, () -> h.setBlock(lava, Blocks.BEDROCK));
        h.succeedWhen(() -> h.assertTrue(!silverfish.isAlive() || silverfish.getHealth() < whole, "The silverfish with no lava near is not fought"));
    }

    /**
     * The twelve frames at this height, turned inward as a player sets them, none with an eye. Over lava they are a stronghold's:
     * a ring of stone with a pool in it on the floor, the frames two blocks over it. On the ground the floor under the ring is whole.
     */
    private static List<BlockPos> ring(GameTestHelper h, int y, boolean lava) {
        List<BlockPos> frames = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            frames.add(frame(h, X + i, y, Z, Direction.SOUTH)); frames.add(frame(h, X + i, y, Z + 4, Direction.NORTH));
            frames.add(frame(h, X, y, Z + i, Direction.EAST)); frames.add(frame(h, X + 4, y, Z + i, Direction.WEST));
        }
        if (lava) for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++)
            h.setBlock(new BlockPos(X + x, 1, Z + z), x == 0 || x == 4 || z == 0 || z == 4 ? Blocks.STONE_BRICKS : Blocks.LAVA);
        return frames;
    }

    /** A spawner where a stronghold has it: in the middle before the ring's north side, two blocks off, at the frames' height, on stone. */
    private static BlockPos spawner(GameTestHelper h, int y) {
        BlockPos spawner = new BlockPos(X + 2, y, Z - 2);
        for (int below = 1; below < y; below++) h.setBlock(spawner.atY(below), Blocks.STONE_BRICKS);
        h.setBlock(spawner, Blocks.SPAWNER);
        return spawner;
    }

    private static BlockPos frame(GameTestHelper h, int x, int y, int z, Direction facing) {
        BlockPos pos = new BlockPos(x, y, z);
        h.setBlock(pos, Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, facing));
        return pos;
    }

    private static int eyes(GameTestHelper h, List<BlockPos> frames) {
        return (int) frames.stream().filter(frame -> h.getBlockState(frame).is(Blocks.END_PORTAL_FRAME) && h.getBlockState(frame).getValue(EndPortalFrameBlock.HAS_EYE)).count();
    }

    private static int portal(GameTestHelper h, int y) {
        int result = 0;
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) if (h.getBlockState(new BlockPos(X + x, y, Z + z)).is(Blocks.END_PORTAL)) result++;
        return result;
    }

    /** Not over the five by five of the ring, at any height. */
    private static boolean outside(GameTestHelper h, CompanionEntity companion) {
        BlockPos at = companion.blockPosition().subtract(h.absolutePos(BlockPos.ZERO));
        return at.getX() < X || at.getX() > X + 4 || at.getZ() < Z || at.getZ() > Z + 4;
    }

    private static CompanionEntity setup(GameTestHelper h) {
        for (int x = 44; x < 84; x++) for (int z = 44; z < 84; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
        CompanionEntity companion = h.spawn(AICompanionMod.COMPANION.get(), 56, 1, 64);
        var player = h.makeMockServerPlayerInLevel();
        player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ() - 3);
        companion.setOwnerId(player.getUUID());
        player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player);
        companion.setSearchRadius(16);
        return companion;
    }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    private static void done(GameTestHelper h, CompanionEntity companion) {
        h.assertTrue(!companion.isGatheringTask() && !companion.isWorking(), "Work still active: " + companion.getLastTaskResult());
    }

    private static int count(CompanionEntity companion) {
        int result = 0;
        boolean inBag = false;
        ItemStack held = companion.getMainHandItem();
        for (int slot = 0; slot < companion.getContainerSize(); slot++) {
            if (companion.getItem(slot).is(Items.ENDER_EYE)) result += companion.getItem(slot).getCount();
            inBag |= companion.getItem(slot) == held;
        }
        // The stack he holds may have left the bag for his hand.
        return result + (!inBag && held.is(Items.ENDER_EYE) ? held.getCount() : 0);
    }
}
