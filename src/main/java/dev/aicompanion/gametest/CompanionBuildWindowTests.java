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
    public static void theOrderSaysOneShortLineAndTheWindowsChoiceReplacesItWithoutDoubling(GameTestHelper h) {
        Setup s = setup(h);
        h.assertTrue(CompanionBuildPlacement.replaceFor(s.player, NAME), "With no order waiting the window's «Строить» starts one");
        h.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null, "replaceFor started the order itself: it is the client's to start, like the phrase");
        h.assertTrue(s.companion.startWork("build_house", NAME, 1).success(), "Order rejected");
        h.runAfterDelay(20, () -> {
            h.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
            String told = s.companion.getLastTaskResult();
            h.assertTrue(told.equals(CompanionBuildPlacement.summary(s.player, NAME).line()), "He did not say the summary's line: " + told);
            for (String gone : new String[]{"×", "блоков", "Сам добуду", "Принести придётся", "подтверд", "Выбери", "Enter", "минут"})
                h.assertTrue(!told.contains(gone), "The line still carries «" + gone + "»: " + told);
            h.assertTrue(told.length() < 120, "The line is not short: " + told);
            // The same building again, an unknown one: nothing to start, the order stays.
            h.assertTrue(!CompanionBuildPlacement.replaceFor(s.player, NAME) && !CompanionBuildPlacement.replaceFor(s.player, "замок дракона") && !CompanionBuildPlacement.replaceFor(s.player, null), "A choice that changes nothing asked for an order");
            h.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order was lost");
            // Another building: the waiting order is dropped, and the order the phrase makes is the one that waits.
            h.assertTrue(CompanionBuildPlacement.replaceFor(s.player, OTHER_NAME), "The other building's order is not to be started");
            h.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null && !s.companion.isGatheringTask(), "The old order outlived the choice: " + s.companion.getLastTaskResult());
            h.assertTrue(CompanionBuildPlacement.confirm(s.player, NAME, 0, BlockPos.ZERO) != null, "The dropped order could still be confirmed");
            h.assertTrue(s.companion.startWork("build_house", OTHER_NAME, 1).success(), "Order rejected");
            h.runAfterDelay(20, () -> {
                h.assertTrue(OTHER_NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The new order does not wait: " + CompanionBuildPlacement.pendingName(s.player));
                h.assertTrue(s.companion.getLastTaskResult().equals(CompanionBuildPlacement.summary(s.player, OTHER_NAME).line()), s.companion.getLastTaskResult());
                CompanionBuildPlacement.cancel(s.player);
                h.assertTrue(CompanionBuildPlacement.pendingName(s.player) == null && !s.companion.isGatheringTask(), "The cancel left an order");
                h.succeed();
            });
        });
    }

    /**
     * The window's path through the queue and the plan runner, as the client takes it: the phrase's order is in the queue and waits for its
     * place, the window's «Строить» for another building drops it on the server and makes the new order. Nothing was placed, dug or gathered,
     * so the dropped order's end is the plain «replaced» line (the client does not say it), not «Работа прервана».
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void replacingAWaitingOrderFromTheWindowProducesNoInterruptionMessage(GameTestHelper h) {
        Setup s = setup(h);
        var link = CompanionMultitaskSupport.link(h, s.companion);
        var old = link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, NAME)), "gametest-window-old", () -> false);
        int[] stage = {0};
        var fresh = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<String>>();
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The phrase's order does not wait for a placement");
                h.assertTrue(CompanionBuildPlacement.replaceFor(s.player, OTHER_NAME), "The window's choice did not ask for an order");
                fresh.set(link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, OTHER_NAME)), "gametest-window-new", () -> false));
                stage[0] = 1;
            }
            h.assertTrue(old.isDone(), "The dropped order is still running");
            String report = old.join();
            h.assertTrue(report.contains(CompanionBuildPlacement.REPLACED) && !report.contains("прервана"), "The dropped order ends with: " + report);
            h.assertTrue(OTHER_NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The new order does not wait: " + CompanionBuildPlacement.pendingName(s.player));
            h.assertTrue(s.companion.getLastTaskResult().equals(CompanionBuildPlacement.summary(s.player, OTHER_NAME).line()), s.companion.getLastTaskResult());
            h.assertTrue(!fresh.get().isDone(), "The new order ended: " + fresh.get().getNow(""));
            CompanionBuildPlacement.cancel(s.player);
            link.close();
        });
    }

    /** A real work that is stopped keeps its message: the window's choice leaves a building that is under way alone, and a stop of the work itself (not through the queue) says «Работа прервана». */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aRealWorkThatIsStoppedKeepsItsInterruptionMessage(GameTestHelper h) {
        Setup s = setup(h);
        var link = CompanionMultitaskSupport.link(h, s.companion);
        var order = link.submit(List.of(new dev.aicompanion.client.PlannedAction("build_house", 0, 1, NAME)), "gametest-window-real", () -> false);
        int[] stage = {0};
        h.succeedWhen(() -> {
            if (stage[0] == 0) {
                h.assertTrue(NAME.equals(CompanionBuildPlacement.pendingName(s.player)), "The order does not wait for a placement");
                String refused = CompanionBuildPlacement.confirm(s.player, NAME, 0, h.absolutePos(new BlockPos(18, GROUND, 14)));
                h.assertTrue(refused == null, "Confirmation refused: " + refused);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                // The building is under way: the window's choice of another one asks for an order but drops nothing.
                h.assertTrue(CompanionBuildPlacement.replaceFor(s.player, OTHER_NAME) && s.companion.hasActiveWorkOrder() && !order.isDone(), "The work under way was dropped");
                s.companion.stopGatheringForPlayer();
                stage[0] = 2;
            }
            h.assertTrue(order.isDone(), "The stopped work is still running");
            String report = order.join();
            h.assertTrue(report.contains("Работа прервана") && !report.contains(CompanionBuildPlacement.REPLACED), "The stopped work ends with: " + report);
            link.close();
        });
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
