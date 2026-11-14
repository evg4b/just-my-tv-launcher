package com.evg4b.justmytv;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Map;

/**
 * Remote buttons anywhere on the TV (PLAN 7.8): a press of an assigned button launches its app. Matched by scancode,
 * since the TCL_* keys have no standard keycode. Enabled by hand in the TV's accessibility settings.
 */
public class RemoteKeyService extends AccessibilityService {
    /** scancode -> package; the launcher updates it when the user changes an assignment (same process). */
    static volatile Map<String, String> keys;
    /** Bound by the system right now. After an app update this TV keeps the service listed but does not bind it. */
    static volatile boolean running;

    @Override
    protected void onServiceConnected() {
        running = true;
        if (keys == null) keys = StateJson.parse(new LauncherState(this).read()).remote;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        Map<String, String> k = keys;
        String pkg = k == null ? null : k.get(String.valueOf(e.getScanCode()));
        if (pkg == null) return false;
        Intent i = launchIntent(getPackageManager(), pkg);
        if (i == null) return false;   // the app is gone: the button does what it did before
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            try {
                startActivity(i);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                return false;
            }
        }
        return true;   // DOWN and UP both, so the system's own action does not run as well
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i == null ? null : i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
