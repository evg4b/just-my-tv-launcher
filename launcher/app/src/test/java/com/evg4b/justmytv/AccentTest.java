package com.evg4b.justmytv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class AccentTest {
    private static int[] fill(int argb) {
        int[] p = new int[32 * 18];
        Arrays.fill(p, argb);
        return p;
    }

    @Test public void blackAndGreyGiveWhite() {
        assertEquals(Accent.WHITE, Accent.of(fill(0xFF000000)));
        assertEquals(Accent.WHITE, Accent.of(fill(0xFF808080)));
        assertEquals(Accent.WHITE, Accent.of(fill(0xFF0A0A0C)));   // almost black, a hint of blue
        assertEquals(Accent.WHITE, Accent.of(new int[0]));
    }

    @Test public void blueGivesALightBlue() {
        int c = Accent.of(fill(0xFF0D47A1));
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        assertEquals(255, b);
        assertTrue(r < g && g < b && r >= 130);   // light, not the deep blue itself
    }

    @Test public void auroraGivesTeal() {   // the gradient's three colours (res/drawable/aurora.xml)
        int[] p = fill(0xFF13547A);
        Arrays.fill(p, p.length / 3, 2 * p.length / 3, 0xFF2B825B);
        Arrays.fill(p, 2 * p.length / 3, p.length, 0xFF80D0C7);
        int c = Accent.of(p);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        assertTrue(g > r && b > r);   // green and blue lead
    }
}
