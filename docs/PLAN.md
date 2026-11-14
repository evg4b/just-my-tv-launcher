# Your own home screen for Android TV: research and plan (version 2)

Status: **built.** M0–M7 are implemented in `launcher/` and checked on the TV (2026-11-14); what is left is listed in
OPEN-QUESTIONS (T16–T19, S1, S5). The plan below is kept as written, with status lines per stage (section 15). This document is meant so that the next iteration of the model can build the project
without a human writing the code. Every claim is marked with its source:

- `path:line`: a file in `sources/` (CleanYourTV or Couchy), checked by reading;
- `curl`: a live response from the Open-Meteo API, saved as fixtures;
- `WebFetch` / `gh`: a page or repository, checked on 2026-11-13;
- `ADB`: a command on the TV `<TV_IP>`, read-only;
- **verify on the TV**: not yet checked; the plan says what exactly to do.

Visual mockup: [docs/screen-mockup.html](screen-mockup.html) (960×540 dp, the panels and focus are interactive).

---

> Personal values (IP addresses, personal package names, device identifiers) are replaced with placeholders such as `<TV_IP>`. The original values are in the local file `private/redactions.md`, which is not stored in git.

## 0. Summary

We take:

- **The architecture and build from CleanYourTV** (`sources/cleanyourtv`): native Java, Android Views, RecyclerView,
  R8, the Docker build. This is what makes it fast.
- **The grid with a bar** (`dock_row`): at rest the bar sits at the bottom edge, and the next row peeks up from below; scrolling down expands the grid. Move / App info menu on a long OK press (`tile_menu`).
- **Video wallpapers** from Couchy (`sources/couchy-launcher`): ExoPlayer (Media3), `SurfaceView`, no sound,
  the player is released when the screen is left. Rewritten for Views.
- **The sliding settings panel on the right** from Couchy: 440 dp, a 260 ms slide. Simplified to 8 items (10.1).
- **Weather** from Open-Meteo: no key, no registration, no device geolocation.
- **Input switching** (HDMI and others) as a launcher feature (section 7.7).

We do not take: themes, extensions, module cards, the dock as a separate block, Live TV, debloat, an app library,
17 languages, a clock screensaver, RTL, trust-all TLS. Apple and Amazon aerials are taken (decision D49, see section 3).

**Constraints:** the APK size is not limited. The only hard requirement is **speed** (budgets in section 12).

### 0.1. What changed in version 2, and why

The review of version 1 found contradictions and gaps. The decisions that are now recorded:

| # | Question | In v1 | In v2 | Basis |
|---|---|---|---|---|
| 1 | How the bar works | "pinned at the top" | The bar is **the first row of the grid** (`position < columns`), the same `RecyclerView`. At rest it sits **at the bottom edge of the screen**; the next row peeks up from below by 18 dp. Scrolling down expands the grid. There is no name above the bar (`dock_title` is off) | `MainActivity.java:706–709`, `config.xml:413`, `config.xml:417` |
| 2 | Tile size | 150 dp fixed, and "from the width and columns" | Only the formula: `tileW = min(180 dp, (cell − 12 dp) / 1.10)`, `cell = innerWidth / columns`. The only size setting is the column count (4–7); there is no separate scale | `AppAdapter.java:108–114` |
| 3 | Gaps | 10 dp (`GAP_SIZES[1]`) | No gap setting. Cell = `innerWidth / columns`, the tile is centred in its cell. Gap = `cell − tileW` (≈27 dp with 5 columns) | `AppAdapter.java:111`; Couchy stretches the gap to fill the row (`LauncherScreen.kt:998`) |
| 4 | Corner radius | "squircle `SmoothCornerShape`" | Views cannot draw squircles. The corner radius is 16 dp × `min(1, tileW/180)`, via `ViewOutlineProvider` | `AppAdapter.java:899–901`, `SmoothCornerShape.kt:10–17` |
| 5 | Poster at startup | The poster is the theme's `windowBackground` | `windowBackground` is a **static resource** (fixed at build time), so it does not work for a video the user chose. The poster is an `ImageView` above the `SurfaceView`. A cold start still shows the theme's frame; see 6.2 | Android: the theme is set at build time |
| 6 | Window | "transparent", and "the poster as the window background" | The window does not need to be transparent: the background is the poster or a gradient. See 6.2 | Couchy: `LauncherScreen.kt:247` (`setBackgroundDrawable`), comment at `:1501` |
| 7 | Library versions | Media3 1.4.1, RecyclerView 1.3.2, DynamicAnimation 1.0.0 | **Media3 1.11.1** (stable, 10.09.2026), **RecyclerView 1.4.0**, **DynamicAnimation 1.1.0**. We do not take 1.12.0-beta01: it is a beta and less than two weeks old | WebFetch (maven, release notes); `dl.google.com` metadata |
| 8 | Manifest | not described | A `<queries>` block is required (Android 11). Without it, `queryIntentActivities` does not see all apps. The `INTERNET` permission is needed for the weather; `QUERY_ALL_PACKAGES` is not needed | `cleanyourtv/.../AndroidManifest.xml:27–71` |
| 9 | Choosing a video | `ACTION_OPEN_DOCUMENT` | Keep `ACTION_OPEN_DOCUMENT`. On this TV `GET_CONTENT` shows the system chooser | ADB (section 18) |
| 10 | Settings button | only `MENU` / `⋮` | A visible round gear button at the top right. The TCL remote may not have a MENU key | `StatusBar.kt` (Couchy), `config.xml:313` (`top_menu`) |
| 11 | Icons | "Material Symbols, Apache-2.0" and "ic_* from CleanYourTV (MIT)" | UI icons are Material Icons (Apache-2.0), already in CleanYourTV. Weather icons are **our own VectorDrawables**, so there are no licence questions | `ICONS-LICENSE`, `LICENSE` in `google/material-design-icons` |
| 12 | The "Wallpaper" icon | `ic_picture` | `ic_picture.xml` is, by its shape, a TV icon (`M21,3H3...`). Use a separate icon for "Wallpaper" | `drawable/ic_picture.xml` |
| 13 | Font | not specified | **Outfit** (SIL OFL 1.1), the same as in CleanYourTV | `res/font/outfit*.xml`, `FONT-LICENSE-OFL.txt` |
| 14 | Header | time and date centred or on the right, buttons with labels | Clock and date **at the top left** (52 sp light, date 14 sp caps). At the top right: round icons **without labels**: inputs, network, settings | `activity_main.xml` in CleanYourTV: `paddingStart 56 dp`, `clock 52 sp`; `icon_only_buttons`, `icon_only_inputs` |
| 15 | Weather | in the top bar | **At the top left, under the clock and date**: icon, temperature, city. The bottom of the screen has only the bar | at the user's request (refined in the third round of corrections) |
| 16 | Shading | top only | Top only, like `ScrimTop` in Couchy: there is no weather at the bottom, so nothing needs shading there | `LauncherScreen.kt:118–121` |

---

## 1. Sources and licences

| Folder | What it is | Language / UI | Licence | What we take |
|---|---|---|---|---|
| `sources/cleanyourtv` | CleanYourTV, a native launcher (reference, not a dependency) | Java, Android Views, RecyclerView | MIT (code) | The grid, the dock bar, the menu, Docker, the banner formats, `tools/tv.py` |
| `sources/couchy-launcher` | **Couchy Launcher** (your "Kochi"; see below) | Kotlin, Compose for TV | **GPLv3** | The video wallpapers (as an idea and parameters), the settings panel (as an idea), the colour, size, and timing values |

**About the name.** `sources/` contains `couchy-launcher` (its README says "Couchy Launcher", `com.conreo.couchytv`). I assume
this is the launcher with video wallpapers that you had in mind.

**Licences and what to do with them:**

- **CleanYourTV (MIT):** the code may be taken if the notice is kept. The icons in `res/drawable/ic_*.xml` are
  Material Icons. Their licence is **Apache-2.0** (`LICENSE` in the `google/material-design-icons` repository, checked
  with WebFetch). A `NOTICE` file mentioning Apache-2.0 is needed.
- **Couchy (GPLv3):** the code may not be copied if the APK is distributed. For personal sideloading over ADB it is allowed,
  but releases are planned (D48). **Decided (D47): Couchy code is not copied; we write our own from the description in this document.** The document
  contains no copied fragment of Couchy, only values (colours, sizes, timings), and those are not protected by a licence.
- **Outfit (SIL OFL 1.1):** the font files go in `res/font/` plus `FONT-LICENSE-OFL.txt`, as in CleanYourTV
  (`launcher/app/src/main/res/FONT-LICENSE-OFL.txt`, "Copyright 2021 The Outfit Project Authors").
- **Open-Meteo (data under CC BY 4.0):** attribution in "About" (section 10).

---

### 1.1. The rule for using `sources/`

`sources/` is reference material only. The folder is attached as git submodules, and it exists so that we can read other people's code
and copy what we need from it. Nothing from it may be imported or used inside the app itself.

**Allowed:**

- reading the files and working out how they work;
- copying code, resources (drawables, layouts, fonts, strings), scripts, and configs into our repository;
- rewriting the copies, changing them, and using them as a starting point;
- keeping the licence notices and recording the origin in a comment in the file (for example, "based on CleanYourTV, MIT").

**Forbidden:**

- importing classes or packages from `sources/` (Java, Kotlin, Gradle modules);
- attaching `sources/` as a dependency, a module, or an included project in `settings.gradle`;
- referring to paths in `sources/` from code, the manifest, resources, scripts, `build.gradle`, the Dockerfile, or tests;
- running scripts from `sources/`, or reading its resources during the build or while the app runs;
- leaving a comment in our code that points to `sources/`: the origin is written as text, not as a path.

Once copied, a file belongs to us. It lives at its own path in our repository and does not depend on the `sources/` folder.

**Licences when copying:**

| Source | Licence | What to keep when copying |
|---|---|---|
| CleanYourTV | MIT | The text of the MIT licence and the author's notice in the file |
| Material Icons (inside CleanYourTV) | Apache-2.0 | A `NOTICE` file in `app/` mentioning Apache-2.0 |
| Outfit (inside CleanYourTV) | SIL OFL 1.1 | The `FONT-LICENSE-OFL.txt` file next to the font |
| Couchy Launcher | GPLv3 | **Code from Couchy is not copied.** We write our own from the description in this document. Then GPL does not apply to our project, even if the APK is published on the release page. If we did copy, a release with an APK would be a distribution, and the whole project would become GPLv3 (OPEN-QUESTIONS, Q10) |

**Releases.** An APK built and published on the release page is a distribution, even if the repository is private and carries no ads. A release
with an APK includes `LICENSE` (ours, and the MIT one from CleanYourTV) and `NOTICE` (Apache-2.0, OFL).

**Removal after implementation.** When the app is built, verified on the TV, and has passed the last milestone (M7), the folder is removed. The order:

1. Check that there are no references: `grep -rn "sources/" --include=*.java --include=*.xml --include=*.gradle --include=*.sh --include=Dockerfile .`. The result must be empty.
2. `git submodule deinit -f sources/cleanyourtv sources/couchy-launcher`
3. `git rm -f sources/cleanyourtv sources/couchy-launcher`
4. Remove the entries from `.gitmodules` and the directories `.git/modules/sources/`.
5. In the documents, replace the references to `sources/` with the text "based on CleanYourTV", or delete them.
6. Commit "Remove reference sources". Push only with permission.

Until the folder is removed, check 1 is run before every commit.

## 2. Why CleanYourTV is fast (what we take)

Facts from `sources/cleanyourtv`:

1. **Nothing extra.** `AGENTS.md:147–148`: "no INTERNET permission, no background services, no blur or video,
   1 GB of RAM". For us two of these change (INTERNET and video); the rest stays as a rule.
2. **One Activity, no Compose.** `MainActivity.java` is an ordinary `Activity` with a `RecyclerView`.
3. **Minimal dependencies.** Only `recyclerview` and `dynamicanimation` (`launcher/app/build.gradle:46–47`).
4. **R8 in release** (`build.gradle:23–25`): `minifyEnabled true`, `shrinkResources true`,
   `proguard-android-optimize.txt`. The file's comment says: "faster start on a slow TV CPU".
5. **Banners are prepared in advance** (section 5.1); `onBind` only sets a ready `Bitmap`.
6. **A grid without list animations:** `setHasFixedSize(true)`, `setItemAnimator(null)` (`MainActivity.java:677–678`).
7. **Its own `requestChildRectangleOnScreen`** (`MainActivity.java:639–676`): focus scrolls exactly far enough to bring the tile into the visible area above the bottom edge.
8. **The dock band is drawn by a decorator** (`DockDecoration.java:89`, `onDraw`), not by a separate View.
9. **The focus spring:** `SpringForce` with stiffness 260 and damping 0.58 (normal mode), or 320 / 0.76
   (`advanced_effects`), `MainActivity.java:817–818`.
10. **The Docker build** (section 11) and `tools/tv.py` for screenshots (section 12).

### 2.1. CleanYourTV constants that we take

From `AppAdapter.java`:

| Constant | Value | Line | Meaning |
|---|---|---|---|
| `DESIGN_W_DP` / `DESIGN_H_DP` | 180 / 101 dp | 102 | the tile size in the design, 16:9 |
| `FOCUS_SCALE` | 1.10 | 100 | the focus scale |
| `ITEM_EXTRA_DP` | 45 dp | 139 | the height around a tile: padding 8+8, gap 7, label 22 |
| `DOCK_EXTRA_DP` | 18 dp | 141 | the decorator's offsets above and below the bar (6+12) |
| `BOTTOM_ROOM_DP` | 30 dp | 143 | the space below the last row |
| `TOP_ROOM_DP` | 12 dp | 147 | the space above the first row (without the dock) |

From `MainActivity.java`: the grid's `COLUMNS = 4` by default (`config.xml:421`), but at 960 dp with 4 columns the tile is
180 dp. We default to 5 (section 7.2).

### 2.2. Code: the grid with a bar

Based on the meaning of the CleanYourTV code, simplified. One manager, one scroll function.

```java
// BarGridLayoutManager.java: the first row (positions 0..cols-1) is the bar.
final class BarGridLayoutManager extends GridLayoutManager {
    private final int cols;

    BarGridLayoutManager(Context ctx, int cols) {
        super(ctx, cols, RecyclerView.VERTICAL, false);
        this.cols = cols;
    }

    @Override
    public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                 @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
        int pos = parent.getChildAdapterPosition(child);
        int visTop = parent.getPaddingTop();                       // the bar's resting top: barTop, section 7.2
        int visBottom = parent.getHeight() - parent.getPaddingBottom();
        int top = child.getTop() + rect.top;
        int bottom = child.getTop() + rect.bottom;
        int dy;
        if (pos >= 0 && pos < cols) dy = child.getTop() - visTop;  // bar: returns to its resting line
        else if (top < visTop) dy = top - visTop;                  // a row moves up: catch up from above
        else if (bottom > visBottom) dy = bottom - visBottom;      // a row moves down: catch up from below
        else return false;                                         // already visible: do not move
        if (dy == 0) return false;
        if (immediate) parent.scrollBy(0, dy); else parent.smoothScrollBy(0, dy);
        return true;
    }
}
```

