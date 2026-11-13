package dev.aicompanion.client;

/**
 * The geometry of the model screen: its thinking switch, the two free-entry sampling fields and the reference block
 * under them. Kept out of the screen so that a GameTest can check it without a client at the GUI sizes the game allows
 * (854×480 is 427×240 at GUI scale 2; 1920×1080 is 960×540 at scale 2 and 1920×1080 at scale 1). Every row stays above
 * «Назад» and none overlaps another at any of them.
 */
public final class CompanionModelLayout {
    public static final int ROW_WIDTH = 300, ROW_HEIGHT = 20, FIELD_HEIGHT = 20, COLUMN_GAP = 20, LINE = 12;
    public static final int BACK_HEIGHT = 20, LABEL_HEIGHT = 9, HINT_LINES = 2;
    /**
     * The reference block the screen draws, one entry per row the layout keeps for it: «Справка», the answer limit per
     * attempt, both answer budgets, the empty field and a rejected value. Its length is the number of rows reserved.
     */
    public static final String[] REFERENCE = {
            "Справка",
            "Ответ — до 90 с на одну попытку.",
            "Бюджет: 1024 токена без размышления, 4096 с ним.",
            "Свои реплики: 600 и 2048 токенов.",
            "Пустое поле — модель отвечает со своими настройками.",
            "Отклонённое значение — ответа нет, придёт ошибка.",
    };

    private CompanionModelLayout() { }

    /** The content column: a margin on both sides, never wider than the row and the two fields need. */
    public static int contentWidth(int width) { return Math.max(200, Math.min(340, width - 40)); }

    /** The x of the content column. */
    public static int left(int width) { return (width - contentWidth(width)) / 2; }

    /** The width of the thinking switch: as wide as a row, within the content column. */
    public static int switchWidth(int width) { return Math.min(ROW_WIDTH, contentWidth(width)); }

    /** The x of the thinking switch, centred over the fields. */
    public static int switchX(int width) { return (width - switchWidth(width)) / 2; }

    /** The width of one sampling column: the two columns and the gap between them fill the content column. */
    public static int columnWidth(int width) { return (contentWidth(width) - COLUMN_GAP) / 2; }

    /** The y of the thinking switch: the first row under the title. */
    public static int thinkingY(int height) { return Math.max(30, height / 2 - 130); }

    /** The y of the field labels: under the switch and above the fields. */
    public static int labelsY(int height) { return thinkingY(height) + 28; }

    /** The y of the sampling fields. */
    public static int fieldsY(int height) { return labelsY(height) + LABEL_HEIGHT + 2; }

    /** The y of the line under the fields: the server's own rule, or why a typed value is not sent. */
    public static int hintY(int height) { return fieldsY(height) + FIELD_HEIGHT + 2; }

    /** The y of the first line of the reference block, under the hint's two rows. */
    public static int referenceTop(int height) { return hintY(height) + HINT_LINES * LINE + 2; }

    /** The y of «Назад». */
    public static int backY(int height) { return height - 35; }

    /** The lowest y the reference block may reach: above «Назад» and its margin. */
    public static int referenceBottom(int height) { return backY(height) - 15; }
}
