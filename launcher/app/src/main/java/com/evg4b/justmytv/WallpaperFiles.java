package com.evg4b.justmytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Files behind the wallpaper (PLAN 6.1): the picture, the video's poster frame, the codec check. Slow: off the main thread. */
final class WallpaperFiles {
    private static final int W = 1920, H = 1080;   // the TV renders 1080p (PLAN 18)

    private WallpaperFiles() { }

    static File poster(Context ctx) { return new File(ctx.getFilesDir(), "poster.webp"); }

    /**
     * The picture's 16:9 centre at no more than screen size, or null. Only that region is decoded: a 12 MP portrait
     * photo would otherwise be decoded almost whole (48 MB) for a screen that shows a third of it.
     */
    @SuppressWarnings("deprecation")   // BitmapRegionDecoder.newInstance(InputStream, boolean): the API 31 one is too new
    static Bitmap picture(Context ctx, Uri uri) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        } catch (IOException | SecurityException e) {
            Log.w("Wallpaper", "picture unreadable", e);
            return null;
        }
        int w = o.outWidth, h = o.outHeight;
        if (w <= 0 || h <= 0) return null;
        int cw = Math.min(w, h * W / H), ch = Math.min(h, w * H / W);
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample(cw, ch);
        Bitmap b;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapRegionDecoder r = BitmapRegionDecoder.newInstance(in, false);
            try {
                int x = (w - cw) / 2, y = (h - ch) / 2;
                b = r.decodeRegion(new Rect(x, y, x + cw, y + ch), d);
            } finally {
                r.recycle();
            }
        } catch (IOException e) {   // a format the region decoder does not read (GIF, BMP): the whole picture
            d.inSampleSize = sample(w, h);
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, d);
            } catch (IOException | SecurityException | OutOfMemoryError e2) {
                Log.w("Wallpaper", "picture failed", e2);
                return null;
            }
        } catch (SecurityException | OutOfMemoryError e) {
            Log.w("Wallpaper", "picture failed", e);
            return null;
        }
        if (b == null) return null;
        if (b.getWidth() > W) {
            Bitmap s = Bitmap.createScaledBitmap(b, W, Math.max(1, b.getHeight() * W / b.getWidth()), true);
            if (s != b) b.recycle();
            b = s;
        }
        b.prepareToDraw();
        return b;
    }

    /** The largest power of two that keeps w x h at least screen size. */
    private static int sample(int w, int h) {
        int s = 1;
        while (w / (s * 2) >= W && h / (s * 2) >= H) s *= 2;
        return s;
    }

    /** The decoded poster frame, or null. RGB_565: it is opaque and only shown until the video's first frame. */
    static Bitmap readPoster(Context ctx) {
        File f = poster(ctx);
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b != null) b.prepareToDraw();
        return b;
    }

    /** Save the video's first frame as poster.webp, so a return to Home shows it instead of a gradient (PLAN 6.2). */
    @SuppressWarnings("deprecation")
    static void savePoster(Context ctx, Uri uri) {
        File out = poster(ctx);
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, uri);
            Bitmap frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
                return;
            }
            if (frame.getWidth() > W || frame.getHeight() > H) {
                float s = Math.min((float) W / frame.getWidth(), (float) H / frame.getHeight());
                frame = Bitmap.createScaledBitmap(frame, Math.round(frame.getWidth() * s), Math.round(frame.getHeight() * s), true);
            }
            File tmp = new File(out.getPath() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) {
                frame.compress(Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP, 85, os);
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(out);
        } catch (IOException | RuntimeException e) {
            Log.w("Wallpaper", "no poster frame", e);
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        } finally {
            try {
                r.release();
            } catch (IOException ignored) { }
        }
    }

    /** False when the clip is above 1080p or not H.264/HEVC: the user gets a hint, the choice is not blocked (PLAN 6.1). */
    static boolean suitsTv(Context ctx, Uri uri) {
        MediaExtractor x = new MediaExtractor();
        try {
            x.setDataSource(ctx, uri, null);
            for (int i = 0; i < x.getTrackCount(); i++) {
                MediaFormat f = x.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("video/")) continue;
                boolean codec = mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC) || mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC);
                int w = f.containsKey(MediaFormat.KEY_WIDTH) ? f.getInteger(MediaFormat.KEY_WIDTH) : 0;
                int h = f.containsKey(MediaFormat.KEY_HEIGHT) ? f.getInteger(MediaFormat.KEY_HEIGHT) : 0;
                return codec && (long) w * h <= (long) W * H;
            }
            return false;   // no video track
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            x.release();
        }
    }
}
