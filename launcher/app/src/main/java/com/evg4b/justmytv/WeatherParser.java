package com.evg4b.justmytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Open-Meteo responses, parsed with the platform org.json (PLAN 4.5). Pure, JVM-tested; never throws. */
final class WeatherParser {
    private WeatherParser() { }

    static final class Now {
        final int temp;         // rounded, in the units of the request
        final String unit;      // "°C" or "°F", from current_units
        final int code;
        final boolean isDay;
        final String time;      // "2026-11-14T00:15", local time

        Now(int t, String u, int c, boolean d, String tm) { temp = t; unit = u; code = c; isDay = d; time = tm; }
    }

    static final class Place {
        final String name, admin1, country;
        final double lat, lon;

        Place(String n, String a, String c, double la, double lo) { name = n; admin1 = a; country = c; lat = la; lon = lo; }
    }

    /** null: the response was not recognised (show the cache). */
    static Now parseForecast(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("error", false)) return null;
            JSONObject cur = root.getJSONObject("current");
            JSONObject units = root.optJSONObject("current_units");
            String unit = units == null ? "°C" : units.optString("temperature_2m", "°C");
            double t = cur.getDouble("temperature_2m");
            return new Now((int) Math.round(t), unit, cur.getInt("weather_code"),
                    cur.optInt("is_day", 1) == 1, cur.optString("time", ""));
        } catch (JSONException e) {
            return null;
        }
    }

    /** The places found; empty if there is no results key ("nothing found") or the response is broken. */
    static List<Place> parseGeocode(String json) {
        List<Place> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray arr = new JSONObject(json).optJSONArray("results");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Place(o.optString("name", ""), o.optString("admin1", ""), o.optString("country", ""),
                        o.getDouble("latitude"), o.getDouble("longitude")));
            }
        } catch (JSONException e) {
            // a response without the required fields: what was read so far
        }
        return out;
    }
}
