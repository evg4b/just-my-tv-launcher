package com.justmytv.tvhome;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

/** Tiles (PLAN 7.3). Sizes, outline and listeners are set once per holder; onBind only sets content. */
final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface Listener {
        void onLaunch(App app);

        void onTileFocused(View card, int position);

        /** Move focus to a position (row wrap at the ends of a row); out of range means stay. */
        void focusPosition(int position);

        void onMenu(App app, View card);
    }

    /** Tile colours for apps without a banner (PLAN 5.3). */
    private static final int[] PALETTE = {0xFF37474F, 0xFF4E342E, 0xFF1B5E20, 0xFF0D47A1, 0xFF4A148C,
            0xFF880E4F, 0xFF3E2723, 0xFF263238, 0xFF33691E};
    private static final int BANNER_BG = 0xFF1C1C1E;

    private final GridMetrics m;
    private final int columns;
    private final boolean labels;
    private final Listener listener;
    private List<App> items = Collections.emptyList();

    AppAdapter(GridMetrics m, int columns, boolean labels, Listener listener) {
        this.m = m;
        this.columns = columns;
        this.labels = labels;
        this.listener = listener;
        setHasStableIds(true);   // a rescan keeps focus on the same app
    }

    List<App> items() { return items; }

    void setItems(List<App> apps) {
        items = apps;
        notifyDataSetChanged();
    }

    /** Move mode: one step of the tile being moved. */
    void move(int from, int to) {
        items.add(to, items.remove(from));
        notifyItemMoved(from, to);
        // the bar's "Up goes to the header" depends on the position
        notifyItemChanged(from);
        notifyItemChanged(to);
    }

    @Override public int getItemCount() { return items.size(); }

    @Override public long getItemId(int position) { return items.get(position).pkg.hashCode(); }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Holder h = new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false));
        ViewGroup.LayoutParams lp = h.card.getLayoutParams();
        lp.width = m.tileW;
        lp.height = m.tileH;
        ((ViewGroup.MarginLayoutParams) h.name.getLayoutParams()).topMargin = m.labelMargin;
        if (!labels) h.name.setVisibility(View.GONE);
        h.color.setCornerRadius(m.cornerPx);
        h.dust.setCornerRadius(m.cornerPx);
        h.card.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onLaunch(items.get(p));
        });
        h.card.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onMenu(items.get(p), v);
            return true;
        });
        h.card.setOnFocusChangeListener((v, has) -> {
            float s = has ? GridMetrics.FOCUS_SCALE : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(180).start();
            h.name.animate().alpha(has ? 1f : 0f).setDuration(150).start();
            int p = h.getBindingAdapterPosition();
            if (has && p != RecyclerView.NO_POSITION) listener.onTileFocused(v, p);
        });
        // Left and Right at the end of a row go on to the neighbouring row, if there is one (PLAN 7.6)
        h.card.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return false;
            boolean right = code == KeyEvent.KEYCODE_DPAD_RIGHT, left = code == KeyEvent.KEYCODE_DPAD_LEFT;
            if (right && (p % columns == columns - 1 || p == items.size() - 1)) listener.focusPosition(p + 1);
            else if (left && p % columns == 0) listener.focusPosition(p - 1);
            else return false;
            return true;
        });
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int pos) {
        App a = items.get(pos);
        boolean banner = a.hasBanner();
        // banners may be transparent: the dark tile colour stays under them
        h.card.setBackground(banner ? new LayerDrawable(new Drawable[]{h.color, new RoundArt(a.art, m.cornerPx)}) : h.bg);
        h.icon.setImageBitmap(banner ? null : a.art);
        h.icon.setVisibility(banner || a.art == null ? View.GONE : View.VISIBLE);
        h.title.setText(a.label);
        h.title.setVisibility(banner ? View.GONE : View.VISIBLE);
        h.color.setColor(banner ? BANNER_BG : PALETTE[Math.abs(a.pkg.hashCode() % PALETTE.length)]);
        h.name.setText(a.label);
        h.card.setContentDescription(a.label);
        // Up from the bar goes to the header buttons (PLAN 7.6)
        h.card.setNextFocusUpId(pos < columns ? R.id.btn_settings : View.NO_ID);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView icon;
        final TextView title, name;
        final GradientDrawable color = new GradientDrawable();
        // the "dust" over a coloured tile: 10 % white at the top, 14 % black at the bottom (PLAN 5.3)
        final GradientDrawable dust = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x1AFFFFFF, 0x24000000});
        final Drawable bg = new LayerDrawable(new Drawable[]{color, dust});

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.card);
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            name = v.findViewById(R.id.name);
        }
    }
}
