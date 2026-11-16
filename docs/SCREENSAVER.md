# Screensaver: plan

A screensaver (an Android "dream") that shows Home's own wallpaper with the clock, the date and the weather, and nothing
else: a calm, clear screen for when the TV is idle, in the launcher's look. It is part of the launcher app and reads the
launcher's settings, so it needs no setup of its own. Decided in D112.

Status: **built** (steps S1–S6). Facts marked *verified* were read on the TV over ADB on 2026-11-16. Checked on the TV
through Settings → Screensaver → Preview: the overlay, the animated gradient, and the hand-over of the video decoder
from Home to the screensaver and back. Not yet checked: the real screensaver started by the TV after its delay (it
must first be chosen in TV settings) and the Unsplash and video-file wallpapers in it.

## 1. Is it possible

Yes.

- The TV runs Android TV 11 with the standard screensaver (`DreamService`). *Verified:* `screensaver_enabled=1`,
  `screensaver_activate_on_sleep=1`, the current one is Google's Ambient
  (`com.google.android.apps.tv.dreamx/.service.Backdrop`).
- Screensavers from other apps are offered: the TV lists `com.android.dreams.basic/.Colors`, Google's Backdrop and
  TCL Gallery's `ScreensaverService`. *Verified* with `cmd package query-services -a android.service.dreams.DreamService`.
  Ours appears in the same list once the app declares one.
- It starts after the TV's own "Screen saver" delay. *Verified:* `screen_off_timeout=600000` (10 minutes).
- An app cannot choose the screensaver for the user (`screensaver_components` is a secure setting) and this TV has no
  `android.settings.DREAM_SETTINGS` page to open. *Verified:* no activity answers that action. The user picks it once in
  TV Settings → Device Preferences → Screen saver.
- The chosen screensaver can be started on demand: `com.android.systemui/.Somnambulator` is exported with the
  `DESK_DOCK` category. *Verified.* This serves the "Start now" row and the tests on the TV.

## 2. What it shows

The screensaver mirrors Home's wallpaper choice (Settings → Wallpaper). There is no separate wallpaper setting.

| Home's wallpaper | The screensaver shows |
|---|---|
| Gradient | The **animated gradient** (section 3) |
| Picture (a file the user chose) | The **animated gradient**: the user's own pictures are left out (owner's decision, D112) |
| Photos (Unsplash) | The Unsplash photo on screen at Home, changed on the same timer through the same fade through black, with the photographer and Unsplash credited as on Home (Unsplash guidelines, D105) |
| Video (a file the user chose) | The same video, looped, coming up from its poster frame as on Home |
| Aerials | The aerials, with the same collection and themes; offline or with no matching clip, the animated gradient instead of Home's still one |

On top of the wallpaper, and nothing else:

- **Clock**, large, in Outfit, 12 or 24 h as in Settings → Clock.
- **Date** under it, when Settings → Clock → Date is on.
- **Weather** under the date, as at Home: the icon, the temperature, the city; hidden when the weather is off or there is
  no city.
- **Photo credit** at the bottom right, only for Unsplash photos.
- A soft dark gradient behind the text (the `scrim_bottom` drawable), so white text reads over any picture.
- **Dimming** as set in Settings → Wallpaper → Dimming, applied as at Home (not to the gradient).

Placement: the clock block sits at the bottom left, 7 % from the left edge and 12 % from the bottom. Every 5 minutes it
moves by up to 3 % of the screen (a small random drift, eased over 2 seconds), so the same pixels are not lit for hours.
No tiles, no buttons, no focus ring, no greeting.

Start and end: the screen fades in from black over 1.2 s, as the video reveal does. Any button wakes the TV; Android
closes the screensaver and returns to whatever was on screen (`setInteractive(false)`).

## 3. The animated gradient

A slow, ever-changing colour field, like an ambient lamp: no shapes, no motion of objects, just colour drifting.

- **Palettes**, three colours each, top-left to bottom-right, from the Sunset scheme (D110) and around it:
  - Sunset: `#E4577F` → `#F47D56` → `#FFBE8E`
  - Dusk: `#2A1533` → `#7E3459` → `#D9714C`
  - Lagoon: `#0F3A5C` → `#1D7A8C` → `#7FD1C7`
  - Aurora: `#13547A` → `#2B825B` → `#80D0C7`
  - Lavender: `#2B2150` → `#6A4C93` → `#E0A3C8`
