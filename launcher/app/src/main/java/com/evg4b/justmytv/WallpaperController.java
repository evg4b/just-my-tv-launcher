package com.evg4b.justmytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The wallpaper of one screen (PLAN 6): poster, video surface, photos, aerials, dimming and the photo credit. Home and the
 * screensaver each own one (SCREENSAVER 4.2). With an {@link AmbientGradient} (the screensaver) a gradient wallpaper is
 * the animated gradient and the user's own picture is left out (D112); without one (Home) it is the still Dusk gradient.
 * Only one controller plays at a time (SCREENSAVER 4.3): starting one stops the other, and a video both show (the same
 * aerials, the same file) is handed over instead, so it goes on without a new clip. Main thread only.
 */
final class WallpaperController {
    /** The one playing now; a second start stops it, so two players and two photo timers never run together. */
    private static WallpaperController current;
    /** Stopped by the one playing while its own screen stayed shown: started again when that one stops. */
    private static WallpaperController waiting;
    /** The screensaver just closed: its video plays on for a moment, for Home to take over as it comes back. */
    private static WallpaperController parked;
    private static final long PARK_MS = 3_000;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable unpark = () -> {
        WallpaperController p = parked;
        parked = null;
        if (p != null) p.halt();
    };
    private static String aerialsRaw;

    final PhotoWallpaper photos;
    private final Context ctx;
    private final SurfaceView surface;
    private final ImageView poster;
    private final View dim;
    private final TextView credit;
    private final AmbientGradient ambient;
    private final VideoWallpaper video;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService decode = Executors.newSingleThreadExecutor();
    private StateJson state;
    private int gen;                  // a newer apply makes older background results stale
    private boolean started, skipApple;
    private List<String> aerialUrls;

    /** credit and ambient may be null; onFrame gets a small copy of each revealed video frame; photoChanged follows the photo. */
    WallpaperController(Context ctx, SurfaceView surface, ImageView poster, View dim, TextView credit,
                        AmbientGradient ambient, Consumer<Bitmap> onFrame, Runnable photoChanged) {
        this.ctx = ctx;
        this.surface = surface;
        this.poster = poster;
        this.dim = dim;
        this.credit = credit;
        this.ambient = ambient;
        video = new VideoWallpaper(ctx, surface, poster, new VideoWallpaper.Listener() {
            @Override public void onAppleFailed() {
                skipApple = true;   // for this session (PLAN 6.3): Apple's TLS chain failed here
                aerialUrls = null;
            }

            @Override public void onFrame(Bitmap frame) { if (onFrame != null) onFrame.accept(frame); }
        });
        photos = new PhotoWallpaper(ctx, poster, () -> {
            showCredit();
            if (photoChanged != null) photoChanged.run();
        });
    }

    /** The wallpaper type this screen shows: Home's own, or its screensaver counterpart. */
    private String type() { return ambient == null ? state.wallpaper : ScreensaverMode.of(state); }

    boolean playing() { return video.playing(); }

    /** Poster, video surface and dimming for the state's type; starts the video or photos if the screen is shown. */
    void apply(StateJson st) {
        state = st;
        int g = ++gen;
        video.stop();
        video.cover();
        photos.stop();
        String type = type();
        boolean moving = type.equals("video") || type.equals("aerials");
        surface.setVisibility(moving ? View.VISIBLE : View.GONE);
        resetPoster();   // until the picture or the poster frame is decoded
        if (type.equals("picture") && !state.imageUri.isEmpty()) {
            Uri uri = Uri.parse(state.imageUri);
            decode.execute(() -> {
                Bitmap b = WallpaperFiles.picture(ctx, uri);
                ui.post(() -> {
                    if (g != gen) return;
                    if (b != null) poster.setImageBitmap(b);
                    else poster.setImageResource(R.drawable.dusk);   // the file is gone or unreadable: not black
                });
            });
        } else if (type.equals("video") && !state.videoUri.isEmpty()) {
            decode.execute(() -> {
                Bitmap b = WallpaperFiles.readPoster(ctx);
                ui.post(() -> { if (g == gen && b != null && !video.playing()) poster.setImageBitmap(b); });
            });
        }
        applyDim();
        showCredit();
        if (started) play();
    }

    /**
     * The screen is shown: play. Stops the one playing, and starts it again when this one stops while it is still shown.
     * True when a playing video was taken over rather than started.
     */
    boolean start() {
        WallpaperController from = current != null && current != this ? current : parked != this ? parked : null;
        if (from != null && from == parked) {
            main.removeCallbacks(unpark);
            parked = null;
        }
        boolean handed = from != null && sameVideo(from);
        if (handed) {
            from.video.handTo(video);
            if (aerialUrls == null) aerialUrls = from.aerialUrls;
            skipApple |= from.skipApple;
            Log.i("Wallpaper", "video handed over, no restart");
        }
        if (from != null) {
            if (from == current) waiting = from;
            from.halt();
        }
        if (waiting == this) waiting = null;
        current = this;
        started = true;
        play();
        return handed;
    }

    /** Both show the same moving wallpaper and the other one plays it now. */
    private boolean sameVideo(WallpaperController o) {
        if (state == null || o.state == null || !o.video.playing()) return false;
        String t = type();
        return t.equals(o.type()) && (t.equals("aerials") || (t.equals("video") && state.videoUri.equals(o.state.videoUri)));
    }

