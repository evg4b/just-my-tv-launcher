# Open questions

This file lists everything that is not yet decided. Each question states what it blocks and what is needed to close it.

Blocks **M0**: the skeleton cannot start. Blocks **M1**: the grid cannot start. **Does not block**: decided as we go.

## Decisions from the user

| No. | Question | Blocks | Default if not answered |
|---|---|---|---|
| Q1 | ~~May I start M0?~~ **Answer: yes** (2026-11-13) | — | — |
| Q2 | ~~Commit and push? To which branch?~~ **Answer: commit by logical steps, push to `main`** (D59) | — | — |
| Q3 | ~~Record the TV state?~~ **Done** 2026-11-13 before the M0 install (`~/.just-my-tv-launcher/<TV_IP>.json`) | — | — |
| Q4 | Inputs icon: keep `ic_input.xml` (looks like a remote) or replace it? | Does not block | Keep it, check on the TV |
| Q5 | Wallpaper icon: `ic_picture.xml` is a TV icon. Replace it? | Does not block | Replace with the Material "image" icon |
| Q6 | Date in the header: two lines (as in the original) or one line ("Friday, 13 November")? | Does not block | Two lines |
| Q7 | Measure Couchy on the TV as a baseline (`am start -W`, `dumpsys meminfo`) before M0? You answered "not yet" | Performance targets | Do not do it until the first working M1 |
| Q8 | Interface languages: ru + en, ru only, or en only? | Strings and font | ru + en |
| Q9 | ~~Apple/Amazon aerials: needed?~~ **Answer: needed** (D49). | — | — |
| Q10 | Distribution: confirmed that APKs are published only on the release page (no ads, no store). This counts as distribution under the GPL. Code from Couchy is not copied (D47), so GPL does not apply to the project. Confirm: do you agree with the rule "do not copy from Couchy"? | Copying from Couchy | Yes, do not copy |
| Q11 | Video format for wallpapers: 1080p H.264, no sound, 20–60 s. Is that suitable? | Video | Yes |
| Q12 | Removing apps from the launcher: needed, or hiding only? | Menu | Hiding only |
| Q13 | Which remote buttons should be assigned by default, besides YouTube? List: NETFLIX, TCL CHANNEL, prime video, bookmark, TV, layers, grid | Remote, M7 | Only YouTube → SmartTube |
| Q14 | Margin from the screen edge (overscan): is a setting needed? Options 0 / 2 / 4 % | Appearance | Needed, default 2 % |
| Q15 | Publish the documentation to GitHub (`origin` points to the repository `<GITHUB_OWNER>/just-my-tv-launcher`)? The repository's visibility has not been checked | Push | Do not publish until you decide |
| Q16 | Not used (the number was dropped in an earlier round; kept so the other numbers stay stable) | — | — |
| Q17 | Video for our own background: a user file, our own clip made from the `aerial` image, or a clip from Wikimedia Commons? (WALLPAPERS, Video) | M4 | A user file, with `aerial` as the fallback |
| Q18 | Include the Unsplash set `ambient-glass` (licence: Unsplash)? | M4 | Not included |
| Q19 | The community aerial set on GitHub (38 entries, `C1` and `C2`): include it or not? | Video, M4 | Do not include |
| Q20 | Confirm that personal use of the streamed Apple and Amazon aerials is acceptable to you (service terms). We will not distribute the videos, but links to their servers will be in the release | Video, release | Include them as stated in D49, and keep RISKS 45 in mind |

## Technical checks on the TV (closed in the milestone named in "Blocks")

