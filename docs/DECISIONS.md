# Decision log

Each decision is recorded with its date, the options considered, the chosen option, and the reason. If a decision is
revisited, the old entry is not deleted: a new row is added with a reference to it.

Date: 13 November 2026, unless stated otherwise.

## Architecture and build

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D1 | A new standalone launcher, not a PR to CleanYourTV | CleanYourTV forbids network access and video (`AGENTS.md:147–148`), and we need both | PLAN 0, 1 |
| D2 | Java and Android Views, no Compose or Kotlin | As in CleanYourTV: faster start on a weak CPU, fewer dependencies | PLAN 2, 14 |
| D3 | `minSdk` 26, `compileSdk` 34, `targetSdk` 34 | Compatibility and modern APIs. The TV runs Android 11. **Revised by D54** (compileSdk) | PLAN 11.5, BEST-PRACTICES 0 |
| D4 | Dependencies: `recyclerview` 1.4.0, `dynamicanimation` 1.1.0, `media3-exoplayer` 1.11.1 | Latest stable versions, older than two weeks on the check date | PLAN 11.5 |
| D5 | Docker build, debug-key signing, one key via `HOST_KEY_DIR` | As in CleanYourTV: no SDK installation on the Mac | PLAN 11.6 |
| D6 | APK size is not limited | The user: speed matters most | PLAN 0 |
| D7 | No new dependencies for network or images | `HttpURLConnection`, `BitmapFactory`, `org.json` from the platform | PLAN 4.7, BEST-PRACTICES 4 |
| D8 | Records are not used | Technically they work (AGP ≥ 8.1, SDK 34). There is simply no reason to use them | PLAN 9.2, BEST-PRACTICES 7 |

## Home screen

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D9 | The bar is the first row of the grid, one `RecyclerView` | Two lists would mean two focus mechanisms. As in CleanYourTV (`dock_row`) | PLAN 7.2 |
| D10 | At rest the bar sits at the bottom edge, and the next row peeks up by 18 dp | The user: as in the original launcher | PLAN 7.1, 7.2 |
| D11 | Scrolling down expands the grid and the bar moves up | The user: it "expands only when I scroll down" | PLAN 7.1 |
| D12 | There is no title above the bar (`dock_title` is off) | The user: the "Films" heading is not wanted. The name appears under the focused tile | PLAN 0.1, 7.2 |
| D13 | Clock and date at the top left, 52 sp light | The user: as in the original launcher (CleanYourTV `activity_main.xml`) | PLAN 0.1 (row 14) |
| D14 | Icons at the top right, round, 40 dp, no labels, centred in the header | The user: icons without labels, vertically aligned | PLAN 0.1 (row 14) |
| D15 | Weather and city under the clock at the left, nothing at the bottom of the screen | The user: weather moved up under the time | PLAN 0.1 (row 15) |
| D16 | Shading only at the top | There is no weather at the bottom any more | PLAN 0.1 (row 16) |
| D17 | Five columns by default at 960 dp (tile 147×83 dp) | CleanYourTV formula, `tileWidthPx` | PLAN 7.2 |
| D18 | Placeholder icon tiles removed, all tiles are coloured | The user: the "All apps" icon looks terrible | PLAN 7.5 |
| D19 | After returning from an app, focus goes back to the last tile | The user liked the idea. As `LastOpened` in CleanYourTV | PLAN 7.6, 9.1 |
| D20 | Background: video wallpaper, the decoder is released on `ON_STOP` | Based on Couchy's approach, without its code | PLAN 6.1, 6.2 |

