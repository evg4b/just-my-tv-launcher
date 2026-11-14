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

/**
 * The Photos wallpaper (D101, D102): a random Unsplash photo through Unsplash's API with the owner's access key, a new
 * one every few minutes while Home is on screen, changed with the aerials' transition: 1 s to black, then 1.2 s in.
 * The current photo is kept in files/ so Home shows it at once next time, also offline.
 */
final class PhotoWallpaper {
    private static final String RANDOM = "https://api.unsplash.com/photos/random?orientation=landscape&content_filter=high";
    private static final String SIZE = "&w=1920&h=1080&fit=crop&fm=jpg&q=80";   // Imgix parameters on urls.raw
    private static final int MAX_BYTES = 5 * 1024 * 1024;   // a 1920x1080 JPEG is well under 1 MB
    private static final long RETRY_MS = 2 * 60_000;         // after a network failure
    private static final long LIMIT_MS = 15 * 60_000;        // after the hourly limit was hit (50 for a new key)
    private static final long FADE_OUT_MS = 1_000, FADE_IN_MS = 1_200;   // as VideoWallpaper

    /** What the last attempt found, for the settings screen. */
    enum Status { OK, NO_KEY, BAD_KEY, LIMIT, FAILED }

    /** One random photo from the API: where to get it, who took it, and the link Unsplash asks to be called. */
    static final class Photo {
        final String image, author, downloadLocation;
        Photo(String image, String author, String downloadLocation) {
            this.image = image;
            this.author = author;
            this.downloadLocation = downloadLocation;
        }
    }

    private final Context ctx;
    private final ImageView poster;
    private final File photo, author;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::fetch;
    private final Runnable changed;
    private ValueAnimator fade;
    private long everyMs;
    private String key = "";
    private volatile Status status = Status.OK;
    private int gen;            // a stop or a new start makes running downloads stale
    private boolean running, fetching;

    /** changed: after each attempt, on the main thread (the settings show the status and the author). */
    PhotoWallpaper(Context ctx, ImageView poster, Runnable changed) {
        this.ctx = ctx;
        this.poster = poster;
        this.changed = changed;
        photo = new File(ctx.getFilesDir(), "photo.jpg");
        author = new File(ctx.getFilesDir(), "photo.txt");
    }

    /** Show the kept photo (from black), then change it when it is everyMs old. */
    void start(long everyMs, String key) {
        stop();
        running = true;
        this.everyMs = everyMs;
        this.key = key;
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
        if (key.isEmpty()) {   // nothing can be fetched; the kept photo stays, or the gradient
            status = Status.NO_KEY;
            if (!photo.exists()) poster.setImageResource(R.drawable.aurora);
            return;
        }
        long age = System.currentTimeMillis() - photo.lastModified();
        main.postDelayed(tick, photo.exists() ? Math.max(0, everyMs - age) : 0);
    }

    Status status() { return status; }

    /** A download is running ("Next photo" shows Loading…). */
    boolean fetching() { return fetching; }

    /** A new interval keeps the photo on screen; the next change is due everyMs after the kept photo was written. */
    void setInterval(long everyMs) {
        this.everyMs = everyMs;
        if (!running || fetching || key.isEmpty()) return;
        main.removeCallbacks(tick);
        long age = System.currentTimeMillis() - photo.lastModified();
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
        if (!running || fetching || key.isEmpty()) return;
        main.removeCallbacks(tick);
        fetch();
        changed.run();   // shows Loading…
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
        String k = key;
        io.execute(() -> {
            Status st = download(k);
            Bitmap b = st == Status.OK ? WallpaperFiles.picture(ctx, Uri.fromFile(photo)) : null;
            status = st == Status.OK && b == null ? Status.FAILED : st;
            main.post(() -> {
                if (g != gen) return;
                fetching = false;
                changed.run();
                if (b != null) show(b, true);
                else if (!photo.exists()) poster.setImageResource(R.drawable.aurora);   // nothing to show: not black
                // a rejected key waits for a new one (a new start); the limit resets within the hour
                if (status != Status.BAD_KEY) {
                    main.postDelayed(tick, b != null ? everyMs : status == Status.LIMIT ? LIMIT_MS : RETRY_MS);
                }
            });
        });
    }

    /** Ask the API for a random photo, download it into files/photo.jpg and its author into photo.txt. */
    private Status download(String key) {
        Photo p;
        HttpURLConnection c = null;
        try {
            c = open(RANDOM, key);
            int code = c.getResponseCode();
            if (code == 401) return Status.BAD_KEY;
            if (code == 403 || code == 429) return Status.LIMIT;   // "Rate Limit Exceeded"
            if (code != 200) {
                Log.w("Photos", "HTTP " + code);
                return Status.FAILED;
            }
            try (InputStream in = c.getInputStream()) {
                p = parse(WeatherClient.read(in, 256 * 1024));
            }
        } catch (IOException | RuntimeException e) {
            Log.w("Photos", "request failed", e);
            return Status.FAILED;
        } finally {
            if (c != null) c.disconnect();
        }
        if (p == null) return Status.FAILED;
        File tmp = new File(ctx.getFilesDir(), "photo.tmp");
        try {
            c = (HttpURLConnection) new URL(p.image).openConnection();
            c.setConnectTimeout(5_000);
            c.setReadTimeout(10_000);
            if (c.getResponseCode() != 200) return Status.FAILED;
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                int total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_BYTES) return Status.FAILED;
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(photo)) return Status.FAILED;
            try (OutputStream out = new FileOutputStream(author)) {
                out.write(p.author.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            Log.w("Photos", "download failed", e);
            return Status.FAILED;
        } finally {
            c.disconnect();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        ping(p.downloadLocation, key);
        return Status.OK;
    }

    /** Unsplash's API guidelines: call the photo's download_location when it is used. Its answer does not matter. */
    private static void ping(String url, String key) {
        if (url.isEmpty()) return;
        HttpURLConnection c = null;
        try {
            c = open(url, key);
            c.getResponseCode();
        } catch (IOException | RuntimeException e) {
            Log.w("Photos", "download_location failed", e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5_000);
        c.setReadTimeout(5_000);
        c.setRequestProperty("Authorization", "Client-ID " + key);
        c.setRequestProperty("Accept-Version", "v1");
        return c;
    }

    /** The image address (cropped to 1920x1080), the author and download_location from /photos/random; null if unusable. */
    static Photo parse(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject urls = o.optJSONObject("urls"), user = o.optJSONObject("user"), links = o.optJSONObject("links");
            String raw = urls == null ? "" : urls.optString("raw", "");
            if (!raw.startsWith("https://")) return null;
            String image = raw + (raw.contains("?") ? SIZE : "?" + SIZE.substring(1));
            String name = user == null ? "" : user.optString("name", "").trim();
            String dl = links == null ? "" : links.optString("download_location", "");
            return new Photo(image, name, dl.startsWith("https://api.unsplash.com/") ? dl : "");
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
        changed.run();   // the credit on Home follows the photo on screen
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
