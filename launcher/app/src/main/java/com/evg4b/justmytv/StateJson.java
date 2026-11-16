package com.evg4b.justmytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * launcher_state.json as fields with defaults (PLAN 9.1). Pure (no android.*), JVM-tested.
 * A corrupt or empty file gives the defaults; values out of range are clamped; keys this version does not know
 * (the wallpaper until M4, say) are kept as they were.
 */
final class StateJson {
    List<String> order = new ArrayList<>();
    Set<String> hidden = new LinkedHashSet<>();
    Set<String> known = new LinkedHashSet<>();
    String lastFocused = "";

    // wallpaper (PLAN 6)
    String wallpaper = "gradient";    // gradient | picture | photos | video | aerials
    String videoUri = "", imageUri = "";
    int dim = 20;                      // 0, 20, 40 or 60 %
    int photoMinutes = 5;              // the Photos wallpaper changes every 5, 15, 30 or 60 min (D101)
    String unsplashKey = "";           // the owner's Unsplash access key (D102); stays on the TV
    String aerialSource = "";          // "" (both), "A" or "Z" (D100)
    Set<String> aerialThemes = allThemes();   // never empty

    // ui
    int columns = 5;              // 4..7
    int rowsBelow = 0;            // 0..3
    boolean labels = true;
    int corner = 16;              // 0, 8 or 16 dp
    boolean showInputsButton = true;
    boolean inputsConnectedOnly = false;
    boolean inputsHideAv = false;
    boolean hideSettingsButton = false;   // honoured only while a remote button opens the settings (D109)
    static final String[] EDGES = {"both", "top", "bottom", "off"};
    String edgeDim = "top";        // one of EDGES: which screen edges get a dark gradient

    // clock
    boolean h24 = true;
    boolean showDate = true;
    boolean greeting = false;

    // weather
    boolean weatherEnabled = true;
    String city = "", admin1 = "";
    double lat, lon;
    boolean fahrenheit = false;
    String weatherLast = "";      // the last good forecast response
    long fetchedAt = 0;           // ms

    // remote buttons (PLAN 7.8): scancode -> package; default YouTube -> SmartTube
    Map<String, String> remote = defaultRemote();

    private JSONObject raw = new JSONObject();