The band decorator (drawn before the children, so it sits under the tiles):

```java
// BarBand.java
final class BarBand extends RecyclerView.ItemDecoration {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int cols;
    private final float radius, padX, padY;

    BarBand(float density, int cols) {
        this.cols = cols;
        paint.setColor(0xD8141416);          // #141416 at 85 %: a value to verify on the TV
        radius = 26 * density;
        padX = 22 * density;                 // CleanYourTV: up to 22 dp on the sides
        padY = 14 * density;                 // and 14 dp above and below
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State s) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(v);
            if (pos < 0 || pos >= cols) continue;
            if (!any) { r.set(v.getLeft(), v.getTop(), v.getRight(), v.getBottom()); any = true; }
            else r.union(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        }
        if (!any) return;
        r.left -= padX; r.right += padX; r.top -= padY; r.bottom += padY;
        c.drawRoundRect(r, radius, radius, paint);
    }
}
```

---

## 3. Video wallpapers in Couchy: what is there and what we take

File to read (section 1.1): `sources/couchy-launcher/app/src/main/java/com/conreo/couchytv/ui/LauncherScreen.kt`, the function
`VideoWallpaper` (lines 1359–1520). The parameters we take (checked by reading):

| Parameter | Value in Couchy | Line | In our app |
|---|---|---|---|
| Player | `ExoPlayer.Builder` (Media3 1.4.1) | 1397–1416 | Media3 **1.11.1** |
| Buffer | `setBufferDurationsMs(10_000, 15_000, 2_500, 5_000)` | 1405 | the same |
| Time priority | `setPrioritizeTimeOverSizeThresholds(true)` | 1406 | the same |
| Decoder | `DefaultRenderersFactory.setEnableDecoderFallback(true)` | 1388 | the same |
| Sound | `volume = 0f` | 1412 | the same |
| Repeat | `REPEAT_MODE_ONE` | 1410 | the same |
| Scaling | `VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING` | 1414 | the same (cover) |
| Output | `SurfaceView`, `setVideoSurfaceView` | 1511 | the same |
| Lifecycle | build on `ON_START`, release on `ON_STOP` | 1421–1423 | the same |
| Watchdog | 2.5 s, only in `RESUMED` | 1479–1489 | **3 s**, only in `RESUMED` |
| Clip fade | 450 ms out, 1100 ms in | 1473, 1495 | a 900 ms poster fade (section 6.2) |
| Speed | `VIDEO_SPEEDS` 0.25–1× | 132 | **not implemented** |

**What we do NOT take:**

- `trustAllHttpClient` (TLS without certificate checks, line 1335): **never taken.** If standard TLS on the TCL does not work with the Apple servers, we disable the aerials from that server, and we do not disable certificate checks.
- The aerial list (`assets/aerials.json`, 289 entries: 139 Apple, 112 Amazon, 38 from the GitHub community) **is carried over as data** (D49). The videos are not downloaded or stored; they are streamed only when the video wallpaper is enabled. The community set (38 entries) is not included until decided (Q19).
- The grey gradient "cover" over the SurfaceView (line 1517): this is exactly what is seen as the "intermediate gradient" at startup. We replace it with the poster (6.2).
- The `LoadingScreen` with the "breathing" logo (lines 1524–1545): a black background plus a logo is the cause of the black frame at startup.

**Cost.** Media3 adds size to the APK. The size is not limited. Decoding 1080p H.264 on the TCL is the main risk for CPU and RAM; the mitigation is in section 6.

---

## 4. Weather: Open-Meteo

### 4.1. What has been checked

| What | Value | Source |
|---|---|---|
| Forecast endpoint | `https://api.open-meteo.com/v1/forecast` | `curl` |
| Geocoding endpoint | `https://geocoding-api.open-meteo.com/v1/search` | WebFetch `open-meteo.com/en/docs/geocoding-api` |
| Units | `temperature_unit=celsius` (default) or `fahrenheit`; `wind_speed_unit=kmh\|ms\|mph\|kn` | WebFetch `/en/docs`, checked with `curl` (`temperature_unit=fahrenheit` returned °F) |
| Free limit | fewer than 10,000 calls per day, 5,000 per hour, 600 per minute; 300,000 per month | WebFetch `open-meteo.com/en/terms` |
| Condition | non-commercial use only | WebFetch `/en/terms` |
| Data licence | CC BY 4.0 | WebFetch `/en/terms` |
| Key | not needed for non-commercial use; limits apply per IP and per application | WebFetch `/en/terms` |
| Parameter error | HTTP 400, body `{"error":true,"reason":"..."}` | `curl`, a wrong variable name |

Load: once every 30 minutes while Home is shown, that is about 48 calls per day, or 0.5% of the daily limit.

### 4.2. Current weather request

The minimum set of parameters: `latitude`, `longitude`, `current=temperature_2m,weather_code,is_day`,
`timezone=auto` (the time in the response becomes local), and `temperature_unit` only if °F is needed.

```text
https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,weather_code,is_day&timezone=auto
```

An example of a real response (`curl`, 2026-11-14 00:15, Berlin; the coordinates come from the example in the documentation, not from user data).
Save it as the fixture `src/test/resources/forecast_berlin.json`:

```json
{"latitude":52.512604,"longitude":13.419517,"generationtime_ms":0.050187110900878906,
 "utc_offset_seconds":3600,"timezone":"Europe/Berlin","timezone_abbreviation":"GMT+1","elevation":38.0,
 "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code","is_day":""},
 "current":{"time":"2026-11-14T00:15","interval":900,"temperature_2m":4.4,"weather_code":1,"is_day":0}}
```

What matters in this:

- The values in `current` are numbers, not strings: `temperature_2m` = `4.4` (rounded to whole degrees when shown).
- `interval` = 900 s: the data is updated every 15 minutes. Updating more often is pointless.
- `is_day` = 0 at night, 1 during the day.
- The response contains `current_units.temperature_2m` (`°C` or `°F`). Display that value rather than guessing it.

### 4.3. Geocoding (entering a city)

```text
https://geocoding-api.open-meteo.com/v1/search?name=Berlin&count=5&language=ru&format=json
```

Parameters (WebFetch): `name` is required (two characters give an exact match; three or more give a prefix match); `count` is 1–100,
default 10; `language` defaults to `en`; `format` is `json` or `protobuf`. An example response (`curl`, abridged):

```json
{"results":[{"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,
  "elevation":74.0,"feature_code":"PPLC","country_code":"DE","admin1_id":2950157,
  "timezone":"Europe/Berlin","population":3426354,"country":"Germany","admin1":"State of Berlin"}]}
```

**An empty result is not an empty array but a missing key:** `curl` for a place name that does not exist returned
`{"generationtime_ms":0.16}`. The parser must treat a missing `results` key as "nothing found".

Fields such as `country`, `admin1`, and others may be absent ("Empty fields are not returned", WebFetch). Do not crash.

### 4.4. WMO codes (corrected against the `/en/docs` page)

| Code | Description (Open-Meteo) | Icon (our VectorDrawable) |
|---|---|---|
| 0 | Clear sky | `wx_sun` (day) / `wx_moon` (night) |
| 1 | Mainly clear | `wx_sun_cloud` / `wx_moon_cloud` |
| 2 | Partly cloudy | `wx_sun_cloud` / `wx_moon_cloud` |
| 3 | Overcast | `wx_cloud` |
| 45, 48 | Fog / depositing rime fog | `wx_fog` |
| 51, 53, 55 | Drizzle light / moderate / dense | `wx_drizzle` |
| 56, 57 | Freezing drizzle light / dense | `wx_sleet` |
| 61, 63 | Rain slight / moderate | `wx_rain` |
| 65 | Rain heavy | `wx_rain_heavy` |
| 66, 67 | Freezing rain light / heavy | `wx_sleet` |
| 71, 73, 75, 77 | Snowfall slight / moderate / heavy, snow grains | `wx_snow` |
| 80, 81, 82 | Rain showers slight / moderate / **violent** | `wx_rain` (82: `wx_rain_heavy`) |
| 85, 86 | Snow showers slight / **heavy** | `wx_snow` |
| 95 | Thunderstorm | `wx_storm` |
| 96, 99 | Thunderstorm with slight / **heavy** hail | `wx_hail` |
| 97 | **Heavy** thunderstorm | `wx_storm` |

Corrections relative to v1: 82 is "Violent rain showers", 86 is "Heavy snow showers", 97 is "Heavy thunderstorm"
(v1 said "Severe"), and 96/99 are hail, which only models with an explicit hail forecast report (WebFetch, a note under the table).

Unknown code: the icon `wx_unknown` (a cloud with a question mark), and the text "—". Do not crash.

**The weather icons are our own VectorDrawables** (12 of them, drawn by hand, 24×24 dp), with no external library and no licence.
The simplified shapes for the mockup are in `docs/screen-mockup.html`. Why not Material: I could not verify the exact names of the weather symbols
in `google/material-design-icons` through the API, and our own set is 12 simple paths.

### 4.5. Code: WMO mapping and response parsing

```java
// WeatherCode.java
final class WeatherCode {
    static final int UNKNOWN = -1;

    /** Icon name (res/drawable/wx_*.xml). Night has its own branch. */
    static String icon(int code, boolean isDay) {
        switch (code) {
            case 0:  return isDay ? "wx_sun" : "wx_moon";
            case 1: case 2: return isDay ? "wx_sun_cloud" : "wx_moon_cloud";
            case 3:  return "wx_cloud";
            case 45: case 48: return "wx_fog";
            case 51: case 53: case 55: return "wx_drizzle";
            case 56: case 57: case 66: case 67: return "wx_sleet";
            case 61: case 63: case 80: case 81: return "wx_rain";
            case 65: case 82: return "wx_rain_heavy";
            case 71: case 73: case 75: case 77: case 85: case 86: return "wx_snow";
            case 95: case 97: return "wx_storm";
            case 96: case 99: return "wx_hail";
            default: return "wx_unknown";
        }
    }
}
```

```java
// WeatherParser.java: parses the JSON with no dependencies (org.json from the platform)
final class WeatherParser {
    static final class Now {
        final int tempC;        // the temperature rounded, in the units of the request
        final String unit;      // "°C" or "°F", taken from current_units
        final int code;
        final boolean isDay;
        final String time;      // "2026-11-14T00:15", local time
        Now(int t, String u, int c, boolean d, String tm) { tempC = t; unit = u; code = c; isDay = d; time = tm; }
    }

    /** null: the response was not recognised (do not crash; show the cache). */
    static Now parseForecast(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("error", false)) return null;
            JSONObject cur = root.getJSONObject("current");
            String unit = root.optJSONObject("current_units") == null ? "°C"
                    : root.getJSONObject("current_units").optString("temperature_2m", "°C");
            double t = cur.getDouble("temperature_2m");
            return new Now((int) Math.round(t), unit, cur.getInt("weather_code"),
                    cur.optInt("is_day", 1) == 1, cur.optString("time", ""));
        } catch (JSONException e) {
            return null;
        }
    }

    /** The list of places found; an empty list if there is no results key. */
    static List<Place> parseGeocode(String json) {
        List<Place> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("results");
            if (arr == null) return out;                 // "nothing found"
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Place(o.optString("name", ""), o.optString("admin1", ""),
                        o.optString("country", ""), o.getDouble("latitude"), o.getDouble("longitude")));
            }
        } catch (JSONException e) {
            // a response without the required fields: an empty list
        }
        return out;
    }

    static final class Place {
        final String name, admin1, country; final double lat, lon;
        Place(String n, String a, String c, double la, double lo) { name = n; admin1 = a; country = c; lat = la; lon = lo; }
    }
}
```

### 4.6. Code: network and cache

```java
// WeatherClient.java: one background thread; the result is posted to the main thread through a Handler
final class WeatherClient {
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    interface Callback { void onResult(WeatherParser.Now now); }

    void refresh(double lat, double lon, boolean fahrenheit, Callback cb) {
        String u = FORECAST + "?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day&timezone=auto"
                + (fahrenheit ? "&temperature_unit=fahrenheit" : "");
        io.execute(() -> {
            WeatherParser.Now now = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(5_000);     // 5 s to connect
                c.setReadTimeout(5_000);        // 5 s to read
                if (c.getResponseCode() == 200) {
                    now = WeatherParser.parseForecast(readAll(c.getInputStream()));
                }
            } catch (IOException e) {
                // no network: the cache stays, with no dialogs
            } finally {
                if (c != null) c.disconnect();
            }
            WeatherParser.Now result = now;
            main.post(() -> cb.onResult(result));
        });
    }

    private static String readAll(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) sb.append(line);
            return sb.toString();
        }
    }
}
```

The cache and the timer live in `LauncherState` (section 9): the fields `weather.last` (JSON of the last response) and `weather.fetchedAt`
(ms). The timer: `Handler.postDelayed(refresh, 30 * 60_000)`, removed in `onStop`. When Home is shown and the cache is older than
30 minutes, refresh at once.

### 4.7. Specification (what we show)

1. **Location.** Enter a city → geocoding → choose from the list → save `name`, `admin1`, `lat`, `lon`. We do not use the device's
   location: it would need a separate permission and it is unnecessary on a TV.
2. **Network.** `HttpURLConnection` and `org.json` from the platform. **Zero new dependencies.** Timeouts 5 s / 5 s.
3. **Frequency.** When Home is shown and the cache is older than 30 minutes; plus a timer every 30 minutes while Home is visible.
4. **Cache.** The last successful response is stored in `launcher_state.json`. With no network we show the cache with "updated 14:20".
5. **On screen (top left, under the clock).** A 22 dp icon, the temperature as a whole number, and the place name in a small font (13 sp).
   If the cache is older than 3 hours, a status dot from `status_dot.xml` (it exists in CleanYourTV) in colour `#FFB74D`.
6. **Errors.** Any exception goes to the log (`Log.w`), and we show the cache. No dialogs.
7. **Attribution.** The line "Weather data: Open-Meteo.com (CC BY 4.0)" in "About" (section 10).

---

## 5. Icons and banners

### 5.1. App icons

**Source and order** (the same as CleanYourTV, `AppAdapter.java:266–311`, and Couchy, `AppRepository.kt:83–156`):

1. The banner from the app's resources: `ActivityInfo.banner`, otherwise `ApplicationInfo.banner`. It is loaded through
   `pm.getResourcesForApplication(ai).getDrawableForDensity(id, dpi)`, where `dpi = max(DENSITY_XHIGH, densityDpi)`
   (`AppAdapter.java:375`), so the artwork is not reduced before the focus scale is applied.
2. If there is none, `ActivityInfo.loadBanner(pm)`.
3. **A square "banner" is not a banner.** A banner is accepted only if `w ≥ 1.4 × h` or the size is unknown
   (`AppAdapter.java:381–388`, `isBannerShaped`). Otherwise it is an icon.
