package com.justmytv.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A banner cropped to fill (centerCrop) inside a round rect, drawn through a shader: no clipToOutline, which is slow on the TV GPU. */
final class RoundArt extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final BitmapShader shader;
    private final Bitmap bitmap;
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final float radius;

    RoundArt(Bitmap bitmap, float radius) {
        this.bitmap = bitmap;
        this.radius = radius;
        shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rect.set(b);
        float s = Math.max(b.width() / (float) bitmap.getWidth(), b.height() / (float) bitmap.getHeight());
        matrix.setScale(s, s);
        matrix.postTranslate(b.left + (b.width() - bitmap.getWidth() * s) / 2f, b.top + (b.height() - bitmap.getHeight() * s) / 2f);
        shader.setLocalMatrix(matrix);
    }

    @Override public void draw(Canvas c) { c.drawRoundRect(rect, radius, radius, paint); }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
