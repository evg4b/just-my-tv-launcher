package com.justmytv.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Open-Meteo over HttpURLConnection on one background thread; results come back on the main thread (PLAN 4.6). */
final class WeatherClient {
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODE = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int MAX_BYTES = 256 * 1024;   // a forecast is under 1 KB; anything this big is not one

    interface Forecast { void onResult(String body, WeatherParser.Now now); }   // both null on failure

    interface Places { void onResult(List<WeatherParser.Place> places); }      // null on a network failure

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    void forecast(double lat, double lon, boolean fahrenheit, Forecast cb) {
        String u = FORECAST + "?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day&timezone=auto"
                + (fahrenheit ? "&temperature_unit=fahrenheit" : "");
        io.execute(() -> {
            String body = get(u);
            WeatherParser.Now now = WeatherParser.parseForecast(body);
            String good = now == null ? null : body;
            main.post(() -> cb.onResult(good, now));
        });
    }

    void geocode(String name, String language, Places cb) {
        String u;
        try {
            u = GEOCODE + "?name=" + URLEncoder.encode(name, "UTF-8") + "&count=5&format=json&language="
                    + URLEncoder.encode(language, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);   // UTF-8 is always there
        }
        io.execute(() -> {
            String body = get(u);
            List<WeatherParser.Place> places = body == null ? null : WeatherParser.parseGeocode(body);
            main.post(() -> cb.onResult(places));
        });
    }

    void shutdown() {
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    /** The body of a 200 response, or null; errors go to the log only (PLAN 4.7). */
    private static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5_000);
            c.setReadTimeout(5_000);
            if (c.getResponseCode() != 200) {
                Log.w("Weather", "HTTP " + c.getResponseCode());
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (IOException | RuntimeException e) {
            Log.w("Weather", "request failed", e);
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
