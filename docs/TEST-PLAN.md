# Test plan: the TV, 2026-11-17

One session on the TCL G03 (Android 11) checks everything built since alpha 4 that has run only on the emulator, then
the open TV checks of [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md) that need someone at the TV. Nothing here is checked on the
emulator.

## What is under test

| Commit | Change | Checked so far |
|---|---|---|
| `563ca42` | Home continues the video where it left it (D113) | Emulator: the video file. TV: never |
| `2b60730` | The screensaver gradient redraws 10 times a second | Emulator: about 9 frames a second |
| `487312d` | The aerials list is parsed once | Emulator: the counts and playback |
| `0f7dc89` | Tile pictures are hardware bitmaps (GPU only) | Emulator: both load paths, the Apps screen |
| `569039f`, `ecea170` | Screensaver and the Home ↔ screensaver hand-over (D112) | TV: aerials and the hand-over once, by eye; the real timeout never |
| `d683989` | Home makes itself opaque again on resume | TV: installed; the cause never reproduced |

## Who does what

- **C**: Claude over ADB: install, key presses with `input keyevent`, logs, `dumpsys`, screenshots.
- **Y**: you, at the TV: the real remote, judging motion by eye, the router, the TV's language.
- `screencap` and `screenrecord` cannot see the video plane on this TV (T18). Anything about how video *looks* is **Y**.
  Whether video *plays* is checked by **C** from the log and from `dumpsys media.resource_manager`.

A check passes only if every expected result holds. A failure is written down with its log and the session goes on;
nothing is fixed during the session unless it blocks the next checks.

Time: about 3 hours with you there for about 1 hour (sections 6, 7 and the "Y" rows), plus section 5.7 running by
itself.

## 0. Before starting

| # | Step | Who | Expected |
|---|---|---|---|
| 0.1 | `adb connect <TV_IP>:5555`; if it fails with the port open: `adb kill-server; adb start-server` and connect again | C | `device` in `adb devices` |
| 0.2 | Record the TV state: `TV_HOST=<TV_IP> tools/record.sh` (read-only, OPERATIONS 3) | C | A new dated record; the old ones are kept |
| 0.3 | Note the current settings to put back at the end: wallpaper type, aerial collection and themes, dimming, edge dimming, labels | C | Written in the results table |
| 0.4 | Build at `main`'s head: `tools/docker-build.sh launcher assembleRelease`. The Docker key matches the TV's build; a local debug build cannot update it | C | `app-release.apk`; its SHA-256 noted |
| 0.5 | Build the "before" APK for the memory comparison: check out `487312d`, build the same way, copy the APK to the scratchpad, check out `main` again | C | Two APKs, before and after the hardware bitmaps |

## 1. Memory baseline and the hardware bitmaps (`0f7dc89`)

Same conditions for both runs: wallpaper Gradient (no video decoder in the numbers), Home on screen, nothing touched.

| # | Step | Who | Expected |
|---|---|---|---|
| 1.1 | Install the "before" APK; `am force-stop com.evg4b.justmytv`; press Home; wait 30 s; `dumpsys meminfo com.evg4b.justmytv` three times, 10 s apart | C | Note TOTAL PSS, Native Heap, Graphics. Note how many apps are installed (Settings → Apps) |
| 1.2 | Install the new APK; repeat 1.1 exactly | C | Native Heap lower than in 1.1 by roughly 0.35 MB per app with a banner. TOTAL PSS not higher |
| 1.3 | All tiles show their banner or icon with rounded corners; the focused tile grows; names under the tiles as set | C screenshot | As before the change |
| 1.4 | `logcat -d \| grep -iE "hardware bitmap\|FATAL"` | C | Nothing |
| 1.5 | Settings → Apps: every app has its picture in the list, hidden ones too | C screenshot | Pictures present |
| 1.6 | Rows below the bar: scroll down until rows go under the clock (the TopFade layer turns on), then up | C keys, screenshot | Rows fade out under the header, no black boxes, no missing tiles |
| 1.7 | Move mode on a tile (long OK on the remote, then Move): move it two places, OK; then Back on another move | Y, C watches the log | The coral ring follows, the order is kept / restored, no crash |
| 1.8 | Tile menu → Change image on one app, pick a 16:9 picture; then Reset image | Y picks the file | The tile shows the picture at once; Reset brings the app's banner back |
| 1.9 | Memory trim: open SmartTube, `am send-trim-memory com.evg4b.justmytv BACKGROUND`, press Home | C | Tiles come back within a second; no blank tiles left; the wallpaper is back |
| 1.10 | Grid smoothness after the change: `dumpsys gfxinfo com.evg4b.justmytv reset`, 30 D-pad moves across the grid, `dumpsys gfxinfo` again | C | Missed frames no worse than T16 (3 of 250); GPU median about 12 ms |

