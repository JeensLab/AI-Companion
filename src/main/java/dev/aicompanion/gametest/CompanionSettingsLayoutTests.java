package dev.aicompanion.gametest;

import dev.aicompanion.AICompanionMod;
import dev.aicompanion.client.CompanionSettingsLayout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The settings page at every GUI size the auto scale picks: the header entries never cover each other and stay on the
 * screen, the field rows never reach the label above them, the note or the footer, and a height that cannot hold every
 * row gets pages instead of overlaps — with every row on exactly one of them. The arithmetic is
 * {@link CompanionSettingsLayout}, with no client class, so the screen and these tests read the same places.
 */
@GameTestHolder(AICompanionMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionSettingsLayoutTests {

    // ---- the settings page at every GUI size the auto scale picks ----

    /**
     * 320×240 and 384×288 are reachable by the auto scale. Before the fix a header entry sat inside «Вещи после смерти»
     * (the «Метки» page at 152..222 in 100..250 at 320, at 216..286 at 384): the entries are now on a second header row
     * below 418 px, so no entry covers another at any of the sizes, and none runs off the screen.
     */
    @GameTest(template = "empty")
    public static void settingsHeaderEntriesNeverCoverEachOtherAtAnyGuiSize(GameTestHelper h) {
        int[][] sizes = {{320, 240}, {384, 288}, {418, 314}, {640, 360}, {854, 480}, {1920, 1080}};
        for (int[] size : sizes) {
            int width = size[0], height = size[1];
            String at = width + "×" + height + ": ";
            CompanionSettingsLayout.Header header = CompanionSettingsLayout.header(width);
            List<CompanionSettingsLayout.Box> entries = header.entries();
            for (int a = 0; a < entries.size(); a++)
                for (int b = a + 1; b < entries.size(); b++)
                    h.assertTrue(!entries.get(a).overlaps(entries.get(b)),
                            at + "header entries " + a + " and " + b + " overlap: " + entries.get(a) + " " + entries.get(b));
            for (CompanionSettingsLayout.Box entry : entries)
                h.assertTrue(entry.inside(width, height), at + "a header entry is off the screen: " + entry);
            // The title is drawn only in the span the entries leave, so it can never reach one of them.
            for (CompanionSettingsLayout.Box entry : entries)
                h.assertTrue(header.titleTo() <= entry.x() || header.titleFrom() >= entry.right(),
                        at + "the title span reaches an entry: " + header.titleFrom() + ".." + header.titleTo() + " " + entry);
            // Two rows below 418 px, one row from it: the narrow window stacks the entries instead of shrinking them apart.
            boolean stacked = header.behavior().y() > header.memory().y();
            h.assertTrue(stacked == (width < CompanionSettingsLayout.ONE_ROW_MIN_WIDTH),
                    at + "the header rows are not the ones this width needs: stacked=" + stacked);
            h.assertTrue(header.firstY() >= entries.stream().mapToInt(CompanionSettingsLayout.Box::bottom).max().orElse(0) + 8,
                    at + "the first field row reaches the header: " + header.firstY());
        }
        h.succeed();
    }

    /**
     * A label must not reach the field above it: 37 px is the least pitch that leaves the 9-px glyphs clear of a 20-px
     * field, and before the fix the pitch floored at 28, so «Характер компаньона» painted over «ID голоса Inworld» at a
     * GUI height of 240 and 288. A height that cannot hold every row gets pages instead of overlaps, and every row is on
     * one of them.
     */
    @GameTest(template = "empty")
    public static void settingsFieldRowsStayClearOfTheirLabelsTheNoteAndTheFooter(GameTestHelper h) {
        int[][] sizes = {{320, 240}, {384, 288}, {418, 314}, {640, 360}, {854, 480}, {1920, 1080}};
        for (int[] size : sizes) {
            int width = size[0], height = size[1];
            String at = width + "×" + height + ": ";
            h.assertTrue(CompanionSettingsLayout.rowGap(height) >= CompanionSettingsLayout.ROW_PITCH_MIN,
                    at + "the pitch is under the label and field it must hold: " + CompanionSettingsLayout.rowGap(height));
            int perPage = CompanionSettingsLayout.rowsPerPage(width, height);
            int pages = CompanionSettingsLayout.pages(width, height);
            h.assertTrue(pages >= 1 && perPage * pages >= CompanionSettingsLayout.ROW_COUNT,
                    at + "the pages cannot hold every row: " + perPage + "×" + pages);
            CompanionSettingsLayout.Footer footer = CompanionSettingsLayout.footer(width, height, pages);
            List<CompanionSettingsLayout.Box> buttons = footer.boxes();
            for (int a = 0; a < buttons.size(); a++) {
                h.assertTrue(buttons.get(a).inside(width, height), at + "a footer button is off the screen: " + buttons.get(a));
                for (int b = a + 1; b < buttons.size(); b++)
                    h.assertTrue(!buttons.get(a).overlaps(buttons.get(b)),
                            at + "footer buttons overlap: " + buttons.get(a) + " " + buttons.get(b));
            }
            int[] seen = new int[CompanionSettingsLayout.ROW_COUNT];
            for (int page = 0; page < pages; page++) {
                List<CompanionSettingsLayout.Row> rows = CompanionSettingsLayout.page(width, height, page);
                h.assertTrue(!rows.isEmpty(), at + "page " + page + " has no rows");
                for (CompanionSettingsLayout.Row row : rows) {
                    seen[row.index()]++;
                    CompanionSettingsLayout.Box field = row.field();
                    CompanionSettingsLayout.Box label = CompanionSettingsLayout.label(width, row);
                    h.assertTrue(!label.overlaps(field), at + "row " + row.index() + " label covers its field: " + label + " " + field);
                    h.assertTrue(field.bottom() <= CompanionSettingsLayout.contentBottom(height),
                            at + "row " + row.index() + " reaches the note: " + field.bottom());
                    h.assertTrue(field.bottom() <= CompanionSettingsLayout.noteY(height), at + "row " + row.index() + " reaches the note");
                    for (CompanionSettingsLayout.Box button : footer.boxes())
                        h.assertTrue(!field.overlaps(button), at + "row " + row.index() + " reaches a footer button: " + field + " " + button);
                    h.assertTrue(field.x() >= 0 && field.right() <= width, at + "row " + row.index() + " is off the screen: " + field);
                    h.assertTrue(CompanionSettingsLayout.fieldRight(width) <= width,
                            at + "the second column runs off the screen: " + CompanionSettingsLayout.fieldRight(width));
                }
                for (int a = 0; a < rows.size(); a++) {
                    for (int b = a + 1; b < rows.size(); b++) {
                        h.assertTrue(!rows.get(a).field().overlaps(rows.get(b).field()),
                                at + "two fields of a page overlap: " + rows.get(a).field() + " " + rows.get(b).field());
                        h.assertTrue(rows.get(b).labelY() >= rows.get(a).field().bottom(),
                                at + "the label of row " + rows.get(b).index() + " reaches the field above it: "
                                        + rows.get(b).labelY() + " after " + rows.get(a).field().bottom());
                    }
                }
            }
            for (int row = 0; row < CompanionSettingsLayout.ROW_COUNT; row++)
                h.assertTrue(seen[row] == 1, at + "row " + row + " is on " + seen[row] + " pages");
        }
        h.succeed();
    }
}
