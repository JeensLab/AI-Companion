package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionSwitchLayout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Review of PR #158: at 854×480 (427×240 at GUI scale 2) the behaviour screen pages its switches four rows at a time —
 * five pages for the switches there are now — while 1920×1080 (960×540) keeps two. Paging is deliberate, and no switch
 * may become unreachable on the smaller screen. No client is needed: the geometry is pure arithmetic.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionScreenLayoutTests {
    @GameTest(template = "empty")
    public static void everySwitchStaysReachableAtASmallGui(GameTestHelper h) {
        // The switches the screen adds today, and counts above and below it: the layout must cover any of them.
        for (int rows : new int[]{20, 1, 45}) {
            for (int height : new int[]{240, 540}) {
                int perPage = CompanionSwitchLayout.perPage(height), pages = CompanionSwitchLayout.pages(rows, height);
                List<Integer> seen = new ArrayList<>();
                for (int page = 0; page < pages; page++) {
                    int from = CompanionSwitchLayout.firstRow(rows, height, page);
                    int onPage = CompanionSwitchLayout.rowsOnPage(rows, height, page);
                    h.assertTrue(onPage >= 1 && onPage <= perPage, "Page " + page + " of " + pages + " holds " + onPage + " rows");
                    int step = CompanionSwitchLayout.step(height, onPage);
                    int y = CompanionSwitchLayout.firstY(CompanionSwitchLayout.top(height), height, onPage);
                    h.assertTrue(y >= CompanionSwitchLayout.HIGHEST_ROW, "A row at height " + height + " covers the sampling fields");
                    h.assertTrue(step >= CompanionSwitchLayout.ROW_HEIGHT, "Rows overlap at height " + height);
                    for (int i = 0; i < onPage; i++) {
                        int rowY = y + i * step;
                        h.assertTrue(rowY + CompanionSwitchLayout.ROW_HEIGHT <= CompanionSwitchLayout.bottom(height),
                                "A row at height " + height + " reaches the note");
                        seen.add(from + i);
                    }
                }
                Set<Integer> unique = new HashSet<>(seen);
                h.assertTrue(seen.size() == rows && unique.size() == rows,
                        "Not every switch is reachable at height " + height + " with " + rows + " rows: " + seen);
            }
        }
        h.assertTrue(CompanionSwitchLayout.perPage(240) == 4 && CompanionSwitchLayout.pages(20, 240) == 5,
                "854×480 no longer pages four rows at a time");
        h.assertTrue(CompanionSwitchLayout.pages(20, 540) == 2, "1920×1080 no longer keeps two pages");
        h.assertTrue(CompanionSwitchLayout.firstRow(20, 240, 5) == 0, "A page past the last does not wrap to the first");
        h.succeed();
    }
}