- **Motion**: each colour stop eases into the next palette's over 40 s, holds for 20 s, and moves on, in a fixed order that
  loops (a full cycle about 5 minutes). The gradient's angle turns slowly, one full turn in 4 minutes, so light seems to
  move across the screen.
- **Rendering**: one custom view, `AmbientGradient`, draws a single `LinearGradient` over the whole screen with a
  dithered `Paint` (against banding, as `dusk.xml` is dithered, D83). Colours are blended with `ArgbEvaluator` from a
  clock, not accumulated, so a dropped frame never drifts the colours. It redraws at most 30 times a second through
  `postInvalidateOnAnimation` and only while attached and shown: the TV's GPU is slow (PLAN 11.3), and the change per
  frame is far below what 60 fps would show.
- Brightness is kept moderate (the screen is often on in a dark room): the palettes above already avoid white, and the
  dimming setting does not apply to the gradient, as at Home.

## 4. How it is built

### 4.1 New files

| File | What it is |
|---|---|
| `Screensaver.java` | `extends DreamService`. `onAttachedToWindow`: `setInteractive(false)`, `setFullscreen(true)`, `setScreenBright(true)`, inflates `ScreensaverView`. `onDreamingStarted` reads the state and starts the wallpaper; `onDreamingStopped` stops it |
| `ScreensaverView.java` | The whole screen: wallpaper, overlay, drift. Hosted by the dream and by the preview |
| `res/layout/screensaver.xml` | `SurfaceView` (video), `ImageView` (poster, photos), `AmbientGradient`, dim view, bottom scrim, the clock block, the credit |
| `AmbientGradient.java` | The animated gradient view (section 3) |
| `ScreensaverPreview.java` | The preview activity (section 5) |

Manifest: a `<service>` for `Screensaver` with `android:permission="android.permission.BIND_DREAM_SERVICE"` and the
`android.service.dreams.DreamService` intent filter; label "Just My TV Launcher", so that name is what the TV's list
shows. **Changed while building:** no `settingsActivity` metadata. It can only name an activity to start explicitly, and
an explicit start of Home makes a second launcher (D107).

### 4.2 Code moved out of MainActivity, shared by Home and the screensaver

Home's wallpaper logic lives in `MainActivity` today (`applyWallpaper`, `startVideo`, `playAerials`, `applyDim`,
`showPhotoCredit`, the network callback for aerials, about 150 lines). It moves into one class, so both screens behave
the same and a fix lands in both:

- **`WallpaperController`** (new): owns one `VideoWallpaper`, one `PhotoWallpaper`, the aerials list and the
  "Apple failed" flag. `apply(StateJson)`, `start()`, `stop()`, `onResume()`, `onPause()`, `networkUp()`. Constructed with
  a `Context`, the `SurfaceView`, the poster `ImageView`, the dim view and a callback for the photo credit.
  `MainActivity` keeps calling the same things it calls now, through it.
- **`HeaderBinder`** (new, static methods): fills the clock format, the date's visibility and the weather row from a
  `StateJson`; `MainActivity.applyClock` and `showWeather` call it, and so does `ScreensaverView`.
- **`ScreensaverMode.of(StateJson)`** (new, pure): maps Home's wallpaper to the screensaver's (the table in section 2).
  JVM-tested.

`VideoWallpaper`, `PhotoWallpaper`, `Aerials`, `WallpaperFiles`, `WeatherParser`, `WeatherCode` are reused as they are.

### 4.3 One player at a time

The TV has 2 GB of RAM and one video decoder path worth using; Home and the screensaver must never play at the same
time.

- Home already stops its video and photos in `onStop` (PLAN 6.1). If Android stops Home when a screensaver starts over
  it, nothing more is needed. This is the first thing to check on the TV (S2).
- **As built:** `WallpaperController` keeps a static "current" one. Starting a controller stops the one playing and
  remembers it; when the new one stops and the old one's screen was never hidden (Home under the screensaver), the old
  one starts again. If Home was stopped meanwhile, it simply starts in `onStart` as before. *Verified* with the preview:
  Home's decoder was released before the preview's started, and Home's came back when the preview closed.
- Photos: both screens use the same `photo.jpg` / `next.jpg` files; since only one controller plays at a time, only
  one `PhotoWallpaper` runs, with no extra guard.

## 5. Shared settings

The screensaver is part of the launcher app and runs in its process, so it reads the same `launcher_state.json` through
`LauncherState` and `StateJson.parse`, each time it starts. There is nothing to copy or sync: a change in the launcher's
settings shows the next time the screensaver starts.