## 2. Video continues where Home left it (`563ca42`, D113)

| # | Step | Who | Expected |
|---|---|---|---|
| 2.1 | Wallpaper Video with the test clip. `logcat -c`. Home 20 s → SmartTube 5 s → Home 8 s → SmartTube 3 s → Home | C | Log `Wallpaper: stopped at N ms` at each leave; each N is the previous N plus the time on Home (modulo the clip's length), never back near 0 |
| 2.2 | Same as 2.1 with Aerials | C | The same clip on return: the positions grow as in 2.1, and `dumpsys media.resource_manager` shows one decoder only while Home is shown |
| 2.3 | Watch three returns to Home with Aerials | Y | Black, then the same scene fades in where it was. No gradient flash, no second fade, no blink after the fade |
| 2.4 | Leave Home in the last seconds of an aerial (watch for the fade to black), come back | Y, C log | A different clip plays (an aerial with under 5 s left is not resumed) |
| 2.5 | Change the wallpaper (Aerials → Video → Aerials) in Settings | C | The new type starts from its beginning; no `stopped at` value carried into another file |
| 2.6 | Back from a video app (SmartTube playing something) | Y | Home's video goes on; SmartTube's playback was not disturbed when it was opened |

## 3. Aerials list parsed once (`487312d`)

| # | Step | Who | Expected |
|---|---|---|---|
| 3.1 | Settings → Wallpaper → Aerials: the clip count with Collection All / Apple / Amazon | C | 251 / 139 / 112 |
| 3.2 | Themes: turn three themes off one by one, then on again | Y | Each toggle and the count under it react at once, no stutter |
| 3.3 | Leave the Themes screen: the playlist follows | C log | A new clip from the chosen themes starts once |
| 3.4 | Aerials still play after a cold start (`am force-stop`, Home) | C | `ExoPlayerImpl: Init` and a revealed frame (resource_manager shows the decoder) |

## 4. Screensaver gradient at 10 frames a second (`2b60730`)

| # | Step | Who | Expected |
|---|---|---|---|
| 4.1 | Wallpaper Gradient. Settings → Screensaver → Preview; `dumpsys gfxinfo com.evg4b.justmytv reset`; 10 s; `dumpsys gfxinfo com.evg4b.justmytv` | C | About 100 frames (at most 120) |
| 4.2 | Watch the preview for 2 minutes, through at least one colour change | Y | Moves smoothly; no visible steps in the turn or in the colours; no banding stripes (D83) |
| 4.3 | Same frame count in the real screensaver (5.1 started with Gradient) | C | About 100 frames in 10 s |

## 5. The screensaver on the TV (D112, SCREENSAVER 7)

| # | Step | Who | Expected |
|---|---|---|---|
| 5.1 | `settings get secure screensaver_components` | C | `com.evg4b.justmytv/.Screensaver`. If not, **you** run the `settings put` command (SCREENSAVER 1); C does not change it |
| 5.2 | Start now: `am start -n com.android.systemui/.Somnambulator`, once per wallpaper type: Gradient, Picture, Video, Aerials, Photos (if an Unsplash key is set) | C, Y watches | Gradient and Picture → the animated gradient; Video → the same file; Aerials → aerials; Photos → the photo with its credit. Clock, date and weather bottom left |
| 5.3 | Hand-over, Aerials: Home on screen, start the screensaver, wait 30 s, press OK | C log, Y watches | `video handed over, no restart` both ways; resource_manager keeps one client; on screen the scene does not restart or blink, only the clock block fades in and out |
| 5.4 | Wake keys: OK, Back, Home, a number key, from the screensaver started over SmartTube and over Home | Y | Each returns to the app that was in front; nothing is launched by the key |
| 5.5 | The real start: leave the TV idle for the TV's own screensaver delay (it is not changed for the test) | Y / C log | Ours starts by itself |
| 5.6 | Aerials with no internet at the start: the router's internet unplugged, start the screensaver | Y | The animated gradient, no error; aerials start when the internet is back |
| 5.7 | Long run: Gradient screensaver for 1 hour; `dumpsys meminfo` every 15 min | C | PSS flat (no steady growth); still about 10 frames a second at the end |
| 5.8 | Memory with Aerials in the screensaver after 10 min | C | PSS near Home's 69 MB with video (T19), not double |
| 5.9 | One player: `dumpsys media.player` / resource_manager while the screensaver plays over Home | C | One ExoPlayer decoder, not two |

## 6. Home and Back always reach Home (`d683989`)

| # | Step | Who | Expected |
|---|---|---|---|
| 6.1 | 15 rounds: open an app from a tile (SmartTube, YouTube, File Manager+, TV Settings, an HDMI input), press Home | C keys, Y for HDMI | Home is in front every time: `dumpsys activity activities` shows `topResumedActivity` = MainActivity |
| 6.2 | Same with Back out of each app | Y | Back leaves the app to Home |
| 6.3 | After the screensaver (section 5), Home and Back | Y | Both work |
| 6.4 | `logcat -d -s Home` over the whole session | C | Any `was translucent: made opaque again` line is noted with what ran before it (the cause is still unknown) |

## 7. Open checks that need you (OPEN-QUESTIONS)

| # | Check | Ref | Who | Expected |
|---|---|---|---|---|
| 7.1 | YouTube and NETFLIX buttons on the real remote; Gear opens the panel; Mute assigned "As before" mutes | T19, T20 | Y | Each does its assignment, once per press |
| 7.2 | Remote buttons service: turn it off and on in Accessibility; the gear hides when Hidden is set | T20 | Y | Settings → Remote buttons says on; the gear hides; Gear toggles the panel |
| 7.3 | Reboot (`adb reboot`, then reconnect): Home after boot; the remote service bound after boot; the video grant still works | T1, T18, T19 | C, Y | Our Home comes up; a remote button works; the video wallpaper plays without choosing the file again |
| 7.4 | No internet (router's internet unplugged, Wi-Fi stays): the crossed-out Wi-Fi icon; the weather shows the cached value with its time | T17, T20 | Y, C screenshot | Icon shown; weather row with "updated HH:mm" |
| 7.5 | TV language Russian: every settings screen, the tile menu, the screensaver date | T19 | Y switches the language, C screenshots | No cut-off text; Cyrillic in the app's font or a clean fallback |
| 7.6 | Banding in the Dusk and Sunset gradients | T19, D83 | Y | No visible stripes |
| 7.7 | 30 minutes of the video wallpaper on Home | T18 | Y glances, C samples PSS | The loop never blinks; PSS flat |

## 8. Quick regression of the rest

One pass, screenshots by C, a look by Y: the focus ring takes the wallpaper's colour (white on a dark one) and changes
smoothly; Edge dimming in all four settings; names under the tiles off: the bar does not ride up; the side panel's
backdrop fades in and out; the weather row and its refresh; the Inputs panel lists HDMI 1–3 and switches; the Photos
credit; the Sunset colours in panels and menus.

## 9. After the session

| # | Step | Who |
|---|---|---|
| 9.1 | Put back the settings from 0.3; the TV keeps the newest build if nothing failed, otherwise the build of `563ca42` | C |
| 9.2 | Write the results into OPEN-QUESTIONS (T-rows closed or updated) and add T21 for this session | C |
| 9.3 | Decide: push, and whether this is alpha 5 | Y |

## Results

| # | Pass / fail | Numbers and notes |
|---|---|---|
| 0.3 | | settings before: |
| 1.1 | | before: PSS / Native / Graphics, apps: |
| 1.2 | | after: PSS / Native / Graphics |
| 1.10 | | frames / missed / GPU median |
| 2.1 | | stopped at: |
| 2.2 | | stopped at: |
| 4.1 | | frames in 10 s: |
| 4.3 | | frames in 10 s: |
| 5.7 | | PSS at 0 / 15 / 30 / 45 / 60 min: |
| 5.8 | | PSS: |
| 6.4 | | |
