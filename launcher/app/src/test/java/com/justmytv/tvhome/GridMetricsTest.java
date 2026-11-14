package com.justmytv.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GridMetricsTest {
    @Test public void fiveColumnsOnTheTv() {               // 1920x1080 px, density 2: the table in PLAN 7.2
        GridMetrics m = new GridMetrics(1920, 1080, 2f, 5, 0, 16);
        assertEquals(295, m.tileW);                          // 147.6 dp
        assertEquals(166, m.tileH);                          // 83 dp
        assertEquals(256, m.itemH);                          // 128 dp
        assertEquals(772, m.barTop);                         // 386 dp
        assertEquals(26, m.cornerPx);                        // 13 dp
        assertEquals(180, m.labelMargin);
    }

    @Test public void fourColumnsHitTheMaximum() {
        assertEquals(360, new GridMetrics(1920, 1080, 2f, 4, 0, 16).tileW);   // 180 dp
    }

    @Test public void rowsBelowLiftTheBar() {
        assertEquals(772 - 256, new GridMetrics(1920, 1080, 2f, 5, 1, 16).barTop);
        assertEquals(0, new GridMetrics(1920, 1080, 2f, 5, 0, 0).cornerPx);
    }

    @Test public void scroll() {
        // the bar comes back to its line from wherever it is
        assertEquals(-200, GridMetrics.scrollDy(true, 572, 828, 772, 240, 1080));
        // a row below the screen comes up to the bottom edge
        assertEquals(204, GridMetrics.scrollDy(false, 1028, 1284, 772, 240, 1080));
        // a row fully on screen stays put
        assertEquals(0, GridMetrics.scrollDy(false, 500, 756, 772, 240, 1080));
        // a row under the header comes down to the ceiling
        assertEquals(-140, GridMetrics.scrollDy(false, 100, 356, 772, 240, 1080));
    }
}
