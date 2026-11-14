package com.justmytv.tvhome;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The settings screens (PLAN 10.1) and the Inputs panel (PLAN 7.7). All settings apply at once. */
final class Screens {
    private final MainActivity a;
    private final SidePanel panel;

    final SidePanel.Screen root, apps, inputs;

    Screens(MainActivity a, SidePanel panel) {
        this.a = a;
        this.panel = panel;
        root = this::root;
        apps = this::apps;
        inputs = this::inputs;
    }

    private String s(int id) { return a.getString(id); }

    private void root(SidePanel p) {
        p.title(s(R.string.settings));
        p.section(s(R.string.wallpaper), this::wallpaper);
        p.section(s(R.string.weather), this::weather);
        p.section(s(R.string.apps), apps);
        p.section(s(R.string.appearance), this::appearance);
        p.section(s(R.string.inputs), this::inputSettings);
        p.section(s(R.string.clock), this::clock);
        p.section(s(R.string.about), this::about);
        p.item(s(R.string.tv_settings), null, () -> a.start(new Intent(Settings.ACTION_SETTINGS)));
    }

    /** All launchable apps in grid order, hidden ones too (PLAN 9.5). */
    private void apps(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.apps));
        p.item(s(R.string.show_all), null, () -> {
            if (st.hidden.isEmpty()) return;
            st.hidden.clear();
            a.save();
            a.applyApps();
            panel.refresh();
        });
        Map<String, App> byPkg = new HashMap<>();
        for (App app : a.installed) byPkg.put(app.pkg, app);
        for (String pkg : new ArrayList<>(st.order)) {
            App app = byPkg.get(pkg);
            if (app == null) continue;
            TextView[] v = new TextView[1];
            v[0] = p.item(app.art, app.label, shown(!st.hidden.contains(pkg)), () -> {
                boolean hide = !st.hidden.contains(pkg);
                a.setHidden(pkg, hide);
                v[0].setText(shown(!hide));
            });
        }
    }

    private String shown(boolean shown) { return s(shown ? R.string.shown : R.string.hidden); }

    private void appearance(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.appearance));
        p.choice(s(R.string.columns), new String[]{"4", "5", "6", "7"}, st.columns - 4, i -> {
            st.columns = 4 + i;
            layoutChanged();
        });
        p.choice(s(R.string.rows_below), new String[]{"0", "1", "2", "3"}, st.rowsBelow, i -> {
            st.rowsBelow = i;
            layoutChanged();
        });
        p.toggle(s(R.string.labels), st.labels, on -> {
            st.labels = on;
            layoutChanged();
        });
        int[] corners = {0, 8, 16};
        p.choice(s(R.string.corners), new String[]{"0 dp", "8 dp", "16 dp"}, st.corner / 8, i -> {
            st.corner = corners[i];
            layoutChanged();
        });
    }

    private void layoutChanged() {
        a.save();
        a.applyLayout();
    }

    private void inputSettings(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.inputs));
        p.toggle(s(R.string.inputs_button), st.showInputsButton, on -> {
            st.showInputsButton = on;
            a.save();
            a.updateInputsButton();
        });
        p.toggle(s(R.string.connected_only), st.inputsConnectedOnly, on -> {
            st.inputsConnectedOnly = on;
            a.save();
            a.updateInputsButton();
        });
        p.toggle(s(R.string.hide_av), st.inputsHideAv, on -> {
            st.inputsHideAv = on;
            a.save();
            a.updateInputsButton();
        });
    }

    private void clock(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.clock));
        p.toggle(s(R.string.h24), st.h24, on -> {
            st.h24 = on;
            a.save();
            a.applyClock();
        });
        p.toggle(s(R.string.show_date), st.showDate, on -> {
            st.showDate = on;
            a.save();
            a.applyClock();
        });
        p.toggle(s(R.string.greeting), st.greeting, on -> {
            st.greeting = on;
            a.save();
            a.applyClock();
        });
    }

    private void about(SidePanel p) {
        String version = "";
        try {
            version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) { }
        p.title(s(R.string.about));
        p.note(a.getString(R.string.about_text, version));
    }

    // ---- wallpaper (PLAN 6, 10.1) ----

    private static final String[] TYPES = {"gradient", "picture", "video", "aerials"};

    private void wallpaper(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.wallpaper));
        String[] names = {s(R.string.wp_gradient), s(R.string.wp_picture), s(R.string.wp_video), s(R.string.wp_aerials)};
        p.choice(s(R.string.wallpaper_type), names, java.util.Arrays.asList(TYPES).indexOf(st.wallpaper), i -> {
            st.wallpaper = TYPES[i];
            a.save();
            a.applyWallpaper();
        });
        p.item(s(R.string.choose_video), s(st.videoUri.isEmpty() ? R.string.not_chosen : R.string.chosen), a::pickVideo);
        p.item(s(R.string.choose_picture), s(st.imageUri.isEmpty() ? R.string.not_chosen : R.string.chosen), a::pickPicture);
        p.choice(s(R.string.dimming), new String[]{"0 %", "20 %", "40 %", "60 %"}, st.dim / 20, i -> {
            st.dim = i * 20;
            a.save();
            a.applyDim();
        });
        p.note(s(R.string.aerials_note));
    }

    // ---- weather (PLAN 4.7, 10.1) ----

    private void weather(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.weather));
        p.toggle(s(R.string.weather), st.weatherEnabled, on -> {
            st.weatherEnabled = on;
            a.save();
            a.showWeather();
            if (on) a.refreshWeather();
        });
        p.item(s(R.string.city), (st.hasCity() ? st.city : s(R.string.city_not_set)) + "  ›", () -> panel.push(this::city));
        p.choice(s(R.string.units), new String[]{"°C", "°F"}, st.fahrenheit ? 1 : 0, i -> {
            st.fahrenheit = i == 1;
            st.weatherLast = "";   // the cache is in the old units
            a.save();
            a.showWeather();
            a.refreshWeather();
        });
        p.item(s(R.string.refresh_now), null, a::refreshWeather);
        p.note(s(R.string.weather_attribution));
    }

    // what the City screen shows below the field: results, "searching", "nothing found"
    private String cityQuery = "", cityStatus = "";
    private List<WeatherParser.Place> cityResults = new ArrayList<>();

    private void city(SidePanel p) {
        p.title(s(R.string.city));
        p.edit(s(R.string.city_hint), cityQuery, q -> {
            cityQuery = q;
            cityResults = new ArrayList<>();
            cityStatus = s(R.string.searching);
            panel.refresh();
            a.weather.geocode(q, Locale.getDefault().getLanguage(), places -> {
                if (!q.equals(cityQuery)) return;   // a newer search is running
                cityResults = places == null ? new ArrayList<>() : places;
                cityStatus = places == null ? s(R.string.no_network) : places.isEmpty() ? s(R.string.nothing_found) : "";
                panel.refresh();
            });
        });
        if (!cityStatus.isEmpty()) p.note(cityStatus);
        for (WeatherParser.Place place : cityResults) {
            StringBuilder where = new StringBuilder();
            for (String part : new String[]{place.admin1, place.country}) {
                if (part.isEmpty()) continue;
                if (where.length() > 0) where.append(", ");
                where.append(part);
            }
            p.item(place.name, where.toString(), () -> {
                StateJson st = a.state;
                st.city = place.name;
                st.admin1 = place.admin1;
                st.lat = place.lat;
                st.lon = place.lon;
                st.weatherLast = "";
                st.fetchedAt = 0;
                a.save();
                a.showWeather();
                a.refreshWeather();
                cityResults = new ArrayList<>();
                cityStatus = "";
                panel.back();
            });
        }
    }

    // ---- Inputs panel (PLAN 7.7) ----

    private void inputs(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.inputs));
        List<Inputs.Row> rows = Inputs.list(a, st.inputsConnectedOnly);
        List<Inputs.Row> av = new ArrayList<>();
        for (Inputs.Row r : rows) {
            if (r.hdmi) inputRow(p, r);
            else av.add(r);
        }
        if (!av.isEmpty() && !st.inputsHideAv) {
            p.section(s(R.string.more), q -> {
                q.title(s(R.string.more));
                for (Inputs.Row r : av) inputRow(q, r);
            });
        }
        if (rows.isEmpty()) p.note(s(R.string.no_inputs));
        p.item(s(R.string.system_inputs), null, () -> {
            panel.close();
            a.start(new Intent(Inputs.VIEW_INPUTS));
        });
    }

    /** The state as a dot: green connected, grey waiting (TCL reports empty ports as waiting), dim disconnected. */
    private void inputRow(SidePanel p, Inputs.Row r) {
        TextView v = p.item(r.label, "●", () -> {
            if (Inputs.switchTo(a, r.id)) panel.close();
            else Toast.makeText(a, R.string.input_not_opened, Toast.LENGTH_SHORT).show();
        });
        v.setTextColor(r.state == 0 ? 0xFF66BB6A : r.state == 1 ? 0xFF9E9E9E : 0x40FFFFFF);
    }
}
