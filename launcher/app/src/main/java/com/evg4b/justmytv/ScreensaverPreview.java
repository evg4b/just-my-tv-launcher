package com.evg4b.justmytv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** Settings → Screensaver → Preview: the screensaver's screen, whatever screensaver the TV uses; any button closes it. */
public class ScreensaverPreview extends Activity {
    private ScreensaverView screen;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        screen = new ScreensaverView(this);
        setContentView(screen.root);
    }

    @Override protected void onStart() {
        super.onStart();
        screen.start();
    }

    @Override protected void onStop() {
        screen.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        screen.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) finish();   // on release, so the press does not reach Home behind
        return true;
    }
}