4. If there is no banner, `ResolveInfo.loadIcon(pm)` (square, 128 px, as in Couchy `AppRepository.kt:146`).
5. If there is no icon either, a tile in a colour taken from the package hash (one of 9 colours from Couchy `Theme.kt:tileColor`) with a 48 dp icon.

```java
// AppArt.java: preparing a banner
static final class Art { final Bitmap bitmap; final boolean isBanner; /* ... */ }

static Bitmap bannerBitmap(Context ctx, PackageManager pm, ResolveInfo ri, int bannerW) {
    ApplicationInfo ai = ri.activityInfo.applicationInfo;
    int id = ri.activityInfo.banner != 0 ? ri.activityInfo.banner : ai.banner;
    Drawable d = null;
    if (id != 0) {
        try {
            int dpi = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
            d = pm.getResourcesForApplication(ai).getDrawableForDensity(id, dpi, null);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) { }
    }
    if (d == null) d = ri.activityInfo.loadBanner(pm);
    if (d == null) return null;
    int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
    if (w > 0 && h > 0 && w < h * 1.4f) return null;         // a square: an icon, not a banner
    Bitmap b = Bitmap.createBitmap(bannerW, bannerW * 9 / 16, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    d.setBounds(0, 0, b.getWidth(), b.getHeight());
    d.draw(c);
    return b;
}
```

### 5.2. Banner size and cache

- **Size.** `bannerW = clamp(180 dp × 1.10 × density, 320, 720)`. 180 dp is the largest tile in the design, and
  1.10 is the focus scale. On the TV (density 2) this is 396 px. The current tile of 147 dp = 294 px, so the banner is larger than
  needed: it will not lose sharpness, and scaling to 1080p is done by the TV (section 18).
- **Cache file name:** `iconcache/<pkg>-<lastUpdateTime>-b<bannerW>.webp`. `lastUpdateTime` comes from
  `PackageInfo.lastUpdateTime` (as in Couchy `AppRepository.kt:120`). A change of version or density creates a new file.
- **Format.** `Bitmap.CompressFormat.WEBP_LOSSY` on API 30 and later, `WEBP` (quality 90) on API 26–29.
- **Cleanup.** On every scan, delete the files whose names are not in the list of valid names (as Couchy `validCacheNames`).
- **Concurrency.** A thread pool of `max(2, cores − 1)` (Couchy `AppRepository.kt:79–80`). The reason, from Couchy's comment:
  on a weak TV a full pool takes all the CPU and drops frames.
- **Decoding.** `prepareToDraw()` on the `Bitmap` after it is created (Couchy `AppRepository.kt:155`), so that
  the texture upload does not happen during a frame.

### 5.3. Fallback for a tile with no icon and no banner

The colours from Couchy `Theme.kt` (`tileColor`): `#37474F, #4E342E, #1B5E20, #0D47A1, #4A148C, #880E4F, #3E2723,
#263238, #33691E`. The choice: `palette[abs(pkg.hashCode()) % 9]`. On top, a "dust" layer `#1AFFFFFF` at the top and `#24000000`
at the bottom (`AppCard.kt`: 10% and 14%). A 48 dp icon in the centre, a 13 sp label at the bottom.

### 5.4. Interface icons

Taken from CleanYourTV (`launcher/app/src/main/res/drawable/`, Material Icons, Apache-2.0), 22 dp, colour `#FFFFFF`:

| File | What it looks like | Where it appears in our UI |
|---|---|---|
| `ic_wifi.xml` | Wi-Fi | a round icon at the top right (state: `ACCESS_NETWORK_STATE`, no SSID) |
| `ic_settings.xml` | gear | the round "Settings" icon at the top right, and the "Appearance" item |
| `ic_apps.xml` | 3×3 grid | the "Apps" item |
| `ic_swap_vert.xml` | two arrows | the "Move" mode |
| `ic_lock.xml` | padlock | "Hide" (closed) |
| `ic_lock_open.xml` | open padlock | "Show" |
| `ic_input.xml` | by its shape, like a remote | **check on the TV**; a different icon may suit "Inputs" better (OPEN-QUESTIONS Q4) |
| `ic_av.xml` | connectors | "More" (AV inputs) |
| `ic_picture.xml` | **a TV icon**, not a picture | do not use for "Wallpaper"; a different icon is needed |

The "Wallpaper", "Weather", "Clock", "About", and "Appearance" items need new short vectors. They come from the same
Material Icons set, exported as `VectorDrawable` through Android Studio → New → Vector Asset. The list of names and
the check against the catalogue happen in milestone M6.

### 5.5. Font

**Outfit** (`res/font/outfit.ttf`, `outfit_light.xml`, `outfit_regular.xml`, `outfit_medium.xml`,
`outfit_extralight.xml`, SIL OFL 1.1; `values/fonts.xml:5–9`). The licence file is `FONT-LICENSE-OFL.txt`.
Use:

| Where | Font | Size | Colour |
|---|---|---|---|
| Tile label (focused) | medium | 14 sp | `#F2FFFFFF` (`item_app.xml`) |
| Tile label (not focused) | medium | 14 sp | `#8CFFFFFF` (dimmed) |
| Label on a tile without a banner | regular | 13 sp | `#FFFFFF` |
| Weather: temperature | medium | 18 sp | `#F2FFFFFF` |
| Header: clock | light | 52 sp | `#FFFFFFFF` |
| Weather: city | light | 13 sp | `#99FFFFFF` |
| Header: date | regular, caps | 14 sp | `#A6FFFFFF` |
| Panel heading | medium | 22 sp | `#F2FFFFFF` |
| Panel row | regular | 17 sp | `#F2FFFFFF`; secondary text 14 sp, `#8CFFFFFF` |

---

### 5.6. Memory: what to do when the system asks for memory back

Android watches memory and sends the app a signal, `onTrimMemory(level)`: "memory is low, free what you can".
The levels: `RUNNING_LOW` and `RUNNING_CRITICAL` mean the app is on screen; `UI_HIDDEN` and above mean the app has gone to the background.

Our TV has little memory: of 2 GB, about 480 MB is free (section 18). So:

- **The launcher in the background** (Home not on screen): release all banner bitmaps from memory. They stay in the WebP cache on disk,
  and are loaded again when the launcher returns.
- **The launcher on screen, `RUNNING_CRITICAL`:** release only the spare bitmaps (icons of tiles without a banner, and extra pictures).
  Banners of visible tiles are kept, so the grid does not flicker.
- **The video player** is released on `ON_STOP` (section 6.1), and the signal does not affect it.

This is what CleanYourTV does in `MemoryTrimPolicy.java`: it releases the pictures when Home is not on screen.
Check: measure PSS before and after going to the background (section 12.3).

## 6. Video wallpapers: how to make them fast

### 6.1. Requirements

1. **Source.** A user file through SAF (`ACTION_OPEN_DOCUMENT`, `video/*`), with a persistent read permission and streaming
   from a content URI. No copy is needed.
2. **Clip format (prepared on a PC before upload):** MP4 or MOV, **H.264 (AVC)**, 1920×1080, 25–30 fps, 6–10 Mbps,
   **no sound**, 20–60 s so that it loops. Reduce 4K to 1080p beforehand.

   ```bash
   ffmpeg -i in.mov -an -vf "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080" \
          -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart out.mp4
   ```

   Why H.264: the `OMX.realtek.video.decoder` on this TV is a hardware decoder and handles AVC up to High 5.1 (ADB, section 18).
   The decoder also supports HEVC and AV1, but H.264 is the safe choice. `-pix_fmt yuv420p` is needed so that
   the hardware decoder does not reject another pixel format. **Verify on the TV** with a long run (M4).
3. **Check on selection.** `MediaExtractor` or `MediaMetadataRetriever`: if the resolution is above 1920×1080 or
   the codec is not AVC/HEVC, show the warning "1080p H.264 is better for TV". Do not block the selection.

**Choosing a file on this TV (ADB, read-only):**

- `ACTION_OPEN_DOCUMENT` with `video/mp4` resolves to `com.alphainventor.filemanager/.activity.PickerActivity`,
  `isDefault=true`. So there is a system file picker, and it is the Fluffy file manager.
- `ACTION_GET_CONTENT` with `video/mp4` gives `ResolverActivity` (the app chooser). Do not use it.
- `/sdcard` contains `Movies`, `Documents`, and `Download`. Fallback path: put the file in `Movies` and pick it through Fluffy.

```java
// Choosing a video: OPEN_DOCUMENT only, and the permission is kept at once
Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("video/*");
startActivityForResult(i, REQ_VIDEO);

@Override
protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req != REQ_VIDEO || res != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    state.setWallpaperUri(uri.toString());   // section 9
}
```

**The player** (class `VideoWallpaper`, works with the `SurfaceView` and the poster from 6.2):

```java
// VideoWallpaper.java
final class VideoWallpaper {
    private static final long WATCHDOG_MS = 3_000;

    private final Context ctx;
    private final SurfaceView surface;
    private final ImageView poster;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private boolean resumed, rendered;

    private final Runnable watchdog = () -> {
        if (resumed && !rendered) fallback("no first frame in " + WATCHDOG_MS + " ms");
    };

    VideoWallpaper(Context ctx, SurfaceView surface, ImageView poster) {
        this.ctx = ctx; this.surface = surface; this.poster = poster;
    }

    void onResume() {
        resumed = true;
        // the watchdog may have fired while not resumed and done nothing: arm it again
        if (player != null && !rendered) { main.removeCallbacks(watchdog); main.postDelayed(watchdog, WATCHDOG_MS); }
    }
    void onPause()  { resumed = false; }

    /** ON_START: build the player and prepare the first frame; no sound. */
    void start(Uri uri) {
        if (uri == null || player != null) return;
        rendered = false;
        player = new ExoPlayer.Builder(ctx)
                .setRenderersFactory(new DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 15_000, 2_500, 5_000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build())
                .build();
        player.setVolume(0f);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                rendered = true;
                main.removeCallbacks(watchdog);
                hidePoster();
            }
            @Override public void onPlayerError(PlaybackException e) {
                fallback(e.getErrorCodeName());
            }
        });
        player.setVideoSurfaceView(surface);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.setPlayWhenReady(true);
        main.postDelayed(watchdog, WATCHDOG_MS);
    }

    /** ON_STOP: release the decoder; the poster is opaque again. */
    void stop() {
        main.removeCallbacks(watchdog);
        if (player != null) { player.release(); player = null; }
        poster.animate().cancel();
        poster.setAlpha(1f);
        poster.setVisibility(View.VISIBLE);
    }

    private void hidePoster() {
        poster.animate().alpha(0f).setDuration(900)
                .setInterpolator(new AccelerateDecelerateInterpolator())   // "ease-in-out"
                .withEndAction(() -> poster.setVisibility(View.GONE))
                .start();
    }

    private void fallback(String reason) {
        Log.w("Launcher", "wallpaper fallback: " + reason);
        main.removeCallbacks(watchdog);
        if (player != null) { player.release(); player = null; }
        poster.setAlpha(1f);
        poster.setVisibility(View.VISIBLE);       // the poster (or the gradient) stays
    }
}
```

What matters here:

- `ExoPlayer.Builder` and `player.*` are called only from the main thread (a Media3 requirement).
- `setPlayWhenReady(true)` is set at once. The poster already covers the `SurfaceView`, so while decoding runs the poster is visible, not a black frame.
- The watchdog fires only in `RESUMED` (as in Couchy, the watchdog block in `VideoWallpaper`, `LauncherScreen.kt:1479`): while the window is not visible,
  the `SurfaceView` has no surface, and no frames will arrive.
- `onRenderedFirstFrame()` with no arguments is the Media3 API that Couchy already uses (line 1454).

**Fallback to a picture.** The first failure (a player error, or no first frame within the watchdog time) runs `fallback`
at once: the user gets the poster or a picture from `wallpaper.image` (JPG/WebP, `BitmapFactory` with `inSampleSize`, with no
extra dependencies). There is no retry loop: the next attempt is the next `onStart`.

### 6.2. Smooth start: no black screen and no intermediate gradient

**As built (2026-11-14, D92–D94).** The plan below expected the gradient as the window background and a fade at the first
frame. On the TV both looked wrong, so: only the Gradient type ever shows the gradient; the window background is black; a
video or aerial comes up from black (a file video from its poster frame) and fades in over 1.2 s, but only once it is
playing plus 1 s, because the TV blanks its video window and switches its picture engine on for 0.6–0.9 s after the
first frame. Measured on Home: first frame at +0.8 s, TV settled at +1.7 s, fade from +2.5 s to +3.7 s.

The problem in Couchy: the window is first black (or the theme), then a grey gradient "cover", then the video. The cause is
`LoadingScreen` (`LauncherScreen.kt:1524–1545`) and `coverBrush` (`:1517`).

**What can be done and what cannot (important):**

1. **Cold start** (the TV is switched on, and the system starts Home). The system draws the **theme background** (`android:windowBackground`)
   until the first frame of our Activity. That background is a resource, fixed at build time. The video chosen by the user
   does not change it. Therefore:
   - the theme's `windowBackground` is the **default poster** (`res/drawable/window_bg.xml` → `poster_default.webp`,
     1920×1080, in the Aurora colours from Couchy's `Theme.kt`). For the built-in gradient and for the default poster,
     the system's first frame matches ours;
   - for the **video chosen by the user**, a black frame will remain until the Activity's first frame. Its length
     depends on the TV and is **measured** with `screenrecord` (criterion below). If the black frame is longer than 300 ms, reconsider.
2. **Returning to Home** (from another app, when the Activity is still alive). The `SurfaceView` loses its surface on
   `onStop`. Here the full-screen poster holds the picture until `onRenderedFirstFrame` arrives again.
   There is no black frame.

**Hierarchy (`activity_main.xml`, from bottom to top):**

```text
FrameLayout
 ├─ SurfaceView  (video, at the bottom)                  ← setVideoSurfaceView
 ├─ ImageView    (poster, opaque, above the video)       ← hidePoster() after 900 ms
 ├─ View         (scrim: 60 % → 0 by 20 % of the height) ← LauncherScreen.kt:118–121
 ├─ RecyclerView (the grid, bar = row 1)
 ├─ FrameLayout  (glass_host: the focus ring, 7.4)
 ├─ LinearLayout (header: clock and date on the left, icons on the right; weather under the clock)
 └─ FrameLayout  (panels: settings, inputs; hidden)
```

**Timings** (from Couchy, corrected):

| What | Value | Source |
|---|---|---|
| The poster fades after the first frame | 900 ms, ease-in-out | v1; Couchy: 1100 ms in |
| Icons and labels appear after the poster | after 200 ms, fade plus a 300 ms slide | v1 |
| Tiles | at the first frame, from the saved `order` and the banner cache on disk; with no cache, coloured fallback tiles (5.3) | review of v2 |
| App scan | starts in `onStart` on the background pool, **in parallel** with the video; never waits for `onRenderedFirstFrame` (with a picture or gradient wallpaper, or a failed clip, there is no first frame) | review of v2 |
| Boot logo | **none** | Couchy `LauncherScreen.kt:900–909` and `:1524–1545` |

