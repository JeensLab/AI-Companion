package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionAgenda;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
    private static final int TIMEOUT = 24000, NIGHT = 18000, TORCHES_FEW = 8, BREAD_MIN = 4, STICKS_MIN = 4;
    private static long clock;

    private CompanionMineNightTests() { }

    @BeforeBatch(batch = "mine_night") public static void night(ServerLevel level) { clock = level.getDayTime(); level.setDayTime(NIGHT); }
    @AfterBatch(batch = "mine_night") public static void day(ServerLevel level) { level.setDayTime(clock); CompanionFixtureCleanup.afterAtomicTests(level); }
    @AfterBatch(batch = "mine_hand_over") public static void afterHandOver(ServerLevel level) { CompanionFixtureCleanup.afterAtomicTests(level); }

    /** Dusk, and the bag holds the least the mine needs (a pickaxe, a few torches, bread and sticks): he says so and goes down at once, with no top-up on the surface at night. */
    @GameTest(template = "marathon_cave", batch = "mine_night", timeoutTicks = TIMEOUT)
    public static void goesDownAtDuskWithTheMinimumKit(GameTestHelper h) {
        Rig r = rig(h, true, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, BREAD_MIN), new ItemStack(Items.TORCH, TORCHES_FEW),
                new ItemStack(Items.STICK, STICKS_MIN), new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.CRAFTING_TABLE));
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
