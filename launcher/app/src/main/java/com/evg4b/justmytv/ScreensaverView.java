package com.evg4b.justmytv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * The screensaver's screen (SCREENSAVER 2, 5), hosted by {@link Screensaver} and by {@link ScreensaverPreview}. It reads
 * the launcher's state at every start, so the launcher's settings apply with nothing to sync. Main thread only.
 */
final class ScreensaverView {
    private static final long DRIFT_EVERY = 5 * 60_000L, DRIFT_MS = 2_000, FADE_IN_MS = 1_200;
    private static final float DRIFT = 0.03f;                // of the screen, each way
    private static final long WEATHER_EVERY = 30 * 60_000L;  // as at Home (PLAN 4.6)

    final View root;
    private final Context ctx;
    private final LauncherState store;
    private final WallpaperController wallpaper;
    private final WeatherClient weather = new WeatherClient();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View block;
    private StateJson state;
    private boolean started;

    private final Runnable drift = new Runnable() {
        @Override public void run() {
            ui.postDelayed(this, DRIFT_EVERY);
            float dx = root.getWidth() * DRIFT, dy = root.getHeight() * DRIFT;
            block.animate().translationX((random.nextFloat() * 2 - 1) * dx).translationY(-random.nextFloat() * dy)
                    .setDuration(DRIFT_MS).start();
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            ui.postDelayed(this, WEATHER_EVERY);
            refreshWeather();
        }
    };

    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) { ui.post(wallpaper::networkUp); }
    };

    ScreensaverView(Context ctx) {
        this.ctx = ctx;
        store = new LauncherState(ctx);
        root = LayoutInflater.from(ctx).inflate(R.layout.screensaver, null);
        block = root.findViewById(R.id.clock_block);
        wallpaper = new WallpaperController(ctx, root.findViewById(R.id.video), root.findViewById(R.id.poster),
                root.findViewById(R.id.dim), root.findViewById(R.id.photo_credit), root.findViewById(R.id.ambient),
                null, null);
    }

    void start() {
        if (started) return;
        started = true;
        state = StateJson.parse(store.read());
        HeaderBinder.clock(root, state);
        HeaderBinder.weather(root, state, false);
        wallpaper.apply(state);
        wallpaper.onResume();
        if (wallpaper.start()) {
            // Home's video goes on: only the clock and the shade behind it come in
            for (int id : new int[]{R.id.clock_block, R.id.scrim_bottom, R.id.photo_credit}) {
                View v = root.findViewById(id);
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(FADE_IN_MS).start();
            }
        } else {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(FADE_IN_MS).start();   // from black, as the video reveal
        }
        ui.postDelayed(drift, DRIFT_EVERY);
        long age = System.currentTimeMillis() - state.fetchedAt;
        ui.postDelayed(weatherTick, Math.max(0, WEATHER_EVERY - age));
        connectivity().registerDefaultNetworkCallback(network);
    }

    void stop() {
        if (!started) return;
        started = false;
        ui.removeCallbacks(drift);
        ui.removeCallbacks(weatherTick);
        connectivity().unregisterNetworkCallback(network);
        wallpaper.onPause();
        wallpaper.stop();
    }

    void shutdown() {
        stop();
        wallpaper.shutdown();
        weather.shutdown();
    }

    private ConnectivityManager connectivity() { return ctx.getSystemService(ConnectivityManager.class); }

    /** As at Home; the answer is saved back to the launcher's file (SCREENSAVER 5), so Home has it too. */
    private void refreshWeather() {
        StateJson st = state;
        if (!st.weatherEnabled || !st.hasCity() || connectivity().getActiveNetwork() == null) return;
        double lat = st.lat, lon = st.lon;
        boolean f = st.fahrenheit;
        weather.forecast(lat, lon, f, (body, now) -> {
            if (body == null || !started) return;
            st.weatherLast = body;
            st.fetchedAt = System.currentTimeMillis();
            HeaderBinder.weather(root, st, false);
            long at = st.fetchedAt;
            new Thread(() -> {
                // read again: only the forecast is ours to change; anything set at Home meanwhile stays
                StateJson fresh = StateJson.parse(store.read());
                if (fresh.lat != lat || fresh.lon != lon || fresh.fahrenheit != f) return;   // the city or units changed
                fresh.weatherLast = body;
                fresh.fetchedAt = at;
                store.write(fresh.toJson());
            }, "screensaver-state").start();
        });
    }
}
