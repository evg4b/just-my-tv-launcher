# Problem cases and how to handle them

This is a reference, not a list of requirements. It describes what can go wrong on our TV, why it matters, and how it is best
handled. The decisions are made during implementation, and some of them are already recorded in [PLAN.md](PLAN.md).

The status of each item:

- **verified**: confirmed on the TV, or from files in `sources/` (reference, PLAN 1.1);
- **from CleanYourTV**: described in its documentation or code, not checked on our TV;
- **not verified**: our assumption, to be checked during implementation.

A summary by priority:

| Priority | Items | Why |
|---|---|---|
| Critical | 1, 5, 7, 16, 23 | Loss of HOME, navigation, or installation |
| Important | 2, 8, 9, 17, 18, 21, 31, 39 | Break things, but can be worked around |
| Useful | the rest | Quality and edge cases |

---

## Startup and HOME

### 1. A crash on startup breaks HOME
- **What can happen.** The launcher crashes on start, and the Home button leads to a black screen or an error.
- **How to handle it.** A crash counter in `launcher_state.json`. After 2–3 consecutive crashes on start, a plain emergency list
  without video is launched. The counter resets once the first frame is shown successfully.
- **Status.** not verified (our logic).

### 2. The system kills the process to free memory
- **What can happen.** When returning to Home, the launcher starts again, and the video and the grid load again.
- **How to handle it.** The state is read quickly and does not depend on the network. Focus is restored from `lastFocused`.
  The poster is shown at once, until the first video frame arrives (section 6.2 of the plan).
- **Status.** not verified on the TV.

### 3. No network on startup
- **What can happen.** The weather is empty or hangs, and the first frame is delayed.
- **How to handle it.** The weather does not block the first frame. Timeouts are 5 s. Until the first response arrives, we show the cache or nothing.
- **Status.** the timeouts are in the plan; the behaviour on startup is not verified.

### 4. The TV boots into another app
- **What can happen.** After power-on, a TV app (Live TV or another) opens immediately, and Home is not the first screen.
- **How to handle it.** Nothing by default. The "open on start" option (`open_on_start` in CleanYourTV) can be added later if needed.
- **Status.** from CleanYourTV (`boot_to_live_tv`, `openOnStart`).

### 5. A repeated HOME intent while the launcher is already open
- **What can happen.** Pressing Home recreates the video and resets focus.
- **How to handle it.** `launchMode="singleTask"`. In `onNewIntent` we recreate nothing; we only restore focus to `lastFocused` if needed.
- **Status.** from CleanYourTV (manifest, `singleTask`).

### 6. A firmware update restores the stock HOME
- **What can happen.** After an update, the TV opens the stock screen again on Home.
- **How to handle it.** We do not change the firmware. After an update, check HOME with the command from section 20 of the plan.
  If HOME was overwritten, restore it with the command or through the rollback (section 11.8).
- **Status.** from CleanYourTV (`docs/firmware-updates.md`, the TROUBLESHOOTING notes on TCL).

### 7. ADB disappears after a reboot
- **What can happen.** After a reboot, the APK cannot be installed and the rollback cannot run over the network.
- **How to handle it.** Port 5555 lasts only until the next reboot. Before a long session and before a reboot, enable
  `adb tcpip 5555` over USB. The installation instructions (section 11.7) must state this explicitly.
- **Status.** from CleanYourTV (`TROUBLESHOOTING.md`: "Port 5555 from adb tcpip usually lasts only until the next reboot").

### 8. A forced stop, and the firmware restores the stock HOME
- **What can happen.** The user or the system force-stops the launcher, and TCL immediately brings back the stock screen.
- **How to handle it.** We do not change the firmware. We only observe and record the behaviour (section 16 of the plan). CleanYourTV
  has `GuardRevival` for this, but that is a separate decision, to be made if needed.
- **Status.** from CleanYourTV (described for TCL G10 and `GuardRevival`); not checked on our TV.

---

## Apps

### 9. An app is removed or disabled while the launcher runs
- **What can happen.** The tile under focus no longer exists, and focus is lost.
- **How to handle it.** Rescan on `PACKAGE_REMOVED`, with a delay of about 500 ms. Focus moves to a neighbouring tile
  instead of staying in empty space.
- **Status.** not verified (our logic).

### 10. An app has no TV entry point
- **What can happen.** A phone app opens in its portrait interface or crashes.
- **How to handle it.** First try `getLeanbackLaunchIntentForPackage`. If it is missing, either launch
  `getLaunchIntentForPackage`, or show "not a TV app". The second option is safer.
