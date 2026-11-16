package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.entity.CompanionObtain;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The server takes 1..64 blocks per gathering step, so a plan must never ask for more in one. */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionObtainSplitTests {
    @GameTest(template = "empty")
    public static void gatheringIsSplitAtSixtyFour(GameTestHelper h) {
        var plan = CompanionObtain.plan(h.getLevel(), List.of(), false, "minecraft:furnace", 9);
        h.assertTrue(!plan.refused(), "Refused: " + plan);
        h.assertTrue(plan.steps().stream().allMatch(step -> step.count() <= 64), "A step above 64: " + plan);
        int stone = plan.steps().stream().filter(step -> step.type().equals("gather_stone")).mapToInt(CompanionObtain.Step::count).sum();
        h.assertTrue(stone == 72, "Stone gathered " + stone + ": " + plan);
        h.succeed();
    }
}