| Setting (launcher) | Used by the screensaver for |
|---|---|
| Wallpaper → Type | Which wallpaper (section 2) |
| Wallpaper → video, aerial collection and themes, photo interval, Unsplash key, Dimming | The same as at Home |
| Clock → 24-hour, Date | The clock format and the date line |
| Weather → on/off, city, °C/°F | The weather line |
| The cached forecast (`weather.last`, `fetchedAt`) | Shown at once; refreshed when older than 30 minutes, as at Home, and saved back so Home has it too |

The screensaver adds **no new stored settings**. The only write is the weather refresh: it re-reads the file, sets the
two weather fields and writes it through `LauncherState` (AtomicFile, PLAN 9.2), so a change made at Home a moment
earlier is never lost.

**Settings → Screensaver** (new section in the launcher, information and two actions, no options):

- A line on what it shows: "Home's wallpaper with the clock and the weather."
- **Status**: "In use" or "Not chosen", read from `Settings.Secure` `screensaver_components` (reading is allowed).
- **How to turn it on**: "TV Settings → Device Preferences → Screen saver → Just My TV Launcher. It starts after the TV's
  screen-saver delay."
- **Start now**: starts `com.android.systemui/.Somnambulator`, which shows the screensaver chosen in TV settings. While
  ours is not chosen, this row opens the preview instead.
- **Preview**: a full-screen activity hosting `ScreensaverView`, closed by any button; works whatever screensaver the
  TV uses.

## 6. Steps

| Step | What | Done when |
|---|---|---|
| S1 | Move the wallpaper logic into `WallpaperController` and the clock and weather into `HeaderBinder`; no change in behaviour | Home looks and behaves as before on the TV with each of the five wallpaper types; unit tests pass |
| S2 | A bare `Screensaver` with a black screen and the clock, registered in the manifest | It is listed in TV Settings → Screen saver; `Somnambulator` starts it; any button wakes; *answer recorded*: does Home get `onStop` under it |
| S3 | `AmbientGradient` and `ScreensaverMode` | The gradient drifts smoothly on the TV with no banding and no dropped-frame jumps; mapping tests pass |
| S4 | Wallpapers in the screensaver through `WallpaperController`, the one-player rule, the photo guard | Each Home wallpaper shows its screensaver counterpart (section 2); never two players (checked in `logcat` and `dumpsys media.player`) |
| S5 | Overlay: clock, date, weather, credit, scrim, drift, fade-in | Matches section 2; weather refresh saved back |
| S6 | Settings → Screensaver, preview activity, strings (English and Russian), user guide section, decision and README updates | Rows work on the TV; docs updated |

## 7. Tests

**Unit (JVM):** `ScreensaverMode.of` for each wallpaper type, including Picture → gradient and an unknown type →
gradient; the palette blend at fixed times (start, mid-fade, hold, wrap-around).

**On the TV** (T-numbers to be added to OPEN-QUESTIONS when built):

- Start: `adb shell am start -n com.android.systemui/.Somnambulator` with ours chosen; and once by waiting out the
  10-minute delay.
- Each wallpaper type, including aerials with the network off at start, and Photos crossing a timer change.
- Wake with OK, Back, Home and a number key: the TV returns to the app that was in front, video apps included.
- Memory: PSS with aerials playing in the screensaver, against Home's 69 MB (T19).
- One player: no second `ExoPlayer` while Home is behind the screensaver.
- Several hours of the gradient: no growth in memory, no stutter.

## 8. Risks and open questions

- **Home under the screensaver** may stay started (section 4.3); handled by the hook either way, but the answer decides
  how much code S4 needs.
- **SmartTube and other players** keep the screen awake while they play, so no screensaver starts over a playing video.
  That is Android's normal behaviour, not something to fix.
- **TCL updates** could replace the screensaver list or Google's Ambient could be forced back by a system update;
  nothing to do but re-choose it.
- **Aerials at night** need the network; offline they fall back to the animated gradient, which needs nothing.
- **Burn-in**: the panel is LCD, so the drift is a precaution, not a requirement; it stays small so it is not noticed.

## 9. Not in scope

- A wallpaper choice of its own for the screensaver (it mirrors Home by design).
- Showing the user's own pictures in the screensaver.
- Using the animated gradient on Home too (possible later: `AmbientGradient` would drop into `activity_main.xml`).
- Choosing the screensaver or its delay from the launcher (Android allows that only to system apps).
