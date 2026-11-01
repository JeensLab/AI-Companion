package dev.aicompanion.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionLocalCommands;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.entity.CompanionActionResult;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Issue #71: «веди на восток» — he leads the player along the heading, goes round what stands in the way and back to the line,
 * stops before lava and wide water, waits for a player left behind, and stops at what was looked for. The arenas are open to
 * the sky: he finds the ground of the way by the height map, which a barrier roof over the test would hide.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCourseTests {
    /** He starts here (relative), heading east along z = LINE. */
    private static final int START_X = 20, LINE = 64;

    @GameTest(template = "large_search", timeoutTicks = 1600, skyAccess = true)
    public static void walksTheBlocksAskedAndHoldsTheLine(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        long id = lead(h, companion, "east", 40, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(result.success() && result.message().contains("40 блоков на восток"), result.message());
            BlockPos at = at(h, companion);
            h.assertTrue(at.getX() >= START_X + 36 && Math.abs(at.getZ() - LINE) <= 3, "Ended at " + at.toShortString());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 1600, skyAccess = true)
    public static void walksADiagonal(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        long id = lead(h, companion, "northeast", 30, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            BlockPos at = at(h, companion);
            int east = at.getX() - START_X, north = LINE - at.getZ();
            h.assertTrue(result.success() && result.message().contains("на северо-восток") && east + north >= 2 * 19 && Math.abs(east - north) <= 4,
                    result.message() + " at " + at.toShortString());
        });
    }

    /**
     * Game 01.11: north — the sea; back south — the sea; and so on. On a strip of land between two waters the second water is
     * said honestly (over water only, he fears it and cannot steer a boat), and the first one is not walked to again.
     */
    @GameTest(template = "large_search", timeoutTicks = 3000, skyAccess = true)
    public static void secondWaterIsSaidHonestlyAndNotWalkedBackAndForth(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        for (int x = 1; x < 127; x++) for (int z = 1; z < 127; z++) if (z < LINE - 10 || z > LINE + 10) h.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
        ServerPlayer player = owner(h, companion);
        h.onEachTick(() -> player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ()));
        long[] id = {0};
        java.util.List<CompanionActionResult> results = new java.util.ArrayList<>();
        h.onEachTick(() -> {
            if (results.size() >= 3) return;
            if (id[0] != 0) {
                CompanionActionResult result = companion.workResult(id[0]);
                if (result == null || companion.isWorking()) return;
                results.add(result); id[0] = 0;
            }
            String heading = results.size() == 1 ? "south" : "north";
            CompanionActionResult started = CompanionEvents.executeAction(player, "walk_heading", heading, 0);
            if (started.success()) id[0] = companion.activeWorkId(); else results.add(started);
        });
        h.succeedWhen(() -> {
            h.assertTrue(results.size() >= 3, "Results so far: " + results.stream().map(CompanionActionResult::message).toList());
            h.assertTrue(results.get(0).message().contains("большая вода") && !results.get(0).message().contains("лодкой"), results.get(0).message());
            h.assertTrue(results.get(1).message().contains("большая вода") && results.get(1).message().contains("лодкой"), results.get(1).message());
            h.assertTrue(!results.get(2).success() && results.get(2).message().contains("уже") && results.get(2).message().contains("лодкой"), results.get(2).message());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 2400, skyAccess = true)
    public static void goesRoundAWallAndBackToTheLine(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        for (int z = LINE - 14; z <= LINE + 14; z++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(40, y, z), Blocks.STONE);
        long id = lead(h, companion, "east", 50, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            BlockPos at = at(h, companion);
            h.assertTrue(result.success() && at.getX() >= START_X + 46 && Math.abs(at.getZ() - LINE) <= 3,
                    result.message() + " at " + at.toShortString());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 2400, skyAccess = true)
    public static void crossesABrook(GameTestHelper h) {
        CompanionEntity companion = arena(h, Blocks.WATER.defaultBlockState());
        for (int x = 42; x < 126; x++) for (int z = 1; z < 127; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        long id = lead(h, companion, "east", 40, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(result.success() && at(h, companion).getX() >= START_X + 36, result.message());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 2400, skyAccess = true)
    public static void stopsAtWideWater(GameTestHelper h) {
        CompanionEntity companion = arena(h, Blocks.WATER.defaultBlockState());
        long id = lead(h, companion, "east", 0, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("вода"), result.message());
            h.assertTrue(at(h, companion).getX() < 41, "Went into the water: " + at(h, companion).toShortString());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 2400, skyAccess = true)
    public static void offersAWayRoundALake(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        // The lake reaches farther south than north of his line: the nearer way round is to the north.
        for (int x = 40; x <= 110; x++) for (int z = 40; z <= 120; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
        long id = lead(h, companion, "east", 0, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("вода") && result.message().contains("Можно обойти")
                    && result.message().contains("на север, потом снова на восток"), result.message());
        });
    }

    /**
     * Game 01.11: on an island he stopped by the sea in the south, and the next order (look for a village) sent him south again.
     * Water all round is said as an island, and the same heading from the same shore is not walked again.
     */
    @GameTest(template = "large_search", timeoutTicks = 2400, skyAccess = true)
    public static void anIslandIsSaidAndTheSameWaterNotWalkedAgain(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        for (int x = 1; x < 127; x++) for (int z = 1; z < 127; z++)
            if (x < START_X - 6 || x > START_X + 6 || z < LINE - 6 || z > LINE + 6) h.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
        long id = lead(h, companion, "east", 0, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("острове"), result.message());
            CompanionActionResult again = CompanionEvents.executeAction(owner(h, companion), "walk_heading", "east:village", 0);
            h.assertTrue(!again.success() && again.message().contains("уже упёрлись") && !companion.isWorking(), again.message());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 2400, skyAccess = true)
    public static void stopsBeforeLava(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        for (int x = 40; x <= 44; x++) for (int z = 1; z < 127; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
        long id = lead(h, companion, "east", 0, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("лава"), result.message());
            h.assertTrue(at(h, companion).getX() < 40 && companion.getHealth() >= companion.getMaxHealth() && !companion.isOnFire(),
                    "Lava reached: " + at(h, companion).toShortString() + ", health " + companion.getHealth());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 3000, skyAccess = true)
    public static void waitsForAPlayerLeftBehind(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        ServerPlayer player = owner(h, companion);
        long id = lead(h, companion, "east", 0, false);
        double[] farthest = {0};
        java.util.Set<String> urged = new java.util.LinkedHashSet<>();
        h.onEachTick(() -> {
            farthest[0] = Math.max(farthest[0], Math.hypot(companion.getX() - player.getX(), companion.getZ() - player.getZ()));
            String said = companion.socialObservation();
            if (dev.aicompanion.CompanionConversation.isUrgent(said) && said.contains("отстал")) urged.add(said);
        });
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(!result.success() && result.message().contains("отстал"), result.message());
            h.assertTrue(farthest[0] <= 28, "Went " + farthest[0] + " blocks from the player");
            // Hurried on twice, in words of the model, before he gives up.
            h.assertTrue(urged.size() == 2, "Urged: " + urged);
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 600, skyAccess = true)
    public static void stopEndsTheWalk(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        long id = lead(h, companion, "east", 0, true);
        h.runAfterDelay(60, () -> CompanionEvents.executeAction(owner(h, companion), "stop", "", 1));
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(result.status() == CompanionActionResult.Status.CANCELLED, result.status() + ": " + result.message());
        });
    }

    @GameTest(template = "large_search", timeoutTicks = 1600, skyAccess = true)
    public static void stopsAtTheAnimalsLookedFor(GameTestHelper h) {
        CompanionEntity companion = arena(h, null);
        h.spawn(EntityType.COW, 62, 2, LINE).setNoAi(true);
        long id = lead(h, companion, "east:cow", 0, true);
        h.succeedWhen(() -> {
            CompanionActionResult result = finished(h, companion, id);
            h.assertTrue(result.success() && result.message().contains("коров"), result.message());
            h.assertTrue(at(h, companion).getX() < 62, "Walked past: " + at(h, companion).toShortString());
        });
    }

    @GameTest(template = "empty")
    public static void headingsAreHeardAndChecked(GameTestHelper h) {
        String[][] said = {
                {"Олег, веди на северо-восток", "companion work walk_heading 0 northeast"},
                {"пройди 200 блоков на запад", "companion work walk_heading 200 west"},
                {"иди на юго запад 30 блоков", "companion work walk_heading 30 southwest"},
                {"веди нас к востоку", "companion work walk_heading 0 east"},
                {"давай пошли в сторону севера", "companion work walk_heading 0 north"},
                {"веди на юг, пока не найдем деревню", "companion work walk_heading 0 south:village"},
                {"веди на север, пока не скажу стоп", "companion work walk_heading 0 north"},
                {"стоп", "companion stop"}};
        for (String[] pair : said) {
            String routed = CompanionLocalCommands.route(pair[0], "Олег");
            h.assertTrue(pair[1].equals(routed), "«" + pair[0] + "» routed as " + routed);
        }
        for (String other : new String[]{"иди на север и добудь дерево", "веди на юг, пока не найдем дракона", "что на севере"}) {
            String routed = CompanionLocalCommands.route(other, "Олег");
            h.assertTrue(routed == null || !routed.contains("walk_heading"), "«" + other + "» routed as " + routed);
        }
        var walk = CompanionPlanCodec.decode(message("{\"action\":\"walk_heading\",\"item\":\"northeast\"}")).actions();
        h.assertTrue(walk.size() == 1 && walk.getFirst().count() == 0 && "northeast".equals(walk.getFirst().item()), "Decoded " + walk);
        h.assertTrue(CompanionPlanCodec.decode(message("{\"action\":\"walk_heading\",\"item\":\"west:village\",\"count\":500}")).actions().getFirst().count() == 500, "500 blocks refused");
        for (String bad : new String[]{"{\"action\":\"walk_heading\",\"item\":\"up\"}", "{\"action\":\"walk_heading\",\"item\":\"east:dragon\"}",
                "{\"action\":\"walk_heading\",\"item\":\"east\",\"count\":501}"})
            h.assertTrue(rejects(() -> CompanionPlanCodec.decode(message(bad))), "Decoded " + bad);
        h.succeed();
    }

    /**
     * Bedrock under a stone floor (y=1) with a bedrock border; {@code water}, when given, fills the floor from x=40 eastwards.
     * He stands at (START_X, 2, LINE), the player behind him.
     */
    private static CompanionEntity arena(GameTestHelper h, BlockState water) {
        for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            boolean border = x == 0 || x == 127 || z == 0 || z == 127;
            h.setBlock(new BlockPos(x, 1, z), border ? Blocks.BEDROCK.defaultBlockState() : water != null && x >= 40 ? water : Blocks.STONE.defaultBlockState());
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

    /** Where he stands, counted from the arena's corner the same way absolutePos places things. */
    private static BlockPos at(GameTestHelper h, CompanionEntity companion) { return companion.blockPosition().subtract(h.absolutePos(BlockPos.ZERO)); }

    private static ServerPlayer owner(GameTestHelper h, CompanionEntity companion) {
        return h.getLevel().getServer().getPlayerList().getPlayer(companion.getOwnerId());
    }

    /** Gives the order; {@code follows}: the player keeps three blocks behind him, as a player led does. */
    private static long lead(GameTestHelper h, CompanionEntity companion, String item, int blocks, boolean follows) {
        ServerPlayer player = owner(h, companion);
        h.assertTrue(CompanionEvents.executeAction(player, "walk_heading", item, blocks).success(), "Walk not accepted: " + companion.getLastTaskResult());
        if (follows) h.onEachTick(() -> player.teleportTo(companion.getX() - 3, companion.getY(), companion.getZ()));
        return companion.activeWorkId();
    }

    private static CompanionActionResult finished(GameTestHelper h, CompanionEntity companion, long id) {
        CompanionActionResult result = companion.workResult(id);
        h.assertTrue(result != null && !companion.isWorking(), "Still walking: " + companion.getLastTaskResult() + " at " + at(h, companion).toShortString());
        return result;
    }

    private static JsonObject message(String step) {
        JsonObject function = new JsonObject();
        function.addProperty("name", "queue_companion_plan");
        function.addProperty("arguments", "{\"steps\":[" + step + "]}");
        JsonObject call = new JsonObject();
        call.add("function", function);
        JsonArray calls = new JsonArray();
        calls.add(call);
        JsonObject message = new JsonObject();
        message.add("tool_calls", calls);
        return message;
    }

    private static boolean rejects(Runnable decode) {
        try { decode.run(); return false; }
        catch (IllegalStateException invalid) { return true; }
    }
}
