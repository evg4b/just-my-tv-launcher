package com.evg4b.justmytv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AmbientGradientTest {
    private static final long STEP = AmbientGradient.HOLD_MS + AmbientGradient.FADE_MS;

    @Test public void holdsThenEasesIntoTheNextPalette() {
        assertArrayEquals(AmbientGradient.PALETTES[0], AmbientGradient.colorsAt(0));
        assertArrayEquals(AmbientGradient.PALETTES[0], AmbientGradient.colorsAt(AmbientGradient.HOLD_MS));
        assertArrayEquals(AmbientGradient.PALETTES[1], AmbientGradient.colorsAt(STEP));
        int mid = AmbientGradient.colorsAt(AmbientGradient.HOLD_MS + AmbientGradient.FADE_MS / 2)[0];
        assertEquals(AmbientGradient.lerp(AmbientGradient.PALETTES[0][0], AmbientGradient.PALETTES[1][0], 0.5f), mid);
    }

    @Test public void loopsBackToTheFirstPalette() {
        assertArrayEquals(AmbientGradient.PALETTES[0], AmbientGradient.colorsAt(STEP * AmbientGradient.PALETTES.length));
    }

    @Test public void lerpEnds() {
        assertEquals(0xFF102030, AmbientGradient.lerp(0xFF102030, 0xFFF0E0D0, 0f));
        assertEquals(0xFFF0E0D0, AmbientGradient.lerp(0xFF102030, 0xFFF0E0D0, 1f));
        assertEquals(0xFF808080, AmbientGradient.lerp(0xFF000000, 0xFFFFFFFF, 0.5f));   // 127.5 rounds up
    }

    @Test public void angleTurnsOncePerTurn() {
        assertEquals(135f, AmbientGradient.angleAt(0), 0.001f);
        assertEquals(135f, AmbientGradient.angleAt(AmbientGradient.TURN_MS), 0.001f);
        assertEquals(315f, AmbientGradient.angleAt(AmbientGradient.TURN_MS / 2), 0.001f);
    }
}
