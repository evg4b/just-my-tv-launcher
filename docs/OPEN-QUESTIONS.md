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
| T16 | Grid scroll performance. **TV check 2026-11-14 (18 apps, keys over ADB):** 250 frames, 3 missed the deadline, GPU median 12 ms, the same as at M1. Still open: 60 apps and the real remote | M6 | `gfxinfo` while scrolling 60 apps down and up, on the real remote too |
| T17 | M2, M3 and M5. **TV check 2026-11-14, passed:** long OK opens the menu (it ran off the screen under the bar: fixed, D86); Move with the arrows and OK; Hide survives a restart and Settings → Apps brings the app back; App details opens; MENU opens Settings, every row works with the D-pad, BACK steps back and closes; columns and rows apply at once (4 columns with 2 rows covered the clock: fixed, D87); the Inputs panel lists HDMI 1–3 (now in order, D88) and switches HDMI 1 → 2 (`dumpsys tv_input` shows `hdmi_port=2`); city search with the TV keyboard (the keyboard stayed over the results: fixed), the weather row, °F. Move followed by MENU moved the tile behind the panel: fixed. TV_INPUT is now left to TCL's source bar (D89). **Open:** Wi-Fi off shows the cache with its time | M6 | `tools/tv.py --screenshot`, the real remote |
| T18 | M4. **TV check 2026-11-14, passed:** "Choose video…" shows a chooser (File Manager+, or a stub: DocumentsUI is not on this TV); File Manager+ gives a persisted grant; a 1080p H.264 test clip plays (the video layer's buffers advance); the decoder is released behind an opaque app and kept behind the TV Settings side panel, which leaves Home visible; Amazon and Apple aerials stream (Apple after D85); a 3000×4000 picture shows, PSS 45 MB after decoding only the visible region (82 MB before). The start of a video and the aerial clip changes were reworked after you watched them (gradient before the video, then two flickers): D92–D94; you confirmed the result. **Open, needs eyes:** `screencap` and `screenrecord` cannot see the video plane on this TV, so the poster-then-video return without a black frame, the cold-start check of PLAN 6.2 and the 30-minute loop are yours to watch; the grant after a reboot; PSS after 10 minutes of video | M6 | PLAN 6.2 and 12.2, looking at the TV |
| T19 | M6 and M7. **TV check 2026-11-14, passed:** after a state record, `enabled_accessibility_services` was set to TV Home's service only (Home Button's entry was in the list, but accessibility was off, so it was not running) and the service bound. Keys injected with `input`, monkey or `injectInputEvent` never reach an accessibility key filter, so a virtual keyboard from the `hid` tool stood in for the remote: Mute assigned to Gallery opened Gallery from inside File Manager+ without muting; set back to "As before", the same press muted and unmuted the TV. Settings → Remote buttons shows the service on and YouTube → SmartTube. Russian strings: only the city hint is notably longer than in English, and it sits in a full-width field. PSS 69 MB with the service bound and video on. **Open, needs you:** YouTube and NETFLIX on the real remote (scancodes 245 and 242 cannot be made with a virtual keyboard); the service after a reboot; the Russian screens and the Cyrillic fallback font with the TV language set to Russian; banding in the Aurora gradient (D83), by eye | M6 | The real remote, looking at the TV |
| T20 | D107–D109. **TV check 2026-11-15, passed** (state recorded first; keys and the action's Home intent sent over ADB): Launcher settings from inside SmartTube brings up Home with the panel and leaves one MainActivity record (`dumpsys activity`); a second press closes it; Inputs while Settings is open closes the panel, as Menu does; Home closes an open panel; with Wi-Fi validated the network icon is hidden; Appearance → Settings icon is offered with Gear assigned Launcher settings, and set to Hidden the gear stays while the service is unbound after the update. **Open, needs you:** turn the service off and on, then the gear hides and the real Gear button toggles the panel; the crossed-out Wi-Fi icon with the router's internet unplugged (Wi-Fi itself cannot be dropped: ADB runs over it); Mute through `adjustSuggestedStreamVolume` | Remote | The real remote, the router |

## Decided but not yet done

| No. | What | Why it is not done |
|---|---|---|
| S1 | Disable `nooklauncher` | It is not disabled right now. It will be disabled during implementation, by agreement. Waiting for your go-ahead: it changes the TV |
| S2 | ~~Disable `flar2.homebutton`~~ **Done differently, 2026-11-14:** its accessibility service was already off; TV Home's service replaced it in `enabled_accessibility_services`. The app itself stays enabled. `tools/restore.sh` puts the recorded list back | — |
| S3 | ~~Record the TV state~~ **Done:** recorded before every TV change (latest 2026-11-14) | — |
| S4 | ~~Review `ic_input.xml` and `ic_picture.xml`~~ **Done as recommended in Q4 and Q5:** the Inputs icon is kept; Wallpaper uses the Material "image" icon (`ic_image.xml`), and `ic_picture.xml` is gone | — |
| S5 | ~~Delete `sources/` (submodules)~~ **Done 2026-11-14 (D98)** | — |
