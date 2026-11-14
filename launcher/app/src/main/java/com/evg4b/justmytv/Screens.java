package com.evg4b.justmytv;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

    final SidePanel.Screen root, apps, inputs, wallpaper = this::wallpaperRows;

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
        p.section(R.drawable.ic_image, s(R.string.wallpaper), wallpaper);
        p.section(R.drawable.wx_sun_cloud, s(R.string.weather), this::weather);
        p.section(R.drawable.ic_apps, s(R.string.apps), apps);
        p.section(R.drawable.ic_palette, s(R.string.appearance), this::appearance);
        p.section(R.drawable.ic_input, s(R.string.inputs), this::inputSettings);
        p.section(R.drawable.ic_remote, s(R.string.remote), this::remote);
        p.section(R.drawable.ic_schedule, s(R.string.clock), this::clock);
        p.section(R.drawable.ic_info, s(R.string.about), this::about);
        p.item(a.getDrawable(R.drawable.ic_tv), s(R.string.tv_settings), null, () -> a.start(new Intent(Settings.ACTION_SETTINGS)));
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
            v[0] = p.item(art(app), app.label, shown(!st.hidden.contains(pkg)), () -> {
                boolean hide = !st.hidden.contains(pkg);
                a.setHidden(pkg, hide);
                v[0].setText(shown(!hide));
            });
        }
    }

    private Drawable art(App app) { return app.art == null ? null : new BitmapDrawable(a.getResources(), app.art); }

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

    // ---- remote buttons (PLAN 7.8) ----

    /** Linux scancodes of the TCL RC902N_M1 buttons, in the order of R.array.remote_buttons (docs/remote-buttons.md).
     *  Not listed: power, the Assistant (no event), and D-pad, OK, Back and Home, whose loss would lock the user out. */
    private static final int[] SCANCODES = {139, 395, 233, 365, 235, 749, 204, 242, 245, 227, 752, 115, 114, 113};

    private void remote(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.remote));
        p.item(s(R.string.remote_service), s(!serviceListed() ? R.string.off : RemoteKeyService.running ? R.string.on : R.string.remote_restart),
                () -> a.start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        String[] names = a.getResources().getStringArray(R.array.remote_buttons);
        for (int i = 0; i < SCANCODES.length; i++) {
            String code = String.valueOf(SCANCODES[i]), name = names[i];
            String pkg = st.remote.get(code);
            p.item(name, pkg == null ? s(R.string.remote_default) : pkg.equals(RemoteKeyService.DISABLED) ? s(R.string.remote_disabled)
                    : label(pkg), () -> panel.push(q -> pickApp(q, name, code)));
        }
        p.note(s(R.string.remote_note));
    }

    private void pickApp(SidePanel p, String button, String code) {
        p.title(button);
        p.item(s(R.string.remote_default), null, () -> assign(code, null));
        p.item(s(R.string.remote_disabled), null, () -> assign(code, RemoteKeyService.DISABLED));
        for (App app : a.installed) p.item(art(app), app.label, null, () -> assign(code, app.pkg));
    }

    private void assign(String code, String pkg) {
        StateJson st = a.state;
        if (pkg == null) st.remote.remove(code);
        else st.remote.put(code, pkg);
        a.save();
        RemoteKeyService.keys = new HashMap<>(st.remote);
        panel.back();
    }

    private String label(String pkg) {
        for (App app : a.installed) if (app.pkg.equals(pkg)) return app.label;
        return pkg;
    }

    private boolean serviceListed() {
        String on = Settings.Secure.getString(a.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(a.getPackageName() + "/");
    }

    // ---- wallpaper (PLAN 6, 10.1) ----

    private static final String[] TYPES = {"gradient", "picture", "photos", "video", "aerials"};
    private static final int[] PHOTO_MINUTES = {5, 15, 30, 60};   // 2 requests a photo; a new key has 50 an hour

    private boolean themesChanged;

    /** Leaving Themes (back to Wallpaper, or the panel closing): one new playlist for all the toggles. */
    void panelClosed() { applyThemes(); }

    private void applyThemes() {
        if (!themesChanged) return;
        themesChanged = false;
        a.aerialsChanged();
    }

    private void wallpaperRows(SidePanel p) {
        applyThemes();   // back from Themes
        StateJson st = a.state;
        p.title(s(R.string.wallpaper));
        String[] names = {s(R.string.wp_gradient), s(R.string.wp_picture), s(R.string.wp_photos), s(R.string.wp_video),
                s(R.string.wp_aerials)};
        p.choice(s(R.string.wallpaper_type), names, java.util.Arrays.asList(TYPES).indexOf(st.wallpaper), i -> {
            st.wallpaper = TYPES[i];
            a.save();
            a.applyWallpaperSoon();   // not at every step on the way from one type to another
            panel.refresh();   // the photo and aerial options come and go with the type
        });
        String note = null;   // under the rows, after Dimming
        switch (st.wallpaper) {
            case "picture":
                p.item(s(R.string.choose_picture), s(st.imageUri.isEmpty() ? R.string.not_chosen : R.string.chosen), a::pickPicture);
                break;
            case "video":
                p.item(s(R.string.choose_video), s(st.videoUri.isEmpty() ? R.string.not_chosen : R.string.chosen), a::pickVideo);
                break;
            case "photos": {
                String key = st.unsplashKey;
                p.item(s(R.string.unsplash_key), (key.isEmpty() ? s(R.string.city_not_set) : "…" + key.substring(Math.max(0, key.length() - 4)))
                        + "  ›", () -> panel.push(this::unsplashKey));
                String[] every = {s(R.string.min_5), s(R.string.min_15), s(R.string.min_30), s(R.string.hour_1)};
                int cur = 0;
                while (PHOTO_MINUTES[cur] != st.photoMinutes && cur < PHOTO_MINUTES.length - 1) cur++;
                p.choice(s(R.string.photo_every), every, cur, i -> {
                    st.photoMinutes = PHOTO_MINUTES[i];
                    a.save();
                    a.photos.setInterval(st.photoMinutes * 60_000L);
                });
                p.item(s(R.string.next_photo), a.photos.fetching() ? s(R.string.loading) : null, a.photos::next);
                StringBuilder n = new StringBuilder();
                switch (a.photos.status()) {
                    case NO_KEY: n.append(s(R.string.photos_no_key)).append('\n'); break;
                    case BAD_KEY: n.append(s(R.string.photos_bad_key)).append('\n'); break;
                    case LIMIT: n.append(s(R.string.photos_limit)).append('\n'); break;
                    case FAILED: n.append(s(R.string.photos_failed)).append('\n'); break;
                    default: break;
                }
                String by = a.photos.author();
                if (!by.isEmpty()) n.append(a.getString(R.string.photo_by, by)).append('\n');
                note = n.append(s(R.string.photos_note)).toString();
                break;
            }
            case "aerials": {
                String[] sources = {s(R.string.aerials_all), "Apple", "Amazon"};
                p.choice(s(R.string.aerials_collection), sources, java.util.Arrays.asList(Aerials.SOURCES).indexOf(st.aerialSource), i -> {
                    st.aerialSource = Aerials.SOURCES[i];
                    a.aerialsChanged();
                    panel.refresh();   // the count below
                });
                p.item(s(R.string.aerials_themes), a.getString(R.string.n_of_m, st.aerialThemes.size(), Aerials.THEMES.length) + "  ›",
                        () -> panel.push(this::aerialThemes));
                note = aerialsCount(st) + "\n" + s(R.string.aerials_note);
                break;
            }
            default:
                break;
        }
        if (!st.wallpaper.equals("gradient")) {   // the gradient is never dimmed (applyDim)
            p.choice(s(R.string.dimming), new String[]{"0 %", "20 %", "40 %", "60 %"}, st.dim / 20, i -> {
                st.dim = i * 20;
                a.save();
                a.applyDim();
            });
        }
        if (note != null) p.note(note);
    }

    /** The owner's Unsplash access key (D102): typed once, kept in the state file on the TV. */
    private void unsplashKey(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.unsplash_key));
        android.widget.EditText e = p.edit(s(R.string.unsplash_key_hint), st.unsplashKey, k -> {
            st.unsplashKey = k.trim();
            a.save();
            a.applyWallpaper();
            panel.back();
        });
        // a key is case-sensitive: no capitals or suggestions from the keyboard
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        if (!st.unsplashKey.isEmpty()) {
            p.item(s(R.string.unsplash_key_remove), null, () -> {
                st.unsplashKey = "";
                a.save();
                a.applyWallpaper();
                panel.back();
            });
        }
        p.note(s(R.string.unsplash_key_note));
    }

    private static final int[] THEME_NAMES = {R.string.theme_nature, R.string.theme_space, R.string.theme_city,
            R.string.theme_water, R.string.theme_animals, R.string.theme_calm};

    /** Which aerials play (D100); the last theme cannot be turned off. */
    private void aerialThemes(SidePanel p) {
        StateJson st = a.state;
        p.title(s(R.string.aerials_themes));
        for (int i = 0; i < Aerials.THEMES.length; i++) {
            String t = Aerials.THEMES[i];
            p.toggle(s(THEME_NAMES[i]), st.aerialThemes.contains(t), on -> {
                if (on) st.aerialThemes.add(t);
                else if (st.aerialThemes.size() > 1) st.aerialThemes.remove(t);
                a.save();
                themesChanged = true;   // the clip changes once, when this screen is left
                panel.refresh();   // the count, and the last theme back on
            });
        }
        p.note(aerialsCount(st));
    }

    private String aerialsCount(StateJson st) {
        int n = Aerials.parse(a.aerialsJson(), false, st.aerialSource, st.aerialThemes).size();
        return n == 0 ? s(R.string.aerials_none) : a.getResources().getQuantityString(R.plurals.aerials_count, n, n);
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
