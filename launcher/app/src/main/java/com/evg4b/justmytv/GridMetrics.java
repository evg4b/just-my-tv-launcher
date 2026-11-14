package com.evg4b.justmytv;

/** Grid sizes from the screen and the column count (PLAN 7.2), and the focus scroll rule. Pure, JVM-tested. */
final class GridMetrics {
    static final float FOCUS_SCALE = 1.10f;
    static final int CEILING_DP = 120;   // rows scrolled up stop below the header

    final int tileW, tileH, itemH, barTop, cornerPx, labelMargin;

    GridMetrics(int widthPx, int heightPx, float density, int columns, int rowsBelow, int cornerDp) {
        float cell = (widthPx - 2 * 44 * density) / columns;
        float w = Math.max(80 * density, Math.min(180 * density, (cell - 12 * density) / FOCUS_SCALE));
        tileW = Math.round(w);
        tileH = Math.round(w * 101 / 180f);
        itemH = tileH + Math.round(45 * density);               // padding 8 + 8, gap 7, label 22
        // 8 dp padding + 18 dp peek; the band (8 dp above the tiles) stays under the header, or big tiles with rows
        // below would cover the clock and the weather
        barTop = Math.max(Math.round((CEILING_DP + 8) * density), Math.round(heightPx - (1 + rowsBelow) * itemH - 26 * density));
        cornerPx = Math.round(cornerDp * density * Math.min(1f, w / (180 * density)));
        labelMargin = tileH + Math.round(7 * density);          // from the item's top padding edge
    }

    /**
     * How far to scroll so the focused item is shown (PLAN 2.2, with CleanYourTV's fix: a row already on screen
     * stays put). The bar returns to its resting line; a row above the header ceiling comes down to it; a row
     * below the screen comes up to the bottom edge.
     */
    static int scrollDy(boolean bar, int itemTop, int itemBottom, int barTop, int ceiling, int height) {
        if (bar) return itemTop - barTop;
        if (itemTop < ceiling) return itemTop - ceiling;
        if (itemBottom > height) return itemBottom - height;
        return 0;
    }
}
