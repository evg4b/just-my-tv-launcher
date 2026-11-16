package com.evg4b.justmytv;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The clock, the date and the weather from the state, for Home and the screensaver (SCREENSAVER 4.2). Both layouts use
 * the same view ids; a view a layout does not have (the divider, the stale dot, the update time) is skipped.
 */
final class HeaderBinder {
    static final long WEATHER_OLD = 3 * 60 * 60_000L;   // a cache older than this gets the dot and its time

    private HeaderBinder() { }

    /** 12/24 h and the date (PLAN 10.1, Clock). */
    static void clock(View root, StateJson st) {
        String fmt = st.h24 ? "HH:mm" : "h:mm";
        TextClock clock = root.findViewById(R.id.clock);
        clock.setFormat12Hour(fmt);
        clock.setFormat24Hour(fmt);
        int date = st.showDate ? View.VISIBLE : View.GONE;
        show(root, R.id.date, date);
        show(root, R.id.date_divider, date);
    }

    /** The cached forecast; hidden when off, without a city or before the first answer. failed: the last refresh failed. */
    static void weather(View root, StateJson st, boolean failed) {
        View row = root.findViewById(R.id.weather);
        WeatherParser.Now now = st.weatherEnabled && st.hasCity() ? WeatherParser.parseForecast(st.weatherLast) : null;
        row.setVisibility(now == null ? View.GONE : View.VISIBLE);
        if (now == null) return;
        ((ImageView) root.findViewById(R.id.wx_icon)).setImageResource(WeatherCode.res(WeatherCode.icon(now.code, now.isDay)));
        ((TextView) root.findViewById(R.id.wx_temp)).setText(now.temp + "°");
        ((TextView) root.findViewById(R.id.wx_city)).setText(st.city);
        boolean old = System.currentTimeMillis() - st.fetchedAt > WEATHER_OLD;
        show(root, R.id.wx_dot, old ? View.VISIBLE : View.GONE);
        TextView updated = root.findViewById(R.id.wx_updated);
        if (updated == null) return;
        updated.setVisibility(old || failed ? View.VISIBLE : View.GONE);
        String time = new SimpleDateFormat(st.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date(st.fetchedAt));
        updated.setText(root.getContext().getString(R.string.weather_updated, time));
    }

    private static void show(View root, int id, int visibility) {
        View v = root.findViewById(id);
        if (v != null) v.setVisibility(visibility);
    }
}
