package com.evg4b.justmytv;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.dreams.DreamService;

/** The screensaver (D112, SCREENSAVER): chosen by the user in TV Settings → Device Preferences → Screen saver. */
public class Screensaver extends DreamService {
    private ScreensaverView screen;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any button wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);
        screen = new ScreensaverView(this);
        setContentView(screen.root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        screen.start();
    }

    @Override public void onDreamingStopped() {
        screen.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (screen != null) screen.shutdown();
        super.onDetachedFromWindow();
    }

    /** The TV's screensaver is on and is this one. The keys are Android's own (Settings.Secure, hidden constants). */
    static boolean inUse(Context ctx) {
        String chosen = Settings.Secure.getString(ctx.getContentResolver(), "screensaver_components");
        boolean on = Settings.Secure.getInt(ctx.getContentResolver(), "screensaver_enabled", 1) != 0;
        String me = new ComponentName(ctx, Screensaver.class).flattenToString();
        return on && chosen != null && chosen.split(",")[0].equals(me);
    }
}
