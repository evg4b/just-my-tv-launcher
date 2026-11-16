package com.evg4b.justmytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The screensaver's animated gradient (SCREENSAVER 3): a slow colour field like an ambient lamp. Each palette holds
 * for 20 s, then eases into the next over 40 s; the angle turns once in 4 minutes. Colours come from the clock, not
 * from accumulated steps, so a dropped frame never shifts them. At most ~30 redraws a second: the GPU is slow (PLAN 11.3).
 */
public final class AmbientGradient extends View {
    static final int[][] PALETTES = {
            {0xFF2A1533, 0xFF7E3459, 0xFFD9714C},   // Dusk, Home's gradient: where it starts
            {0xFFE4577F, 0xFFF47D56, 0xFFFFBE8E},   // Sunset
            {0xFF2B2150, 0xFF6A4C93, 0xFFE0A3C8},   // Lavender
            {0xFF0F3A5C, 0xFF1D7A8C, 0xFF7FD1C7},   // Lagoon
            {0xFF13547A, 0xFF2B825B, 0xFF80D0C7},   // Aurora
    };
    static final long HOLD_MS = 20_000, FADE_MS = 40_000, TURN_MS = 240_000;
    private static final long FRAME_MS = 33;

    private final Paint paint = new Paint();
    private boolean running;

    public AmbientGradient(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        paint.setDither(true);   // against banding, as dusk.xml (D83)
    }

    /** Shown and animating, or gone. */
    void setRunning(boolean run) {
        running = run;
        setVisibility(run ? VISIBLE : GONE);
        if (run) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        long t = SystemClock.elapsedRealtime();
        int w = getWidth(), h = getHeight();
        double a = Math.toRadians(angleAt(t));
        float r = (float) Math.hypot(w, h) / 2f, cx = w / 2f, cy = h / 2f;
        float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
        paint.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, colorsAt(t), null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        if (running && isAttachedToWindow()) postInvalidateDelayed(FRAME_MS);
    }

    /** The three stops at time t (ms): palette k held, then eased into k+1; loops over PALETTES. */
    static int[] colorsAt(long t) {
        long step = HOLD_MS + FADE_MS;
        int k = (int) ((t / step) % PALETTES.length);
        long in = t % step;
        float f = in <= HOLD_MS ? 0f : (in - HOLD_MS) / (float) FADE_MS;
        f = f * f * (3 - 2 * f);   // smoothstep: no jolt where a fade starts or ends
        int[] from = PALETTES[k], to = PALETTES[(k + 1) % PALETTES.length], out = new int[3];
        for (int i = 0; i < 3; i++) out[i] = lerp(from[i], to[i], f);
        return out;
    }

    /** Degrees, 135 (top-left to bottom-right, as dusk.xml) at t = 0, one turn per TURN_MS. */
    static float angleAt(long t) { return (135f + 360f * (t % TURN_MS) / TURN_MS) % 360f; }

    static int lerp(int a, int b, float f) {
        int r = 0xFF000000;
        for (int s = 0; s <= 16; s += 8) {
            int x = (a >> s) & 0xFF, y = (b >> s) & 0xFF;
            r |= Math.round(x + (y - x) * f) << s;
        }
        return r;
    }
}
