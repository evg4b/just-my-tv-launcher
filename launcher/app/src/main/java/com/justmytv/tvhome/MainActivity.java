package com.justmytv.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home screen: the app grid with the bar, the focus ring and the header (PLAN 7, milestone M1). */
public class MainActivity extends Activity implements AppAdapter.Listener {
    private static final int COLUMNS = 5;          // PLAN 7.2; becomes a setting in M3
    private static final int CEILING_DP = 120;     // rows scrolled up stop below the header
    private static final int FADE_DP = 36;

    private final Handler ui = new Handler(Looper.getMainLooper());
    // a full pool takes all the CPU on a weak TV and drops frames (PLAN 5.2)
    private final ExecutorService pool =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private final Runnable rescan = this::load;
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            ui.removeCallbacks(rescan);
            ui.postDelayed(rescan, 500);   // several events in a row make one scan (PLAN 9.4)
        }
    };

    private RecyclerView grid;
    private AppAdapter adapter;
    private AppArt art;
    private FocusGlass glass;
    private int generation;
    private int lastPos;        // last focused tile; Down from the header returns to it if it is on the bar
    private boolean artDropped;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float d = dm.density;
        GridMetrics m = new GridMetrics(dm.widthPixels, dm.heightPixels, d, COLUMNS, 0);
        int ceiling = Math.round(CEILING_DP * d);

        art = new AppArt(this);
        glass = new FocusGlass(findViewById(R.id.glass_host), d, m.cornerPx);
        adapter = new AppAdapter(m, COLUMNS, this);

        grid = findViewById(R.id.grid);
        grid.setPadding(grid.getPaddingLeft(), m.barTop, grid.getPaddingRight(), 0);
        grid.setLayoutManager(new BarGridLayoutManager(this, COLUMNS, ceiling));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new BarBand(d, COLUMNS));
        TopFade.attach(grid, ceiling, FADE_DP * d);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { glass.scrolled(dy); }
        });
        grid.setAdapter(adapter);

        header(R.id.btn_inputs, new Intent("com.android.tv.action.VIEW_INPUTS"));   // our panel comes in M3
        header(R.id.btn_network, new Intent(Settings.ACTION_WIFI_SETTINGS));
        header(R.id.btn_settings, new Intent(Settings.ACTION_SETTINGS));            // our panel comes in M3

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        // for the activity's whole life, not onStart/onStop: an app installed while another app is open shows up too
        registerReceiver(packages, f);
        load();
    }

    private void header(int id, Intent intent) {
        View b = findViewById(id);
        b.setOnClickListener(v -> start(intent));
        b.setOnFocusChangeListener((v, has) -> { if (has) glass.hide(); });
        b.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) focusPosition(lastPos < COLUMNS ? lastPos : 0);
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (artDropped) {
            artDropped = false;
            loadArt(adapter.items(), generation);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        ui.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        super.onDestroy();
    }

    /**
     * Memory (PLAN 5.6): when the system is short of memory while Home is in the background, drop the bitmaps; they
     * come back from the WebP cache in onStart. Not on every UI_HIDDEN: that would reload all tiles after each app.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_BACKGROUND || artDropped) return;
        for (App a : adapter.items()) a.art = null;
        adapter.notifyDataSetChanged();
        artDropped = true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; finishing would only restart it with a flash.
    }

    /** Scan on the pool, show the list, then fill in banners as they are ready. */
    private void load() {
        int gen = ++generation;
        pool.execute(() -> {
            List<App> apps = AppScanner.scan(this);
            art.cleanup(apps);
            ui.post(() -> {
                if (gen != generation) return;
                // keep the bitmaps of apps that did not change, so a rescan does not blank the grid
                Map<String, App> old = new HashMap<>();
                for (App a : adapter.items()) old.put(a.pkg, a);
                for (App a : apps) {
                    App o = old.get(a.pkg);
                    if (o != null && o.lastUpdate == a.lastUpdate) a.art = o.art;
                }
                boolean first = adapter.getItemCount() == 0;
                adapter.setItems(apps);
                if (first) focusFirst();
                loadArt(apps, gen);
            });
        });
    }

    private void loadArt(List<App> apps, int gen) {
        for (int i = 0; i < apps.size(); i++) {
            App a = apps.get(i);
            if (a.art != null) continue;
            int pos = i;
            pool.execute(() -> {
                Bitmap b = art.load(a);
                ui.post(() -> {
                    if (gen != generation || b == null) return;
                    a.art = b;
                    adapter.notifyItemChanged(pos);
                });
            });
        }
    }

    private void focusFirst() {
        grid.post(() -> focusPosition(0));
    }

    @Override
    public void focusPosition(int pos) {
        if (pos < 0 || pos >= adapter.getItemCount()) return;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        grid.scrollToPosition(pos);              // not laid out yet: bring it in, then focus it
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
            if (h != null) h.itemView.findViewById(R.id.card).requestFocus();
        });
    }

    @Override
    public void onTileFocused(View card, int pos) {
        lastPos = pos;
        glass.follow(card);
    }

    @Override
    public void onLaunch(App a) {
        start(new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.info.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void start(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }
}
