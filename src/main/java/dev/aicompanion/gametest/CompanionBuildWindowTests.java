package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionBuildPlacement;
import dev.aicompanion.entity.CompanionBuildSummary;
import dev.aicompanion.entity.CompanionBuildSummary.Row;
import dev.aicompanion.entity.CompanionBuildSummary.Who;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionEvents;
import dev.aicompanion.entity.CompanionVillageBuildings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The window of buildings and the short line he says: what the window shows is the server's structured summary (the same numbers the line is
 * made of), and the order its «Строить» starts is the order the phrase starts. The window itself is client code and has no test here.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBuildWindowTests {
    private static final String NAME = "пустынный дом", OTHER_NAME = "бревенчатый дом";
    private static final int GROUND = 1;

    private record Setup(CompanionEntity companion, ServerPlayer player) { }

    @GameTest(template = "empty")
    public static void theSummaryGroupsWhatTheBagCoversWhatHeGetsAndWhatThePlayerBrings(GameTestHelper h) {
        Setup s = setup(h);
        // A building that takes both things he gets himself and things only the player has (the catalogue holds one: the smithies, the temple).
        String name = null;
        CompanionBuildSummary.Summary empty = null;
        for (var building : CompanionVillageBuildings.all()) {
            var summary = CompanionBuildPlacement.summary(s.player, building.name());
            h.assertTrue(summary != null, "No summary for «" + building.name() + "»");
            h.assertTrue(summary.width() == building.width() && summary.depth() == building.depth() && summary.height() == building.height(), "Size of «" + building.name() + "»: " + summary);
            h.assertTrue(summary.rows().stream().allMatch(row -> row.have() == 0 && row.who() != Who.COVERED), "Something is covered by an empty bag in «" + building.name() + "»");
            if (name == null && summary.playerBrings() && summary.companionGets()) { name = building.name(); empty = summary; }
        }
        h.assertTrue(name != null, "No building of the catalogue takes both what he gets and what the player brings");
        Row gets = empty.rows().stream().filter(row -> row.who() == Who.COMPANION && row.have() == 0 && row.need() <= 64).findFirst().orElseThrow();
        Row brings = empty.rows().stream().filter(row -> row.who() == Who.PLAYER && row.need() <= 64).findFirst().orElseThrow();
        // The bag holds all of one row, and part of another that only the player has.
        s.companion.setItem(0, new ItemStack(gets.item(), gets.need()));
        int part = Math.max(1, brings.need() / 2);
        s.companion.setItem(1, new ItemStack(brings.item(), part));
        var now = CompanionBuildPlacement.summary(s.player, name);
        for (Row row : now.rows()) {
            if (row.item() == gets.item()) h.assertTrue(row.who() == Who.COVERED && row.need() == gets.need() && row.have() >= gets.need(), "The covered row: " + row);
            else if (row.item() == brings.item()) {
                h.assertTrue(row.need() == brings.need() && row.have() == part && (part >= brings.need() ? row.who() == Who.COVERED : row.who() == Who.PLAYER), "The part-covered row: " + row);
            } else h.assertTrue(row.need() == empty.rows().stream().filter(r -> r.item() == row.item()).findFirst().orElseThrow().need(), "The need of " + row.item() + " changed with the bag");
        }
        h.assertTrue(now.playerBrings() && now.companionGets() == now.rows().stream().anyMatch(r -> r.who() == Who.COMPANION), "The groups of the bag-filled summary: " + now);
        h.assertTrue(now.rows().stream().mapToInt(Row::need).sum() == empty.rows().stream().mapToInt(Row::need).sum(), "The total need depends on the bag");
        h.assertTrue(CompanionBuildPlacement.summary(s.player, "замок дракона") == null && CompanionBuildPlacement.summary(s.player, null) == null, "A summary for a building that is not in the catalogue");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theShortLineSaysWhoProvidesWhatIsMissing(GameTestHelper h) {
        Row covered = new Row(Items.OAK_PLANKS, 4, 9, Who.COVERED), himself = new Row(Items.COBBLESTONE, 4, 0, Who.COMPANION), given = new Row(Items.GLASS_PANE, 2, 0, Who.PLAYER);
        h.assertTrue("Храм саванны, 7 на 9. Часть материалов принесёшь ты — глянь список.".equals(summary(covered, himself, given).line()), summary(covered, himself, given).line());
        h.assertTrue("Храм саванны, 7 на 9. Часть материалов принесёшь ты — глянь список.".equals(summary(given).line()), summary(given).line());
        h.assertTrue("Храм саванны, 7 на 9. Всё добуду сам.".equals(summary(covered, himself).line()), summary(covered, himself).line());
        h.assertTrue("Храм саванны, 7 на 9. Материалы есть.".equals(summary(covered).line()), summary(covered).line());
        h.assertTrue("Храм саванны, 7 на 9. Материалы есть.".equals(summary().line()), "An empty list of rows");
        h.succeed();
    }

    private static CompanionBuildSummary.Summary summary(Row... rows) { return new CompanionBuildSummary.Summary("храм саванны", 7, 9, 7, List.of(rows)); }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void anOrderWithoutAPlaceSaysOneShortLineAndWaits(GameTestHelper h) {
        Setup s = setup(h);
        h.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> {
            h.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
            String told = s.companion.getLastTaskResult();
            h.assertTrue(told.equals(CompanionBuildPlacement.summary(s.player, NAME).line()), "He did not say the summary's line: " + told);
            for (String gone : new String[]{"×", "блоков", "Сам добуду", "Принести придётся", "подтверд", "Выбери", "Enter", "минут"})
                h.assertTrue(!told.contains(gone), "The line still carries «" + gone + "»: " + told);
            h.assertTrue(told.length() < 120, "The line is not short: " + told);
            CompanionBuildPlacement.cancel(s.player);
            h.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null && !s.companion.isGatheringTask(), "The cancel left an order");
            h.succeed();
        });
    }

    /**
     * Looking at a building is no order (Oleg, 12.11: a ghost opened only to look stopped the building under way, and every look was a
     * task «не выполнено»). With a building under way the player's Enter on a ghost of another one is checked on the server and touches
     * nothing: a bad place is refused with the reason, a good one asks the client for the order, which waits its turn in the queue with
     * the place in it and, when its turn comes, is set down there without asking again.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void aGhostOfAnotherBuildingLeavesTheWorkUnderWayAloneAndItsOrderCarriesThePlace(GameTestHelper h) {
        Setup s = setup(h);
        var link = link(h, s.companion);
        var first = link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, NAME)), "gametest-window-first", () -> false);
        BlockPos corner = h.absolutePos(new BlockPos(18, GROUND, 14)), other = h.absolutePos(new BlockPos(4, GROUND, 4));
        // The arena's ceiling is lower than any building but the desert house: opened over the second site.
        for (int x = 2; x < 14; x++) for (int z = 2; z < 14; z++) h.setBlock(new BlockPos(x, GROUND + 5, z), Blocks.AIR);
        int[] stage = {0};
        var second = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<String>>();
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
                String refused = CompanionBuildPlacement.confirm(s.player, NAME, 0, corner);
                h.assertTrue(refused == null, "Confirmation refused: " + refused);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                var far = CompanionBuildPlacement.settle(s.player, OTHER_NAME, 0, other.offset(300, 0, 300));
                var turned = CompanionBuildPlacement.settle(s.player, OTHER_NAME, 7, other);
                var unknown = CompanionBuildPlacement.settle(s.player, "замок дракона", 0, other);
                h.assertTrue(far.why() != null && !far.order() && turned.why() != null && unknown.why() != null, "A bad place or building was accepted: " + far + turned + unknown);
                var good = CompanionBuildPlacement.settle(s.player, OTHER_NAME, 1, other);
                h.assertTrue(good.why() == null && good.order() && good.queued(), "The place was not accepted as an order to queue: " + good);
                h.assertTrue(s.companion.hasActiveWorkOrder() && !first.isDone() && CompanionBuildPlacement.pendingName(s.player) == null, "Looking at another building touched the work under way");
                second.set(link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, new CompanionBuildPlacement.Site(OTHER_NAME, other, 1).item())), "gametest-window-second", () -> false));
                h.assertTrue(s.companion.hasActiveWorkOrder() && !first.isDone() && !second.get().isDone(), "The new order did not wait its turn");
                s.companion.stopGatheringForPlayer();
                stage[0] = 2;
            }
            h.assertTrue(first.isDone(), "The stopped work is still running");
            h.assertTrue(first.join().contains("Работа прервана") && !first.join().contains(CompanionBuildPlacement.DROPPED), "The stopped work ends with: " + first.join());
            // The second order's turn: it is under way at once, and no placement is asked for.
            // (In the bare arena there is nothing to build it of, so it may already have ended, having built what it could.)
            h.assertTrue(s.companion.hasActiveWorkOrder() && !second.get().isDone() || second.get().getNow("").contains("блоков «" + OTHER_NAME + "»"), "The order with the place is not under way: " + second.get().getNow("") + " / " + s.companion.getLastTaskResult());
            h.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null, "The order with a place asked for the place again");
            link.close();
        });
    }

    /**
     * An order (the model's) that waits for its place and ends without one — given up, or another building's place set — was never begun:
     * its end is the plain «dropped» line, not «Работа прервана», and it is not kept among the tasks.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void anOrderDroppedBeforeItsPlaceIsNotAmongTheTasks(GameTestHelper h) {
        Setup s = setup(h);
        var link = link(h, s.companion);
        var old = link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, OTHER_NAME)), "gametest-window-old", () -> false);
        int[] stage = {0};
        var again = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<String>>();
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(OTHER_NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
                h.assertTrue(link.tasks().size() == 1, "The waiting order is not the one task: " + link.tasks());
                var good = CompanionBuildPlacement.settle(s.player, NAME, 0, h.absolutePos(new BlockPos(18, GROUND, 14)));
                h.assertTrue(good.why() == null && good.order() && !good.queued(), "The other building's place was not accepted: " + good);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                h.assertTrue(old.isDone(), "The dropped order is still running");
                h.assertTrue(old.join().contains(CompanionBuildPlacement.DROPPED) && !old.join().contains("прервана"), "The dropped order ends with: " + old.join());
                h.assertTrue(link.tasks().isEmpty(), "The order that was never begun is among the tasks: " + link.tasks());
                again.set(link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, OTHER_NAME)), "gametest-window-again", () -> false));
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                h.assertTrue(OTHER_NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
                CompanionBuildPlacement.cancel(s.player);
                stage[0] = 3;
            }
            h.assertTrue(again.get().isDone(), "The cancelled order is still running");
            h.assertTrue(again.get().join().contains(CompanionBuildPlacement.DROPPED) && link.tasks().isEmpty(), "The order given up is among the tasks: " + again.get().join() + " / " + link.tasks());
            link.close();
        });
    }

    /** A queue of its own: CompanionMultitaskSupport.link closes every other test's link, and these two tests run side by side. */
    private static dev.aicompanion.client.CompanionParkLink link(GameTestHelper h, CompanionEntity companion) {
        var link = new dev.aicompanion.client.CompanionParkLink();
        link.bind(h.getLevel().getServer(), companion.getOwnerId());
        return link;
    }

    private static Setup setup(GameTestHelper helper) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.BEDROCK);
            if (helper.getBlockState(new BlockPos(x, GROUND, z)).isAir()) helper.setBlock(new BlockPos(x, GROUND, z), Blocks.RED_SAND);
        }
        CompanionEntity companion = helper.spawn(AICompanionMod.COMPANION.get(), 16, GROUND + 1, 16);
        companion.setItem(companion.getContainerSize() - 2, new ItemStack(Items.IRON_SHOVEL));
        var player = helper.makeMockServerPlayerInLevel(); player.teleportTo(companion.getX() - 5, companion.getY(), companion.getZ() - 5);
        companion.setOwnerId(player.getUUID()); player.getPersistentData().putUUID("aicompanion:canonical_companion", companion.getUUID());
        CompanionEvents.getOwnedCompanion(player); companion.setSearchRadius(16);
        CompanionBuildPlacement.clientReady(player);
        return new Setup(companion, player);
    }
}