## Inputs and remote

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D21 | Input switching stays in the launcher | The user: it is useful and must be kept | PLAN 7.7 |
| D22 | The Inputs button is a round icon without a label | The user: no label, just a normal icon | PLAN 7.7 |
| D23 | Switching through `TvContract`, `setPackage("com.tcl.tv")`, with a fallback to the system picker | `com.tcl.tv` is the default handler on this TV | PLAN 7.7 |
| D24 | The remote uses the launcher's own background service | The user: it is the user's own launcher, so a background service is acceptable | PLAN 7.8 |
| D25 | Home Button (`flar2.homebutton`) is disabled during implementation | Two services must not intercept the same keys | PLAN 7.8, remote-buttons |
| D26 | By default, YouTube opens SmartTube (`org.smarttube.stable`) | The user: remap YouTube to SmartTube | PLAN 7.8, remote-buttons |
| D27 | Buttons can only be assigned to apps, with no parameters | The user: launching apps only | PLAN 7.8 |
| D28 | The power button is not remapped | The user: the power button turns the TV off | remote-buttons |

## Settings

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D29 | The settings root has 8 items, including "TV settings" | The user: access to the TV settings as an option, a rarely needed feature | PLAN 10.1 |
| D30 | Reduced motion is not implemented | The user: not needed | PLAN (no mentions remain) |
| D31 | Rows below the bar (0–3) are configured under "Appearance" | As `rows_below_dock` in CleanYourTV | PLAN 10.1 |
| D32 | The greeting above the clock is off by default | It was not in the user's list | PLAN 10.1 |

## Rollback and TV

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D33 | Rollback follows the CleanYourTV scheme: a state record is written before any change, and restore replays it | The user: rollback as in Clean My TV | PLAN 11.8, OPERATIONS |
| D34 | `launcherx` is never enabled | The user: do not enable it, roll back to the current state | PLAN 11.8, 13.1 |
| D35 | The TV firmware is not modified | The user: no firmware changes at all | RISKS 6, 8 |
| D36 | `launcherx` stays disabled | The Google Home panel is not needed | PLAN 13.1 |
| D37 | `nooklauncher` (the old personal fork) stays disabled | The user: leave it disabled. It is not disabled right now; it is just not HOME | PLAN 13.1 |
| D38 | The `<OTHER_DEVICE>` (a phone) is not touched | The user: it is a phone | PLAN 13.1 |
| D39 | A check after reboot is mandatory before M1 | Without it, HOME is not proven | PLAN 20, OPERATIONS |

## Miscellaneous

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D40 | Weather from Open-Meteo, city entered through geocoding, no geolocation | No key needed, geolocation is unnecessary | PLAN 4 |
| D41 | The remote service is a deliberate exception to "no background services" | The user: it is the user's own launcher | PLAN 14 |
| D42 | Personal values are kept in `private/` and listed in `.gitignore` | The user: documents stay clean, values stay local | README, `private/redactions.md` |
| D43 | Background memory release through `onTrimMemory` | The user: understood, keep it | PLAN 5.6 |
| D44 | ~~No commit or push yet~~ **Revised by D59** | The user has not yet said which branch to use | OPEN-QUESTIONS |

## References

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D45 | `sources/` is reference only. Code may be copied and rewritten, but not imported, called, or referenced by the app | The user: code, resources, and scripts from there must not be used by the app, only as a starting point | PLAN 1.1, RISKS 44 |
| D46 | **Done 2026-11-14 (D98).** The `sources/` folder is removed after implementation and verification on the TV | The user: delete it once the app is implemented | PLAN 1.1, OPERATIONS 9 |
| D47 | When copying, licences are kept: MIT (CleanYourTV), Apache-2.0 (Material Icons), OFL (Outfit). Code from Couchy (GPLv3) is not copied | Licences are mandatory. We write our own code from the description, so GPL does not apply to the project | PLAN 1.1, OPEN-QUESTIONS Q10 |
| D48 | No distribution except builds on the release page. The user: the project is for personal use; a release is publishing builds | Publishing an APK on a release page is distribution. That is why D47 matters: Couchy code is not copied, so GPL does not spread to the project | PLAN 1.1, OPEN-QUESTIONS Q10 |
| D49 | Apple and Amazon aerials are wanted. The list (251 links) is copied as data from `assets/aerials.json`. Videos are not stored, they are streamed when enabled. Trust-all TLS is not copied | The user: these links are needed. The streamed videos belong to Apple and Amazon, see RISKS 45 | PLAN 0, 3, 13 |