- **Status.** from CleanYourTV (`AppLaunchActivity`, `MainActivity.java:2596–2597`).

### 11. Launching from a background service is blocked
- **What can happen.** A remote button does not open the app, because Android forbids starting an activity from the background.
- **How to handle it.** An accessibility service can usually start activities, but this has to be checked on our TV. If it
  does not work, show a toast "could not open" and log the reason.
- **Status.** not verified on the TV.

### 12. An assignment to a missing app
- **What can happen.** SmartTube is removed or disabled, but a button is assigned to it.
- **How to handle it.** A toast "App not found". The assignment in the settings is marked as having no app.
- **Status.** not verified (our logic).

### 13. A huge or transparent banner
- **What can happen.** Decoding slows the scan. A transparent banner leaves an empty tile.
- **How to handle it.** Limit the size before decoding (`inSampleSize`). A tile background sits under a transparent banner, like `tile_bg` in CleanYourTV.
- **Status.** from CleanYourTV (`tile_bg`, `isBannerShaped`).

### 14. Two entries for one package
- **What can happen.** One app produces two tiles with the same name (the Sony case in CleanYourTV).
- **How to handle it.** Deduplicate by package and name (already in the plan for the scan).
- **Status.** from CleanYourTV (`AppAdapter.java:291–297`).

### 15. A storm of installation events
- **What can happen.** Installing several apps in a row triggers dozens of scans.
- **How to handle it.** Debounce: one scan per series of events (already in the plan, 9.4).
- **Status.** not verified.

---

## Remote

### 16. The remote service is switched off or lost
- **What can happen.** The service was switched off manually, or it disappeared after reinstalling the APK with a new signature.
  The buttons stop working, and nobody notices.
- **How to handle it.** An indicator in the launcher settings: "remote service enabled" (based on
  `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`). If it is off, the "Remote buttons" item shows how to turn it on.
- **Status.** `enabled_accessibility_services` verified on the TV (`flar2.homebutton/a.i`); behaviour after reinstalling is not verified.

### 17. The keycode for `TCL_*` is unknown
- **What can happen.** `KeyEvent.getKeyCode()` returns `KEYCODE_UNKNOWN`, so the button cannot be told apart by keycode.
- **How to handle it.** Match by `getScanCode()`. Store the scancode and the keycode together, so that no single field is relied on.
- **Status.** not verified on the TV; the scancodes are recorded (`docs/remote-buttons.md`).

### 18. Double handling of a button
- **What can happen.** The same button is handled by both the launcher and the service. For example, OK launches an app twice, or Back closes both the launcher and a panel.
- **How to handle it.** The service does not touch the D-pad, OK, Back, or the volume keys. Assignments to these keys are refused when saved in the settings.
- **Status.** not verified (our logic).

### 19. A remapped button breaks games
- **What can happen.** If a button is assigned to an app, a game that uses that button stops responding to it.
- **How to handle it.** This is expected. An explanation sits next to the assignment in the settings. A mode "give the button to the app
  if it is in focus" could be added, but that is a separate decision.
- **Status.** not verified.

### 20. The remote is replaced or re-paired
- **What can happen.** The codes of another remote differ, and all assignments stop working.
- **How to handle it.** Assignments are stored together with the device name. On a mismatch we show a warning instead of silently disabling them.
- **Status.** not verified (our logic).

---

## Video

### 21. The file is missing or read permission is lost
- **What can happen.** The file is deleted, a flash drive is unplugged, or read permission for SAF is lost after the app data is cleared.
- **How to handle it.** A fallback to the gradient (Aurora). The Wallpaper settings show that the file is unavailable and offer to choose it again.
- **Status.** `takePersistableUriPermission` is described in the plan; behaviour when the permission is lost is not verified.

### 22. An endless player error
- **What can happen.** The clip does not play, the player restarts in a loop, and the CPU load grows.
- **How to handle it.** No more than two restarts in a row, then a fallback to the poster. The counter resets after the first frame.
- **Status.** not verified (our logic).

### 23. Volume 0 does not release audio focus
- **What can happen.** ExoPlayer takes audio focus even at volume 0. Music in another app stops while the launcher is on screen.
- **How to handle it.** Turn off `handleAudioFocus` explicitly (on `AudioAttributes`), and check it with music playing in the background.
- **Status.** not verified on the TV. Important: according to the ExoPlayer documentation the behaviour depends on the settings, so it must be checked.

