package com.evg4b.justmytv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Photos wallpaper (D101): a random Unsplash photo from picsum.photos, a new one every few minutes while Home is on
 * screen, changed with the aerials' transition: 1 s to black, then 1.2 s in. The current photo is kept in files/ so
 * Home shows it at once next time, also offline.
 */
final class PhotoWallpaper {
    private static final String RANDOM = "https://picsum.photos/1920/1080.jpg";
    private static final String INFO = "https://picsum.photos/id/%s/info";
    private static final int MAX_BYTES = 5 * 1024 * 1024;   // a 1920x1080 JPEG is well under 1 MB
    private static final long RETRY_MS = 60_000;             // after a failed download
    private static final long FADE_OUT_MS = 1_000, FADE_IN_MS = 1_200;   // as VideoWallpaper
    private static final Pattern ID = Pattern.compile("/id/([0-9]+)/");

    private final Context ctx;
    private final ImageView poster;
    private final File photo, author;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::fetch;
    private ValueAnimator fade;
    private long everyMs;
    private int gen;            // a stop or a new start makes running downloads stale
    private boolean running, fetching;

    PhotoWallpaper(Context ctx, ImageView poster) {
        this.ctx = ctx;
        this.poster = poster;
        photo = new File(ctx.getFilesDir(), "photo.jpg");
        author = new File(ctx.getFilesDir(), "photo.txt");
    }

    /** Show the kept photo (from black), then change it when it is everyMs old. */
    void start(long everyMs) {
        stop();
        running = true;
        this.everyMs = everyMs;
        poster.animate().cancel();
        poster.setAlpha(1f);
        poster.setVisibility(View.VISIBLE);
        int g = gen;
        if (photo.exists()) {
            io.execute(() -> {
                Bitmap b = WallpaperFiles.picture(ctx, Uri.fromFile(photo));
                main.post(() -> { if (g == gen && b != null) show(b, false); });
            });
        }
        long age = System.currentTimeMillis() - photo.lastModified();   // 0 when there is none: age is huge
        main.postDelayed(tick, photo.exists() ? Math.max(0, everyMs - age) : 0);
    }

    void stop() {
        running = false;
        fetching = false;
        gen++;
        main.removeCallbacks(tick);
        if (fade != null) fade.cancel();
        fade = null;
        poster.clearColorFilter();
    }

    /** A new photo now (Settings → "Next photo"). */
    void next() {
        if (!running || fetching) return;
        main.removeCallbacks(tick);
        fetch();
    }

    /** The photographer of the photo on screen, or "". */
    String author() {
        try (InputStream in = new java.io.FileInputStream(author)) {
            String s = WeatherClient.read(in, 1024);
            return s == null ? "" : s.trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void fetch() {
        if (!running) return;
        fetching = true;
        int g = gen;
        io.execute(() -> {
            Bitmap b = download() ? WallpaperFiles.picture(ctx, Uri.fromFile(photo)) : null;
            main.post(() -> {
                if (g != gen) return;
                fetching = false;
                if (b != null) show(b, true);
                else if (!photo.exists()) poster.setImageResource(R.drawable.aurora);   // nothing to show: not black
                main.postDelayed(tick, b != null ? everyMs : RETRY_MS);
            });
        });
    }

    /** Download a random photo into files/photo.jpg and its author into photo.txt; false on any failure. */
    private boolean download() {
        HttpURLConnection c = null;
        File tmp = new File(ctx.getFilesDir(), "photo.tmp");
        try {
            c = (HttpURLConnection) new URL(RANDOM).openConnection();   // redirects to the photo's own address
            c.setConnectTimeout(5_000);
            c.setReadTimeout(10_000);
            if (c.getResponseCode() != 200) {
                Log.w("Photos", "HTTP " + c.getResponseCode());
                return false;
            }
            String id = idFrom(c.getURL().getPath());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_BYTES) return false;
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(photo)) return false;
            String name = id == null ? null : authorFrom(get(String.format(INFO, id)));
            try (OutputStream out = new FileOutputStream(author)) {
                out.write((name == null ? "" : name).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Log.w("Photos", "download failed", e);
            return false;
        } finally {
            if (c != null) c.disconnect();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5_000);
            c.setReadTimeout(5_000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return WeatherClient.read(in, 16 * 1024);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** The picsum id from a photo's address, as in /id/237/1920/1080.jpg; null if there is none. */
    static String idFrom(String path) {
        Matcher m = path == null ? null : ID.matcher(path);
        return m != null && m.find() ? m.group(1) : null;
    }

    /** "author" from picsum's /info JSON; null if missing. */
    static String authorFrom(String json) {
        if (json == null) return null;
        try {
            String a = new JSONObject(json).optString("author", "").trim();
            return a.isEmpty() ? null : a;
        } catch (JSONException e) {
            return null;
        }
    }

    /** To black and back in on the new photo; straight in from black when nothing is shown yet. */
    private void show(Bitmap b, boolean fadeOut) {
        if (fade != null) fade.cancel();
        if (!fadeOut) {
            fadeIn(b);
            return;
        }
        fade = ValueAnimator.ofInt(0, 255).setDuration(FADE_OUT_MS);
        fade.addUpdateListener(a -> darken((int) a.getAnimatedValue()));
        fade.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) { if (!cancelled) fadeIn(b); }
        });
        fade.start();
    }

    private void fadeIn(Bitmap b) {
        darken(255);
        poster.setImageBitmap(b);
        fade = ValueAnimator.ofInt(255, 0).setDuration(FADE_IN_MS);
        fade.setInterpolator(new AccelerateDecelerateInterpolator());   // ease-in-out, as the video reveal
        fade.addUpdateListener(a -> darken((int) a.getAnimatedValue()));
        fade.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { poster.clearColorFilter(); }
        });
        fade.start();
    }

    /** Black over the photo with no extra layer: a filter on the opaque bitmap (overdraw, PLAN 11.3). */
    private void darken(int alpha) {
        poster.setColorFilter(Color.argb(alpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
    }
}