## Plan review (13 November 2026)

Decisions taken while fixing the review findings in PLAN v2. None of them changes an earlier decision by the user.

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D50 | Settings root has 8 items everywhere | D29 added "TV settings"; §0 and M3 still said 7 | PLAN 0, 10.1, 15 |
| D51 | Columns (4–7) are the only size setting; `uiScale` is removed | With a fixed column count a scale only changes the gaps, so the two settings fought | PLAN 7.2, 10.1 |
| D52 | Cut: "Close app" menu item, our own `VIEW_INPUTS` activity, weather interval setting (fixed 30 min), "Check video" button | Not asked for. "App details" has Force stop; the `VIEW_INPUTS` activity would catch its own fallback; the API updates every 15 min anyway | PLAN 7.7, 8, 10.1, 14 |
| D53 | Aerials (D49) get their own section 6.3 and go into M4; the remote service (D24) gets milestone M7 | Both were decided but had no milestone | PLAN 6.3, 15 |
| D54 | `compileSdk` 36, AGP 8.13.2, Gradle 8.13, build tools 36.0.0; `targetSdk` stays 34 | AAR metadata: Media3 1.11.1 and 1.10.1 need `minCompileSdk=36`, RecyclerView 1.4.0 needs 35. AGP 8.5.2 cannot compile against 36. AGP 9 is not taken: a new DSL for no gain | PLAN 11.5, 11.6, BEST-PRACTICES 0 |
| D55 | The app scan does not wait for the first video frame; the window background is removed after the first frame | Speed: with no video there is no first frame, and the window background was a fourth full-screen layer | PLAN 6.2, 11.3 |
| D56 | Persistence is split: `LauncherState` (file I/O, `AtomicFile`) and `StateJson` (pure, JVM-tested) | `android.util.AtomicFile` is a stub in plain JVM tests | PLAN 9.2, 12.1 |


## Decisions taken at M0

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D57 | ~~Package and application ID `com.justmytv.tvhome`~~ **Replaced by D95** | The owner's handle is on the redaction list, so it does not go into the package name; not `com.gathin.tvhome` (CleanYourTV) | PLAN 11.1, 11.5 |
| D58 | Own banner and icon: the Aurora gradient with the Material "home" glyph; `poster_default.webp` is generated (diagonal Aurora, 1-bit dither, about 8 KB) | CleanYourTV's `banner_tv.xml` is its Live TV banner (magenta, Live TV glyph), not an app banner | PLAN 11.2, 11.3; OPEN-QUESTIONS T12 |
| D59 | Commit by logical steps and push to `main` | The user's answer to Q2 | OPEN-QUESTIONS Q2 |

## Decisions taken at M1

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D60 | Focus scrolling uses CleanYourTV's fixed rule: a row already on screen stays put; a row above the header stops at a 120 dp ceiling; the bar goes back to its line | The plan's rule pulled rows down to the bar line on Up, which CleanYourTV had found to be a bug | PLAN 2.2 |
| D61 | Bitmaps are dropped at `TRIM_MEMORY_BACKGROUND`, not `UI_HIDDEN` | `UI_HIDDEN` comes after every app launch, so every return to Home would reload all tiles | PLAN 5.6 |
| D62 | The scrim view is the top 20 % of the screen, not full screen; the bar band reaches 36 dp below the tiles to cover the focused label | Full-screen transparent fill cost GPU time; the label sat across the band's edge | PLAN 7.5, 11.4 |
| D63 | Until M3 the header icons open the system screens (inputs picker, Wi-Fi, Settings) | The panels are M3; the icons should not do nothing | PLAN 7.7, 10 |
| D64 | Grid scroll performance is tested after all features are in (OPEN-QUESTIONS T16) | The user's request at M1 | PLAN 15 (M6) |

