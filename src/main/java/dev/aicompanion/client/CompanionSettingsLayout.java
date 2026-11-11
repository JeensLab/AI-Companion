package dev.aicompanion.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The arithmetic of the settings page at a GUI size, with no client class, so the screen and the GameTests read the same
 * places. It exists because the page has to stay honest at every auto scale the client picks: 320×240 and 384×288 are
 * reachable, and at those sizes «Метки» sat inside «Вещи после смерти» and the last field row painted over the row above.
 * <p>
 * The rules it keeps: the four header entries never cover each other — below {@link #ONE_ROW_MIN_WIDTH} px they take two
 * rows; a label never reaches the field above it ({@link #ROW_PITCH_MIN} is the least pitch that leaves the 9-px glyphs
 * clear of a field that starts {@link #FIELD_OFFSET} px under its own label and is {@link #FIELD_HEIGHT} px tall); and
 * the field rows stop above the note and the footer, so a height that cannot hold every row gets pages instead of
 * overlaps. A row that is on another page keeps its value: the screen hides it, it does not rebuild it.
 */
public final class CompanionSettingsLayout {
    public static final int ENTRY_HEIGHT = 20;
    public static final int ROW_COUNT = 6;
    public static final int FIELD_HEIGHT = 20;
    /** The field starts this far under its own label. */
    public static final int FIELD_OFFSET = 17;
    /** The height of a label's glyphs. */
    public static final int LABEL_HEIGHT = 9;
    public static final int ROW_PITCH_MIN = 37;
    public static final int ROW_PITCH_MAX = 48;
    /**
     * Below this GUI width the four entries need two header rows: one row is «Память» 6..94, «Вещи после смерти»
     * 100..250, «Метки» 250..320 and «Поведение» 324..412 — 418 px with a 2-px gap between the middle two, and every
     * entry 6 px off the right edge.
     */
    public static final int ONE_ROW_MIN_WIDTH = 418;
    /** The note line under the fields, and the footer buttons under it. */
    public static final int NOTE_FROM_BOTTOM = 62;
    public static final int FOOTER_FROM_BOTTOM = 42;

    private static final int MARGIN = 6;
    private static final int MEMORY_WIDTH = 88;
    private static final int BEHAVIOR_WIDTH = 88;
    private static final int MARKERS_WIDTH = 70;
    private static final int DEATH_X = 100;
    private static final int DEATH_WIDTH_MIN = 70;
    private static final int DEATH_WIDTH_MAX = 150;
    private static final int HEADER_Y = 8;
    private static final int SECOND_HEADER_Y = 30;
    /** The first field row: under one header row, or under two. */
    private static final int FIRST_Y_ONE_ROW = 36;
    private static final int FIRST_Y_TWO_ROWS = 58;

    private CompanionSettingsLayout() { }

    /** A rectangle on the screen: what the screen places, and what the tests compare. */
    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean overlaps(Box other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
        public boolean inside(int width, int height) {
            return x >= 0 && y >= 0 && right() <= width && bottom() <= height;
        }
    }

    /** The four entries of the header; {@code titleFrom..titleTo} is the span the centred title may use. */
    public record Header(Box memory, Box death, Box behavior, Box markers, int titleFrom, int titleTo, int firstY) {
        public List<Box> entries() { return List.of(memory, death, behavior, markers); }
    }

    /** One field row of a page: its label, and the box of its first field. */
    public record Row(int index, int labelY, Box field) { }

    /** The footer buttons; {@code pager} is null when every row fits. */
    public record Footer(Box back, Box save, Box pager) {
        public List<Box> boxes() {
            List<Box> boxes = new ArrayList<>(List.of(back, save));
            if (pager != null) boxes.add(pager);
            return boxes;
        }
    }

    /**
     * The header at this width: one row from {@link #ONE_ROW_MIN_WIDTH}, two rows below it, so «Метки» never sits inside
     * «Вещи после смерти» and no entry runs past the right edge. Two rows leave no span for the title, so the screen
     * draws none there; one row gives the title whatever the entries leave.
     */
    public static Header header(int width) {
        if (width >= ONE_ROW_MIN_WIDTH) {
            int behaviorX = width - 94;
            int markersX = behaviorX - MARKERS_WIDTH - 4;
            int deathWidth = Math.clamp(markersX - DEATH_X - 2, DEATH_WIDTH_MIN, DEATH_WIDTH_MAX);
            Box memory = new Box(MARGIN, HEADER_Y, MEMORY_WIDTH, ENTRY_HEIGHT);
            Box death = new Box(DEATH_X, HEADER_Y, deathWidth, ENTRY_HEIGHT);
            Box markers = new Box(markersX, HEADER_Y, MARKERS_WIDTH, ENTRY_HEIGHT);
            Box behavior = new Box(behaviorX, HEADER_Y, BEHAVIOR_WIDTH, ENTRY_HEIGHT);
            return new Header(memory, death, behavior, markers, death.right() + 1, markers.x() - 1, FIRST_Y_ONE_ROW);
        }
        int deathWidth = Math.clamp(width - DEATH_X - MARGIN, DEATH_WIDTH_MIN, DEATH_WIDTH_MAX);
        Box memory = new Box(MARGIN, HEADER_Y, MEMORY_WIDTH, ENTRY_HEIGHT);
        Box death = new Box(DEATH_X, HEADER_Y, deathWidth, ENTRY_HEIGHT);
        Box markers = new Box(MARGIN, SECOND_HEADER_Y, MARKERS_WIDTH, ENTRY_HEIGHT);
        Box behavior = new Box(MARGIN + MARKERS_WIDTH + 6, SECOND_HEADER_Y, BEHAVIOR_WIDTH, ENTRY_HEIGHT);
        // titleFrom > titleTo: the two rows take the band, and the screen draws no title over them.
        return new Header(memory, death, behavior, markers, width, 0, FIRST_Y_TWO_ROWS);
    }

    /** The pitch of the field rows: 37 px at the least, 48 px at the most, so a label never reaches the field above it. */
    public static int rowGap(int height) {
        return Math.min(ROW_PITCH_MAX, Math.max(ROW_PITCH_MIN, (height - 110) / ROW_COUNT));
    }

    /** The top of the note line: the field rows stop above it. */
    public static int noteY(int height) {
        return height - NOTE_FROM_BOTTOM;
    }

    /** The bottom of the room the field rows have: above the note line with 4 px to spare. */
    public static int contentBottom(int height) {
        return noteY(height) - 4;
    }

    /** How many field rows fit above the note at this GUI size, at the pitch the page uses. */
    public static int rowsPerPage(int width, int height) {
        int firstY = header(width).firstY();
        int gap = rowGap(height);
        int fit = (contentBottom(height) - firstY - FIELD_OFFSET - FIELD_HEIGHT) / gap + 1;
        return Math.clamp(fit, 1, ROW_COUNT);
    }

    /** The pages the field rows take at this GUI size; 1 when every row fits. */
    public static int pages(int width, int height) {
        int perPage = rowsPerPage(width, height);
        return (ROW_COUNT + perPage - 1) / perPage;
    }

    /**
     * The rows of one page, in the order they are laid out: the page's first row takes the first place under the header
     * and every next row the next pitch, so the page holds rows it has room for and no more.
     */
    public static List<Row> page(int width, int height, int page) {
        int perPage = rowsPerPage(width, height);
        int pages = (ROW_COUNT + perPage - 1) / perPage;
        int shown = Math.clamp(page, 0, pages - 1);
        int from = shown * perPage;
        int firstY = header(width).firstY();
        int gap = rowGap(height);
        int fieldWidth = Math.min(380, width - 80);
        int left = fieldLeft(width);
        List<Row> rows = new ArrayList<>();
        for (int index = from; index < Math.min(ROW_COUNT, from + perPage); index++) {
            int labelY = firstY + (index - from) * gap;
            rows.add(new Row(index, labelY, field(left, fieldWidth, index, labelY + FIELD_OFFSET)));
        }
        return rows;
    }

    /** The left edge of every field and label: the column the page is laid out in. */
    public static int fieldLeft(int width) {
        int fieldWidth = Math.min(380, width - 80);
        return (width - fieldWidth) / 2;
    }

    /** The band a row's label takes: the tests compare it with the field of the row above. */
    public static Box label(int width, Row row) {
        return new Box(fieldLeft(width), row.labelY(), Math.min(380, width - 80), LABEL_HEIGHT);
    }

    /** The right edge of the widest field of a row: the second column of «Имя компаньона» and «Модель» ends here too. */
    public static int fieldRight(int width) {
        int fieldWidth = Math.min(380, width - 80);
        return (width - fieldWidth) / 2 + fieldWidth;
    }

    private static Box field(int left, int fieldWidth, int index, int y) {
        int width = index == 1 || index == 3 ? fieldWidth - 70
                : index == 0 || index == 2 ? fieldWidth / 2 - 8 : fieldWidth;
        return new Box(left, y, width, FIELD_HEIGHT);
    }

    /**
     * The footer: «Назад» and «Сохранить» with the page button between them when the rows take pages, so the three
     * buttons fit the narrowest GUI (320 px: 4..104, 110..210, 216..316) and none of them covers another.
     */
    public static Footer footer(int width, int height, int pages) {
        int y = height - FOOTER_FROM_BOTTOM;
        if (pages <= 1) return new Footer(new Box(width / 2 - 104, y, 100, 20), new Box(width / 2 + 4, y, 100, 20), null);
        return new Footer(new Box(width / 2 - 156, y, 100, 20), new Box(width / 2 + 56, y, 100, 20),
                new Box(width / 2 - 50, y, 100, 20));
    }
}
