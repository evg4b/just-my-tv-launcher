package com.justmytv.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Rows scrolled up under the header fade out above the ceiling instead of drawing over the clock.
 * Based on CleanYourTV TopFade (MIT): erases the top of the grid's own picture, so the wallpaper shows through;
 * the grid needs a hardware layer for that, or the erase would punch through the window.
 */
final class TopFade extends RecyclerView.ItemDecoration {
    private final Paint paint = new Paint();
    private final float ceiling;

    private TopFade(float ceiling, float length) {
        this.ceiling = ceiling;
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        // opaque (fully erased) above ceiling-length, clear at the ceiling
        paint.setShader(new LinearGradient(0, ceiling - length, 0, ceiling, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
    }

    static void attach(RecyclerView grid, float ceiling, float length) {
        grid.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        grid.addItemDecoration(new TopFade(ceiling, length));
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (parent.computeVerticalScrollOffset() == 0) return;   // at rest nothing is up there
        c.drawRect(0, 0, parent.getWidth(), ceiling, paint);
    }
}