## Decisions taken at M2, M3 and M5

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D65 | Clock and About open as subsections; the root has 7 items until M4 adds Wallpaper | Clock has three settings, which do not fit one row; a Wallpaper item with nothing behind it would be a dead end | PLAN 10.1 |
| D66 | Settings rows are built in code: on/off rows flip on OK; value rows step with OK, Left and Right | One row type per kind of setting, no XML per screen; Left and Right are the natural keys for a value on a remote | PLAN 10.2 |
| D67 | "Order" in the Apps screen is left out: Move on the tile does it | Two ways to do the same thing | PLAN 9.5 |
| D68 | Move mode puts the tile down on OK or BACK release, with an amber ring while moving | On key down the release would click the tile; the ring shows the mode without a new view | PLAN 8 |
| D69 | Weather is hidden until the first good answer; changing °C/°F drops the cache | Nothing to show without data; a cached °C value labelled °F would be wrong | PLAN 4.7 |
| D70 | "Connected only" hides waiting HDMI ports; AV inputs are under "More" unless "Hide AV" is on | TCL reports empty HDMI ports as connected-standby; AV has no hotplug state | PLAN 7.7 |
| D71 | The header's network icon keeps opening the system Wi-Fi settings | No network screen of our own is planned | PLAN 7.1 |

## Decisions taken at M4

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D72 | All 251 Apple and Amazon aerials are kept; nothing is dropped as 4K | The list has no 4K entry: every Apple link is `SDR_2K_AVC` | PLAN 6.3 |
| D73 | Aerials play as a rolling two-item playlist; a failing clip is replaced by another, and three failures in a row fall back to the poster | One bad link should not end the aerials; a playlist joins clips without a black frame | PLAN 6.1, 6.3 |
| D74 | When an Apple stream fails, Apple is skipped for the rest of the session (not stored) | A TLS chain the TV rejects fails every Apple link; a reboot or an update may fix it | PLAN 6.3, RISKS 45 |
| D75 | The chosen video's first frame is saved as `poster.webp` (RGB_565 in memory) and is the poster for that video | A return to Home shows the clip's own picture, then the clip, instead of the gradient | PLAN 6.2 |
| D76 | The default wallpaper is the gradient (the Aurora poster); dimming is 0 / 20 / 40 / 60 %, default 20, and does not apply to the gradient | No file is needed for the default; the gradient is the theme background too, so the cold start does not change colour; 30 is not on the scale | PLAN 9.1, 10.1 |
| D77 | The "icons appear after the poster" animation (PLAN 6.2 timings) is not done | The grid shows at the first frame; an entrance animation would only delay it | PLAN 6.2 |

## Decisions taken at M6, M7 and the code pass

