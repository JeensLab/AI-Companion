package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionModelLayout;
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
 * may become unreachable on the smaller screen. The model settings have their own screen behind the first row of
 * «Поведение»: its switch, fields and reference block must not overlap and must stay reachable at every GUI size too.
 * No client is needed: the geometry is pure arithmetic.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionScreenLayoutTests {
    @GameTest(template = "empty")
    public static void everySwitchStaysReachableAtASmallGui(GameTestHelper h) {
        // The switches the screen adds today, and counts above and below it: the layout must cover any of them.
        for (int rows : new int[]{CompanionSwitchLayout.BEHAVIOUR_ROWS, 1, 45}) {
            for (int height : new int[]{240, 540}) {
                int perPage = CompanionSwitchLayout.perPage(height), pages = CompanionSwitchLayout.pages(rows, height);
                List<Integer> seen = new ArrayList<>();
                for (int page = 0; page < pages; page++) {
                    int from = CompanionSwitchLayout.firstRow(rows, height, page);
                    int onPage = CompanionSwitchLayout.rowsOnPage(rows, height, page);
                    h.assertTrue(onPage >= 1 && onPage <= perPage, "Page " + page + " of " + pages + " holds " + onPage + " rows");
                    int step = CompanionSwitchLayout.step(height, onPage);
                    int y = CompanionSwitchLayout.firstY(CompanionSwitchLayout.top(height), height, onPage);
                    h.assertTrue(y >= CompanionSwitchLayout.HIGHEST_ROW, "A row at height " + height + " covers the title");
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
        h.assertTrue(CompanionSwitchLayout.perPage(240) == 4 && CompanionSwitchLayout.pages(CompanionSwitchLayout.BEHAVIOUR_ROWS, 240) == 5,
                "854×480 no longer pages four rows at a time");
        h.assertTrue(CompanionSwitchLayout.pages(CompanionSwitchLayout.BEHAVIOUR_ROWS, 540) == 2, "1920×1080 no longer keeps two pages");
        h.assertTrue(CompanionSwitchLayout.firstRow(20, 240, 5) == 0, "A page past the last does not wrap to the first");
        // The model settings left the behaviour screen, but its first row still opens their own screen, on the first page.
        h.assertTrue(CompanionSwitchLayout.MODEL_ENTRY_ROW == 0 && CompanionSwitchLayout.MODEL_ENTRY_LABEL.startsWith("Модель и ответы"),
                "The first row of «Поведение» no longer opens the model screen: " + CompanionSwitchLayout.MODEL_ENTRY_LABEL);
        for (int height : new int[]{240, 360, 480, 540, 1080}) {
            h.assertTrue(CompanionSwitchLayout.firstRow(CompanionSwitchLayout.BEHAVIOUR_ROWS, height, 0) == CompanionSwitchLayout.MODEL_ENTRY_ROW
                            && CompanionSwitchLayout.rowsOnPage(CompanionSwitchLayout.BEHAVIOUR_ROWS, height, 0) >= 1,
                    "The model entry is not on the first page of «Поведение» at height " + height);
        }
        h.succeed();
    }

    /**
     * The model screen behind that row: the thinking switch, the two sampling fields, the line under them and the
     * reference block stand one under another; none of them overlaps another, «Назад» or the edge of the screen at the
     * GUI sizes the game allows. 240 is 854×480 at GUI scale 2, 540 is 1920×1080 at scale 2, 1080 is 1920×1080 at scale 1.
     */
    @GameTest(template = "empty")
    public static void modelScreenRowsAreReachableAndDoNotOverlap(GameTestHelper h) {
        int[] heights = {240, 360, 480, 540, 1080};
        int[] widths = {427, 640, 854, 960, 1920};
        for (int size = 0; size < heights.length; size++) {
            int height = heights[size], width = widths[size];
            String where = width + "×" + height;
            int left = CompanionModelLayout.left(width), content = CompanionModelLayout.contentWidth(width);
            int column = CompanionModelLayout.columnWidth(width);
            h.assertTrue(left >= 4 && left + content <= width - 4, "The content column leaves the screen at " + where);
            h.assertTrue(column >= 60 && 2 * column + CompanionModelLayout.COLUMN_GAP <= content,
                    "The two sampling columns do not fit the content column at " + where);
            int switchX = CompanionModelLayout.switchX(width), switchWidth = CompanionModelLayout.switchWidth(width);
            h.assertTrue(switchX >= 4 && switchX + switchWidth <= width - 4, "The thinking switch leaves the screen at " + where);
            int thinkingY = CompanionModelLayout.thinkingY(height);
            h.assertTrue(thinkingY >= 24 && thinkingY + CompanionModelLayout.ROW_HEIGHT <= CompanionModelLayout.labelsY(height),
                    "The thinking switch covers the title or the field labels at " + where);
            int labelsY = CompanionModelLayout.labelsY(height), fieldsY = CompanionModelLayout.fieldsY(height);
            h.assertTrue(labelsY + CompanionModelLayout.LABEL_HEIGHT <= fieldsY, "The field labels overlap the fields at " + where);
            h.assertTrue(fieldsY + CompanionModelLayout.FIELD_HEIGHT <= CompanionModelLayout.hintY(height),
                    "The fields overlap the line under them at " + where);
            h.assertTrue(CompanionModelLayout.hintY(height) + CompanionModelLayout.HINT_LINES * CompanionModelLayout.LINE
                            <= CompanionModelLayout.referenceTop(height),
                    "The line under the fields overlaps the reference block at " + where);
            int referenceTop = CompanionModelLayout.referenceTop(height);
            h.assertTrue(referenceTop + (CompanionModelLayout.REFERENCE.length - 1) * CompanionModelLayout.LINE
                            + CompanionModelLayout.LABEL_HEIGHT <= CompanionModelLayout.referenceBottom(height),
                    "The reference block reaches «Назад» at " + where);
            int backY = CompanionModelLayout.backY(height);
            h.assertTrue(CompanionModelLayout.referenceBottom(height) <= backY && backY + CompanionModelLayout.BACK_HEIGHT <= height - 4,
                    "«Назад» leaves the screen or overlaps the reference block at " + where);
        }
        // The reference block keeps what the owner needs to read there: the limit, both budgets and a rejected value.
        String reference = String.join(" ", CompanionModelLayout.REFERENCE);
        h.assertTrue(reference.contains("90 с") && reference.contains("1024") && reference.contains("4096")
                        && reference.contains("Отклонённое"),
                "The reference block lost the answer limit, the budgets or a rejected value: " + reference);
        h.succeed();
    }
}
