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
| D46 | The `sources/` folder is removed after implementation and verification on the TV | The user: delete it once the app is implemented | PLAN 1.1, OPERATIONS 9 |
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
| D57 | Package and application ID `com.justmytv.tvhome` | The owner's handle is on the redaction list, so it does not go into the package name; not `com.gathin.tvhome` (CleanYourTV) | PLAN 11.1, 11.5 |
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
