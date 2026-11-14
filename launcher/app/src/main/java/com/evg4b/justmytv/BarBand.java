package com.evg4b.justmytv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** The band under the bar's tiles, drawn before the children (PLAN 2.2). */
final class BarBand extends RecyclerView.ItemDecoration {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int cols;
    private final float radius, padX, padTop, padBottom;

    BarBand(float density, int cols) {
        this.cols = cols;
        paint.setColor(0xD8141416);          // #141416 at 85 %: verify on the TV
        radius = 26 * density;
        padX = 22 * density;
        padTop = 14 * density;
        padBottom = 36 * density;            // covers the focused tile's label (gap 7 + label 22), as in the mockup
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x14FFFFFF);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(v);
            if (pos < 0 || pos >= cols) continue;
            View card = ((ViewGroup) v).getChildAt(0);   // the card (item_app.xml)
            float l = v.getLeft() + card.getLeft(), t = v.getTop() + card.getTop();
            float rr = l + card.getWidth(), b = t + card.getHeight();
            if (!any) { r.set(l, t, rr, b); any = true; }
            else r.union(l, t, rr, b);
        }
        if (!any) return;
        r.left -= padX;
        r.right += padX;
        r.top -= padTop;
        r.bottom += padBottom;
        c.drawRoundRect(r, radius, radius, paint);
        c.drawRoundRect(r, radius, radius, edge);
    }
}
