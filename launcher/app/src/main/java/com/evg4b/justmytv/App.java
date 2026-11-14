package com.evg4b.justmytv;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;

/** One launchable app. {@link #art} is set on the main thread once its banner or icon is ready. */
final class App {
    final ActivityInfo info;
    final String pkg, label;
    final boolean leanback;
    final long firstInstall, lastUpdate;
    Bitmap art;

    App(ActivityInfo info, String label, boolean leanback, long firstInstall, long lastUpdate) {
        this.info = info;
        this.pkg = info.packageName;
        this.label = label;
        this.leanback = leanback;
        this.firstInstall = firstInstall;
        this.lastUpdate = lastUpdate;
    }

    /** Banners are wide; an icon is square (PLAN 5.1). */
    boolean hasBanner() {
        return art != null && art.getWidth() >= art.getHeight() * 1.4f;
    }
}
