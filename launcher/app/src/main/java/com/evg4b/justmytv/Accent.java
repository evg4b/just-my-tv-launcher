package com.evg4b.justmytv;

/** The focus ring's colour from a few pixels of the screen: its main hue, kept light; white on a dark or grey screen. */
final class Accent {
    static final int WHITE = 0xFFFFFFFF;
    private static final float MIN_COLOUR = 0.08f;   // mean saturation x brightness below this: no hue worth showing
    private static final float SAT = 0.45f;          // as light as the old blue ring (#8AB4F8), readable on any photo

    private Accent() {}

    static int of(int[] argb) {
        double x = 0, y = 0, weight = 0;
        for (int p : argb) {
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max == min) continue;   // grey: no hue, no weight
            float s = (max - min) / (float) max, v = max / 255f;
            double h = Math.toRadians(hue(r, g, b, max, min));
            double w = s * v;            // vivid and bright pixels lead; dark corners and haze do not
            x += Math.cos(h) * w;
            y += Math.sin(h) * w;
            weight += w;
        }
        if (argb.length == 0 || weight / argb.length < MIN_COLOUR) return WHITE;
        double deg = Math.toDegrees(Math.atan2(y, x));
        return hsv((float) (deg < 0 ? deg + 360 : deg), SAT, 1f);
    }

    private static float hue(int r, int g, int b, int max, int min) {
        float d = max - min, h;
        if (max == r) h = (g - b) / d;
        else if (max == g) h = 2 + (b - r) / d;
        else h = 4 + (r - g) / d;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, hp = h / 60f, xx = c * (1 - Math.abs(hp % 2 - 1)), m = v - c;
        float r, g, b;
        if (hp < 1) { r = c; g = xx; b = 0; }
        else if (hp < 2) { r = xx; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = xx; }
        else if (hp < 4) { r = 0; g = xx; b = c; }
        else if (hp < 5) { r = xx; g = 0; b = c; }
        else { r = c; g = 0; b = xx; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
