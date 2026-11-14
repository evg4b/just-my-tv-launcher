package com.evg4b.justmytv;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** The first row (positions 0..cols-1) is the bar; focus scrolls by {@link GridMetrics#scrollDy} (PLAN 2.2). */
final class BarGridLayoutManager extends GridLayoutManager {
    private final int cols, ceiling;

    BarGridLayoutManager(Context ctx, int cols, int ceiling) {
        super(ctx, cols, RecyclerView.VERTICAL, false);
        this.cols = cols;
        this.ceiling = ceiling;
    }

    @Override
    public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child, @NonNull Rect rect,
                                                 boolean immediate, boolean focusedChildVisible) {
        int pos = getPosition(child);
        int dy = GridMetrics.scrollDy(pos >= 0 && pos < cols, child.getTop(), child.getBottom(),
                parent.getPaddingTop(), ceiling, parent.getHeight());
        if (dy == 0) return false;
        if (immediate) parent.scrollBy(0, dy);
        else parent.smoothScrollBy(0, dy);
        return true;
    }
}