**Check (required on the TV, not by eye):**

```bash
TV_HOST=<TV_IP> tools/tv.py --screenshot shots/01.png     # screenshot, no recording
adb -s <TV_IP>:5555 shell screenrecord --time-limit 6 /sdcard/start.mp4
adb -s <TV_IP>:5555 pull /sdcard/start.mp4 shots/start.mp4
```

Then frame by frame (`ffmpeg -i shots/start.mp4 -vf fps=10 shots/f_%03d.png`): between the window's first frame and the video,
there must be no frame in which the average brightness of the centre is more than 5% below the brightness of the poster. For a scripted
check use `ffprobe` and `signalstats` (YAVG). The criterion and the tool are recorded in `docs/PLAN.md` after M4.

**Not done:** Live Wallpaper (a separate process with its own lifecycle), `TextureView` (draws on the UI thread and stutters under load), and
streaming from arbitrary URLs. The only network video is the fixed aerial list (6.3).

### 6.3. Aerials: streamed Apple and Amazon clips (D49)

**As built (2026-11-14, D85, D93, D94).** Apple links play after trusting Apple Root CA for `sylvan.apple.com` only. The
rolling playlist no longer cuts between clips: 1 s before a clip ends the picture fades to black, and the next clip fades
in by the rule of 6.2.

**Data.** `res/raw/aerials.json`, copied as data from Couchy `assets/aerials.json` (a list of links, not code; WALLPAPERS,
group D). Each entry is `{"t": title, "u": https link, "s": set}`. We keep sets `A` (Apple, 139) and `Z` (Amazon, 112); the
community sets `C1`/`C2` are not copied until Q19 is decided. Drop the one Apple `4K` entry while copying: the TV renders 1080p.

**Format (checked by reading the file).** All links are progressive files over HTTPS (`.mov`, `.mp4`, `.m4v`), no HLS. Apple
entries are `SDR_2K_AVC`, Amazon entries are under `/content/FHD/`. So `media3-exoplayer` alone plays them; no
`media3-exoplayer-hls`, no OkHttp (the default `DefaultHttpDataSource` uses `HttpURLConnection`).

**Playback.** The same `VideoWallpaper` (6.1) with an `https` URI, with two differences:

- `REPEAT_MODE_OFF`; on `STATE_ENDED` the next random entry is played (never the same one twice in a row);
- the watchdog is **8 s** instead of 3 s: a network start is slower. Verify on the TV at M4.

**Network and TLS.** Platform TLS only: trust-all is never copied (section 3). Couchy's comment says `sylvan.apple.com` serves a
chain that many TV trust stores reject; if that happens on this TV, the Apple entries are skipped and Amazon entries stay.
With no network (`ConnectivityManager`), the aerials type shows the poster and makes no requests. Streams run only while Home is
visible and the "Aerials" wallpaper type is chosen; the player is released on `ON_STOP` as usual.

**Attribution** in "About": the clips belong to Apple and Amazon and are streamed from their servers (RISKS 45).

---

## 7. The home screen

### 7.1. Layout in dp

The TV screen: 960×540 dp (1920×1080 px at density 320; section 18). The full interactive mockup:
[docs/screen-mockup.html](screen-mockup.html).

At rest, what is shown below is what is visible. The bar sits at the bottom edge, and the next row peeks up from below as a 18 dp strip,
as in the original CleanYourTV (`rows_below_dock` = 0, `MainActivity.java:706–709`). The rest of the grid is not hidden
behind a footer: it lies below the screen and opens by scrolling.

```text
┌──────────────────────────────────────────────────────────────────────────────┐ 0
│  19:03                                                    (⇄)  (wifi)  (⚙)   │  clock on the left (52 sp),
│  │  13 NOVEMBER                                                              │  icons on the right, centred
│  │  FRIDAY                                                                   │  in the header; round, 40 dp
│  ◐ 4°  City                                                                  │  weather under the clock
│                                                                              │
│                                                                              │
│  ╭────────╮ ╭────────╮ ╭────────╮ ╭────────╮ ╭────────╮                      │  386.5: top of the bar = row 1
│  │ 147×83 │ │        │ │        │ │        │ │        │                      │
│  ╰────────╯ ╰────────╯ ╰────────╯ ╰────────╯ ╰────────╯                      │  494: bottom of the bar's row
│  ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔  │  522–540: the next row
└──────────────────────────────────────────────────────────────────────────────┘ 540
 ← 44 dp →                                                          ← 44 dp →
```

**Scrolling down** (Down): the grid expands, the bar moves up, and the next rows come in from below the bottom edge.
**Scrolling up** puts the bar back in place. At rest, there is nothing above the bar except the header and the video wallpaper background.

### 7.2. Decisions and formulas

**Columns.** The default is **5** (at 960 dp). The range is 4–7. The formula:

```text
inner      = screenWidthDp − 2 × 44                 // 872 dp at 960
cell       = inner / columns                         // cell width
tileW      = clamp((cell − 12) / 1.10, 80, 180)      // 12 dp is the room for focus and the ring, 1.10 is the scale
tileH      = tileW × 101 / 180                       // 16:9, rounded
itemH      = tileH + 45                              // 45 dp: padding, gap, label (ITEM_EXTRA_DP)

rowsBelow  = ui.rowsBelow (0–3, default 0)          // `rows_below_dock` in CleanYourTV
barTop     = screenHeightDp − (1 + rowsBelow) × itemH − 26
           // 8 dp tile padding + 18 dp peek of the next row: 386.5 dp with 5 columns and rowsBelow = 0
// RecyclerView: paddingTop = barTop, paddingBottom = 0
```

| Columns | Cell | Tile W × H | Row (itemH) | Rows down to the bottom edge (≈ 540 / itemH) |
|---|---|---|---|---|
| 4 | 218 dp | 180 × 101 dp | 146 dp | 3.7 |
| **5 (default)** | 174.4 dp | **147 × 83 dp** | **127.5 dp** | **4.2** |
| 6 | 145.3 dp | 121 × 68 dp | 113 dp | 4.8 |
| 7 | 124.6 dp | 102 × 57 dp | 102 dp | 5.3 |

