package com.evg4b.justmytv;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Banner or icon for a tile, prepared once and kept as WebP in iconcache/ (PLAN 5.1, 5.2).
 * File names carry the package, its lastUpdateTime and the pixel size, so an update or a density change makes a new file.
 * An image the user chose for an app (tile menu → Change image) is kept apart in files/art/, survives app updates and
 * comes first. Tiles get hardware bitmaps: their pixels live only on the GPU, not also in memory (about 350 KB a
 * banner). Thread-safe: each call touches only its own files.
 */
final class AppArt {
    private static final int ICON_PX = 128;

    private final Context ctx;
    private final PackageManager pm;
    private final File dir, own;
    private final int bannerW;

    AppArt(Context ctx) {
        this.ctx = ctx;
        pm = ctx.getPackageManager();
        dir = new File(ctx.getCacheDir(), "iconcache");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        own = new File(ctx.getFilesDir(), "art");
        //noinspection ResultOfMethodCallIgnored
        own.mkdirs();
        float density = ctx.getResources().getDisplayMetrics().density;
        bannerW = Math.max(320, Math.min(720, Math.round(180 * GridMetrics.FOCUS_SCALE * density)));
    }

    private String bannerName(App a) { return a.pkg + "-" + a.lastUpdate + "-b" + bannerW + ".webp"; }

    private String iconName(App a) { return a.pkg + "-" + a.lastUpdate + "-i" + ICON_PX + ".webp"; }

    private File ownFile(String pkg) { return new File(own, pkg + ".webp"); }

    boolean hasOwn(String pkg) { return ownFile(pkg).isFile(); }

    /** Keeps a 16:9 picture as the app's banner, scaled to the banner size. False if it could not be written. */
    boolean setOwn(String pkg, Bitmap picture) {
        Bitmap b = Bitmap.createScaledBitmap(picture, bannerW, bannerW * 9 / 16, true);
        return save(b, own, pkg + ".webp");
    }

    void removeOwn(String pkg) {
        //noinspection ResultOfMethodCallIgnored
        ownFile(pkg).delete();
    }

    /** The user's image, else from the cache, else drawn and cached. Null only if the app has no banner or icon. */
    Bitmap load(App a) {
        for (File f : new File[]{ownFile(a.pkg), new File(dir, bannerName(a)), new File(dir, iconName(a))}) {
            if (!f.isFile()) continue;
            Bitmap b = decode(f);
            if (b != null) return b;
        }
        Bitmap b = banner(a.info);
        String name = bannerName(a);
        if (b == null) {
            b = icon(a.info);
            name = iconName(a);
        }
        if (b == null) return null;
        save(b, dir, name);
        return toGpu(b);
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.HARDWARE;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        return b != null ? b : toGpu(BitmapFactory.decodeFile(f.getPath()));   // no hardware decode here: copy it up
    }

    /** The bitmap moved to the GPU; if the GPU refuses, the same one, uploaded now rather than during a frame. */
    private static Bitmap toGpu(Bitmap b) {
        if (b == null) return null;
        Bitmap h = b.copy(Bitmap.Config.HARDWARE, false);
        if (h == null) {
            b.prepareToDraw();
            return b;
        }
        b.recycle();
        return h;
    }

    /** Delete cached files that belong to no current app (old versions, removed apps, another density). */
    void cleanup(List<App> apps) {
        Set<String> valid = new HashSet<>();
        for (App a : apps) {
            valid.add(bannerName(a));
            valid.add(iconName(a));
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            if (f.getName().endsWith(".webp") && !valid.contains(f.getName())) f.delete();
        }
    }

    private Bitmap banner(ActivityInfo ai) {
        int id = ai.banner != 0 ? ai.banner : ai.applicationInfo.banner;
        Drawable d = null;
        if (id != 0) {
            try {
                // at least xhdpi artwork, so it is not reduced before the focus scale
                int dpi = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                d = pm.getResourcesForApplication(ai.applicationInfo).getDrawableForDensity(id, dpi, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) { }
        }
        if (d == null) d = ai.loadBanner(pm);
        if (d == null) return null;
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        if (w > 0 && h > 0 && w < h * 1.4f) return null;      // a square "banner" is an icon
        return draw(d, bannerW, bannerW * 9 / 16);
    }

    private Bitmap icon(ActivityInfo ai) {
        Drawable d = ai.loadIcon(pm);
        return d == null ? null : draw(d, ICON_PX, ICON_PX);
    }

    private static Bitmap draw(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(b));
        return b;
    }

    @SuppressWarnings("deprecation")
    private static boolean save(Bitmap b, File dir, String name) {
        Bitmap.CompressFormat fmt = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        File tmp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            b.compress(fmt, 90, out);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return tmp.renameTo(new File(dir, name));   // a half-written file is never read as a banner
    }
}
