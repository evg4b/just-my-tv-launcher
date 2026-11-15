package com.evg4b.justmytv;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.Map;

/**
 * Built-in actions a remote button can run instead of opening an app (D106, D107). Stored in the `remote` map as "@" + id;
 * a package name never starts with "@". Turning Wi-Fi or Bluetooth on and off is not here: Android 10+ lets only system
 * apps do it, and the TV's remote is a Bluetooth one.
 */
final class RemoteActions {
    static final String PREFIX = "@";
    static final String LAUNCHER_SETTINGS = "@launcher_settings", INPUTS = "@inputs";
    /** Launcher actions first, then the TV's; the order of the settings list. */
    static final String[] IDS = {"@home", "@back", LAUNCHER_SETTINGS, INPUTS,
            "@tv_settings", "@network", "@picture", "@sound", "@power", "@mute", "@play_pause"};
    static final int[] LABELS = {R.string.act_home, R.string.act_back, R.string.act_launcher_settings, R.string.inputs,
            R.string.act_tv_settings, R.string.act_network, R.string.act_picture, R.string.act_sound, R.string.act_power,
            R.string.act_mute, R.string.act_play_pause};
    /** The Home intent's extra: which launcher panel to toggle (PANEL_SETTINGS or PANEL_INPUTS). */
    static final String EXTRA_PANEL = "com.evg4b.justmytv.PANEL";
    static final String PANEL_SETTINGS = "settings", PANEL_INPUTS = "inputs";

    private RemoteActions() { }

    static boolean isAction(String value) { return value.startsWith(PREFIX); }

    /** The position in IDS, or -1 for an id this version does not know (written by a newer one, say). */
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** The launcher panel an action toggles, or null for an action that is not one. */
    static String panelFor(String id) {
        return LAUNCHER_SETTINGS.equals(id) ? PANEL_SETTINGS : INPUTS.equals(id) ? PANEL_INPUTS : null;
    }

    /** Some remote button opens the launcher settings. */
    static boolean opensSettings(Map<String, String> remote) { return remote.containsValue(LAUNCHER_SETTINGS); }

    /** The gear hides only while a bound service runs a button that opens the settings: they stay reachable (D109). */
    static boolean settingsIconShown(boolean hideWanted, Map<String, String> remote, boolean serviceRunning) {
        return !hideWanted || !serviceRunning || !opensSettings(remote);
    }

    /** Runs a known action (index(id) >= 0). */
    static void run(AccessibilityService s, String id) {
        String panel = panelFor(id);
        if (panel != null) {
            // a Home intent, not an explicit one: only that reaches the Home instance; any other start makes a second launcher
            start(s, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(s.getPackageName())
                    .putExtra(EXTRA_PANEL, panel));
            return;
        }
        switch (id) {
            case "@home": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); break;
            case "@back": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); break;
            case "@power": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); break;
            case "@tv_settings": start(s, new Intent(Settings.ACTION_SETTINGS)); break;
            case "@network": settings(s, Settings.ACTION_WIFI_SETTINGS); break;
            case "@picture": settings(s, Settings.ACTION_DISPLAY_SETTINGS); break;
            case "@sound": settings(s, Settings.ACTION_SOUND_SETTINGS); break;
            case "@mute":   // the path the remote's own Mute key takes: the stream that is playing
                s.getSystemService(AudioManager.class).adjustSuggestedStreamVolume(AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                break;
            case "@play_pause": {   // to the app that plays media, as the remote's own play key would
                AudioManager am = s.getSystemService(AudioManager.class);
                long t = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                break;
            }
            default: break;
        }
    }

    /** A settings page; the TV's main settings when this TV does not have it, rather than a press that does nothing. */
    private static void settings(AccessibilityService s, String action) {
        if (!start(s, new Intent(action))) start(s, new Intent(Settings.ACTION_SETTINGS));
    }

    private static boolean start(AccessibilityService s, Intent i) {
        try {
            s.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
