package com.justmytv.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home screen: the app grid with the bar, the focus ring, the header, the tile menu (PLAN 7, 8, 9). */
public class MainActivity extends Activity implements AppAdapter.Listener {
    private static final int CEILING_DP = 120;     // rows scrolled up stop below the header
    private static final int FADE_DP = 36;
    private static final long WEATHER_EVERY = 30 * 60_000L;   // fixed (PLAN 4.6)
    private static final long WEATHER_OLD = 3 * 3600_000L;    // older than this gets the status dot (PLAN 4.7)

    private final Handler ui = new Handler(Looper.getMainLooper());
    // a full pool takes all the CPU on a weak TV and drops frames (PLAN 5.2)
    private final ExecutorService pool =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private final ExecutorService io = Executors.newSingleThreadExecutor();   // state writes, in order
    private final Runnable rescan = this::load;
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            ui.removeCallbacks(rescan);
            ui.postDelayed(rescan, 500);   // several events in a row make one scan (PLAN 9.4)
        }
    };

    private LauncherState store;
    StateJson state;
    private RecyclerView grid;
    private AppAdapter adapter;
    private AppArt art;
    private FocusGlass glass;
    /** Every launchable app from the last scan, hidden ones included (the Apps screen lists them). */
    List<App> installed = new ArrayList<>();
    private int generation;
    private int lastPos;        // last focused tile; Down from the header returns to it if it is on the bar
    private boolean artDropped, focusedOnce;
    private String movingPkg;   // Move mode (PLAN 8)
    private PopupWindow menu;
    private SidePanel panel;
    private Screens screens;
    private View opener;        // what had focus when the panel opened; it gets it back
    final WeatherClient weather = new WeatherClient();
    private boolean weatherFailed;   // the last refresh failed: show when the cache is from
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            refreshWeather();
            ui.postDelayed(this, WEATHER_EVERY);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new LauncherState(this);
        state = StateJson.parse(store.read());   // a few KB; the grid size depends on it
        art = new AppArt(this);
        glass = new FocusGlass(findViewById(R.id.glass_host), getResources().getDisplayMetrics().density);

        grid = findViewById(R.id.grid);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { glass.scrolled(dy); }
        });
        applyLayout();

        panel = new SidePanel(findViewById(R.id.panel), findViewById(R.id.veil), () -> {
            opener = getCurrentFocus();
            glass.hide();
            blockBehindPanel(true);
        }, () -> {
            blockBehindPanel(false);
            if (opener != null && opener.isAttachedToWindow() && opener.isShown()) opener.requestFocus();
            else focusPosition(Math.min(lastPos, adapter.getItemCount() - 1));
        });
        screens = new Screens(this, panel);
        header(R.id.btn_inputs, () -> panel.open(screens.inputs));
        header(R.id.btn_network, () -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        header(R.id.btn_settings, () -> panel.open(screens.root));
        applyClock();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        // for the activity's whole life, not onStart/onStop: an app installed while another app is open shows up too
        registerReceiver(packages, f);
        load();
    }

    /** (Re)build the grid from the settings: columns, rows below the bar, labels, corners (PLAN 7.2, 10.1). */
    void applyLayout() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float d = dm.density;
        GridMetrics m = new GridMetrics(dm.widthPixels, dm.heightPixels, d, state.columns, state.rowsBelow, state.corner);
        int ceiling = Math.round(CEILING_DP * d);
        List<App> items = adapter == null ? Collections.emptyList() : adapter.items();
        adapter = new AppAdapter(m, state.columns, state.labels, this);
        adapter.setItems(items);
        while (grid.getItemDecorationCount() > 0) grid.removeItemDecorationAt(0);
        grid.setPadding(grid.getPaddingLeft(), m.barTop, grid.getPaddingRight(), 0);
        grid.setLayoutManager(new BarGridLayoutManager(this, state.columns, ceiling));
        grid.addItemDecoration(new BarBand(d, state.columns));
        TopFade.attach(grid, ceiling, FADE_DP * d);
        grid.setAdapter(adapter);
        glass.setCorner(m.cornerPx);
        glass.hide();
    }

    private void header(int id, Runnable click) {
        View b = findViewById(id);
        b.setOnClickListener(v -> click.run());
        b.setOnFocusChangeListener((v, has) -> { if (has) glass.hide(); });
        b.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) focusPosition(lastPos < state.columns ? lastPos : 0);
            return true;
        });
    }

    /** Nothing behind an open panel takes focus, so the D-pad stays inside it. */
    private void blockBehindPanel(boolean block) {
        int f = block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        grid.setDescendantFocusability(f);
        ((ViewGroup) findViewById(R.id.header_buttons)).setDescendantFocusability(f);
    }

    /** 12/24 h, the date and the greeting (PLAN 10.1, Clock). */
    void applyClock() {
        String fmt = state.h24 ? "HH:mm" : "h:mm";
        TextClock clock = findViewById(R.id.clock);
        clock.setFormat12Hour(fmt);
        clock.setFormat24Hour(fmt);
        int date = state.showDate ? View.VISIBLE : View.GONE;
        findViewById(R.id.date).setVisibility(date);
        findViewById(R.id.date_divider).setVisibility(date);
        TextView g = findViewById(R.id.greeting);
        g.setVisibility(state.greeting ? View.VISIBLE : View.GONE);
        if (state.greeting) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            g.setText(h >= 5 && h < 12 ? R.string.greeting_morning : h >= 12 && h < 17 ? R.string.greeting_afternoon
                    : h >= 17 && h < 23 ? R.string.greeting_evening : R.string.greeting_night);
        }
        showWeather();
    }

    // ---- weather (PLAN 4) ----

    void refreshWeather() {
        if (!state.weatherEnabled || !state.hasCity()) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) {   // no network: keep the cache, say how old it is
            weatherFailed = true;
            showWeather();
            return;
        }
        double lat = state.lat, lon = state.lon;
        weather.forecast(lat, lon, state.fahrenheit, (body, now) -> {
            if (lat != state.lat || lon != state.lon) return;   // the city changed meanwhile
            weatherFailed = body == null;
            if (body != null) {
                state.weatherLast = body;
                state.fetchedAt = System.currentTimeMillis();
                save();
            }
            showWeather();
        });
    }

    /** The cached forecast under the clock; hidden when off, without a city or before the first answer. */
    void showWeather() {
        View row = findViewById(R.id.weather);
        WeatherParser.Now now = state.weatherEnabled && state.hasCity() ? WeatherParser.parseForecast(state.weatherLast) : null;
        row.setVisibility(now == null ? View.GONE : View.VISIBLE);
        if (now == null) return;
        ((ImageView) findViewById(R.id.wx_icon)).setImageResource(WeatherCode.res(WeatherCode.icon(now.code, now.isDay)));
        ((TextView) findViewById(R.id.wx_temp)).setText(now.temp + "°");
        ((TextView) findViewById(R.id.wx_city)).setText(state.city);
        boolean old = System.currentTimeMillis() - state.fetchedAt > WEATHER_OLD;
        findViewById(R.id.wx_dot).setVisibility(old ? View.VISIBLE : View.GONE);
        TextView updated = findViewById(R.id.wx_updated);
        updated.setVisibility(old || weatherFailed ? View.VISIBLE : View.GONE);
        String time = new SimpleDateFormat(state.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date(state.fetchedAt));
        updated.setText(getString(R.string.weather_updated, time));
    }

    /** The Inputs icon shows only when it is wanted and there is an input to switch to (PLAN 7.7). */
    void updateInputsButton() {
        boolean show = state.showInputsButton && !Inputs.list(this, state.inputsConnectedOnly).isEmpty();
        findViewById(R.id.btn_inputs).setVisibility(show ? View.VISIBLE : View.GONE);
        if (panel.showing(screens.inputs)) panel.refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyClock();   // the greeting follows the time of day
        updateInputsButton();
        showWeather();
        // refresh at once if the cache is older than the interval, then every 30 minutes while Home is shown
        long age = System.currentTimeMillis() - state.fetchedAt;
        ui.postDelayed(weatherTick, Math.max(0, WEATHER_EVERY - age));
        if (artDropped) {
            artDropped = false;
            loadArt(installed, generation);
        }
    }

    @Override
    protected void onStop() {
        if (movingPkg != null) endMove();
        if (menu != null) menu.dismiss();
        panel.close();
        ui.removeCallbacks(weatherTick);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        ui.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        weather.shutdown();
        io.shutdown();   // let a pending state write finish
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
        for (App a : installed) a.art = null;
        adapter.notifyDataSetChanged();
        artDropped = true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // BACK closes a panel; otherwise Home has nowhere to go back to, and finishing would only restart it
        if (panel.isOpen()) panel.back();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {   // the gear's key, where the remote has one (PLAN 7.6)
            if (panel.isOpen()) panel.close();
            else panel.open(screens.root);
            return true;
        }
        if (code == KeyEvent.KEYCODE_TV_INPUT) {   // only if the system passes it to us
            panel.open(screens.inputs);
            return true;
        }
        return super.onKeyDown(code, e);
    }

    /** Move mode takes the arrows, OK and Back while it is on (PLAN 8). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (movingPkg == null) return super.dispatchKeyEvent(e);
        int cols = state.columns;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (down(e)) moveBy(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down(e)) moveBy(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (down(e)) moveBy(-cols); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (down(e)) moveBy(cols); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (e.getAction() == KeyEvent.ACTION_UP) endMove();   // on UP, so the release does not click the tile
                return true;
            default: return super.dispatchKeyEvent(e);
        }
    }

    private static boolean down(KeyEvent e) { return e.getAction() == KeyEvent.ACTION_DOWN; }

    // ---- apps ----

    /** Scan on the pool, apply the saved order and hiding, show the list, then fill in banners as they are ready. */
    private void load() {
        int gen = ++generation;
        pool.execute(() -> {
            List<App> apps = AppScanner.scan(this);
            art.cleanup(apps);
            ui.post(() -> {
                if (gen != generation) return;
                // keep the bitmaps of apps that did not change, so a rescan does not blank the grid
                Map<String, App> old = new HashMap<>();
                for (App a : installed) old.put(a.pkg, a);
                for (App a : apps) {
                    App o = old.get(a.pkg);
                    if (o != null && o.lastUpdate == a.lastUpdate) a.art = o.art;
                }
                installed = apps;
                applyApps();
                if (panel.showing(screens.apps)) panel.refresh();
                loadArt(apps, gen);
            });
        });
    }

    /** The visible tiles from the scan and the state (PLAN 9.3); saves the state when the scan changed it. */
    void applyApps() {
        List<String> pkgs = new ArrayList<>(installed.size());
        Map<String, App> byPkg = new HashMap<>();
        for (App a : installed) {
            pkgs.add(a.pkg);
            byPkg.put(a.pkg, a);
        }
        AppListMerger.Result r = AppListMerger.merge(state.order, state.known, state.hidden, pkgs);
        if (!r.order.equals(state.order) || !r.known.equals(state.known)) {
            state.order = r.order;
            state.known = new LinkedHashSet<>(r.known);
            save();
        }
        List<App> visible = new ArrayList<>(r.visible.size());
        for (String p : r.visible) visible.add(byPkg.get(p));
        int keep = lastPos;
        adapter.setItems(visible);
        if (panel.isOpen()) return;   // focus goes back to the grid when the panel closes
        if (!focusedOnce && !visible.isEmpty()) {
            focusedOnce = true;
            int i = r.visible.indexOf(state.lastFocused);   // focus returns to the last launched app (PLAN 7.6)
            grid.post(() -> focusPosition(Math.max(0, i)));
        } else if (!visible.isEmpty() && (getCurrentFocus() == null || !getCurrentFocus().isAttachedToWindow())) {
            grid.post(() -> focusPosition(Math.min(keep, visible.size() - 1)));   // the focused tile was hidden
        }
    }

    private void loadArt(List<App> apps, int gen) {
        for (App a : apps) {
            if (a.art != null) continue;
            pool.execute(() -> {
                Bitmap b = art.load(a);
                ui.post(() -> {
                    if (gen != generation || b == null) return;
                    a.art = b;
                    int pos = adapter.items().indexOf(a);
                    if (pos >= 0) adapter.notifyItemChanged(pos);
                });
            });
        }
    }

    void save() {
        String json = state.toJson();   // snapshot on the main thread, write in order on io
        io.execute(() -> store.write(json));
    }

    // ---- focus ----

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

    // ---- menu and Move mode (PLAN 8) ----

    @Override
    public void onMenu(App a, View card) {
        if (movingPkg != null) return;
        menu = TileMenu.show(card,
                getString(R.string.open), (TileMenu.Action) () -> onLaunch(a),
                getString(R.string.move), (TileMenu.Action) () -> startMove(a),
                getString(R.string.hide), (TileMenu.Action) () -> setHidden(a.pkg, true),
                getString(R.string.app_details), (TileMenu.Action) () -> start(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null))));
    }

    void setHidden(String pkg, boolean hidden) {
        if (hidden ? !state.hidden.add(pkg) : !state.hidden.remove(pkg)) return;
        save();
        applyApps();
    }

    private void startMove(App a) {
        movingPkg = a.pkg;
        glass.setMoving(true);
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_SHORT).show();
    }

    private void moveBy(int delta) {
        List<App> items = adapter.items();
        int from = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).pkg.equals(movingPkg)) from = i;
        int to = Math.max(0, Math.min(items.size() - 1, from + delta));
        if (from < 0 || to == from) return;
        adapter.move(from, to);
        lastPos = to;
        grid.post(() -> {
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(to);
            if (vh == null) {
                focusPosition(to);
                return;
            }
            View card = vh.itemView.findViewById(R.id.card);
            card.requestFocus();
            vh.itemView.requestRectangleOnScreen(new Rect(0, 0, vh.itemView.getWidth(), vh.itemView.getHeight()));
            glass.follow(card);
        });
    }

    private void endMove() {
        List<String> visible = new ArrayList<>();
        for (App a : adapter.items()) visible.add(a.pkg);
        state.order = AppListMerger.reorder(state.order, visible);
        save();
        movingPkg = null;
        glass.setMoving(false);
    }

    // ---- launching ----

    @Override
    public void onLaunch(App a) {
        if (movingPkg != null) return;
        if (!a.pkg.equals(state.lastFocused)) {
            state.lastFocused = a.pkg;
            save();
        }
        start(new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.info.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    void start(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }
}