    /** The screen is hidden: release the decoder and the photo timer (PLAN 6.1). */
    void stop() {
        if (waiting == this) waiting = null;   // hidden while it waited: nothing to resume
        if (current != this) {
            halt();
            return;
        }
        current = null;
        WallpaperController w = waiting;
        waiting = null;
        if (w != null) {
            parked = this;   // Home under the screensaver was never stopped: it takes the video over and plays on
            w.start();
            if (parked == this) parked = null;
            halt();
        } else if (ambient != null && video.playing()) {
            started = false;   // the screensaver closed: Home comes back in a moment and takes the video
            photos.stop();
            parked = this;
            main.postDelayed(unpark, PARK_MS);
        } else {
            halt();
        }
    }

    private void halt() {
        started = false;
        video.stop();
        photos.stop();
        if (ambient != null) ambient.setRunning(false);
    }

    void onResume() { video.onResume(); }

    void onPause() { video.onPause(); }

    /** A network came up: aerials that found none start now instead of at the next start. */
    void networkUp() {
        // aerialUrls: the list is read (a start still reading it plays them itself)
        if (started && type().equals("aerials") && aerialUrls != null && !video.playing()) play();
    }

    /** The collection or a theme changed: rebuild the playlist at the next apply. */
    void aerialsChanged() { aerialUrls = null; }

    /** Memory trim: drop the decoded picture or poster frame (up to 8 MB); apply brings it back. */
    void dropPoster() {
        gen++;   // a decode still running is dropped too
        resetPoster();
    }

    void shutdown() {
        stop();
        photos.shutdown();
        decode.shutdownNow();
    }

    private void play() {
        String type = type();
        if (ambient != null) ambient.setRunning(type.equals("gradient"));
        if (type.equals("photos")) {
            photos.start(state.photoMinutes * 60_000L, state.unsplashKey);
        } else if (type.equals("video") && !state.videoUri.isEmpty()) {
            video.startFile(Uri.parse(state.videoUri));
        } else if (type.equals("aerials")) {
            if (!video.playing()) poster.setImageDrawable(new ColorDrawable(Color.BLACK));   // a fallback may have left the gradient up
            if (aerialUrls != null) {
                playAerials(aerialUrls);
                return;
            }
            int g = gen;
            boolean skip = skipApple;
            String source = state.aerialSource;
            java.util.Set<String> themes = new HashSet<>(state.aerialThemes);
            decode.execute(() -> {
                List<String> urls = Aerials.parse(aerialsJson(ctx), skip, source, themes);
                ui.post(() -> {
                    if (g != gen) return;   // the choice changed meanwhile
                    aerialUrls = urls;
                    if (started && type().equals("aerials")) playAerials(urls);
                });
            });
        }
    }

    /** Offline, or no clip matches the chosen collection and themes: a gradient, no requests (PLAN 6.3). */
    private void playAerials(List<String> urls) {
        if (urls.isEmpty() || !online()) showGradient();
        else video.startAerials(urls);
    }

    private boolean online() { return ctx.getSystemService(ConnectivityManager.class).getActiveNetwork() != null; }

    /** The still gradient on the poster (Home), or the animated one behind a hidden poster (screensaver). */
    private void showGradient() {
        if (ambient == null) {
            poster.setImageResource(R.drawable.dusk);
            return;
        }
        poster.setImageDrawable(null);   // transparent: the animated gradient under it shows
        ambient.setRunning(started);
    }

    /**
     * Before the first frame: the gradient for its own type and for a picture or video not chosen yet (nothing will
     * come); otherwise black, so a video or picture comes up from black, never over the gradient.
     */
    private void resetPoster() {
        if (ambient != null) ambient.setRunning(false);
        if (showsGradient()) showGradient();
        else poster.setImageDrawable(new ColorDrawable(Color.BLACK));
    }

    private boolean showsGradient() {
        String t = type();
        return t.equals("gradient") || (t.equals("picture") && state.imageUri.isEmpty())
                || (t.equals("video") && state.videoUri.isEmpty());
    }

    void applyDim() {
        boolean on = state.dim > 0 && !showsGradient();
        dim.setVisibility(on ? View.VISIBLE : View.GONE);
        dim.setBackgroundColor((state.dim * 255 / 100) << 24);
    }

    /** Unsplash's guidelines: the photographer and Unsplash where the photo is shown (D105). */
    void showCredit() {
        if (credit == null || state == null) return;
        String by = type().equals("photos") ? photos.author() : "";
        credit.setVisibility(by.isEmpty() ? View.GONE : View.VISIBLE);
        if (!by.isEmpty()) credit.setText(ctx.getString(R.string.photo_by, by));
    }

    /** The bundled aerials list, read once (about 60 KB) and shared by Home and the screensaver. */
    static synchronized String aerialsJson(Context ctx) {
        if (aerialsRaw == null) {
            try (InputStream in = ctx.getResources().openRawResource(R.raw.aerials)) {
                String text = WeatherClient.read(in, Integer.MAX_VALUE);
                aerialsRaw = text == null ? "" : text;
            } catch (IOException e) {
                return "";
            }
        }
        return aerialsRaw;
    }
}
