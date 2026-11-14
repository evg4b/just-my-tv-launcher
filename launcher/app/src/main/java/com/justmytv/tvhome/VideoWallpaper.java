package com.justmytv.tvhome;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;
import java.util.Random;

/**
 * The video wallpaper (PLAN 6.1, 6.3): Media3 on a SurfaceView under an opaque poster. The poster fades out at the
 * first frame and comes back on stop or failure. A user file loops; aerials play as a rolling two-item playlist,
 * so one clip runs into the next without a black frame. All player calls are on the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
final class VideoWallpaper {
    interface Listener {
        /** An Apple stream failed (its TLS chain, say): skip Apple for the rest of the session. */
        void onAppleFailed();
    }

    private static final long WATCHDOG_FILE_MS = 3_000, WATCHDOG_AERIALS_MS = 8_000;
    private static final int MAX_AERIAL_ERRORS = 3;   // clips in a row that fail before we give up

    private final Context ctx;
    private final SurfaceView surface;
    private final ImageView poster;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private ExoPlayer player;
    private List<String> aerials;      // null: a user file
    private boolean resumed, rendered, appleFailed;
    private int errors;
    private long watchdogMs;

    private final Runnable watchdog = () -> {
        if (resumed && !rendered) fallback("no first frame in " + watchdogMs + " ms");
    };

    VideoWallpaper(Context ctx, SurfaceView surface, ImageView poster, Listener listener) {
        this.ctx = ctx;
        this.surface = surface;
        this.poster = poster;
        this.listener = listener;
    }

    boolean playing() { return player != null; }

    void onResume() {
        resumed = true;
        // while not resumed the watchdog does nothing (no surface, no frames): arm it again
        if (player != null && !rendered) armWatchdog();
    }

    void onPause() { resumed = false; }

    /** A user file, looped. */
    void startFile(Uri uri) {
        if (player != null) return;
        aerials = null;
        build(Player.REPEAT_MODE_ONE, WATCHDOG_FILE_MS);
        player.setMediaItem(MediaItem.fromUri(uri));
        go();
    }

    /** Random aerials, never the same twice in a row. */
    void startAerials(List<String> urls) {
        if (player != null || urls.isEmpty()) return;
        aerials = urls;
        build(Player.REPEAT_MODE_OFF, WATCHDOG_AERIALS_MS);
        queueAerials(null);
        go();
    }

    /** Two random clips, neither the one just played: the rolling playlist of PLAN 6.3. */
    private void queueAerials(String last) {
        String first = Aerials.next(aerials, last, random);
        player.addMediaItem(MediaItem.fromUri(first));
        player.addMediaItem(MediaItem.fromUri(Aerials.next(aerials, first, random)));
    }

    /** ON_STOP: release the decoder; the poster is opaque again. */
    void stop() {
        main.removeCallbacks(watchdog);
        if (player != null) {
            player.release();
            player = null;
        }
        showPoster();
    }

    private void build(int repeat, long watchdogMs) {
        this.watchdogMs = watchdogMs;
        rendered = false;
        errors = 0;
        player = new ExoPlayer.Builder(ctx)
                .setRenderersFactory(new DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 15_000, 2_500, 5_000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build())
                .build();
        player.setVolume(0f);
        player.setRepeatMode(repeat);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                rendered = true;
                errors = 0;
                main.removeCallbacks(watchdog);
                hidePoster();
            }

            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                // aerials: drop the clip that ended, queue another, so the playlist always has a next item
                if (aerials == null || player == null || player.getCurrentMediaItemIndex() == 0) return;
                String now = item == null || item.localConfiguration == null ? null : item.localConfiguration.uri.toString();
                player.removeMediaItem(0);
                player.addMediaItem(MediaItem.fromUri(Aerials.next(aerials, now, random)));
            }

            @Override public void onPlayerError(@NonNull PlaybackException e) {
                onError(e);
            }
        });
        player.setVideoSurfaceView(surface);
    }

    private void go() {
        player.prepare();
        player.setPlayWhenReady(true);
        armWatchdog();
    }

    private void armWatchdog() {
        main.removeCallbacks(watchdog);
        main.postDelayed(watchdog, watchdogMs);
    }

    private void onError(PlaybackException e) {
        if (aerials == null || ++errors >= MAX_AERIAL_ERRORS) {
            fallback(e.getErrorCodeName());
            return;
        }
        // one aerial failed: drop it and go on with another; Apple's chain fails on some TV trust stores (PLAN 6.3)
        MediaItem bad = player.getCurrentMediaItem();
        String url = bad == null || bad.localConfiguration == null ? "" : bad.localConfiguration.uri.toString();
        Log.w("Wallpaper", "aerial failed: " + e.getErrorCodeName() + " " + url);
        if (Aerials.isApple(url) && !appleFailed) {
            appleFailed = true;
            listener.onAppleFailed();
            List<String> rest = new java.util.ArrayList<>();
            for (String u : aerials) if (!Aerials.isApple(u)) rest.add(u);
            if (rest.isEmpty()) {
                fallback("no aerials left");
                return;
            }
            aerials = rest;
        }
        player.clearMediaItems();
        queueAerials(url);
        rendered = false;
        showPoster();
        go();
    }

    private void hidePoster() {
        poster.animate().alpha(0f).setDuration(900)
                .setInterpolator(new AccelerateDecelerateInterpolator())   // ease-in-out
                .withEndAction(() -> poster.setVisibility(View.GONE))
                .start();
    }

    private void showPoster() {
        poster.animate().cancel();
        poster.setAlpha(1f);
        poster.setVisibility(View.VISIBLE);
    }

    /** The first failure of a file (or three aerials in a row): release, keep the poster, no retry until the next start. */
    private void fallback(String reason) {
        Log.w("Wallpaper", "fallback: " + reason);
        main.removeCallbacks(watchdog);
        if (player != null) {
            player.release();
            player = null;
        }
        if (aerials != null) poster.setImageResource(R.drawable.aurora);   // no aerial plays: a still gradient, not black
        showPoster();
    }
}
