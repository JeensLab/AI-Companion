package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import dev.aicompanion.entity.CompanionFacts;
import dev.aicompanion.entity.CompanionPlaybooks;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.aicompanion.gametest.CompanionDiamondGoalTests.CENTER;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.GROUND;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.Rig;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.givenToPlayer;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.report;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.rig;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.start;
import static dev.aicompanion.gametest.CompanionDiamondGoalTests.state;

/**
 * The night and the long goal of diamonds (runs of 07.11, seeds 20261031 and 314159): deaths on the surface at night on the top-up of the
 * kit, and diamonds already in the bag held back behind food that no hunt could find after a death.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionMineNightTests {
    private static final String DUSK = "Темнеет — спускаюсь, факелов хватит на начало.";
    private static final int TIMEOUT = 24000, BENCH_TIMEOUT = 12000, NIGHT = 18000, TORCHES_FEW = 8, BREAD_MIN = 4, STICKS_MIN = 4, STICKS_NIGHT = 6, STONE_PICKAXE_STONE = 3,
            COAL_NIGHT = 8, STICKS_COAL = 14, TORCHES_FULL = 32;
    private static long clock;

    private CompanionMineNightTests() { }

    @BeforeBatch(batch = "mine_night") public static void night(ServerLevel level) { clock = level.getDayTime(); level.setDayTime(NIGHT); }
    @AfterBatch(batch = "mine_night") public static void day(ServerLevel level) { level.setDayTime(clock); CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "mine_hand_over") public static void afterHandOver(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Dusk, and the bag holds the least the mine needs (a pickaxe, a few torches, bread and sticks): he says so and goes down at once, with no top-up on the surface at night. */
    @GameTest(template = "marathon_cave", batch = "mine_night", timeoutTicks = TIMEOUT)
    public static void goesDownAtDuskWithTheMinimumKit(GameTestHelper h) {
        Rig r = rig(h, true, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, BREAD_MIN), new ItemStack(Items.TORCH, TORCHES_FEW),
                new ItemStack(Items.STICK, STICKS_NIGHT), new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.CRAFTING_TABLE));
        start(h, r, 1);
        boolean[] said = {false};
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            said[0] |= DUSK.equals(c.autonomyLine());
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
        });
        h.succeedWhen(() -> {
            h.assertTrue(said[0], "Nothing said about the dusk | " + report(h, r));
            h.assertTrue(r.c().mineSite().exists(), "No mine begun | " + report(h, r));
        });
    }

    /** Dusk with the minimum of the kit and coal in the bag: the coal becomes torches before the descent, so he goes down with the full stock, not with the few he held. */
    @GameTest(template = "marathon_cave", batch = "mine_night", timeoutTicks = TIMEOUT)
    public static void theNightKitMakesTorchesFromTheCoalHeCarries(GameTestHelper h) {
        Rig r = rig(h, true, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, BREAD_MIN), new ItemStack(Items.TORCH, TORCHES_FEW),
                new ItemStack(Items.COAL, COAL_NIGHT), new ItemStack(Items.STICK, STICKS_COAL), new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.CRAFTING_TABLE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE));
        start(h, r, 1);
        boolean[] said = {false}, down = {false};
        h.onEachTick(() -> {
            CompanionEntity c = r.c();
            said[0] |= DUSK.equals(c.autonomyLine());
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
            if (c.mineSite().exists() && !down[0]) {
                down[0] = true;
                h.assertTrue(count(c, Items.TORCH) >= TORCHES_FULL, "He went down with " + count(c, Items.TORCH) + " torches and coal in the bag | " + report(h, r));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(said[0], "Nothing said about the dusk | " + report(h, r));
            h.assertTrue(r.c().mineSite().exists() && count(r.c(), Items.TORCH) >= TORCHES_FULL, "No mine begun with the full torches | " + report(h, r));
        });
    }

    /** The torches of the night are made only within the sticks to spare: the sticks of the pickaxes below are not spent on them. */
    @GameTest(template = "marathon_cave", timeoutTicks = 200)
    public static void theNightPlanKeepsTheSticksOfThePickaxes(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        var mine = CompanionPlaybooks.of("find_diamonds", 1).milestones().getLast();
        c.setItem(0, new ItemStack(Items.BREAD, BREAD_MIN));
        c.setItem(1, new ItemStack(Items.TORCH, TORCHES_FEW));
        c.setItem(2, new ItemStack(Items.COAL, COAL_NIGHT));
        c.setItem(3, new ItemStack(Items.STICK, STICKS_COAL));
        c.setItem(4, new ItemStack(Items.CRAFTING_TABLE));
        c.setItem(5, new ItemStack(Items.COBBLESTONE, STONE_PICKAXE_STONE));
        var steps = mine.nightSteps(CompanionFacts.of(c));
        h.assertTrue(steps.size() == 2, "The coal of the bag does not make torches before the descent: " + steps);
        h.assertTrue("obtain_item".equals(steps.getFirst().type()) && "minecraft:torch".equals(steps.getFirst().item()) && steps.getFirst().count() == TORCHES_FULL, "The first step is not the torches: " + steps);
        h.assertTrue("dig_for_diamonds".equals(steps.getLast().type()), "The last step is not the mine: " + steps);
        c.setItem(3, new ItemStack(Items.STICK, STICKS_NIGHT));
        h.assertTrue(mine.nightSteps(CompanionFacts.of(c)).size() == 1, "The sticks of the pickaxes were spent on torches: " + mine.nightSteps(CompanionFacts.of(c)));
        c.setItem(1, new ItemStack(Items.TORCH, TORCHES_FULL));
        c.setItem(3, new ItemStack(Items.STICK, STICKS_COAL));
        h.assertTrue(mine.nightSteps(CompanionFacts.of(c)).size() == 1, "Torches were made with a full stock: " + mine.nightSteps(CompanionFacts.of(c)));
        h.succeed();
    }

    /**
     * Runs 07.11 (seed 7, retreat and void-5): the bench of the kit stayed on the surface and no stone pickaxe was in the bag, so below he could not make one
     * and climbed; at dusk the bare minimum sent him down again, and the order ended on "no stone pickaxe". The bag lacks both: the top-up fells the tree for a bench first.
     */
    @GameTest(template = "marathon_cave", batch = "mine_night", timeoutTicks = BENCH_TIMEOUT)
    public static void theDuskDescentWaitsForAPickaxeAndABench(GameTestHelper h) {
        Rig r = rig(h, true, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, 16), new ItemStack(Items.TORCH, 38),
                new ItemStack(Items.STICK, 23), new ItemStack(Items.COBBLESTONE, 28));
        r.c().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(CENTER + 5, GROUND + y, CENTER), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(CENTER + 5, GROUND + 5, CENTER), Blocks.OAK_LEAVES);
        start(h, r, 1);
        h.onEachTick(() -> {
            h.assertTrue(!DUSK.equals(r.c().autonomyLine()), "He went down at dusk without a pickaxe and a bench | " + report(h, r));
            h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r));
        });
        h.succeedWhen(() -> h.assertTrue(count(r.c(), Items.STONE_PICKAXE) >= 1 && count(r.c(), Items.CRAFTING_TABLE) >= 1, "No pickaxe and bench yet | " + report(h, r)));
    }

    private static int count(CompanionEntity companion, Item item) {
        int count = 0;
        for (int slot = 0; slot < companion.getContainerSize(); slot++) if (companion.getItem(slot).is(item)) count += companion.getItem(slot).getCount();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) if (companion.getItemBySlot(slot).is(item)) count += companion.getItemBySlot(slot).getCount();
        return count;
    }

    /**
     * Run 07.11 (seed 314159): down to -58 with the sticks of two pickaxes, and the climb started with none. The tunnels below stop at the last pickaxe, so the kit
     * he goes down with at dusk holds the sticks of one more than the minimum of the day's kit.
     */
    @GameTest(template = "marathon_cave", timeoutTicks = 200)
    public static void theNightKitKeepsSticksForThePickaxesBelow(GameTestHelper h) {
        CompanionEntity c = h.spawn(AICompanionMod.COMPANION.get(), 2, 2, 2);
        var mine = CompanionPlaybooks.of("find_diamonds", 1).milestones().getLast();
        c.setItem(0, new ItemStack(Items.BREAD, BREAD_MIN));
        c.setItem(1, new ItemStack(Items.TORCH, TORCHES_FEW));
        c.setItem(2, new ItemStack(Items.STICK, STICKS_NIGHT));
        h.assertTrue(!mine.kitMinimum(CompanionFacts.of(c)) && !mine.nightKit(CompanionFacts.of(c)), "A bag without a bench meets the kit minimum");
        c.setItem(3, new ItemStack(Items.CRAFTING_TABLE));
        h.assertTrue(!mine.kitMinimum(CompanionFacts.of(c)), "A bag without a pickaxe or stone meets the kit minimum");
        c.setItem(4, new ItemStack(Items.COBBLESTONE, STONE_PICKAXE_STONE));
        c.setItem(2, new ItemStack(Items.STICK, STICKS_MIN));
        h.assertTrue(mine.kitMinimum(CompanionFacts.of(c)) && !mine.nightKit(CompanionFacts.of(c)), "The sticks of two pickaxes make the kit he goes down with at dusk");
        c.setItem(2, new ItemStack(Items.STICK, STICKS_NIGHT));
        h.assertTrue(mine.nightKit(CompanionFacts.of(c)), "The sticks of three pickaxes do not make the kit he goes down with at dusk");
        h.succeed();
    }

    /** A diamond already in the bag, no food and no game to hunt (both lost on a death): the diamond goes to the player and the goal ends with it, instead of standing blocked behind the food. */
    @GameTest(template = "marathon_cave", batch = "mine_hand_over", timeoutTicks = TIMEOUT)
    public static void aDeathWithoutFoodDoesNotBlockTheHandOver(GameTestHelper h) {
        Rig r = rig(h, false, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.TORCH, 64), new ItemStack(Items.STICK, 8),
                new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.DIAMOND));
        start(h, r, 2);
        h.onEachTick(() -> h.assertTrue(state(h, r) != CompanionAgenda.State.BLOCKED, "The goal stopped: " + report(h, r)));
        h.succeedWhen(() -> h.assertTrue(state(h, r) == CompanionAgenda.State.COMPLETED && givenToPlayer(h, r) >= 1, "The diamond stayed in the bag | " + report(h, r)));
    }
}