| No. | Decision | Option and reason | Where in the plan |
|---|---|---|---|
| D78 | Remote buttons are matched by scancode; assignable: Menu, Bookmark, Gear, TV, Numbers, Layers, Grid, NETFLIX, YouTube, TCL CHANNEL, prime video, Volume +/−, Mute | **Revises the "any button except power" decision**: D-pad, OK, Back and Home are left out, since assigning one would lock the user out of the screen that undoes it. Assistant sends no event | PLAN 7.8, remote-buttons.md |
| D79 | An assigned button launches on press and swallows the release; if the app is gone the button works as before | The system's own action must not run as well; a removed app must not make a button dead | PLAN 7.8 |
| D80 | The service shares the assignments with the launcher in memory (same process) and reads `launcher_state.json` when it starts | No second file, no IPC | PLAN 7.8, 9.1 |
| D81 | Tiles are drawn with a `BitmapShader` round rect and rounded drawables, not `clipToOutline` | The outline clip cost about 4 ms of GPU per frame on the TV (M1 measurement) | PLAN 7.3 |
| D82 | `TopFade` keeps its hardware layer only while rows are scrolled up | At rest the layer was a full-screen offscreen pass for nothing | PLAN 7.5 |
| D83 | The default wallpaper and window background are the dithered gradient shape `aurora.xml`; `poster_default.webp` is removed | **Revises D58:** the bitmap held 8 MB of RAM for a gradient; banding to be checked on the TV (T19) | PLAN 6.2, 11.3 |
| D84 | Settings root has 9 items: Wallpaper, Weather, Apps, Appearance, Inputs, Remote buttons, Clock, About, TV settings | Remote buttons (M7) joins the 8 of PLAN 10.1 | PLAN 10.1 |
| D85 | `network_security_config.xml` trusts Apple Root CA (`res/raw/apple_root_ca.pem`, SHA-256 `B0:B1:73:0E…F0:24`) for `sylvan.apple.com` only, together with the system store | Android 11 lacks that root, so every Apple aerial failed TLS on the TV. Other hosts keep the platform defaults; trust-all stays out (D49) | PLAN 6.3, RISKS 45 |
| D86 | The tile menu is measured and opens above the tile when it does not fit below | Under the bar `showAsDropDown` ran off the screen and showed only "Open" (TV check, T17) | PLAN 8 |
| D87 | The bar rests no higher than 128 dp (the header ceiling plus the band's 8 dp); "Rows below the bar" that do not fit are cut at the bottom | With 4 columns and 2 rows below the bar covered the clock (code review, confirmed on the TV) | PLAN 7.2 |
| D88 | The Inputs panel sorts inputs by label | The TV lists HDMI 1, 3, 2 | PLAN 7.7 |
| D89 | The launcher no longer handles `KEYCODE_TV_INPUT` | TCL's own source bar opens for it over anything we show, so our panel only sat behind it (TV check, T17). The Inputs icon stays | PLAN 7.7 |
| D90 | Each tile holder keeps one banner background (`RoundArt` with a swappable bitmap over a dark layer of its own) | A bind allocated two drawables per banner tile, and the banner shared the icon tiles' colour drawable (code review) | PLAN 7.3 |
| D91 | The "Remote button service" row shows On only while the service is bound, and "Turn off and on" when it is listed but not running | On this TV an app update leaves the service listed but unbound until it is switched off and on (RISKS 46) | PLAN 7.8 |
| D92 | Only the Gradient type shows the gradient. Video and Aerials come up from black (or the video's poster frame) and fade in at the first frame; a picture starts black until it is decoded. The window background is black. If aerials cannot play (offline, or every clip fails), the gradient comes back as a still | **Revises D83 (window background):** the user saw the gradient before every aerial, on Home, after the screensaver and at power-on, and it must not mix with the video | PLAN 6.2, 6.3 |
| D93 | Aerials fade between clips: 1 s before a clip ends the black poster fades in over it, and the next clip fades in from black at its first frame (0.9 s), or after 1.5 s at the latest. A user's own video keeps its seamless loop | The user wants a fade from black at the start and through black from one clip to the next; this replaces the cut of the rolling playlist (PLAN 6.3) | PLAN 6.3 |
| D94 | The fade from black waits until the video is playing (not its first still frame) plus 1 s, then runs 1.2 s ease-in-out; the same at every aerial clip change | When the video plane appears, the TV blanks its video window (`VSCClient: setWinBlank`) and switches its picture engine on (AI scene, gamma, white balance, noise reduction) for 0.6–0.9 s after the first frame. Fading at the first frame showed those as two flickers; now they happen behind black (TV log, 2026-11-14) | PLAN 6.2, 6.3 |
| D95 | Package and application ID `com.evg4b.justmytv`; sources in `com/evg4b/justmytv`. The Gradle project, Docker image and Docker volume keep the name `tvhome` | **Revises D57:** the owner chose a package with their handle. A new application ID is a new app for Android: the TV keeps the old one until it is uninstalled, and settings start fresh. The Docker volume holds the signing key, so it is not renamed | PLAN 11 |
| D96 | The weather's place and its "updated" time use the date's style: Outfit Regular, 14 sp, uppercase, letter spacing 0.14, `#A6FFFFFF` | The owner: the city should look like the date beside the clock | PLAN 4.7, 7.1 |
| D97 | The bar has no background band; `BarBand` is removed | The owner: no dark background behind the dock | PLAN 2.2, 7.3 |
| D98 | `sources/` (CleanYourTV at `b121838`, Couchy at `d3dcd70`) and `.gitmodules` are removed, and so is `docs/icons-showcase.html` | The owner asked for it once the app was built and checked on the TV (D46). Nothing referred to the folder, and the icon showcase was only for choosing icons (S4, closed) | PLAN 1.1, OPERATIONS 9 |
| D99 | The app is called **Just My TV Launcher** (label, remote buttons service, About, docs); About also names the source repository and the licence. Internal names stay `tvhome`: the Gradle project, the Docker image and volume, and `tools/tv.py`'s temporary names | The owner renamed "TV Home" to match the repository. The Docker volume holds the signing key (D95), so it keeps its name | PLAN 11 |
| D100 | Aerials get two filters: Collection (All, Apple, Amazon) and Themes (Landscapes, Space and night sky, Cities, Underwater, Wildlife, Abstract and fireplaces), both shown only for the Aerials type. Themes are a `"k"` field added to `aerials.json`, one per clip, set from titles; at least one theme stays on; no match shows the gradient | The owner asked for Couchy's choice of which videos play and chose both the collection and the themes. Couchy's community sets stay out (Q19) | PLAN 6.3 |
| D101 | A Photos wallpaper type: a random Unsplash photo from picsum.photos, changed every 1, 5 (default), 15, 30 or 60 minutes while Home is visible, with the aerials' fade through black; the last photo is kept for the next start and for offline; "Next photo" changes it at once; the author is shown in the wallpaper settings | The owner asked for new Unsplash wallpapers on a timer with the current transition, and chose Picsum over Unsplash's API, which needs a developer key and is limited to 50 requests an hour | PLAN 6.2a |
| D102 | Photos come from Unsplash's API (`/photos/random`, landscape, content filter high) with the owner's own access key, typed on the TV and kept in the state file; each photo also calls its `download_location`; intervals 5, 15, 30 and 60 minutes. **Revises D101's source** (picsum.photos) | Picsum was down for hours; the owner asked for Unsplash directly with a key they provide. A new key allows 50 requests an hour, and each photo takes two | PLAN 6.2a |
| D103 | A remote button can be set to "Disabled": the service consumes its press and release and does nothing, in every app. Stored as `"-"` in the `remote` map | The owner asked for a way to switch a button off entirely. The list still leaves out D-pad, OK, Back, Home and power, so the TV cannot be locked | PLAN 7.8 |
| D104 | The wallpaper settings show only the rows of the chosen type: "Choose picture…" for Picture, "Choose video…" for Video, the Unsplash rows for Photos, Collection and Themes for Aerials; Dimming is hidden for the gradient, and each type's note comes last | The owner: the video and picture choosers must appear only for their own type | PLAN 10.1 |
| D105 | Review fixes 1–8: Picture or Video with no file shows the gradient (and a picture that cannot be read); the wallpaper type and the aerial Collection apply 0.8 s after the last step; theme toggles apply once, when Themes is left or the panel closes; a new photo interval keeps the photo; "Next photo" shows Loading…; the photographer and Unsplash show under the round buttons on Home (Unsplash's API guidelines); Back in Move mode cancels the move and OK saves it; Hide shows where to get the app back | The owner chose these eight from the UI review | PLAN 6, 8, 10.1 |
