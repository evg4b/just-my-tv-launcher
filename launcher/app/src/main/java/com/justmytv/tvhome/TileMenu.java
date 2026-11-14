package com.justmytv.tvhome;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** The long-OK menu (PLAN 8): a PopupWindow with 56 dp rows, no animations, focus on the first item. */
final class TileMenu {
    interface Action { void run(); }

    private TileMenu() { }

    /** items: label, action pairs. */
    static PopupWindow show(View anchor, Object... items) {
        Context ctx = anchor.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(R.drawable.menu_bg);
        int pad = Math.round(8 * d);
        list.setPadding(pad, pad, pad, pad);
        PopupWindow w = new PopupWindow(list, Math.round(260 * d), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        w.setAnimationStyle(0);
        for (int i = 0; i < items.length; i += 2) {
            Action a = (Action) items[i + 1];
            TextView row = row(ctx, (String) items[i], d);
            row.setOnClickListener(v -> {
                w.dismiss();
                a.run();
            });
            list.addView(row);
        }
        // showAsDropDown cannot flip a WRAP_CONTENT popup, and under the bar it would run off the screen
        list.measure(View.MeasureSpec.makeMeasureSpec(w.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int gap = Math.round(4 * d), h = list.getMeasuredHeight();
        int y = at[1] + anchor.getHeight() + gap;
        if (y + h > anchor.getRootView().getHeight()) y = Math.max(0, at[1] - gap - h);
        w.showAtLocation(anchor, Gravity.TOP | Gravity.START, at[0], y);
        list.getChildAt(0).requestFocus();
        return w;
    }

    static TextView row(Context ctx, String text, float d) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(0xF2FFFFFF);
        t.setTypeface(ctx.getResources().getFont(R.font.outfit_regular));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        int p = Math.round(20 * d);
        t.setPadding(p, 0, p, 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackgroundResource(R.drawable.row_bg);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(56 * d)));
        return t;
    }
}
