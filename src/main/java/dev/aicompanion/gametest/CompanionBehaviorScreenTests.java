package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionSwitchLayout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The two distance rows of «Поведение», as arithmetic: the pickup radius turns through the whole 4..32 the setting allows and
 * reaches 32 (the old step of eight went 4, 12, 20, 28 and never came to it), and the search distance has its own row beside
 * «Строить себе путь», turning through the setting's 16..128. No client is needed: the geometry and the cycles are pure numbers.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionBehaviorScreenTests {
    @GameTest(template = "empty")
    public static void thePickUpRadiusCycleCoversTheWholeRangeAndReachesThirtyTwo(GameTestHelper h) {
        List<Integer> seen = new ArrayList<>();
        int value = CompanionSwitchLayout.PICK_UP_RADIUS_MIN;
        for (int press = 0; press < 16; press++) {
            seen.add(value);
            value = CompanionSwitchLayout.nextPickUpRadius(value);
            if (value == CompanionSwitchLayout.PICK_UP_RADIUS_MIN) break;
        }
        h.assertTrue(seen.equals(List.of(4, 8, 12, 16, 20, 24, 28, 32)),
                "The radius cycle skips a value or never reaches 32: " + seen);
        h.assertTrue(CompanionSwitchLayout.nextPickUpRadius(32) == 4, "32 does not turn back to 4");
        h.assertTrue(CompanionSwitchLayout.nextPickUpRadius(30) == 4,
                "A value off the cycle (30) does not come back into it: " + CompanionSwitchLayout.nextPickUpRadius(30));
        h.succeed();
    }

    /** The search distance row exists beside «Строить себе путь» and turns through the whole setting, 128 included. */
    @GameTest(template = "empty")
    public static void theBehaviourScreenHasTheSearchRadiusRow(GameTestHelper h) {
        h.assertTrue(CompanionSwitchLayout.BEHAVIOUR_ROWS == 27,
                "The behaviour screen's row count did not grow by the search row: " + CompanionSwitchLayout.BEHAVIOUR_ROWS);
        h.assertTrue(CompanionSwitchLayout.SEARCH_RADIUS_LABEL.equals("Дальность поиска"),
                "The search row's label: " + CompanionSwitchLayout.SEARCH_RADIUS_LABEL);
        h.assertTrue(CompanionSwitchLayout.SEARCH_RADIUS_MIN == 16 && CompanionSwitchLayout.SEARCH_RADIUS_MAX == 128,
                "The search row does not cover the setting's 16..128");
        List<Integer> seen = new ArrayList<>();
        int value = CompanionSwitchLayout.nextSearchRadius(CompanionSwitchLayout.SEARCH_RADIUS_MIN);
        while (!seen.contains(value)) {
            seen.add(value);
            value = CompanionSwitchLayout.nextSearchRadius(value);
        }
        h.assertTrue(seen.equals(List.of(32, 64, 128, 16)),
                "The search row does not turn through 32, 64, 128 and back: " + seen);
        h.succeed();
    }
}
