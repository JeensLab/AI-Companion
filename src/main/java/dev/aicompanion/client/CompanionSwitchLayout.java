package dev.aicompanion.client;

/**
 * The geometry of the behaviour screen's switch rows. Kept out of the screen so that a GameTest can check it without a
 * client: the rows stand one under another, closer together when they would reach the note under them, and go on pages
 * when they do not all fit — every switch must stay reachable on a small GUI (854×480 is 427×240 at GUI scale 2).
 * The first row opens the model screen; the rest are the gameplay switches.
 */
public final class CompanionSwitchLayout {
    public static final int ROW_WIDTH = 300, ROW_HEIGHT = 20, ROW_STEP = 30, HIGHEST_ROW = 64, NOTE_FROM_BOTTOM = 64, PAGED_STEP = 24;
    /** The rows the behaviour screen adds: the model entry first, then the gameplay switches. */
    public static final int MODEL_ENTRY_ROW = 0, BEHAVIOUR_ROWS = 20;
    /** The label of that first row: it opens the model screen, and the screen is not loaded where a GameTest runs. */
    public static final String MODEL_ENTRY_LABEL = "Модель и ответы…";

    private CompanionSwitchLayout() { }

    /** The y the rows would start at by the screen's own centring; never above the title and the top of the screen. */
    public static int top(int height) { return Math.max(HIGHEST_ROW, height / 2 - 90); }

    /** The lowest y a row may end at: above the note and the buttons under it. */
    public static int bottom(int height) { return height - NOTE_FROM_BOTTOM - 4; }

    /** How many rows fit on one page; at least one, so no row is lost on a very small GUI. */
    public static int perPage(int height) {
        return Math.max(1, (bottom(height) - HIGHEST_ROW - ROW_HEIGHT) / PAGED_STEP + 1);
    }

    /** The pages needed for {@code rows} rows. */
    public static int pages(int rows, int height) {
        int perPage = perPage(height);
        return Math.max(1, (rows + perPage - 1) / perPage);
    }

    /** The index of the first row of a page; a page past the last wraps to the first. */
    public static int firstRow(int rows, int height, int page) { return (page % pages(rows, height)) * perPage(height); }

    /** How many rows a page holds; the last page may hold fewer. */
    public static int rowsOnPage(int rows, int height, int page) { return Math.min(perPage(height), rows - firstRow(rows, height, page)); }

    /** The distance between rows on a page of {@code onPage} rows. */
    public static int step(int height, int onPage) {
        int gaps = Math.max(1, onPage - 1);
        return Math.max(ROW_HEIGHT, Math.min(ROW_STEP, (bottom(height) - HIGHEST_ROW - ROW_HEIGHT) / gaps));
    }

    /** The y of the first row of a page, clamped so that the last row stays above {@link #bottom(int)}. */
    public static int firstY(int top, int height, int onPage) {
        int gaps = Math.max(1, onPage - 1);
        return Math.max(HIGHEST_ROW, Math.min(top, bottom(height) - ROW_HEIGHT - step(height, onPage) * gaps));
    }
}