### 24. The TV is in standby or the screen is off
- **What can happen.** The `SurfaceView` surface is lost, and the video does not come back after waking up.
- **How to handle it.** After waking up, reattach the surface and call `prepare` again. Check the scenario "power off the TV, then on" on the TV.
- **Status.** not verified.

### 25. A seam when the clip loops
- **What can happen.** A visible jump or a black frame at the joint of the clip.
- **How to handle it.** Make the clip with identical first and last frames (section 6.1). Check for 30 minutes on the TV.
- **Status.** not verified.

### 26. The codec cannot play the clip
- **What can happen.** A clip in H.264 High 5.1, or with another pixel format, does not play on the hardware decoder.
- **How to handle it.** Check when the clip is chosen (section 6.1), show a warning, and fall back to the poster. The software decoder
  (`setEnableDecoderFallback`) is the backup option.
- **Status.** the TV decoder is verified (`dumpsys media.player`, section 18); specific clips are not verified.

### 27. Video from a network or USB source
- **What can happen.** The source drops out during playback.
- **How to handle it.** The same fallback as in item 21. We recommend storing the video on internal storage.
- **Status.** not verified.

---

## Inputs and the TV

### 28. An HDMI device is connected after startup
- **What can happen.** The input list is not updated, and the new device is not shown.
- **How to handle it.** Subscribe to `TvInputManager.TvInputCallback` and refresh the list on changes (without this callback,
  section 7.7 of the plan is incomplete).
- **Status.** `TvInputCallback` exists in CleanYourTV (`MainActivity.java:392`).

### 29. Switching the input does not work
- **What can happen.** Pressing "Inputs" does nothing, or a chooser opens.
- **How to handle it.** A fallback to the system input picker (section 7.7). The message "Input did not open".
- **Status.** `com.tcl.tv` as the handler was verified with resolve-activity; the switch itself through the URI is not verified.

### 30. The TV time is not synchronised
- **What can happen.** The clock shows the year 1970 or the wrong hour, when the TV has no network.
- **How to handle it.** Check the year on startup. If the time looks wrong, show the clock with a marker instead of silently.
- **Status.** not verified.

---

## Interface

### 31. A font without Cyrillic
- **What can happen.** Russian text is drawn with the system font, while the rest of the interface uses Outfit. The result looks inconsistent.
- **How to handle it.** Decide before M1: use the system font for Russian, or a Cyrillic-capable font under OFL, or the system font everywhere.
- **Status.** verified: the `outfit.ttf` from CleanYourTV has no Cyrillic characters at all (361 code points in the cmap, 0 Cyrillic).
  This affects CleanYourTV as well.

### 32. Screen-edge cropping (overscan)
- **What can happen.** Some TVs crop 3–5 % at the edges, and the 44 dp margins can end up outside the visible area.
- **How to handle it.** A "margin from the edge" setting in Appearance (0 / 2 / 4 %).
- **Status.** not verified on the TV.

### 33. Focus when the list changes
- **What can happen.** The tile under focus is hidden or removed, and focus jumps unexpectedly.
- **How to handle it.** Focus moves to the nearest visible tile in the same row, then to the same column.
- **Status.** not verified (our logic).

### 34. Long and empty names
- **What can happen.** A name does not fit, or an app has an empty label.
- **How to handle it.** An ellipsis (`ellipsize=end`). An empty name is replaced with the package name.
- **Status.** `ellipsize` and `singleLine` in CleanYourTV (`item_app.xml`).

### 35. Dropped frames when video and the grid run together
- **What can happen.** Grid scrolling stutters while the video plays.
- **How to handle it.** Measure `gfxinfo` with the video running, separately from the measurement without it (section 12.3 of the plan currently
  describes only the case without video).
- **Status.** not verified.

### 36. Settings open while a scan runs
- **What can happen.** Updating the app list changes data that the settings screen is reading.
- **How to handle it.** The settings work with a copy of the list, and the update is applied when the panel closes.
- **Status.** not verified (our logic).

---

## Weather

### 37. The city is not found, or there is no network
- **What can happen.** The geocoding returns an empty response (there is no `results` key), or the network is unavailable.
- **How to handle it.** An empty response means "not found", not an error. For no network, show the cache with its update time. No dialogs.
- **Status.** the empty response was verified with `curl` (section 4.3 of the plan).

### 38. An incomplete API response
- **What can happen.** `weather_code` or `is_day` is missing, or has the value `null`.
- **How to handle it.** An "unknown" icon, without a crash (section 4.5 of the plan).
- **Status.** the structure of the response was verified with `curl`; incomplete responses have not been seen.

