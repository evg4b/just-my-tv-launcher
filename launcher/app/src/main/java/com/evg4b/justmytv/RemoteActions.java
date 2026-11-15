package com.evg4b.justmytv;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * Built-in actions a remote button can run instead of opening an app (D106). Stored in the `remote` map as "@" + id;
 * a package name never starts with "@". Turning Wi-Fi or Bluetooth on and off is not here: Android 10+ lets only system
 * apps do it, and the TV's remote is a Bluetooth one.
 */
final class RemoteActions {
    static final String PREFIX = "@";
    /** Launcher actions first, then the TV's; the order of the settings list. */
    static final String[] IDS = {"@home", "@back", "@launcher_settings", "@inputs",
            "@tv_settings", "@network", "@picture", "@sound", "@power", "@mute", "@play_pause"};
    static final int[] LABELS = {R.string.act_home, R.string.act_back, R.string.act_launcher_settings, R.string.inputs,
            R.string.act_tv_settings, R.string.act_network, R.string.act_picture, R.string.act_sound, R.string.act_power,
            R.string.act_mute, R.string.act_play_pause};

    private RemoteActions() { }

    static boolean isAction(String value) { return value.startsWith(PREFIX); }

    /** The position in IDS, or -1 for an id this version does not know (written by a newer one, say). */
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** Runs a known action (index(id) >= 0). */
    static void run(AccessibilityService s, String id) {
        switch (id) {
            case "@home": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); break;
            case "@back": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); break;
            case "@power": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); break;
            case "@launcher_settings": start(s, new Intent(s, MainActivity.class).setAction(MainActivity.OPEN_SETTINGS)); break;
            case "@inputs": start(s, new Intent(s, MainActivity.class).setAction(MainActivity.OPEN_INPUTS)); break;
            case "@tv_settings": start(s, new Intent(Settings.ACTION_SETTINGS)); break;
            case "@network": start(s, new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
            case "@picture": start(s, new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
            case "@sound": start(s, new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "@mute":
                s.getSystemService(AudioManager.class)
                        .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "@play_pause": {   // to the app that plays media, as the remote's own play key would
                AudioManager am = s.getSystemService(AudioManager.class);
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                break;
            }
            default: break;
        }
    }

    private static void start(AccessibilityService s, Intent i) {
        try {
            s.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // a settings page this TV does not have: the press does nothing
        }
    }
}