**The size setting.** The column count (4–7, "Appearance") is the only size setting: fewer columns means bigger tiles.
There is no separate scale and no "icon size" setting (like Couchy's `ICON_SIZES` steps): with a fixed column count a scale
would only change the gaps, so the two settings would fight.

**Gaps.** There is no setting. The tile is centred in its cell, and the gap = `cell − tileW` (≈27 dp with 5 columns).
This is the same as CleanYourTV, `AppAdapter.java:111`. Couchy stretches the gap to fill the row
(`LauncherScreen.kt:998`); we get the same result through the cell.

**The bar.** The first row = positions `0..columns−1`. The `BarBand` decorator draws the band. The bar is **not pinned** as a separate
View: it sits at the bottom edge (`paddingTop` = `barTop`) and moves up with the rows when scrolling. This is how the
dock works in CleanYourTV: "push the dock row towards the bottom of the screen… the rest of the grid stays below until you
press Down" (`MainActivity.java:706–709`). One `RecyclerView` and one focus mechanism; a pinned bar was rejected, because
two lists would mean two focus mechanisms and two scrolls. There is no name above the bar (`dock_title` is off): the label is shown only under the tile in focus.

**When there are few apps.** If `count ≤ columns`, it is a single row: the bar is the whole screen. There is no separate mode.

**Margins and padding:** 44 dp on each side (Couchy `GridSection`, `LauncherScreen.kt:991`); the bottom of the grid is the bottom of the screen; the top of the grid = `barTop` (386.5 dp with 5 columns). At rest, only the header and the video wallpaper background are above the bar.

### 7.3. Code: layout and adapter

```xml
<!-- res/layout/item_app.xml: one cell of the grid -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingTop="8dp" android:paddingBottom="8dp"
    android:clipChildren="false" android:clipToPadding="false">

    <FrameLayout
        android:id="@+id/card"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal|top"
        android:background="@drawable/tile_bg"
        android:focusable="true" android:clickable="true">
        <ImageView android:id="@+id/banner"
            android:layout_width="match_parent" android:layout_height="match_parent"
            android:scaleType="centerCrop" />
        <ImageView android:id="@+id/icon"
            android:layout_width="48dp" android:layout_height="48dp"
            android:layout_gravity="center" android:layout_marginBottom="14dp"
            android:visibility="gone" />
    </FrameLayout>

    <!-- marginTop is set in code: 8 + tileH + 7 dp. CleanYourTV hard-codes 108 dp for its fixed 101 dp tile;
         with a computed tile that value is wrong. -->
    <TextView android:id="@+id/name"
        android:layout_width="match_parent" android:layout_height="22dp"
        android:layout_gravity="center_horizontal|top"
        android:gravity="center" android:singleLine="true" android:ellipsize="end"
        android:includeFontPadding="false"
        android:alpha="0"
        android:textColor="#F2FFFFFF" android:textSize="14sp"
        android:fontFamily="@font/outfit_medium" />
</FrameLayout>
```

The size of `card` and the label position are set **once, in `onCreateViewHolder`**, using the formula in 7.2:
`card` gets `tileW` × `tileH`, and the label gets `topMargin = 8 dp + tileH + 7 dp` (the label then ends exactly at
`itemH − 8 dp`, matching `ITEM_EXTRA_DP` = 45). The cell (a `FrameLayout` with `layout_width="match_parent"`) stretches
to `cell`, and `card` is centred in it. When the column count changes, the adapter is recreated.

```java
// AppAdapter.java: a fragment. Sizes, outline and listeners are set once per holder; onBind only sets content.
@NonNull @Override
public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
    Holder h = new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false));
    ViewGroup.LayoutParams lp = h.card.getLayoutParams();
    lp.width = tileW; lp.height = tileH;                       // 7.2, computed once per column count
    ((ViewGroup.MarginLayoutParams) h.name.getLayoutParams()).topMargin = labelTop;   // 8 dp + tileH + 7 dp
    h.card.setOutlineProvider(roundOutline(cornerPx));
    h.card.setClipToOutline(true);
    h.card.setOnClickListener(v -> AppLauncher.launch(v.getContext(), items.get(h.getBindingAdapterPosition()).pkg));
    h.card.setOnLongClickListener(v -> {
        int p = h.getBindingAdapterPosition();
        listener.onMenu(items.get(p).pkg, p);
        return true;
    });
    h.card.setOnFocusChangeListener((v, has) -> focused(v, has, h.name));
    return h;
}

@Override
public void onBindViewHolder(@NonNull Holder h, int pos) {
    App a = items.get(pos);
    if (a.banner != null) { h.banner.setImageBitmap(a.banner); h.banner.setVisibility(View.VISIBLE); }
    else { h.banner.setVisibility(View.GONE); /* a tile with an icon, see 5.3 */ }

    h.name.setText(a.label);
}

private static ViewOutlineProvider roundOutline(float r) {
    return new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
    };
}

/** Focus: scale 1.10 over 180 ms, and the label fades in. The spring belongs to the ring (2.3). */
private void focused(View card, boolean has, TextView name) {
    float s = has ? 1.10f : 1f;
    card.animate().scaleX(s).scaleY(s).setDuration(180).start();
    name.animate().alpha(has ? 1f : 0f).setDuration(150).start();
    if (has) glass.follow(card);
}
```

The corner radius: `cornerPx = 16 dp × min(1, tileW / (180 dp))` (CleanYourTV `AppAdapter.java:899–901`).
At 147 dp this is 13.1 dp.

### 7.4. Code: focus ring (spring)

One "glass" View for the whole screen, not one per tile. **Where it lives:** in `glass_host`, which sits **above** the
`RecyclerView` (section 11.4). It cannot go under the grid: `BarBand` is drawn by the `RecyclerView` at 85 % opacity and
would cover the ring on the bar row. Above the grid, the ring is a stroke with a transparent middle, so it frames the
tile without hiding it.

```java
// FocusGlass.java: a 2 dp ring and a 6 dp glow, moved by a spring
final class FocusGlass {
    private final View view;
    private final SpringAnimation sx, sy;
    private final float pad;      // 6 dp: the glow around the tile

    FocusGlass(FrameLayout host, float density) {      // host = glass_host, above the grid
        pad = 6 * density;
        view = new View(host.getContext());
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(16 * density);
        g.setStroke((int) (2 * density), 0xFF8AB4F8);     // Couchy's default accent
        view.setBackground(g);
        view.setElevation(0);
        host.addView(view, new FrameLayout.LayoutParams(0, 0));
        sx = new SpringAnimation(view, DynamicAnimation.TRANSLATION_X)
                .setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        sy = new SpringAnimation(view, DynamicAnimation.TRANSLATION_Y)
                .setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
    }

    void follow(View card) {
        // glass_host and the grid are siblings: convert through the common parent, then into the host
        ViewGroup host = (ViewGroup) view.getParent();
        ViewGroup common = (ViewGroup) host.getParent();
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        common.offsetDescendantRectToMyCoords(card, r);
        r.offset(-host.getLeft(), -host.getTop());
        float s = 1.10f;
        float w = card.getWidth() * s + 2 * pad, h = card.getHeight() * s + 2 * pad;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = (int) w; lp.height = (int) h;
        view.setLayoutParams(lp);
        sx.animateToFinalPosition(r.exactCenterX() - w / 2f);
        sy.animateToFinalPosition(r.exactCenterY() - h / 2f);
    }
}
```

Stiffness 260 and damping 0.58 are CleanYourTV values (`MainActivity.java:817–818`). There is no animation switch in the MVP.

While scrolling, the ring must be recalculated: `RecyclerView.addOnScrollListener` calls `glass.follow(focusedCard)`.

### 7.5. Visuals (summary)

| Element | Value | Source |
|---|---|---|
| Tile | 147 × 83 dp (5 columns), radius 13 dp (16 × 147/180) | 7.2 |
| Tile background | `#1C1C1E`, radius 16 dp | CleanYourTV `drawable/tile_bg.xml` |
| Banner | `centerCrop`, 16:9 | `item_app.xml` |
| Focus | scale 1.10, ring 2 dp `#8AB4F8`, glow 6 dp | Couchy `AppCard.kt`, CleanYourTV `pad` |
| Focus shadow | `elevation 8 dp`, black 50 % (as in Couchy); **not** on every tile, only on the ring | Couchy `AppCard.kt` (`focusedGlow`) |
| Hidden tile | mask `#99000000` (60 %) | Couchy `AppCard.kt` (`isHidden`) |
| Label | 14 sp, Outfit medium, one line, `ellipsize=end`, shown only for the focused tile | `item_app.xml`, `always_show_names=false` |
| Weather | top left, under the clock and date; temperature 18 sp medium, city 13 sp light | our own decision |
| Bar band | `#141416` at 85 %, radius 26 dp, 22 dp beyond the tiles at the sides and 14 dp above and below (verify the colour on the TV) | `BarBand`, 2.2 |
| Fade of rows under the bar | gradient 36–48 dp from the top | `TopFade`, CleanYourTV `MainActivity.java:693` |
| Scrim | top `#99000000` (60 %) → 0 at 20 % of the height | Couchy `LauncherScreen.kt:118–121` |

**Why the picture is sharp.** The text and the ring are vector graphics; the banner is resampled once, to 396 px (5.2). The rest is
done by the TV's scaling (section 18). No new tricks are needed.

**Why Couchy is slow (from the code, not measured).** Each focused card has a scale, an `elevation` shadow and a ring at the same time;
all rows use `DstIn` and `graphicsLayer`. This is Compose, and on a weak GPU everything is recalculated on every frame
(`LauncherScreen.kt:759–785`). Ours: one ring, one scale animation, the shadow is not on every tile, `setItemAnimator(null)`.

### 7.6. Navigation

- **Focus after returning from an app.** Focus returns to the last launched tile (as `LastOpened` in CleanYourTV,
  `docs/focus-3-back-from-app.png`). The package is stored in `launcher_state.json` (field `lastFocused`). If the tile no longer
  exists (removed or hidden), focus goes to the first visible tile.
- `←` `→` move focus along the row. At the end of a row, focus moves to the neighbouring row only if there is one.
  The default focus search of `RecyclerView` sometimes sends focus to the wrong place at the edges. If that happens, bring back
  the edge check from CleanYourTV `GridNavigation.java`:

  ```java
  static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
      boolean towardStart = left != rtl;
      if (towardStart) return position % columns == 0;
      return position % columns == columns - 1 || position == count - 1;
  }
  ```
- `↓` `↑` move focus along the column; the grid scrolls to follow the focus (`BarGridLayoutManager`, 2.2).
  From the bar, `↑` leads to the icons at the top right (inputs, network, settings): `setNextFocusUpId` on the bar's tiles; from the icons,
  `↓` returns to the bar (`nextFocusDown`, as in CleanYourTV `activity_main.xml`).
- `OK` (`DPAD_CENTER`, `ENTER`) launches the app. **Long OK** opens the menu (section 8). In Views this is
  `setOnLongClickListener` on `card`: a long press of OK arrives as a long click (`AppAdapter.java:513`).
- `MENU` or the gear: the settings panel (section 10).
- `BACK`: if a panel is open, close it; otherwise do nothing. We do not close Home.
- The `INPUT` button / `KEYCODE_TV_INPUT`: open the "Inputs" panel (if the system passes this key to the app;
  **check on the TV**. Without the accessibility service we do not do this).

### 7.7. Inputs: switching HDMI and other sources

**What was checked on the TV (ADB, read-only):**

- `dumpsys tv_input`: HDMI 1–3 are registered in `TvInputManager`, state `1` (CONNECTED_STANDBY). HDMI 4
  (`hdmi_port=4`) is not registered as an input. One passthrough input of type 3 (`<HW_ID>`, state 0).
- `cmd package resolve-activity --brief -a android.intent.action.VIEW -t vnd.android.cursor.item/channel`
  resolves to `com.tcl.tv/.TVActivity` (isDefault). **This is without a URI.** With the `passthrough` URI: not checked.
- CleanYourTV describes the same TCL quirk (`config.xml:377–383`): "some TVs, TCL among them, report an empty
  HDMI port as connected-standby". That is why there is a "connected only" filter.
- CleanYourTV: on the TCL G03 (our same model), the channel URI opened a chooser, because the Fluffy file manager
  also accepted the URI (`RemoteKeys.java:446–455`). The decision there: send it to the Live TV app with `setPackage`.

**List of inputs (code):**

```java
// Inputs.java
static final class Row {
    final String id, label; final boolean hdmi; final int state;   // state: see TvInputManager.INPUT_STATE_*
    Row(String id, String label, boolean hdmi, int state) { this.id = id; this.label = label; this.hdmi = hdmi; this.state = state; }
}

static List<Row> list(Context ctx, boolean connectedOnly) {
    List<Row> out = new ArrayList<>();
    TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
    if (tim == null) return out;
    for (TvInputInfo info : tim.getTvInputList()) {
        if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;   // hidden in Settings → Inputs
        boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
        int state = tim.getInputState(info.getId());
        // AV without hotplug is always under "More"; HDMI in CONNECTED_STANDBY too (TCL)
        if (connectedOnly && (!hdmi || state != TvInputManager.INPUT_STATE_CONNECTED)) continue;
        out.add(new Row(info.getId(), String.valueOf(info.loadLabel(ctx)), hdmi, state));
    }
    return out;
}
```

The constants `TvInputManager.INPUT_STATE_CONNECTED` = 0, `INPUT_STATE_CONNECTED_STANDBY` = 1,
`INPUT_STATE_DISCONNECTED` = 2 (from the Android SDK; **check** that they match `dumpsys` at M3).

**Switching (code):**

```java
static void switchTo(Context ctx, String inputId) {
    Uri channel = TvContract.buildChannelUriForPassthroughInput(inputId);
    Intent view = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(channel, "vnd.android.cursor.item/channel")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    // First the TV app (on this TV, com.tcl.tv); otherwise we select nothing ourselves
    try {
        ctx.startActivity(new Intent(view).setPackage("com.tcl.tv"));
        return;
    } catch (ActivityNotFoundException | SecurityException ignored) { }
    try {
        ctx.startActivity(view);                 // may show the app chooser
    } catch (ActivityNotFoundException | SecurityException e) {
        Toast.makeText(ctx, R.string.input_not_opened, Toast.LENGTH_SHORT).show();
    }
}
```

**Fallback:** if switching fails, show the message "Input did not open" and a button "System input selection", which sends
`new Intent("com.android.tv.action.VIEW_INPUTS")` to the system. We do **not** declare an activity for `VIEW_INPUTS` ourselves
(CleanYourTV's `InputsRequestActivity`): once enabled it would catch this very intent, and the fallback would open our own panel.

**"Inputs" panel:** on the right, 440 dp, with the same slide as the settings (section 10). Rows: HDMI 1–3 with `loadLabel()`,
the state as a dot (green: connected, grey: waiting), and a "More" row for AV. The "Connected only" option hides waiting HDMI inputs.

**"Inputs" button: a round icon with no label, at the top right** (`icon_only_inputs` in CleanYourTV). It is shown only if the list is
not empty (`MainActivity.java:3229–3245`: the button is hidden when there are no inputs).

**Check on the TV:** switch HDMI 1 → HDMI 2 with a device connected, take a screenshot (`tools/tv.py --screenshot`), and check that
the picture changed. Also check that an empty port does not appear when the "Connected only" option is on.

---

### 7.8. Remote buttons (implemented in M7)

The full table of buttons, codes and behaviour is recorded in [docs/remote-buttons.md](remote-buttons.md). The raw records are in
`docs/remote-captures/`. Implementation is the separate milestone M7 (section 15).

Decisions (from the user's answers):

- **Our own service in the launcher**, not the Home Button app. A "Remote buttons" screen in the launcher settings: button → app.
- Any button (except power) can be assigned to an app, with no parameters.
- Default: YouTube → SmartTube (`org.smarttube.stable`).
- An accessibility service is needed to catch buttons outside the launcher. It is enabled manually on the TV. This is our own
  background service and it runs all the time. That is deliberate.
- **`flar2.homebutton` (Home Button) is disabled during implementation**: otherwise two services would intercept the same keys.
- The `TCL_*` buttons may not have a standard keycode, so they are matched by scancode (see remote-buttons.md).

#### How the remote service works

Three parts. All three are needed, otherwise the service will not receive the keys.

1. **Declaration in the manifest.** The service is protected by a system permission, so without it the system will not start it.

```xml
<service android:name=".RemoteKeyService"
    android:exported="false"
    android:label="@string/remote_service_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
        android:resource="@xml/remote_keys" />
</service>
```

2. **Service settings** (`res/xml/remote_keys.xml`). Keys arrive only when **both** `flagRequestFilterKeyEvents` and
   `canRequestFilterKeyEvents="true"` are set; without the second one `onKeyEvent` is never called (CleanYourTV
   `input_key_service.xml`). `accessibilityEventTypes` is left out (0), so the system sends no accessibility events.
   `canRetrieveWindowContent="false"`: the service does not read the content of the screens.

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityFlags="flagRequestFilterKeyEvents"
    android:canRequestFilterKeyEvents="true"
    android:canRetrieveWindowContent="false"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:description="@string/remote_service_desc" />
```

3. **The service class.** It receives each press through `onKeyEvent(KeyEvent)`. If the button is assigned to an app, the service
   launches the app and returns `true` (the event is consumed). Otherwise it returns `false` and the key goes on.

**Enabling on the TV.** Manually: TV Settings → Accessibility → the remote service. Through ADB (only if it is needed without a screen):
`settings put secure enabled_accessibility_services <package>/.RemoteKeyService` and
`settings put secure accessibility_enabled 1`. This changes the state of the TV, so we record it for the rollback (section 11.8).

## 8. Application menu (long OK)

Items (in the order of Couchy `Menus.kt:54–59`, without removal):

1. **Open**: `AppLauncher.launch`.
2. **Move**: Move mode (below).

3. **Hide** / **Show**: we write the package to `hidden` (section 9).
4. **App details**: `ACTION_APPLICATION_DETAILS_SETTINGS` with a `package:` URI
   (`AppAdapter.java:519–523`, CleanYourTV). "Force stop" is there, so there is no "Close app" item of our own:
   `killBackgroundProcesses` is best effort and, from Android 14, can only stop the caller's own processes.

**No uninstall in the MVP.** If it is needed later: `REQUEST_DELETE_PACKAGES` with the system dialog (Couchy
`AndroidManifest.xml:30`), not `pm uninstall`.

The menu is a `PopupWindow` with a list of `TextView`s, 56 dp each, with no animations. Focus is on the first item.

**Move mode** (as `tile_menu` in CleanYourTV, `MainActivity.java:1533–1644`, `movingKey`):

```java
// when "Move" is chosen: intercept the arrows and OK while the mode is active
@Override
public boolean dispatchKeyEvent(KeyEvent e) {
    if (movingPkg == null || e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
    switch (e.getKeyCode()) {
        case KeyEvent.KEYCODE_DPAD_LEFT:  swapBy(-1); return true;
        case KeyEvent.KEYCODE_DPAD_RIGHT: swapBy(+1); return true;
        case KeyEvent.KEYCODE_DPAD_UP:    swapBy(-columns); return true;
        case KeyEvent.KEYCODE_DPAD_DOWN:  swapBy(+columns); return true;
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER:
        case KeyEvent.KEYCODE_BACK:       endMove(); return true;   // OK or Back: put it down
        default: return super.dispatchKeyEvent(e);
    }
}
```

`swapBy` changes the tile's position in `order` (section 9) and calls `notifyItemMoved`. In Move mode `order` is updated on each move,
but it is not committed: we save it on `endMove`.

---

## 9. Hiding and ordering apps

### 9.1. Data model

One JSON file in `filesDir`, written through `AtomicFile` (as `LibraryApps.java` in CleanYourTV and `ConfigStore` in
Couchy). An example with real default values:

```json
{
  "version": 1,
  "order":   ["com.example.films", "com.example.music"],
  "hidden":  ["com.example.bloat"],
  "known":   ["com.example.films", "com.example.music", "com.example.bloat"],
  "wallpaper": {
    "type": "video",                       // video | aerials | picture | gradient
    "uri": "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fwall.mp4",
    "dim": 30,
    "poster": "poster.webp",
    "imagePath": null
  },
  "gradient": { "colors": ["#13547A", "#2B825B", "#80D0C7"] },
  "weather": {
    "enabled": true, "name": "City", "admin1": "", "lat": 52.52, "lon": 13.41,
    "fahrenheit": false,
    "last": "{\"current\":{...}}", "fetchedAt": 1763000000000
  },
  "clock": { "h24": true, "showDate": true, "greeting": false },
  "ui": { "columns": 5, "rowsBelow": 0, "labels": true, "corner": 16, "showInputsButton": true,
          "inputsConnectedOnly": false, "inputsHideAv": false },
  "inputs": { "lastId": "" },
  "lastFocused": "com.example.films"
}
```

Default values: `wallpaper.dim` = 30 (dimming from the top, in percent; the scale 0 / 20 / 40 / 60 is in section 10.1),
`gradient.colors` = the Aurora colours from Couchy (`Theme.kt`, `WALLPAPERS[1]`), `clock.h24` = true (Couchy
`LauncherConfig.h24 = true`), `ui.columns` = 5, `ui.rowsBelow` = 0, `ui.corner` = 16, `clock.showDate` = true. `ui.labels` = true means "label under
the focused tile" (7.5); false hides it everywhere. The example shows comments for reading only: the real file is plain JSON.

`known` is the list of packages that were installed at the last scan. It is **not** "all packages ever seen": otherwise a
reinstalled app would not move to the end of the list. Couchy has the same idea in `knownApps` (`Config.kt:36`).

### 9.2. Code: reading and writing

```java
// LauncherState.java
final class LauncherState {
    private final AtomicFile file;

    LauncherState(Context ctx) { file = new AtomicFile(new File(ctx.getFilesDir(), "launcher_state.json")); }

    /** A missing file → an empty string; StateJson.parse turns a corrupt or empty string into the defaults. */
    String read() {
        try {
            return new String(file.readFully(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    void write(JSONObject o) throws IOException {
        FileOutputStream out = file.startWrite();
        try {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException e) {
            file.failWrite(out);
            throw e;
        }
    }
}
```

Write once per changed item, not on every key press (section 10). `AtomicFile` protects against a broken write when the TV is switched off.

**Do not use `record`** (Java 16+). It works fine at runtime on Android 11, but D8/R8 in older builds and lint may complain,
and there is no reason to take that risk here. We use ordinary classes.

### 9.3. Scanning apps and recalculating the list

**Scan**: as in CleanYourTV (`AppAdapter.java:266–287`) and Couchy (`AppRepository.kt:83`): two `queryIntentActivities`
requests, with `CATEGORY_LEANBACK_LAUNCHER` and `CATEGORY_LAUNCHER`, deduplication by package, our own package excluded,
`exported` is required.

```java
// AppScanner.java
static final class Found {
    final String pkg; final boolean leanback; final long firstInstall, lastUpdate;
    Found(String p, boolean l, long f, long u) { pkg = p; leanback = l; firstInstall = f; lastUpdate = u; }
}

static List<Found> scan(Context ctx) {
    PackageManager pm = ctx.getPackageManager();
    Map<String, Found> out = new LinkedHashMap<>();
    for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
        boolean leanback = cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
            if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || out.containsKey(pkg)) continue;
            long first = 0, upd = 0;
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                first = pi.firstInstallTime; upd = pi.lastUpdateTime;
            } catch (PackageManager.NameNotFoundException ignored) { }
            out.put(pkg, new Found(pkg, leanback, first, upd));
        }
    }
    List<Found> list = new ArrayList<>(out.values());
    list.sort((x, y) -> Long.compare(x.firstInstall, y.firstInstall));   // merge() expects install order (9.3)
    return list;
}
```

`scan` runs on the banner thread pool (5.2), never on the main thread: `getPackageInfo` per package is slow on the TV.

On Android 11, `queryIntentActivities` needs a `<queries>` block in the manifest (section 11). Without it the list will be incomplete.

**Recalculation** is a pure function and is easy to test:

```java
// AppListMerger.java
final class AppListMerger {
    static final class Result {
        final List<String> order;     // full order, kept
        final Set<String> known;      // = currently installed
        final List<String> visible;   // order minus hidden
        Result(List<String> o, Set<String> k, List<String> v) { order = o; known = k; visible = v; }
    }

    /**
     * order: the saved order; known: what was there at the last scan; hidden: hidden apps;
     * installed: currently installed, sorted by firstInstallTime (new ones at the end).
     */
    static Result merge(List<String> order, Set<String> known, Set<String> hidden, List<String> installed) {
        Set<String> inst = new LinkedHashSet<>(installed);
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String p : order) if (inst.contains(p)) next.add(p);      // keep the order, drop removed apps
        for (String p : installed) if (!known.contains(p)) next.add(p); // new ones go to the end, in install order
        List<String> visible = new ArrayList<>();
        for (String p : next) if (!hidden.contains(p)) visible.add(p);
        return new Result(new ArrayList<>(next), new HashSet<>(inst), visible);
    }
}
```

Important: `known` is the set of **currently installed** apps, not a union. So a reinstall counts as new (it goes to the end),
and a removed package is dropped from `order` but stays in `hidden` until the user clears it.

### 9.4. Updating while running

Installing and removing apps while Home is open:

```java
// register in onStart, unregister in onStop; no service
IntentFilter f = new IntentFilter();
f.addAction(Intent.ACTION_PACKAGE_ADDED);
f.addAction(Intent.ACTION_PACKAGE_REMOVED);
f.addDataScheme("package");
registerReceiver(pkgReceiver, f);
```

`pkgReceiver` calls `rescan()` with a 500 ms delay (several events in a row make one scan). There are no background services.

### 9.5. "Apps" screen (settings)

- A list of all launchable apps: a 40 dp icon, the name, and a "show" switch.
- "Order": Move mode for the whole row (9.3).
- "Show all" clears `hidden`.
- No search (30–60 apps, scrolled through).

---

## 10. Settings panel on the right

Reference: `couchy-launcher/app/src/main/java/com/conreo/couchytv/ui/SettingsSheet.kt`.

**Sizes (verified):** `width(440.dp)`, `RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)`
(`SettingsSheet.kt:183–186`); the slide from the right is `slideInHorizontally(tween(260))` + `fadeIn(tween(260))`
(`:196–199`); going back is the same slide in reverse.

### 10.1. Root (8 items)

| # | Item | Subsection | Contents |
|---|---|---|---|
| 1 | **Wallpaper** | yes | type (video / aerials / picture / gradient), "Choose video…" (SAF; the codec warning from 6.1 is shown on selection), "Choose picture…", dimming 0 / 20 / 40 / 60 % |
| 2 | **Weather** | yes | on/off, "City…" (input → geocoding → list), °C / °F, "Refresh now", attribution. The interval is fixed at 30 min (4.6) |
| 3 | **Apps** | yes | list with switches, "Order", "Show all" (9.5) |
| 4 | **Appearance** | yes | columns 4–7, rows below the bar 0–3 (`rows_below_dock`, default 0), labels on/off, radius 0 / 8 / 16 dp |
| 5 | **Inputs** | yes | the "Inputs" icon at the top right on/off; "connected only"; hide AV |
| 6 | **Clock** | no | 12 / 24 h; show the date (default yes); greeting above the clock (default no) |
| 7 | **About** | no | version, build, attributions (Open-Meteo CC BY 4.0; Material Icons Apache-2.0; Media3 Apache-2.0; Outfit OFL 1.1) |
| 8 | **TV settings** | no | opens the system TV settings (`Settings.ACTION_SETTINGS`). It is a rarely used function, so it is here and not on the home screen |

Items 4 and 5 were renumbered compared with v1: "Inputs" was added as item 5 (section 7.7).

**What we drop from Couchy:** the language setting (we use one interface with `values/` and `values-ru/` resources),
saving the configuration to a JSON file for export (later, if needed), the first-run wizard (a short hint on the first screen),
categories and sections (they are not in our plan), and the VPN button.

**All settings apply immediately,** with no "Save" button.

### 10.2. Code: panel

```java
// SidePanel.java: a 440 dp sliding panel
final class SidePanel {
    private final View panel, veil;
    private final int width;

    SidePanel(View panel, View veil, float density) {
        this.panel = panel; this.veil = veil;
        this.width = Math.round(440 * density);
        panel.getLayoutParams().width = width;
        panel.setTranslationX(width);
    }

    boolean isOpen() { return panel.getVisibility() == View.VISIBLE; }

    void open() {
        panel.setVisibility(View.VISIBLE);
        veil.setVisibility(View.VISIBLE);
        panel.animate().translationX(0).setDuration(260).start();
    }

    void close() {
        panel.animate().translationX(width).setDuration(260)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        veil.setVisibility(View.GONE);
    }
}
```

Subsections: `ViewPropertyAnimator` on `translationX` and `alpha` (the equivalent of `AnimatedContent`). Forward: the current screen
moves out to the left, and the new one comes in from the right. Back: the reverse. `BACK` in a subsection returns to the root;
at the root it closes the panel.

Focus: when the panel opens, the first item of the root gets `requestFocus()`. The remote works without a mouse (D-pad, OK, BACK).

### 10.3. "About" text (example)

```text
Android TV launcher · version 1.0 (build 2026-11-13)
Weather: Open-Meteo.com, data under CC BY 4.0
Icons: Material Icons, Apache License 2.0
Video: AndroidX Media3, Apache License 2.0
Font: Outfit, SIL Open Font License 1.1
```

---

## 11. Build and tools

Practices for services, APIs and performance: [docs/BEST-PRACTICES.md](BEST-PRACTICES.md). This is a reference, not requirements.

### 11.1. Repository layout

```text
just-my-tv-launcher/
├── launcher/                    # new launcher (Gradle project)
│   ├── settings.gradle          # copy from CleanYourTV, rootProject.name = "tvhome"
│   ├── build.gradle             # AGP 8.13.2 (CleanYourTV has 8.5.2, too old for compileSdk 36, see 11.5)
│   ├── gradle.properties        # org.gradle.jvmargs=-Xmx2g, android.useAndroidX=true
│   ├── gradlew, gradle/wrapper  # Gradle 8.13 (the minimum for AGP 8.13)
│   └── app/
│       ├── build.gradle
│       ├── proguard-rules.pro   # -dontobfuscate (as in CleanYourTV)
│       ├── NOTICE               # Apache-2.0 (Material Icons, Media3), OFL (Outfit)
│       └── src/
│           ├── main/AndroidManifest.xml
│           ├── main/java/<your package>/   # see 2.2, 5, 6, 7, 9
│           ├── main/res/...                # layout, drawable, font, values, values-ru
│           └── test/java/...               # JVM tests (section 12)
├── tools/
│   ├── docker/Dockerfile        # copy from CleanYourTV
│   ├── docker-build.sh          # copy from CleanYourTV
│   ├── tv.py, requirements.txt  # screenshots and ADB wrapper (section 12)
│   └── docker-build.md          # copy from CleanYourTV (signing notes)
├── docs/
│   ├── PLAN.md                  # this document
│   └── screen-mockup.html       # home screen mockup
└── sources/                     # submodules, reference only; removed after implementation (section 1.1)
```

Package name: `com.justmytv.tvhome` (D57). Not `com.gathin.tvhome` (that is the CleanYourTV package).

### 11.2. Manifest

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <!-- Weather. No location access and no Wi-Fi name (SSID), so FINE_LOCATION is not needed. -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Android 11+: package visibility. Without this block the app list is incomplete. -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent>
        <intent>
            <action android:name="android.settings.APPLICATION_DETAILS_SETTINGS" />
            <data android:scheme="package" />
        </intent>
        <intent>
            <action android:name="android.intent.action.OPEN_DOCUMENT" />
        </intent>
        <intent>
            <action android:name="com.android.tv.action.VIEW_INPUTS" />
        </intent>
    </queries>

    <application
        android:allowBackup="false"
        android:banner="@drawable/banner_tv"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="false"
        android:theme="@style/Theme.Launcher">

        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboard|keyboardHidden|uiMode"
            android:excludeFromRecents="true"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="landscape"
            android:stateNotNeeded="true">
            <!-- Home: CVTE firmware sends HOME + COMMON_HOME (CleanYourTV, AndroidManifest.xml:90–100). -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.COMMON_HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <!-- In the TV app list. -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- The remote service (7.8) is added at M7. -->
    </application>
</manifest>
```

Why `android:banner` and `banner_tv`: this is the app banner in the TV list. We take `drawable/banner_tv.xml` from CleanYourTV
(the file is in `res/drawable/banner_tv.xml`).

### 11.3. Theme and window background

```xml
<!-- res/values/themes.xml -->
<resources>
    <style name="Theme.Launcher" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@drawable/window_bg</item>   <!-- default poster, 6.2 -->
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:colorBackground">#FF000000</item>
    </style>
</resources>
```

**Overdraw.** Speed is the one hard requirement, and a weak GPU pays for every full-screen layer. The window background is
needed only until our first frame. Once the poster `ImageView` has its image and the first frame is drawn, remove it:

```java
// MainActivity.onCreate, after the poster image is set
getWindow().getDecorView().post(() -> getWindow().setBackgroundDrawable(null));
```

The root `FrameLayout` has no background (the opaque poster or the video covers the screen). That leaves the poster (until its
fade ends) and the scrim as the only full-screen layers.

```xml
<!-- res/drawable/window_bg.xml -->
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/poster_default"
    android:gravity="fill" />
```

`poster_default.webp`: 1920×1080, the Aurora gradient from Couchy (`#13547A` → `#2B825B` → `#80D0C7`), about 50 KB as WebP. **Revised (D83):** replaced by the dithered gradient shape `drawable/aurora.xml`, which needs no bitmap.

### 11.4. Home screen layout

```xml
<!-- res/layout/activity_main.xml -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <!-- no background: the poster or the video covers the screen (overdraw, 11.3) -->

    <SurfaceView
        android:id="@+id/video"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <ImageView
        android:id="@+id/poster"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop" />

    <View
        android:id="@+id/scrim"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/scrim_top" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/grid"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:paddingStart="44dp"
        android:paddingEnd="44dp"
        android:paddingBottom="0dp" />
    <!-- grid: paddingTop is set in code to barTop (section 7.2). An XML comment cannot go inside a tag. -->

    <!-- focus ring (7.4): above the grid, so the bar band does not cover it; not focusable -->
    <FrameLayout
        android:id="@+id/glass_host"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <LinearLayout
        android:id="@+id/topbar"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:layout_gravity="top"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="56dp"
        android:paddingEnd="56dp">
        <!-- 56 dp: the clock, the weather (marginStart 56 dp) and the first tile's left edge
             (44 + (cell − tileW) / 2 ≈ 57 dp at 5 columns) line up on one vertical -->
        <!-- header: clock and date on the left; round icons on the right: inputs, network, settings -->
        <!-- weather and city on the left, under the clock and date: LinearLayout with layout_gravity="top|start", marginStart 56 dp, marginTop 100 dp -->
    </LinearLayout>

    <View
        android:id="@+id/veil"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#59000000"
        android:visibility="gone" />

    <FrameLayout
        android:id="@+id/panel"
        android:layout_width="440dp"
        android:layout_height="match_parent"
        android:layout_gravity="end"
        android:background="@drawable/panel_bg"
        android:visibility="gone" />
</FrameLayout>
```

`scrim_top.xml`: take the exact values from Couchy `ScrimTop` (`#99000000` at the top, `#00000000` at 20 % of the height,
`LauncherScreen.kt:118–121`).

```xml
<!-- res/drawable/scrim_top.xml: top to bottom, 60 % black fading out by 20 % of the height -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="90"
        android:startColor="#99000000"
        android:centerColor="#00000000"
        android:centerY="0.2"
        android:endColor="#00000000" />
</shape>
```

Also needed: `res/drawable/panel_bg.xml`, a fill of `#E61E2026` with a 16 dp radius on the left (`topLeftRadius`,
`bottomLeftRadius`), as in Couchy `SettingsSheet.kt:183`.

### 11.5. Gradle

```groovy
// launcher/app/build.gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.justmytv.tvhome'
    compileSdk 36                       // required: Media3 1.11.1 has minCompileSdk=36 in its AAR metadata

    defaultConfig {
        applicationId 'com.justmytv.tvhome'
        minSdk 26                       // Media3 from 1.9 requires minSdk 23; we take 26, as in CleanYourTV
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }

    buildTypes {
        release {
            // R8: fast startup on a weak CPU (CleanYourTV, build.gradle:23–25)
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            // Installed over ADB: signed with the debug key, as in CleanYourTV (build.gradle:27)
            signingConfig signingConfigs.debug
        }
    }

    packagingOptions {
        resources {
            excludes += ['kotlin/**', 'META-INF/*.version', 'META-INF/*.kotlin_module', 'DebugProbesKt.bin']
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.recyclerview:recyclerview:1.4.0'
    implementation 'androidx.dynamicanimation:dynamicanimation:1.1.0'
    implementation 'androidx.media3:media3-exoplayer:1.11.1'
    testImplementation 'junit:junit:4.13.2'
}
```

- The versions are stable and at least two weeks old on 2026-11-13. Media3 1.11.1 was released on 10 September 2026 (checked with WebFetch
  on the release notes). RecyclerView 1.4.0 and DynamicAnimation 1.1.0 are the latest stable versions in the `dl.google.com` metadata.
- **compileSdk 36 is required, not optional.** Checked in the AAR metadata (`META-INF/com/android/build/gradle/aar-metadata.properties`,
  dl.google.com, 2026-11-13): `media3-exoplayer` 1.11.1 and 1.10.1 both declare `minCompileSdk=36`, and `recyclerview` 1.4.0
  declares 35. So rolling back to 1.10.1 does not help. AGP 8.5.2 (CleanYourTV) does not support API 36; **AGP 8.13.2** does
  (max API 36.1, Gradle 8.13, JDK 17, build tools 35.0.0 or newer; AGP release notes). We stay on AGP 8.x: AGP 9 changes the
  build DSL and gains nothing here. `compileSdk` only changes which APIs compile; `targetSdk` stays 34 and the TV stays on API 30.
- No Compose, no Kotlin, no OkHttp, no DataStore.

### 11.6. Docker and the build

Copy into `tools/` from CleanYourTV (a copy, not a link, section 1.1): `docker/Dockerfile`, `docker-build.sh`, `docker-build.md`, `tv.py`,
`requirements.txt`. Versions in the Dockerfile, **raised from CleanYourTV's**: Temurin 17, Android SDK `platforms;android-36`,
`build-tools;36.0.0`, a Gradle Docker volume. On Apple Silicon the script builds `linux/amd64` by itself.

```bash
tools/docker-build.sh launcher assembleRelease
tools/docker-build.sh launcher testReleaseUnitTest lintRelease assembleRelease
```

Signing key (`tools/docker-build.md`, `docker-build.sh:17–21`): `HOST_KEY_DIR="$HOME/.android"`. Use the same key for the Docker build
and the native build, otherwise you get `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

The first build takes about 7 minutes (v1 and the CleanYourTV docs). On Apple Silicon it runs through Rosetta.

### 11.7. Installing on the TV

```bash
adb connect <TV_IP>:5555
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
```

Home screen: on the TV press Home → choose the launcher → "Always" (checked: right now HOME is Couchy). Through ADB:
`adb shell cmd package set-home-activity <package>/.MainActivity` (the command from the Couchy README; **check** on the TCL).
To restore Couchy: the same way. Until that is done, the before/after comparison is made by switching Home.

---

### 11.8. Rollback: recording the state and restoring it

The principle is the same as in CleanYourTV (`docs/restore.md`): **before the first change to the TV, we record its state; the rollback replays the record.**

**Record** (`tools/record.sh`, once, before installation, file `~/.just-my-tv-launcher/<TV address>.json`):

- who owns HOME (currently `com.conreo.couchytv/.MainActivity`);
- the state of the packages in the list: enabled / disabled by the user / not installed. Now:
  `com.google.android.apps.tv.launcherx` is disabled by the user (`enabled=3`);
  `<PKG_OLD_LAUNCHER>` is not disabled, it is just not HOME (`enabled=0`);
  `flar2.homebutton` is installed, its service is enabled;
- the list of enabled accessibility services (`enabled_accessibility_services`).

**Rollback** (`tools/restore.sh`, `TV_HOST=<TV_IP>`):

- HOME goes back to whoever owned it in the record (Couchy);
- `launcherx` stays disabled; we do not enable it;
- `nooklauncher` stays in the state from the record;
- the list of accessibility services is restored as it was;
- our launcher stays installed (removal is done in Settings → Apps, as a separate action).

If there is no record, the script guesses nothing: it prints the current state and reports that the rollback is manual.

**Not done:** firmware rollback or any firmware changes.

## 12. Tests and checks

### 12.1. JVM tests (`./gradlew testReleaseUnitTest`)

```java
// AppListMergerTest.java
public class AppListMergerTest {
    @Test public void newAppsGoLastAndUninstalledLeaveOrder() {
        AppListMerger.Result r = AppListMerger.merge(
                Arrays.asList("a", "b", "c"),          // saved order
                new HashSet<>(Arrays.asList("a", "b", "c")),
                new HashSet<>(),
                Arrays.asList("a", "c", "d"));         // b removed, d new
        assertEquals(Arrays.asList("a", "c", "d"), r.order);
        assertEquals(Arrays.asList("a", "c", "d"), r.visible);
    }

    @Test public void hiddenStaysHidden() {
        AppListMerger.Result r = AppListMerger.merge(
                Arrays.asList("a", "b"),
                new HashSet<>(Arrays.asList("a", "b")),
                new HashSet<>(Arrays.asList("a")),     // a is hidden
                Arrays.asList("a", "b"));
        assertEquals(Arrays.asList("a", "b"), r.order);
        assertEquals(Collections.singletonList("b"), r.visible);
    }

    @Test public void reinstallGoesLast() {
        // scan 1: b is removed, so it leaves order and known
        AppListMerger.Result r1 = AppListMerger.merge(
                Arrays.asList("b", "a", "c"),
                new HashSet<>(Arrays.asList("a", "b", "c")),
                new HashSet<>(),
                Arrays.asList("a", "c"));
        assertEquals(Arrays.asList("a", "c"), r1.order);
        // scan 2: b is installed again and goes to the end, not back to the front
        AppListMerger.Result r2 = AppListMerger.merge(r1.order, r1.known, new HashSet<>(), Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("a", "c", "b"), r2.order);
    }
}
```

```java
// WeatherCodeTest.java
@Test public void wmoMapping() {
    assertEquals("wx_sun", WeatherCode.icon(0, true));
    assertEquals("wx_moon", WeatherCode.icon(0, false));
    assertEquals("wx_storm", WeatherCode.icon(97, true));    // heavy thunderstorm
    assertEquals("wx_hail", WeatherCode.icon(99, true));
    assertEquals("wx_unknown", WeatherCode.icon(42, true));  // unknown code
}
```

```java
// WeatherParserTest.java: the fixture from section 4.2, saved as src/test/resources/forecast_berlin.json
@Test public void parsesCurrent() throws IOException {
    String json = readResource("forecast_berlin.json");
    WeatherParser.Now now = WeatherParser.parseForecast(json);
    assertEquals(4, now.tempC);                  // 4.4 → 4
    assertEquals("°C", now.unit);
    assertEquals(1, now.code);
    assertFalse(now.isDay);
}

@Test public void noResultsKeyIsEmptyList() {
    assertTrue(WeatherParser.parseGeocode("{\"generationtime_ms\":0.16}").isEmpty());
}

@Test public void errorBodyIsNull() {
    assertNull(WeatherParser.parseForecast("{\"error\":true,\"reason\":\"bad\"}"));
}
```

`JSONObject` in JVM tests: the platform `org.json` is not available in plain JUnit. Options: add
`testImplementation 'org.json:json:20240303'` (for tests only, so it does not end up in the APK), or move the parsing
into a separate class without `android.*`. **Recommendation:** `testImplementation 'org.json:json:20240303'`.

Required tests: `AppListMerger` (9.3), `WeatherCode` (4.5), `WeatherParser` (4.5), `StateJson`
(a corrupt or empty string → default values).

`LauncherState` itself cannot be tested on the plain JVM: `android.util.AtomicFile` is only a stub there. So the file is split
in two: `LauncherState` does the file I/O through `AtomicFile` (no logic), and `StateJson` turns a `String` into
settings with defaults and back (no `android.*`, tested). No Robolectric.

**Lint:** `lintRelease` with no errors.

### 12.2. Manual checks on the TV

```bash
TV_HOST=<TV_IP> tools/tv.py --screenshot shots/home.png
TV_HOST=<TV_IP> tools/tv.py --screenshot shots/after_right.png DPAD_RIGHT
```

- D-pad from each edge of a row: left from the first tile, right from the last, up and down.
- Long OK → menu → "Hide" → the tile disappears → restart the launcher → the hiding is kept.
- Choose a video → it plays with no sound, and the loop has no jerk at the join (30 minutes).
- Home from another app and back → the decoder is released and working again, no black frame (6.2).
- Turn off Wi-Fi → the weather shows the cache with its update time, no crash.
- Change the city → the weather is updated.
- Switch HDMI 1 → HDMI 2 with a picture on screen (7.7).

`tools/tv.py` talks to the TV over the network (`TV_HOST`). It is used only for checks and does not change the device.

### 12.3. Performance (budgets, targets to be checked)

| Metric | How to measure | Target (proposal) |
|---|---|---|
| Cold start to the first frame | `adb shell am start -W -n <package>/.MainActivity` (`TotalTime`) | < 1 s |
| PSS on Home without video | `adb shell dumpsys meminfo <package>` | < 120 MB |
| PSS with 1080p video | the same, after 10 minutes | < 200 MB |
| Frames while scrolling the grid (60 apps, down and up) | `adb shell dumpsys gfxinfo <package>` (Janky frames) | < 2 % |
| Decoder released when going to the background | `ON_STOP` logs and `dumpsys media.codec` | yes |
| Black frame at video start | `screenrecord` + frame-by-frame analysis (6.2) | 0 frames |
| HDMI 1 → 2 switch | screenshot after the switch | the picture changed |

**Measured on the TV, 2026-11-14** (release build, 18 apps):

| Metric | Result |
|---|---|
| Cold start (`TotalTime`, 3 runs) | 555–615 ms: within the target |
| PSS on Home without video | 45 MB with a picture, 49 MB with the gradient |
| PSS with video after 10 minutes | aerials playing: 76 MB at start, 97 MB after 5 minutes, within the target. At 10 minutes the TV screensaver had taken over (96 MB), so a full 10 minutes of video in front is still to measure with the screensaver off |
| Frames while scrolling | 18 apps, keys over ADB: 3 of 250 frames missed the deadline (1.2 %), GPU median 12 ms. "Janky frames" is not usable with ADB keys (T16); 60 apps not checked |
| Decoder released in the background | yes, behind an opaque app (`dumpsys media.resource_manager`); kept behind the TV Settings side panel, which leaves Home visible |
| Black frame at video start | not measurable here: `screencap` and `screenrecord` do not see the video plane on this TV; by eye (T18) |
| HDMI 1 → 2 | switched; `dumpsys tv_input` shows the session on `hdmi_port=2` |

**Couchy baseline** on this TV, **before** we build our own: `am start -W` and `dumpsys meminfo` for
`com.conreo.couchytv`. The user answered "not yet" (OPEN-QUESTIONS Q7).

---

## 13. Decisions needed from you

Open questions are kept only in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md), and settled ones in [DECISIONS.md](DECISIONS.md).
This section used to repeat them and drifted out of date, so it now only points there.

---

### 13.1. Decisions made after version 2

- **Remote:** our own service in the launcher; `flar2.homebutton` is disabled during implementation (section 7.8).
- **nooklauncher** (`<PKG_OLD_LAUNCHER>`, a fork of the Kotlin launcher): keep it disabled. Right now it is **not disabled**,
  only not assigned HOME (`enabled=0`). Disabling it is a separate step, done during implementation, with agreement.
- **launcherx** (the stock Google launcher): stays disabled (`enabled=3`, disabled by the user). The Google Home panel
  is not needed on the TV.
- **<OTHER_IP>** is the phone; we do not touch it. All checks are done only on `<TV_IP>`.

## 14. What we do **not** do (explicit list)

- Themes, presets, appearance switching. One built-in look.
- Extensions, module cards, `addons/`, widget tiles.
- A dock as a separate block (neither a "glass" panel at the bottom nor Couchy's Dock mode). The bar is row 1 of the grid (section 7.2).
- A horizontal carousel as the main mode, and several sections with separate grids.
- App sections and categories (Couchy `categories`).
- Live TV (tuner, channels), CEC control.
- Debloat, `pm disable-user`, TV install scripts other than `adb install`.
- The app library (`app_library`), a clock screensaver (`ClockDream`), Lunar, Shamsi, holiday skins.
- `HomeKeyService` and the Home interception from CleanYourTV: we do not take them. The remote service (section 7.8) is our own background service, deliberately.
- Trust-all TLS. Aerial streaming is enabled only by the user (section 3).
- Compose, Kotlin, `record`.
- Device location and the Wi-Fi SSID (would need `ACCESS_FINE_LOCATION`).
- WorkManager, analytics, telemetry. The background remote service (section 7.8) is a deliberate exception.
- A "Close app" menu item (`killBackgroundProcesses`): "App details" has "Force stop" (section 8).
- Our own `VIEW_INPUTS` activity (`InputsRequestActivity` in CleanYourTV): the fallback uses the system one (7.7).
- A UI scale setting (columns are the only size setting, 7.2), a weather interval setting (fixed 30 min), a "Check video" button.
- Uninstalling apps (only `REQUEST_DELETE_PACKAGES`, if it is ever needed).
- Changing the video speed.

---

## 15. Stages for the next iteration

Each stage is built and checked on the TV. The check commands are in section 12.

**M0. Skeleton.** `launcher/` with Gradle (section 11), an empty `MainActivity` with HOME and LEANBACK_LAUNCHER, a manifest with `<queries>`, a Docker build. *Done when:* `tools/docker-build.sh launcher assembleRelease` produces an APK; the APK installs and opens as Home (choose "Always").
**Done 2026-11-13:** state recorded, installed, HOME set with `cmd package set-home-activity` (works on the TCL), the Home key opens the poster screen. T1 (HOME after a reboot) is still open.

**M1. Grid with the bar.** Scan (9.3), banners and the WebP cache (5), `RecyclerView` + `BarGridLayoutManager` (2.2),
`BarBand` (2.2), tile and label (7.3), focus ring (7.4). 5 columns, the formula from 7.2.
*Done when:* at rest the bar is visible and the next row peeks up from below; scrolling down expands the grid; the header has the clock and date on the left and the icons on the right, centred; the weather is on the left under the clock; D-pad works across the whole grid without jerks; scrolling down through 60 apps is smooth (`gfxinfo`); with 5 apps it looks like one row; a screenshot from the TV matches `docs/screen-mockup.html` in layout.
**Status 2026-11-13:** on the TV: scan, banners and icon tiles with the WebP cache, the bar band, the focus ring, the header (clock, date, three icons that open the system screens until M3), D-pad across the whole grid with row wrap, OK launches and Home returns to the same tile. Not done: the weather under the clock (M5), the 60-app scroll check (T16, deferred to M6 at the user's request).

**M2. Menu and hiding.** Long OK → menu (8), `LauncherState` (9.2), `AppListMerger` (9.3), reinstall and removal.
*Done when:* the `AppListMerger` unit tests are green; hiding survives a restart.
**Status 2026-11-13:** built (merger and state JVM tests green), pushed; not yet checked on the TV (OPEN-QUESTIONS T17). **2026-11-14:** checked on the TV (T17); fixes D86–D89.

**M3. Settings panel and inputs.** `SidePanel` 440 dp (10.2), a root of 8 items (10.1), the "Apps" subsection,
the "Inputs" panel (7.7), the "Inputs" icon at the top right. *Done when:* the panel opens and closes with the button and BACK;
focus works with the D-pad; switching HDMI 1 → HDMI 2 is verified with a screenshot.
**Status 2026-11-13:** built and pushed; not yet checked on the TV (T17). The root has 7 items until M4 adds Wallpaper; Clock and About open as subsections (D65). **2026-11-14:** checked on the TV (T17); fixes D86–D89.

**M4. Video wallpapers.** SAF (6.1), `VideoWallpaper` (6.1), poster (6.2), 3 s watchdog, scrim, picture fallback,
the "Aerials" type (6.3: `res/raw/aerials.json`, shuffle on end, 8 s watchdog, platform TLS only).
*Done when:* 30 minutes of a looped clip with no sound and no crashes; the decoder is released in the background; the
`screenrecord` measurement is recorded in this file (section 6.2); memory metrics are recorded; an Apple and an Amazon aerial
each play on the TV, or the failing set is skipped and the reason (for example the TLS error) is recorded in RISKS 45.
**Status 2026-11-13:** built (aerials and state JVM tests green), pushed; not yet checked on the TV (OPEN-QUESTIONS T18). **2026-11-14:** checked on the TV (T18): a file video, Amazon and Apple aerials (Apple after D85), a picture; the black-frame and 30-minute checks need eyes. The aerial list has no 4K entry to drop: every Apple link is `SDR_2K_AVC` ("UHD" in some names is the source), so all 251 are kept (D72).

**M5. Weather.** Geocoding, `HttpURLConnection`, `WeatherParser` (4.5), WMO icons (our own VectorDrawable), cache,
attribution. *Done when:* the parser unit tests on the fixture pass; with the network off, the cache is shown with its update time;
the attribution is visible.
**Status 2026-11-13:** built (WeatherCode and WeatherParser JVM tests green on the fixture; the request URLs checked against the live API), pushed; not yet checked on the TV (T17). **2026-11-14:** checked on the TV (T17); fixes D86–D89.

**M6. Polish and measurements.** Interface icons (5.4), sizes, radii, `values-ru` strings, "About", performance measurements (12.3) including the grid scroll check OPEN-QUESTIONS T16,
baseline recorded in this file. *Done when:* all items in 12.3 are checked and there are screenshots from the TV.
**Status 2026-11-13:** Russian strings, settings icons and About are built and pushed; the code pass (D81–D83) is done. The measurements (12.3), T16 and the screenshots wait for the TV (T19). **2026-11-14:** the measurements are in 12.3; screenshots were taken during the TV checks.

**M7. Remote service.** `RemoteKeyService` (7.8): manifest entry, `remote_keys.xml`, the "Remote buttons" settings screen
(button → app), default YouTube → SmartTube. Before enabling it: a new state record (OPERATIONS 3), then `flar2.homebutton`
is disabled (D25, S2). Closes T2–T5 and T14. *Done when:* every assigned button launches its app from inside another app;
unassigned buttons behave as before; after a reboot the service is still enabled; PSS of the service process is recorded.
**Status 2026-11-13:** built and pushed; not yet checked on the TV (T19). `flar2.homebutton` is still enabled: disabling it changes the TV and needs a state record and your go-ahead (OPERATIONS 3, S2). **2026-11-14:** enabled after a state record and checked with a virtual keyboard (T19); the real remote and a reboot remain. After an app update or a force stop the TV stops running the service (RISKS 46, D91).

---

## 16. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Video stutters on the TCL (CPU or decoder) | Medium | Fallback to a picture, 1080p H.264 requirement, measurements on the TV |
| ExoPlayer increases memory use | Medium | Buffers reduced, release on `ON_STOP`, PSS measurements |
| The decoder cannot handle the clip (profile, pixel format) | Medium | `setEnableDecoderFallback(true)`, 3 s watchdog, fallback to a picture |
| **Black frame at cold start with the chosen video** | High | The default poster as `windowBackground` (6.2); for the chosen video, measure it; if > 300 ms, reconsider |
| Choosing a video shows a picker (Fluffy / chooser) | Medium | `ACTION_OPEN_DOCUMENT`, not `GET_CONTENT` (section 18); fallback path `/sdcard/Movies` |
| App list incomplete on Android 11 | High without `<queries>` | The `<queries>` block in the manifest (11.2) |
| Weather does not load (no network, limit, API changed) | Low | Cache, timeouts, no dialogs, field and error checks (4.5) |
| Input switching does not work on the TCL | Medium | `setPackage("com.tcl.tv")`, fallback to the system selection, screenshot check at M3 |
| A chooser when switching input (the channel URI opens Fluffy) | Medium | `setPackage` to `com.tcl.tv`; without a package only on error |
| The Home intent does not work on the TCL | Low | Intent filters from CleanYourTV (HOME + COMMON_HOME + DEFAULT); check at M0 |
| The build needs compileSdk 36 and AGP 8.13 (checked, 11.5) | Known | Docker image with `platforms;android-36`; `targetSdk` stays 34 |
| Licences (Couchy GPLv3, Open-Meteo CC BY, Material Icons Apache, Outfit OFL) | Medium | Section 1, our own wording, `NOTICE`, attributions in "About" |
| The Docker build on a Mac is slow | Low | A native build with JDK 17 + SDK 36 as the fallback |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when switching between Docker and native builds | Medium | One key via `HOST_KEY_DIR`, see `tools/docker-build.md` |
| Aerial streams fail (Apple TLS chain, network) | Medium | Platform TLS only; skip Apple entries if they fail, keep Amazon; poster without a network (6.3, RISKS 45) |
| A TV reboot returns HOME to Couchy or to the stock launcher | Medium | Check after reboot (section 20). We do not change the firmware; we only observe and record |
| The remote service runs all the time and uses memory | Low | One key subscription, PSS measurement at M7 |
| After a forced stop, the TCL brings back its own HOME | Medium | We observe and document it; we do not change the firmware |
| Rollback does not work without a recorded state | Medium | The record is made before the first change (section 11.8) |

---

A detailed review of the problem cases and how to handle them: [docs/RISKS.md](RISKS.md). This is a reference, not requirements.

## 17. Sources

**Source code for reference (submodules, `sources/`, removed after implementation, section 1.1):**

- CleanYourTV: `cleanyourtv/AGENTS.md:147–148` (rules), `cleanyourtv/docs/launcher.md`,
  `cleanyourtv/launcher/app/build.gradle` (dependencies and R8), `cleanyourtv/launcher/app/src/main/AndroidManifest.xml`
  (intent filters, `<queries>`, `VIEW_INPUTS`), `cleanyourtv/launcher/app/src/main/res/values/config.xml`
  (`dock_row:310`, `tile_menu:409`, `columns:421`, `show_inputs_pill:377`, `inputs_connected_only:383`,
  `inputs_cec_state_only:388`), `cleanyourtv/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`
  (grid 600–700, `onTileFocused` 1818–1900, Inputs 3228–3262), `AppAdapter.java` (scan 266–320,
  banners 362–400, sizes 100–160, `cornerRadius` 899), `DockDecoration.java`, `InputFold.java`, `RemoteKeys.java`
  (425–470, channel URI), `GridNavigation.java`, `docker/Dockerfile`, `docker-build.sh`, `tools/tv.py`
  (`--screenshot`, `TV_HOST`), `ICONS-LICENSE-FLUENT-EMOJI.txt`, `res/FONT-LICENSE-OFL.txt`.
- Couchy: `couchy-launcher/app/build.gradle.kts` (media3 1.4.1, minSdk 21), `AndroidManifest.xml` (INTERNET,
  KILL_BACKGROUND_PROCESSES, REQUEST_DELETE_PACKAGES, LEANBACK_LAUNCHER), `ui/LauncherScreen.kt` (ICON_SIZES 98,
  `fitRow` 179, `VideoWallpaper` 1359–1520, ScrimTop 118–121, `fadeBrush` 766–785, `LoadingScreen` 1524–1545,
  `GridSection` 978–1000, padding 991, gap 998), `ui/AppCard.kt` (focus 1.10, shadow 8 dp, border 2 / 3 dp, sheen 10 / 14 %),
  `ui/SettingsSheet.kt` (`SettingsScreen` 90, width 440 dp 186, slide 260 ms 196–199), `ui/Menus.kt` (menu 54–59),
  `ui/StatusBar.kt` (padding 40 dp, buttons 40 dp, icons 22 dp), `ui/Theme.kt` (Aurora, `tileColor`),
  `data/AppRepository.kt` (scanDispatcher 79–80, banner 94, cache name 124, `prepareToDraw` 155, icon 128 px ~146), `data/Config.kt`
  (`h24=true`, `scrimMode=1`, `layout=2`).

**Public sources (checked 2026-11-13):**

- Open-Meteo, API documentation, parameters and WMO codes: https://open-meteo.com/en/docs (WebFetch)
- Open-Meteo, geocoding: https://open-meteo.com/en/docs/geocoding-api (WebFetch)
- Open-Meteo, terms of use, limits, CC BY 4.0: https://open-meteo.com/en/terms (WebFetch)
- Open-Meteo, live responses: `curl` to `api.open-meteo.com/v1/forecast` and `geocoding-api.open-meteo.com/v1/search`
  (2026-11-14 00:15 Berlin time; example coordinates from the documentation, not the user's data)
- Media3, stable versions and dates: https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml
  (WebFetch); release list https://developer.android.com/jetpack/androidx/releases/media3 (WebFetch):
  1.11.1: 2026-09-10, 1.11.0: 2026-08-05, 1.10.1: 2026-05-12, 1.12.0-beta01: beta
- RecyclerView and DynamicAnimation, latest stable: `dl.google.com` maven-metadata (WebFetch):
  recyclerview 1.4.0, dynamicanimation 1.1.0
- Material Icons, Apache-2.0 licence: `github.com/google/material-design-icons/LICENSE` (WebFetch)
- Outfit, SIL OFL 1.1: `res/FONT-LICENSE-OFL.txt` in CleanYourTV

**Not yet verified, marked "check on the TV":** HDMI switching through `TvContract` with `com.tcl.tv`;
whether `ic_input.xml` works as the "Inputs" icon; the black-frame time at cold start;
`set-home-activity` on the TCL.

---

## 18. The user's device (checked over ADB at <TV_IP>, read-only)

Commands: `adb connect <TV_IP>:5555`, `getprop`, `/proc/meminfo`, `wm size/density`, `pm list features`,
`cmd package resolve-activity` (HOME, OPEN_DOCUMENT, GET_CONTENT, VIEW channel), `dumpsys media.player` (codecs),
`dumpsys tv_input`, `pm list packages`, `ls /sdcard`. Nothing was changed or installed. `adb disconnect`
after each session.

| Parameter | Value | What it means for the plan |
|---|---|---|
| Manufacturer / model | TCL, "Smart TV", `G03_4K_US_NF` | No vendor-specific HOME hacks are needed. The standard HOME filter (check at M0). |
| Android | 11, SDK 30 (`RP1A.200622.001`) | `minSdk 26` fits. |
| CPU / ABI | Realtek `rtd288o`, **only `armeabi-v7a`** | Media3 and Views are Java, so they fit. There are no native libraries in the APK. |
| RAM | `MemTotal` 2 097 152 kB (~2 GB), `MemAvailable` ~480 MB | The budgets in section 12.3 are realistic. |
| Screen | Android sees **1920×1080 px** (the only mode, `1920x1080@60`), density 320 → **960×540 dp**. From the model and `sys.display-size=3840x2160` the panel is 4K; the TV settings are set to 4K (checked by the user). SurfaceFlinger composites at 1920×1080. | The layout is in dp. Most likely Android renders at 1080p and the TV upscales to 4K. This cannot be confirmed without the firmware. 4K video does not need to be decoded. |
| Features | `leanback_only`, `leanback`, `wifi`, `wifi.direct` | `leanback` in the manifest. No touchscreen. |
| Video decoder | `OMX.realtek.video.decoder` (hardware): AVC up to High 5.1, HEVC, AV1, 3GPP | The 1080p H.264 from section 6 is decoded in hardware. |
| HOME now | `com.conreo.couchytv/.MainActivity` (Couchy 1.0.6) | Couchy is the reference for speed and look. |
| Files on `/sdcard` | `Alarms`, `Android`, `Audiobooks`, `DCIM`, `Documents`, `Download`, `Movies`, `Music`, `Pictures`, `Podcasts`, `Ringtones`, `Notifications`, and two user files in the root | `Movies` is the natural place for video wallpapers. The two user files are not ours; we do not touch them. |
| File picker | `ACTION_OPEN_DOCUMENT`, `video/mp4` → `com.alphainventor.filemanager/.activity.PickerActivity` (isDefault) | A system file picker exists (Fluffy). We use it (6.1). |
| `ACTION_GET_CONTENT`, `video/mp4` | `ResolverActivity` (the app chooser) | Do not use. |
| Channel input (`VIEW`, `vnd.android.cursor.item/channel`) | `com.tcl.tv/.TVActivity` (isDefault), without a URI | Input switching: first `setPackage("com.tcl.tv")` (7.7). With the URI: not checked. |
| Installed | 127 packages, 10 third-party (`pm list packages -3`) | There is no load from extra packages. |
| Inputs (`dumpsys tv_input`) | 4 HDMI ports in the hardware. In `TvInputManager`: HDMI 1–3, state 1 (connected-standby); HDMI 4 is not registered. A passthrough input of type 3 (`<HW_ID>`, state 0). `com.tcl.tvinput`, `com.tcl.channelplus`. | The "connected only" filter is needed (7.7). |

**Conclusions for the plan:**

1. Section 13, item 1 is closed.
2. The reference point for speed is Couchy on this same TV (section 12.3, OPEN-QUESTIONS Q7).
3. Installation: `adb install -r` of an APK with no native code (it fits armeabi-v7a).
4. Choosing a video: through `ACTION_OPEN_DOCUMENT` (Fluffy); the fallback path is `/sdcard/Movies`.

---

## 19. Home screen mockup

File: [docs/screen-mockup.html](screen-mockup.html). It opens in a browser and scales to the window.

What is shown: the clock and date at the top left; round icons at the top right (inputs, network, settings) with no labels; the weather and city on the left under the clock; at rest, the bar sits at the bottom edge and the next row peeks up from below; the down arrow expands the grid and the up arrow brings it back; the focus ring; one hidden tile (60 % mask); the "Settings" and "Inputs" panels on the right (440 dp).

Controls: arrow keys move focus across the grid; `S` opens settings; `I` opens inputs; `Esc` closes the panel.

The weather icons are schematic; the final icons follow sections 5.4 and 4.4.

---

## 20. What to check on the TV before M1

Checklist for the next iteration:

- [ ] Home intent: the launcher appears in the Home list, and "Always" is remembered.
- [ ] At rest the bar is visible and the next row peeks up from below (18 dp); on Down the grid expands (`rows_below_dock` = 0).
- [ ] `queryIntentActivities` returns apps (with and without `<queries>`).
- [ ] `getDrawableForDensity` for the banner: the Couchy banner or another app's banner is visible, and square banners fall back to the icon.
- [ ] Switching HDMI 1 → HDMI 2 through `TvContract` with `setPackage("com.tcl.tv")`: the picture changes.
- [ ] `ACTION_OPEN_DOCUMENT` opens Fluffy; the video is chosen and plays.
- [ ] Black frame at cold start with the chosen video: `screenrecord` (6.2).
- [ ] The Docker build passes with AGP 8.13.2, Gradle 8.13 and compileSdk 36 (11.5).
- [ ] `ic_input.xml` and `ic_picture.xml` look right (OPEN-QUESTIONS Q4, Q5).
- [ ] The state record was made before installation (section 11.8).
- [ ] After a TV reboot: the Home button opens our launcher. Check: `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` (read-only).
- [ ] After a reboot `launcherx` is still disabled: `adb shell pm list packages -d`.
- [ ] After a reboot `enabled_accessibility_services` matches the record (only `flar2.homebutton` before M7; our service after M7): `adb shell settings get secure enabled_accessibility_services`.
- [ ] The rollback `tools/restore.sh` returns HOME to Couchy; `launcherx` stays disabled.

