# Device: TV

All facts were collected over ADB in read-only mode. Nothing on the TV was changed or installed. The TV address is replaced
with `<TV_IP>` in the documents, and hardware identifiers with `<HW_ID>`. The original values are in `private/redactions.md`.

Check dates: 13 November 2026.

## Hardware and system

| Parameter | Value | What it means |
|---|---|---|
| Manufacturer and model | TCL, "Smart TV", product `G03_4K_US_NF` | Based on CleanYourTV experience, no vendor-specific HOME workarounds should be needed (but see OPERATIONS, the section on forced stop) |
| Android | 11, SDK 30, build `RP1A.200622.001`, `release-keys` | `minSdk` 26 is suitable, `WEBP_LOSSY` and other API 30 features are available |
| Processor | Realtek `rtd288o` | |
| ABI | `armeabi-v7a` only (32-bit) | There is no native code in the APK, which is safe |
| Memory | `MemTotal` 2,097,152 kB (about 2 GB), `MemAvailable` about 480 MB under the load at the time | Memory budgets in PLAN 12.3, `onTrimMemory` in PLAN 5.6 |
| Display | mode `1920x1080@60` (the only one), `wm size` 1920×1080, density 320 → 960×540 dp | Layout is in dp. The panel is 4K by model, but Android renders 1080p and the TV upscales |
| System size property | `sys.display-size=3840x2160` | The TV settings are set to 4K (confirmed by the user) |
| Features | `android.software.leanback`, `leanback_only`, `wifi`, `wifi.direct` | `leanback` is required in the manifest; there is no touchscreen |
| Video decoder | `OMX.realtek.video.decoder`, hardware: AVC up to High 5.1, HEVC, AV1, 3GPP | 1080p H.264 from PLAN 6.1 decodes in hardware |
| Remote | `TCL_RC902N_M1`, Bluetooth, with a microphone, input device `/dev/input/event3` | See [remote-buttons.md](remote-buttons.md) |

## Home screen and roles

| Parameter | Value | What it means |
|---|---|---|
| Current HOME | `com.conreo.couchytv/.MainActivity` (Couchy 1.0.6), `isDefault=true` | This is the rollback point (OPERATIONS) |
| `cmd role` | the `get-role-holders` command is not supported, `Unknown command` | HOME cannot be assigned through roles, only through `set-home-activity` or a choice on the TV |
| Stock Google launcher | `com.google.android.apps.tv.launcherx`, version `1.0.1002449598`, **disabled by the user** (`enabled=3`) | The Google Home panel does not work. It is not enabled (D34) |
| Old personal fork | `<PKG_OLD_LAUNCHER>`, version 1.0.6, installed 24 October, **not disabled** (`enabled=0`) | Not HOME. Disabling it happens during implementation (S1) |

## Packages that matter for the project

| Package | What it is | Status |
|---|---|---|
| `org.smarttube.stable` | SmartTube, version 4.15 | Installed. Launch point: `SplashActivity` in `com.liskovsoft.smartyoutubetv2.tv.ui.main` |
| `flar2.homebutton` | Home Button, remaps buttons | Installed, its accessibility service is enabled (`flar2.homebutton/a.i`). Disabled during implementation (D25) |
| `com.alphainventor.filemanager` | Fluffy file manager | Installed. Handles `ACTION_OPEN_DOCUMENT` for `video/*` (by default) |
| `com.tcl.tv` | TCL TV app | Installed. Handles `VIEW` for `vnd.android.cursor.item/channel` (by default) |
| `com.tcl.tvinput` | TCL inputs | Installed |
| `com.tcl.keyhelp` | TCL utility | Installed, not used |
| `com.conreo.couchytv` | Couchy | Installed, current HOME, the reference for comparison |
| `com.google.android.youtube.tvmusic` | YouTube Music | Installed. There is no official YouTube TV app |

There are 25 disabled packages in total (`pm list packages -d`), including `com.tcl.tv.plus`, `com.google.android.onetimeinitializer`,
and `com.google.android.apps.tv.launcherx`.

## Inputs (`dumpsys tv_input`)

| Parameter | Value |
|---|---|
| Physical HDMI ports | 4 (`hdmi_port` 1–4) |
| Registered in `TvInputManager` | HDMI 1–3 (state `1`, CONNECTED_STANDBY) |
| HDMI 4 | not registered as an input |
| Passthrough input | type 3, `<HW_ID>`, state `0` |
| Other inputs | TDTV `<HW_ID>` (state `0`), `com.tcl.channelplus` (state `0`) |

Conclusion: an empty HDMI port and a switched-off device look the same on this TV (CONNECTED_STANDBY). That is why the
"connected only" option is needed (PLAN 7.7). CleanYourTV describes the same quirk for TCL (`config.xml`, `inputs_connected_only`).

## File picking and storage

| Parameter | Value |
|---|---|
| `ACTION_OPEN_DOCUMENT`, `video/mp4` | `com.alphainventor.filemanager/.activity.PickerActivity` (by default) |
| `ACTION_GET_CONTENT`, `video/mp4` | `ResolverActivity`, the app chooser. Do not use |
| `/sdcard` | `Alarms`, `Android`, `Audiobooks`, `DCIM`, `Documents`, `Download`, `Movies`, `Music`, `Pictures`, `Podcasts`, `Ringtones`, `Notifications`; at the root, two user files (not ours, not touched) |

`Movies` is the natural place for video wallpapers (PLAN 6.1).

## Network and ADB

| Parameter | Value |
|---|---|
| TV address | `<TV_IP>`, ADB on port 5555 via `adb tcpip` |
| Port after a reboot | closes (CleanYourTV: "usually lasts only until the next reboot"). See OPERATIONS |
| Second device | `<OTHER_DEVICE>`, a phone. Not touched (D38) |

## Not yet verified

- HOME behaviour after a reboot (T1).
- HOME behaviour after a forced stop (RISKS 8). The firmware is not changed; we only observe.
- Launching an app from the remote service on TV 11 (T2).
- The keycode for `TCL_*` (T3).
- Firmware updates and their effect on HOME (RISKS 6).
