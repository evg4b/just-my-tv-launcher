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
 * first frame and comes back on stop or failure. A user file loops seamlessly; aerials play as a rolling two-item
 * playlist, and each clip fades to black before its end and the next fades in from black (D93). The fade in waits
 * until the video plays and the TV's picture path has settled (D94). All player calls
 * are on the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
final class VideoWallpaper {
    interface Listener {
        /** An Apple stream failed (its TLS chain, say): skip Apple for the rest of the session. */
        void onAppleFailed();
    }

    private static final long WATCHDOG_FILE_MS = 3_000, WATCHDOG_AERIALS_MS = 8_000;
    private static final int MAX_AERIAL_ERRORS = 3;   // clips in a row that fail before we give up
    private static final long FADE_MS = 1_000;          // an aerial's fade to black before it ends
    private static final long FADE_IN_MS = 1_200;       // black to video
    // the TV blanks its video window and switches its picture engine on for about 0.6 s after the first frame (D94)
    private static final long SETTLE_MS = 1_000;

    private final Context ctx;
    private final SurfaceView surface;
    private final ImageView poster;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private ExoPlayer player;
    private List<String> aerials;      // null: a user file
    private boolean resumed, rendered, appleFailed;
    private boolean revealPending;     // black is up and waits for the video to play and the TV to settle
    private int errors;
    private long watchdogMs;

    private final Runnable watchdog = () -> {
        if (resumed && !rendered) fallback("no first frame in " + watchdogMs + " ms");
    };
    private final Runnable fadeToBlack = this::fadeToBlack;
    private final Runnable reveal = () -> {
        revealPending = false;
        hidePoster();
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
        main.removeCallbacks(fadeToBlack);
        main.removeCallbacks(reveal);
        revealPending = false;
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
                revealPending = true;
                revealWhenSettled();
            }

            @Override public void onIsPlayingChanged(boolean playing) {
                scheduleFadeToBlack();
                revealWhenSettled();
            }

            @Override public void onPlaybackStateChanged(int state) { scheduleFadeToBlack(); }

            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                if (aerials == null || player == null) return;
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    // the next clip starts under black (normally already faded to it) and fades in once it plays
                    showPoster();
                    revealPending = true;
                    revealWhenSettled();
                    scheduleFadeToBlack();
                }
                // drop the clip that ended, queue another, so the playlist always has a next item
                if (player.getCurrentMediaItemIndex() == 0) return;
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

    /**
     * Fade in only when the video really plays (not at a still first frame while it buffers), and SETTLE_MS later,
     * so the TV's own blanking and picture switches happen behind black instead of flickering through the fade.
     */
    private void revealWhenSettled() {
        main.removeCallbacks(reveal);
        if (revealPending && player != null && player.isPlaying()) main.postDelayed(reveal, SETTLE_MS);
    }

    /** Fade to black FADE_MS before the current aerial ends; again whenever playback or the clip changes. */
    private void scheduleFadeToBlack() {
        main.removeCallbacks(fadeToBlack);
        if (player == null || aerials == null || !player.isPlaying()) return;
        long d = player.getDuration();
        if (d == C.TIME_UNSET) return;
        main.postDelayed(fadeToBlack, Math.max(0, d - player.getCurrentPosition() - FADE_MS));
    }

    private void fadeToBlack() {
        poster.animate().cancel();
        poster.setVisibility(View.VISIBLE);
        poster.animate().alpha(1f).setDuration(FADE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void hidePoster() {
        main.removeCallbacks(reveal);
        poster.animate().alpha(0f).setDuration(FADE_IN_MS)
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
        main.removeCallbacks(fadeToBlack);
        main.removeCallbacks(reveal);
        revealPending = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (aerials != null) poster.setImageResource(R.drawable.aurora);   // no aerial plays: a still gradient, not black
        showPoster();
    }
}