| No. | What to check | Blocks | How to check |
|---|---|---|---|
| T1 | HOME stays ours after a reboot | M0 | Section 20 of the plan, OPERATIONS |
| T2 | Launching an app from the remote service is allowed on TV 11 | Remote, M7 | A test assignment, the log for `ActivityNotFoundException` and refusals |
| T3 | What Android reports for the `TCL_*` buttons: `KEYCODE_UNKNOWN` or a standard keycode | Remote, M7 | A service that logs `getKeyCode()` and `getScanCode()` |
| T4 | Pressing the gear and the assistant button produces an event | Remote, M7 | A separate press, recorded with `getevent` |
| T5 | Mute is recorded from a single press | Remote, M7 | Record again |
| T6 | ~~Media3 1.11.1 builds with AGP 8.5.2 and compileSdk 34~~ **Closed: it cannot.** The AAR requires compileSdk 36; the plan now uses AGP 8.13.2 + compileSdk 36 (PLAN 11.5). Confirm with the first Docker build | M0 | Docker build |
| T7 | ~~`WEBP_LOSSY` is available on API 30~~ **Closed: yes**, `WEBP_LOSSY` and `WEBP_LOSSLESS` were added in API 30 (`Bitmap.CompressFormat`) | — | — |
| T8 | ExoPlayer with `handleAudioFocus=false` does not stop music in another app | M4 | Music playing in the background, the launcher on screen |
| T9 | The black frame on a cold start with the chosen video lasts under 300 ms | M4 | `screenrecord` as in section 6.2 |
| T10 | Switching HDMI 1 → 2 through `TvContract` with `com.tcl.tv` | M3 | Screenshot through `tools/tv.py` |
| T11 | Cyrillic font: system font for ru, or another font under OFL | M1 | Choose and check with a screenshot |
| T12 | Banner 320×180 px: a custom one is needed, check the current `banner_tv` | M1 | Visual check on the TV |
| T13 | Overscan: how much the TV crops at the edges | M1 | Test stripes |
| T14 | `flar2.homebutton` does not interfere with the remote service when disabled | M7 | Disable it and check the assignments |
| T15 | The tile list matches the launcher through `<queries>` without `QUERY_ALL_PACKAGES` | M1 | Compare `queryIntentActivities` with the list |
| T16 | Grid scroll performance, tested after all features are in (user's request at M1). Read GPU time and "Frame deadline missed" from `dumpsys gfxinfo`, not "Janky frames": keys sent with `adb shell input` mark every frame as high input latency. Last numbers at M1 (15 apps): GPU median 7–11 ms; 19 ms with the full-screen scrim and the `TopFade` hardware layer; cold start 1.0–1.3 s after the first run. If it is too slow: replace `TopFade`'s hardware layer with a per-row alpha fade, and draw the rounded tiles with a `BitmapShader` instead of `clipToOutline` | M6 | `gfxinfo` while scrolling 60 apps down and up, on the real remote too |
| T17 | M2, M3 and M5 on the TV (built and pushed without a TV check, at the user's request). Long OK opens the menu; Hide, then a restart keeps it hidden; Move with the arrows and OK; App details opens. Gear and MENU open Settings; every row works with the D-pad; BACK steps back and closes; columns 4–7 and rows below apply at once; Inputs panel lists HDMI 1–3 and switches HDMI 1 → 2 (screenshot); TV_INPUT reaches the app or not. Weather: city search with the TV keyboard, the row under the clock, °F, Wi-Fi off shows the cache with its time | M6 | `tools/tv.py --screenshot`, the real remote |
| T18 | M4 on the TV (built and pushed without a TV check, at the user's request). Fluffy opens for "Choose video…" and the grant survives a reboot; a 1080p H.264 clip loops for 30 minutes silent with no crash; Home from another app and back shows the poster frame, then the video, with no black frame; the decoder is released in the background (`dumpsys media.codec`); the cold-start `screenrecord` check from PLAN 6.2; an Apple and an Amazon aerial play, or the TLS failure is recorded in RISKS 45; a picture shows; PSS with video after 10 minutes | M6 | PLAN 6.2 and 12.2, `tools/tv.py --screenshot` |

## Decided but not yet done

| No. | What | Why it is not done |
|---|---|---|
| S1 | Disable `nooklauncher` | It is not disabled right now. It will be disabled during implementation, by agreement |
| S2 | Disable `flar2.homebutton` | During implementation of the remote service |
| S3 | Record the TV state | Waiting for Q3 |
| S4 | Review `ic_input.xml` and `ic_picture.xml` | Waiting for Q4 and Q5 |
| S5 | Delete `sources/` (submodules) after implementation and verification on the TV | Waiting for M7, the last milestone. Procedure in PLAN 1.1 and OPERATIONS 9 |