    static Map<String, String> defaultRemote() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("245", "org.smarttube.stable");
        return m;
    }

    boolean hasCity() { return !city.isEmpty(); }

    static Set<String> allThemes() { return new LinkedHashSet<>(java.util.Arrays.asList(Aerials.THEMES)); }

    static StateJson parse(String json) {
        StateJson s = new StateJson();
        if (json == null || json.trim().isEmpty()) return s;
        try {
            JSONObject o = new JSONObject(json);
            s.raw = o;
            s.order = strings(o.optJSONArray("order"));
            s.hidden = new LinkedHashSet<>(strings(o.optJSONArray("hidden")));
            s.known = new LinkedHashSet<>(strings(o.optJSONArray("known")));
            s.lastFocused = o.optString("lastFocused", "");
            JSONObject wp = o.optJSONObject("wallpaper");
            if (wp != null) {
                String t = wp.optString("type", "gradient");
                s.wallpaper = t.equals("picture") || t.equals("photos") || t.equals("video") || t.equals("aerials")
                        ? t : "gradient";
                s.videoUri = wp.optString("uri", "");
                s.imageUri = wp.optString("image", "");
                int dm = wp.optInt("dim", 20);
                s.dim = dm == 0 || dm == 40 || dm == 60 ? dm : 20;
                int pm = wp.optInt("photoMinutes", 5);
                s.photoMinutes = pm == 15 || pm == 30 || pm == 60 ? pm : 5;
                s.unsplashKey = wp.optString("unsplashKey", "").trim();
                String src = wp.optString("aerialSource", "");
                s.aerialSource = src.equals("A") || src.equals("Z") ? src : "";
                JSONArray th = wp.optJSONArray("aerialThemes");
                if (th != null) {
                    Set<String> known = new LinkedHashSet<>(strings(th));
                    known.retainAll(allThemes());
                    if (!known.isEmpty()) s.aerialThemes = known;
                }
            }
            JSONObject ui = o.optJSONObject("ui");
            if (ui != null) {
                s.columns = clamp(ui.optInt("columns", 5), 4, 7);
                s.rowsBelow = clamp(ui.optInt("rowsBelow", 0), 0, 3);
                s.labels = ui.optBoolean("labels", true);
                int c = ui.optInt("corner", 16);
                s.corner = c == 0 || c == 8 ? c : 16;
                s.showInputsButton = ui.optBoolean("showInputsButton", true);
                s.inputsConnectedOnly = ui.optBoolean("inputsConnectedOnly", false);
                s.inputsHideAv = ui.optBoolean("inputsHideAv", false);
                s.hideSettingsButton = ui.optBoolean("hideSettingsButton", false);
                String e = ui.optString("edgeDim", "top");
                s.edgeDim = java.util.Arrays.asList(EDGES).contains(e) ? e : "top";
            }
            JSONObject clock = o.optJSONObject("clock");
            if (clock != null) {
                s.h24 = clock.optBoolean("h24", true);
                s.showDate = clock.optBoolean("showDate", true);
                s.greeting = clock.optBoolean("greeting", false);
            }
            JSONObject rm = o.optJSONObject("remote");
            if (rm != null) {
                s.remote = new LinkedHashMap<>();
                for (Iterator<String> it = rm.keys(); it.hasNext(); ) {
                    String k = it.next(), v = rm.optString(k, "");
                    if (!v.isEmpty()) s.remote.put(k, v);
                }
            }
            JSONObject w = o.optJSONObject("weather");
            if (w != null) {
                s.weatherEnabled = w.optBoolean("enabled", true);
                s.city = w.optString("name", "");
                s.admin1 = w.optString("admin1", "");
                s.lat = w.optDouble("lat", 0);
                s.lon = w.optDouble("lon", 0);
                if (Double.isNaN(s.lat) || Double.isNaN(s.lon)) s.city = "";
                s.fahrenheit = w.optBoolean("fahrenheit", false);
                s.weatherLast = w.optString("last", "");
                s.fetchedAt = w.optLong("fetchedAt", 0);
            }
        } catch (JSONException e) {
            return new StateJson();
        }
        return s;
    }

    String toJson() {
        try {
            JSONObject o = new JSONObject(raw.toString());   // a copy, so unknown keys survive
            o.put("version", 1);
            o.put("order", new JSONArray(order));
            o.put("hidden", new JSONArray(hidden));
            o.put("known", new JSONArray(known));
            o.put("lastFocused", lastFocused);
            o.put("wallpaper", obj(o, "wallpaper").put("type", wallpaper).put("uri", videoUri).put("image", imageUri)
                    .put("dim", dim).put("photoMinutes", photoMinutes).put("unsplashKey", unsplashKey).put("aerialSource", aerialSource).put("aerialThemes", new JSONArray(aerialThemes)));
            o.put("ui", obj(o, "ui").put("columns", columns).put("rowsBelow", rowsBelow).put("labels", labels)
                    .put("corner", corner).put("showInputsButton", showInputsButton)
                    .put("inputsConnectedOnly", inputsConnectedOnly).put("inputsHideAv", inputsHideAv)
                    .put("hideSettingsButton", hideSettingsButton).put("edgeDim", edgeDim));
            o.put("remote", new JSONObject(remote));
            o.put("clock", obj(o, "clock").put("h24", h24).put("showDate", showDate).put("greeting", greeting));
            JSONObject w = obj(o, "weather").put("enabled", weatherEnabled).put("name", city).put("admin1", admin1)
                    .put("fahrenheit", fahrenheit).put("last", weatherLast).put("fetchedAt", fetchedAt);
            if (hasCity()) w.put("lat", lat).put("lon", lon);
            o.put("weather", w);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);   // only NaN or infinite numbers get here, and none are stored
        }
    }

    private static JSONObject obj(JSONObject o, String key) {
        JSONObject v = o.optJSONObject(key);
        return v != null ? v : new JSONObject();
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String v = a.optString(i, "");
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