---

## Data and updates

### 39. A corrupted `launcher_state.json`
- **What can happen.** The file is corrupted during a shutdown or a write, and the settings are lost.
- **How to handle it.** `AtomicFile` protects against partial writes. Additionally, keep a copy of the previous version and roll back to it when a read fails.
- **Status.** `AtomicFile` is described in the plan; the backup copy is not described.

### 40. Schema migration
- **What can happen.** A new launcher version cannot read the old JSON, and the settings are reset.
- **How to handle it.** The `version` field exists. Write the migration rules when the schema changes, not in advance.
- **Status.** the `version` field is in the plan.

### 41. The banner cache keeps growing
- **What can happen.** With many apps and updates, the cache takes up a lot of space.
- **How to handle it.** Delete files that are no longer in the current list (already in the plan, 5.2). A size limit as an extra measure.
- **Status.** deleting orphaned files is described in Couchy (`validCacheNames`).

### 42. A new APK signature
- **What can happen.** Installing over ADB fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- **How to handle it.** One key for the Docker and native builds (`HOST_KEY_DIR`, section 11.6 of the plan).
- **Status.** from CleanYourTV (`tools/docker-build.md` in CleanYourTV).

---

## Memory

### 43. The system asks for memory to be freed
- **What can happen.** `onTrimMemory` arrives while the launcher is in the background or on screen, and the cache and video take up space.
- **How to handle it.** Section 5.6 of the plan: in the background we release the banner bitmaps; on screen we release only the spare ones.
- **Status.** from CleanYourTV (`MemoryTrimPolicy.java`); not checked on the TV.

---

## Reference projects

### 44. The app depends on `sources/`
- **What can happen.** Code from `sources/` is imported by mistake, or a manifest, script, resource, or build file refers to it. After the folder is removed,
  the build breaks, or the app cannot be reproduced on another computer.
- **How to handle it.** The rule in PLAN 1.1: copy and rewrite, but do not reference. A check before every commit:
  `grep -rn "sources/" --include=*.java --include=*.xml --include=*.gradle --include=*.sh --include=Dockerfile .`
  The result must be empty. Before the folder is removed, check that the build works with an empty `sources/`.
- **Status.** closed: no code, resource, script or build file referred to `sources/`, the build passed without it, and the
  folder was removed on 2026-11-14 (D98).

---

## Streamed aerials

### 45. Streamed video from Apple and Amazon
- **What can happen.** The videos belong to Apple and Amazon. The links may stop working or change without notice.
  The service terms may not allow this use. The Apple server fails the standard TLS check on some TVs.
  Streaming exposes the device's IP address to the Apple and Amazon servers.
- **How to handle it.** The videos are not downloaded or stored. The links are carried over as data, and only the list of links is distributed.
  Entries with unreachable links are disabled rather than crashing the app. TLS is not disabled (trust-all is not carried over). Streams are enabled
  only when the user chooses. Attribution in "About". The question of whether this is permitted is Q20.
- **Status.** the list was checked from the file (289 entries, 251 Apple and Amazon); the reachability of the links and TLS on the TCL have not been verified.
  **TV check, 2026-11-14:** Amazon links play. Apple links failed with "Trust anchor for certification path not found":
  `sylvan.apple.com` chains to Apple Root CA, which Android 11 does not ship. Fixed without trust-all: the app trusts
  that one root for that one host, next to the system store (D85). After the fix, Apple clips stream too.

### 46. The remote buttons service stops after an update or a force stop
- **What can happen.** On the TCL (Android 11), `adb install -r` of the launcher leaves `Just My TV Launcher remote buttons` in
  `enabled_accessibility_services` but unbound, and `accessibility_enabled` drops to 0. A force stop of the package removes
  the service from the list entirely. Either way the remote buttons stop working without any message.
- **How to handle it.** The app cannot rebind itself (that needs `WRITE_SECURE_SETTINGS`). Settings → Remote buttons shows
  "Turn off and on" when the service is listed but not running (D91), and the user guide says what to do after an update.
  Over ADB, clearing and re-setting `enabled_accessibility_services` rebinds it. That works only once the launcher's process
  is running: right after an install, with the screensaver on, it stayed unbound through seven tries and bound at once
  after Home was brought up. Setting `accessibility_enabled` to 0 first and pausing a few seconds between the steps helps.
- **Status.** verified on the TV, 2026-11-14. Not checked: whether a reboot rebinds it on its own.
