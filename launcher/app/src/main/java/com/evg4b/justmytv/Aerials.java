package com.evg4b.justmytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The streamed Apple and Amazon aerials (PLAN 6.3, D49): res/raw/aerials.json, entries {"t": title, "u": https link,
 * "s": set, "k": theme}. Sets A (Apple) and Z (Amazon) only. Pure, JVM-tested.
 */
final class Aerials {
    static final String APPLE_HOST = "sylvan.apple.com";
    /** Collections (D100): "" is both. */
    static final String[] SOURCES = {"", "A", "Z"};
    /** Themes (D100), in settings order; an entry without a known "k" counts as landscapes. */
    static final String[] THEMES = {"nature", "space", "city", "water", "animals", "calm"};

    private Aerials() { }

    /** The https links; Apple's are left out once its TLS chain has failed on this TV. */
    static List<String> parse(String json, boolean skipApple) {
        return parse(json, skipApple, "", new java.util.HashSet<>(java.util.Arrays.asList(THEMES)));
    }

    /** The links of one collection ("" for both) and the chosen themes. */
    static List<String> parse(String json, boolean skipApple, String source, java.util.Set<String> themes) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String s = o.optString("s"), u = o.optString("u");
                if (!u.startsWith("https://") || !(s.equals("A") || s.equals("Z"))) continue;
                if (!source.isEmpty() && !source.equals(s)) continue;
                if (!themes.contains(theme(o.optString("k")))) continue;
                if (skipApple && isApple(u)) continue;
                out.add(u);
            }
        } catch (JSONException e) {
            // a broken list: no aerials, the poster stays
        }
        return out;
    }

    private static String theme(String k) {
        for (String t : THEMES) if (t.equals(k)) return t;
        return "nature";
    }

    static boolean isApple(String url) { return url.startsWith("https://" + APPLE_HOST + "/"); }

    /** A random link, never the same as the one before (PLAN 6.3). */
    static String next(List<String> urls, String previous, Random r) {
        if (urls.isEmpty()) return null;
        if (urls.size() == 1) return urls.get(0);
        String u;
        do u = urls.get(r.nextInt(urls.size())); while (u.equals(previous));
        return u;
    }
}
