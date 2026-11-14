package com.justmytv.tvhome;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntConsumer;

/**
 * The 440 dp panel on the right (PLAN 10.2): slides in over 260 ms, holds a stack of screens built in code.
 * BACK in a subsection returns to the previous screen; at the root it closes the panel.
 */
final class SidePanel {
    interface Screen { void build(SidePanel p); }

    interface Toggle { void set(boolean on); }

    private final FrameLayout panel;
    private final View veil;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final Context ctx;
    private final float d;
    private final int width;
    private final Deque<Screen> stack = new ArrayDeque<>();
    private final Deque<Integer> focusStack = new ArrayDeque<>();   // the row that opened each subsection
    private final Runnable onOpen, onClose;
    private boolean open;   // not the visibility: the panel stays visible while it slides out

    SidePanel(FrameLayout panel, View veil, Runnable onOpen, Runnable onClose) {
        this.panel = panel;
        this.veil = veil;
        this.onOpen = onOpen;
        this.onClose = onClose;
        ctx = panel.getContext();
        d = ctx.getResources().getDisplayMetrics().density;
        width = Math.round(440 * d);
        scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * d);
        content.setPadding(pad, Math.round(32 * d), pad, pad);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll);
        panel.setTranslationX(width);
    }

    boolean isOpen() { return open; }

    void open(Screen root) {
        stack.clear();
        focusStack.clear();
        stack.push(root);
        render(0, 0);
        if (open) return;
        open = true;
        onOpen.run();
        panel.setVisibility(View.VISIBLE);
        veil.setVisibility(View.VISIBLE);
        panel.animate().translationX(0).setDuration(260).start();
    }

    void close() {
        if (!open) return;
        open = false;
        panel.animate().translationX(width).setDuration(260).withEndAction(() -> panel.setVisibility(View.GONE)).start();
        veil.setVisibility(View.GONE);
        onClose.run();
    }

    void push(Screen s) {
        focusStack.push(focusedRow());
        stack.push(s);
        render(1, 0);
    }

    void back() {
        if (stack.size() <= 1) {
            close();
            return;
        }
        stack.pop();
        render(-1, focusStack.isEmpty() ? 0 : focusStack.pop());
    }

    /** Rebuild the current screen after a change elsewhere (a scan, a new weather result), keeping the focused row. */
    void refresh() {
        if (isOpen()) render(0, focusedRow());
    }

    boolean showing(Screen s) { return isOpen() && stack.peek() == s; }

    private int focusedRow() {
        View f = content.findFocus();
        while (f != null && f.getParent() != content) f = (View) f.getParent();
        return f == null ? 0 : content.indexOfChild(f);
    }

    private void render(int dir, int focusIndex) {
        content.removeAllViews();
        stack.peek().build(this);
        if (dir != 0) {   // forward: comes in from the right; back: from the left
            content.setTranslationX(dir * 40 * d);
            content.setAlpha(0f);
            content.animate().translationX(0).alpha(1f).setDuration(160).start();
        }
        View target = content.getChildAt(Math.min(focusIndex, content.getChildCount() - 1));
        if (target == null || !target.isFocusable()) target = firstFocusable();
        if (target != null) target.requestFocus();
        else scroll.requestFocus();
        if (focusIndex == 0) scroll.scrollTo(0, 0);
    }

    private View firstFocusable() {
        for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i).isFocusable()) return content.getChildAt(i);
        return null;
    }

    // ---- building blocks ----

    void title(String text) {
        TextView t = text(text, 22, 0xF2FFFFFF, R.font.outfit_medium);
        t.setPadding(Math.round(12 * d), 0, 0, Math.round(16 * d));
        content.addView(t);
    }

    void note(String text) {
        TextView t = text(text, 14, 0x8CFFFFFF, R.font.outfit_regular);
        t.setLineSpacing(4 * d, 1f);
        t.setPadding(Math.round(12 * d), Math.round(8 * d), Math.round(12 * d), Math.round(8 * d));
        content.addView(t);
    }

    /** A row: label on the left, value on the right (may be null); returns the value view so callers can update it. */
    TextView item(String label, String value, Runnable onClick) {
        return item(null, label, value, onClick);
    }

    /** icon: an app's banner or icon (a bitmap, 64x36 dp box) or a 22 dp interface vector. */
    TextView item(Drawable icon, String label, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.row_bg);
        int p = Math.round(12 * d);
        row.setPadding(p, 0, p, 0);
        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            boolean art = icon instanceof BitmapDrawable;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round((art ? 64 : 22) * d), Math.round((art ? 36 : 22) * d));
            lp.setMarginEnd(p);
            row.addView(iv, lp);
        }
        TextView l = text(label, 17, 0xF2FFFFFF, R.font.outfit_regular);
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(value == null ? "" : value, 14, 0x8CFFFFFF, R.font.outfit_regular);
        v.setPadding(p, 0, 0, 0);
        row.addView(v);
        if (onClick != null) row.setOnClickListener(x -> onClick.run());
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(56 * d)));
        return v;
    }

    /** A text field; OK on the remote opens the keyboard, its action key submits. */
    EditText edit(String hint, String text, java.util.function.Consumer<String> submit) {
        EditText e = new EditText(ctx);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(text);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setTextColor(0xF2FFFFFF);
        e.setHintTextColor(0x8CFFFFFF);
        e.setTypeface(ctx.getResources().getFont(R.font.outfit_regular));
        e.setBackgroundResource(R.drawable.row_bg);
        int p = Math.round(12 * d);
        e.setPadding(p, 0, p, 0);
        e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setOnEditorActionListener((v, action, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            String q = v.getText().toString().trim();
            if (!q.isEmpty()) submit.accept(q);
            // the TV keyboard stays up over the results otherwise; submit rebuilds the screen, so not v's token
            ctx.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(content.getWindowToken(), 0);
            return true;
        });
        content.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(56 * d)));
        return e;
    }

    /** A subsection row ("›"). */
    void section(String label, Screen s) {
        item(label, "›", () -> push(s));
    }

    void section(int icon, String label, Screen s) {
        item(ctx.getDrawable(icon), label, "›", () -> push(s));
    }

    void toggle(String label, boolean on, Toggle set) {
        boolean[] cur = {on};
        TextView[] v = new TextView[1];
        v[0] = item(label, onOff(on), () -> {
            cur[0] = !cur[0];
            v[0].setText(onOff(cur[0]));
            set.set(cur[0]);
        });
    }

    /** OK and Right go to the next value, Left to the previous one; the change applies at once. */
    void choice(String label, String[] names, int index, IntConsumer set) {
        int[] cur = {Math.max(0, index)};
        TextView[] v = new TextView[1];
        v[0] = item(label, "‹ " + names[cur[0]] + " ›", () -> step(names, cur, v[0], 1, set));
        View row = (View) v[0].getParent();
        row.setOnKeyListener((x, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) step(names, cur, v[0], code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, set);
            return true;
        });
    }

    private static void step(String[] names, int[] cur, TextView v, int by, IntConsumer set) {
        cur[0] = (cur[0] + by + names.length) % names.length;
        v.setText("‹ " + names[cur[0]] + " ›");
        set.accept(cur[0]);
    }

    private String onOff(boolean on) { return ctx.getString(on ? R.string.on : R.string.off); }

    private TextView text(String s, int sp, int color, int font) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(ctx.getResources().getFont(font));
        return t;
    }
}
