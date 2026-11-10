package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionParkHold;
import dev.aicompanion.client.CompanionPlanCodec;
import dev.aicompanion.client.PlannedAction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionParkHoldTests {
    /** Local-command steps that run through the queue but are not in the plan codec's ACTIONS. */
    private static final List<String> LOCAL = List.of("autonomy", "goal_status", "goal_cancel", "goal_defer", "goal_resume", "collect_smelt");

    /** Every action that does not need the furnace's product runs at once; a new action nobody sorted turns this red. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unrelatedActionsDoNotWaitForParkedSmelt(GameTestHelper h) {
        var iron = List.of(new CompanionParkHold.Parked("minecraft:raw_iron", "minecraft:iron_ingot"));
        List<String> waiting = new ArrayList<>();
        // Item-bound actions are held only for their own items, so the stranger item keeps them out of the way here too.
        for (String type : CompanionPlanCodec.ACTIONS.stream().sorted().toList()) check(type, iron, waiting);
        for (String type : LOCAL) check(type, iron, waiting);
        h.assertTrue(waiting.isEmpty(), "Actions wait for a parked smelt they do not depend on: " + waiting);
        h.succeed();
    }

    private static void check(String type, List<CompanionParkHold.Parked> parked, List<String> waiting) {
        if (CompanionParkHold.held(List.of(new PlannedAction(type, 0, 1, "minecraft:dirt")), parked)) waiting.add(type);
    }
}
