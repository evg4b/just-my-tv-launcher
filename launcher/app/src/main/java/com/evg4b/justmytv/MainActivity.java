package com.evg4b.justmytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home screen: the app grid with the bar, the focus ring, the header, the tile menu (PLAN 7, 8, 9). */
public class MainActivity extends Activity implements AppAdapter.Listener {
    private static final int FADE_DP = 36;
    private static final int REQ_VIDEO = 1, REQ_PICTURE = 2, REQ_ART = 3;
    private static final long WEATHER_EVERY = 30 * 60_000L;   // fixed (PLAN 4.6)
    private static final long WEATHER_RETRY = 2 * 60_000L;    // after a failure, as the photos

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
    // shortcut: lost if the system kills the launcher while the picker is open; then the pick is ignored
    private String artPkg;           // the app whose image is being chosen
    private FocusGlass glass;
    private Drawable posterSeen;     // the poster drawable the ring's colour was taken from
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
    private WallpaperController wallpaper;
    PhotoWallpaper photos;           // the Wallpaper settings read it
    private ImageView poster;
    private int inputsGen;           // a newer updateInputsButton makes older results stale
    private boolean started;
    private boolean weatherFailed;   // the last refresh failed: show when the cache is from
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            ui.postDelayed(this, WEATHER_EVERY);   // first: a failed refresh moves it closer
            refreshWeather();
        }
    };

    /** After a failed refresh: again in a few minutes, not at the next half hour (a TV wakes before its Wi-Fi). */
    private void retryWeatherSoon() {
        if (!started) return;
        ui.removeCallbacks(weatherTick);
        ui.postDelayed(weatherTick, WEATHER_RETRY);
    }

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
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                glass.scrolled(dy);
                TopFade.scrolled(rv);
            }
        });
        applyLayout();
        applyEdges();

        poster = findViewById(R.id.poster);
        wallpaper = new WallpaperController(this, findViewById(R.id.video), poster, findViewById(R.id.dim),
                findViewById(R.id.photo_credit), null, glass::match, () -> {
                    if (panel != null && panel.showing(screens.wallpaper)) panel.refresh();
                });
        photos = wallpaper.photos;
        // every wallpaper (picture, photo, gradient, black, a video's poster) lands in the poster: tint on each change
        poster.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (poster.getDrawable() != posterSeen) {
                posterSeen = poster.getDrawable();
                glass.match(posterSeen);
            }
            return true;
        });
        applyWallpaper();
        // the poster view now covers the window; drop the theme background after the first frame (overdraw, PLAN 11.3)
        getWindow().getDecorView().post(() -> getWindow().setBackgroundDrawable(null));

        panel = new SidePanel(findViewById(R.id.panel), findViewById(R.id.veil), () -> {
            opener = getCurrentFocus();
            glass.hide();
            blockBehindPanel(true);
        }, () -> {
            screens.panelClosed();
            blockBehindPanel(false);
            if (opener != null && opener.isAttachedToWindow() && opener.isShown()) opener.requestFocus();
            else focusPosition(Math.min(lastPos, adapter.getItemCount() - 1));
        });
        screens = new Screens(this, panel);
        header(R.id.btn_inputs, () -> panel.open(screens.inputs));
        header(R.id.btn_network, () -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        header(R.id.btn_settings, () -> panel.open(screens.root));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        // for the activity's whole life, not onStart/onStop: an app installed while another app is open shows up too
        registerReceiver(packages, f);
        load();
        openFrom(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openFrom(intent);
    }

    /**
     * A Home intent. With a remote action's panel (RemoteActions) it toggles that panel, as the Menu key does; a plain
     * Home press while Home is already in front closes the panel and ends Move mode.
     */
    private void openFrom(Intent i) {
        String which = i == null ? null : i.getStringExtra(RemoteActions.EXTRA_PANEL);
        if (RemoteActions.PANEL_SETTINGS.equals(which)) togglePanel(screens.root);
        else if (RemoteActions.PANEL_INPUTS.equals(which)) togglePanel(screens.inputs);
        else {
            if (movingPkg != null) endMove();
            panel.close();
        }
    }

    /** Open, or close if a panel is open. Move mode ends first: a panel over it would leave the arrows moving a tile. */
    private void togglePanel(SidePanel.Screen s) {
        if (movingPkg != null) endMove();
        if (panel.isOpen()) panel.close();
        else panel.open(s);
    }

    /** (Re)build the grid from the settings: columns, rows below the bar, labels, corners (PLAN 7.2, 10.1). */
    void applyLayout() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float d = dm.density;
        GridMetrics m = new GridMetrics(dm.widthPixels, dm.heightPixels, d, state.columns, state.rowsBelow, state.corner);
        int ceiling = Math.round(GridMetrics.CEILING_DP * d);
        List<App> items = adapter == null ? Collections.emptyList() : adapter.items();
        adapter = new AppAdapter(m, state.columns, state.labels, this);
        adapter.setItems(items);
        while (grid.getItemDecorationCount() > 0) grid.removeItemDecorationAt(0);
        grid.setPadding(grid.getPaddingLeft(), m.barTop, grid.getPaddingRight(), 0);
        grid.setLayoutManager(new BarGridLayoutManager(this, state.columns, ceiling));
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
        HeaderBinder.clock(getWindow().getDecorView(), state);
        TextView g = findViewById(R.id.greeting);
        g.setVisibility(state.greeting ? View.VISIBLE : View.GONE);
        if (state.greeting) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            g.setText(h >= 5 && h < 12 ? R.string.greeting_morning : h >= 12 && h < 17 ? R.string.greeting_afternoon
                    : h >= 17 && h < 23 ? R.string.greeting_evening : R.string.greeting_night);
        }
        showWeather();
    }

    // ---- wallpaper (PLAN 6) ----

    /** Poster, video surface and dimming for the current type; restarts the video if Home is on screen. */
    void applyWallpaper() { wallpaper.apply(state); }

    void showPhotoCredit() { wallpaper.showCredit(); }

    void applyDim() { wallpaper.applyDim(); }

    private final Runnable applyWallpaperNow = this::applyWallpaper;

    /** After a change from the settings: once the choice has rested, not at every step of Left and Right. */
    void applyWallpaperSoon() {
        ui.removeCallbacks(applyWallpaperNow);
        ui.postDelayed(applyWallpaperNow, 800);
    }

    /**
     * A network came up. A TV turned on reaches Home before its Wi-Fi: the aerials found none, or failed on it, and
     * showed the gradient; they start now instead of at the next return to Home.
     */
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) {
            wallpaper.networkUp();
        }

        @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) { showNetwork(c); }

        @Override public void onLost(@NonNull Network n) { showNetwork(null); }
    };

    /**
     * The Wi-Fi icon shows only when something is wrong (D108): no network, or one without working internet (Android's
     * own check failed: no route, a captive portal). Ethernet that works counts as fine.
     */
    private void showNetwork(NetworkCapabilities c) {
        setHeaderShown(R.id.btn_network, c == null || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
    }

    /** The bundled aerials list (the Wallpaper settings count its clips). */
    String aerialsJson() { return WallpaperController.aerialsJson(this); }

    /** The collection or a theme changed in settings: rebuild the playlist. */
    void aerialsChanged() {
        wallpaper.aerialsChanged();
        save();
        if (state.wallpaper.equals("aerials")) applyWallpaperSoon();
    }

    boolean online() { return connectivity().getActiveNetwork() != null; }

    private ConnectivityManager connectivity() { return getSystemService(ConnectivityManager.class); }

    void pickVideo() { pick("video/*", REQ_VIDEO); }

    void pickPicture() { pick("image/*", REQ_PICTURE); }

    /** OPEN_DOCUMENT only: GET_CONTENT shows the app chooser on this TV (PLAN 6.1). */
    @SuppressWarnings("deprecation")
    private void pick(String type, int req) {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), req);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ART) {
            if (res == RESULT_OK && data != null && data.getData() != null && artPkg != null) setOwnArt(artPkg, data.getData());
            artPkg = null;
            return;
        }
        if ((req != REQ_VIDEO && req != REQ_PICTURE) || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.access_not_kept, Toast.LENGTH_LONG).show();
        }
        String old = req == REQ_VIDEO ? state.videoUri : state.imageUri;
        if (!old.isEmpty() && !old.equals(uri.toString())) {
            try {   // grants are limited per app: give back the one for the file no longer used
                getContentResolver().releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
        }
        if (req == REQ_PICTURE) {
            state.imageUri = uri.toString();
            state.wallpaper = "picture";
            save();
            applyWallpaper();
            return;
        }
        state.videoUri = uri.toString();
        state.wallpaper = "video";
        save();
        pool.execute(() -> {   // the poster frame first, so the start shows it; then a hint if the clip is heavy
            WallpaperFiles.savePoster(this, uri);
            boolean ok = WallpaperFiles.suitsTv(this, uri);
            ui.post(() -> {
                if (!ok) Toast.makeText(this, R.string.video_warning, Toast.LENGTH_LONG).show();
                applyWallpaper();
            });
        });
    }

    /** Copies the picture, cropped to 16:9, as the app's banner; no lasting access to the file is needed. */
    private void setOwnArt(String pkg, Uri uri) {
        pool.execute(() -> {
            Bitmap b = WallpaperFiles.picture(this, uri);
            boolean ok = b != null && art.setOwn(pkg, b);
            ui.post(() -> {
                if (ok) reloadArt(pkg);
                else Toast.makeText(this, R.string.image_failed, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void reloadArt(String pkg) {
        for (App a : installed) {
            if (!a.pkg.equals(pkg)) continue;
            a.art = null;
            loadArt(Collections.singletonList(a), generation);
        }
    }

    // ---- weather (PLAN 4) ----

    void refreshWeather() {
        if (!state.weatherEnabled || !state.hasCity()) return;
        if (!online()) {   // no network: keep the cache, say how old it is
            weatherFailed = true;
            showWeather();
            retryWeatherSoon();
            return;
        }
        double lat = state.lat, lon = state.lon;
        boolean f = state.fahrenheit;
        weather.forecast(lat, lon, f, (body, now) -> {
            if (lat != state.lat || lon != state.lon || f != state.fahrenheit) return;   // the city or units changed meanwhile
            weatherFailed = body == null;
            if (weatherFailed) retryWeatherSoon();
            if (body != null) {
                state.weatherLast = body;
                state.fetchedAt = System.currentTimeMillis();
                save();
            }
            showWeather();
        });
    }

    /** The cached forecast under the clock; hidden when off, without a city or before the first answer. */
    void showWeather() { HeaderBinder.weather(getWindow().getDecorView(), state, weatherFailed); }

    /** The Inputs icon shows only when it is wanted and there is an input to switch to (PLAN 7.7). */
    void updateInputsButton() {
        boolean want = state.showInputsButton, connectedOnly = state.inputsConnectedOnly;
        int gen = ++inputsGen;
        pool.execute(() -> {   // TvInputManager calls are IPC: not on the main thread at every return to Home
            boolean show = want && !Inputs.list(this, connectedOnly).isEmpty();
            ui.post(() -> {
                if (gen != inputsGen) return;   // a newer check, after a quick toggle, wins
                setHeaderShown(R.id.btn_inputs, show);
                if (panel.showing(screens.inputs)) panel.refresh();
            });
        });
    }

    /** Settings → Appearance → Edge dimming: the dark gradients at the top and bottom of the screen. */
    void applyEdges() {
        String e = state.edgeDim;
        findViewById(R.id.scrim).setVisibility(e.equals("both") || e.equals("top") ? View.VISIBLE : View.GONE);
        findViewById(R.id.scrim_bottom).setVisibility(e.equals("both") || e.equals("bottom") ? View.VISIBLE : View.GONE);
    }

    /** The gear can be hidden only while a remote button opens the settings through the bound service (D109). */
    void updateSettingsButton() {
        setHeaderShown(R.id.btn_settings,
                RemoteActions.settingsIconShown(state.hideSettingsButton, state.remote, RemoteKeyService.running));
    }

    /** A round header icon; one that hides while focused hands the focus to the grid, so it is not lost. */
    private void setHeaderShown(int id, boolean show) {
        View b = findViewById(id);
        if (!show && b.hasFocus()) focusPosition(Math.min(lastPos, adapter.getItemCount() - 1));
        b.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyClock();   // the greeting follows the time of day; also shows the cached weather
        updateInputsButton();
        RemoteKeyService.onBindChange = this::updateSettingsButton;   // the service may bind after Home is up (boot)
        updateSettingsButton();
        // refresh at once if the cache is older than the interval, then every 30 minutes while Home is shown
        long age = System.currentTimeMillis() - state.fetchedAt;
        ui.postDelayed(weatherTick, Math.max(0, WEATHER_EVERY - age));
        started = true;
        if (artDropped) applyWallpaper();   // decodes the picture or poster again; first, before every tile
        wallpaper.start();
        if (artDropped) {
            artDropped = false;
            loadArt(installed, generation);
        }
        ConnectivityManager cm = connectivity();
        showNetwork(cm.getNetworkCapabilities(cm.getActiveNetwork()));   // the callback says nothing when there is no network
        cm.registerDefaultNetworkCallback(network, ui);   // on the main thread
    }

    @Override
    protected void onStop() {
        if (movingPkg != null) endMove();
        if (menu != null) menu.dismiss();
        panel.close();
        ui.removeCallbacks(weatherTick);
        connectivity().unregisterNetworkCallback(network);
        RemoteKeyService.onBindChange = null;
        started = false;
        wallpaper.stop();   // release the decoder whenever Home is not on screen (PLAN 6.1)
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        wallpaper.onResume();
    }

    @Override
    protected void onPause() {
        wallpaper.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        ui.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        weather.shutdown();
        wallpaper.shutdown();
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
        wallpaper.dropPoster();   // the picture or poster frame: up to 8 MB
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
        // TV_INPUT is left to the system: TCL's own source bar opens for it anyway, over anything we show (D89)
        if (code == KeyEvent.KEYCODE_MENU) {   // the gear's key, where the remote has one (PLAN 7.6)
            togglePanel(screens.root);
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
                if (e.getAction() == KeyEvent.ACTION_UP) endMove();   // on UP, so the release does not click the tile
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (e.getAction() == KeyEvent.ACTION_UP) cancelMove();
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
        List<Object> items = new ArrayList<>(Arrays.asList(
                getString(R.string.open), (TileMenu.Action) () -> onLaunch(a),
                getString(R.string.move), (TileMenu.Action) () -> startMove(a),
                getString(R.string.change_image), (TileMenu.Action) () -> {
                    artPkg = a.pkg;
                    pick("image/*", REQ_ART);
                }));
        if (art.hasOwn(a.pkg)) items.addAll(Arrays.asList(getString(R.string.reset_image), (TileMenu.Action) () -> {
            art.removeOwn(a.pkg);
            reloadArt(a.pkg);
        }));
        items.addAll(Arrays.asList(
                getString(R.string.hide), (TileMenu.Action) () -> {
                    setHidden(a.pkg, true);
                    Toast.makeText(this, R.string.hidden_hint, Toast.LENGTH_LONG).show();
                },
                getString(R.string.app_details), (TileMenu.Action) () -> start(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)))));
        menu = TileMenu.show(card, items.toArray());
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

    /** Back in Move mode: the tile goes back where it was; the saved order never changed. */
    private void cancelMove() {
        String pkg = movingPkg;
        movingPkg = null;
        glass.setMoving(false);
        View card = getCurrentFocus();
        applyApps();
        if (card != null) glass.follow(card);   // stable ids keep focus on this card, so no focus change will move the ring
        List<App> items = adapter.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).pkg.equals(pkg)) {
                int pos = i;
                grid.post(() -> focusPosition(pos));
                break;
            }
        }
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
