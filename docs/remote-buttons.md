# TCL RC902N_M1 remote buttons: documentation

Status: **recorded and documented; implementation has not started.** The recording was made on 13 November 2026, read-only
(`getevent`), and nothing on the TV at `<TV_IP>` was changed. No new presses are needed for the implementation: all codes below
come from the saved recordings (see "Source files").

## The device

- The remote: Bluetooth, with a microphone. The TV shows it as `TCL_RC902N_M1`, input device `/dev/input/event3`
  (`dumpsys input`, Device 29; `getevent -p`, add device 1).
- There is no key layout for this remote on the TV. `/system/usr/keylayout` contains generic files, including `Generic.kl`,
  which has TCL labels (`TCL_*`). A copy is kept in `docs/remote-captures/Generic.kl.txt`.
- The remote works over Bluetooth HID. This means that **the events reach the kernel level and are visible to any app** that
  reads input (accessibility services, `InputKeyService` in CleanYourTV).

## How the remote sends a press (from the recording)

One press is 4 lines in `getevent`:

1. `EV_MSC MSC_SCAN` with a HID code (`000c0050` and similar): the vendor scancode, sent by the remote.
2. `EV_KEY <code> DOWN` (press).
3. `EV_KEY <code> UP` (release) 0.2–0.3 s later.
4. `SYN_REPORT`.

The recording shows no auto-repeat when a button is held: each button produced one DOWN/UP pair. So a long press (for the menu)
must be measured from the time between DOWN and UP, not from repeats.

The numeric codes in the recording are Linux kernel codes (`getevent -t`, the column `0001 <code> <value>`).
The value `00000001` is a press and `00000000` is a release.

## Button table

The "scancode" column is the Linux code from the recording. Android receives the same value in `KeyEvent.getScanCode()`.
The "Android keycode" column is the standard `KEYCODE_*`, if it exists in `Generic.kl`.

| No. | Button (photo, top to bottom) | Scancode | Label in Generic.kl | Android keycode | What was recorded |
|---|---|---|---|---|---|
| 1 | ≡ (menu, top left) | 139 | MENU | KEYCODE_MENU | confirmed |
| 2 | Power (red, top right) | 116 | POWER | KEYCODE_POWER | **not recorded**: it turns the TV off, by agreement |
| 3 | Bookmark (left, second row) | 395 | TCL_LIST | unknown | recorded, in order |
| 4 | Google Assistant (microphone, centre) | — | — | — | **no events** |
| 5 | Gear (right, second row) | 233 | TCL_SET | unknown | recorded as the only remaining event (see below) |
| 6 | ↑ | 103 | DPAD_UP | KEYCODE_DPAD_UP | confirmed |
| 7 | ↓ | 108 | DPAD_DOWN | KEYCODE_DPAD_DOWN | confirmed |
| 8 | ← | 105 | DPAD_LEFT | KEYCODE_DPAD_LEFT | confirmed |
| 9 | → | 106 | DPAD_RIGHT | KEYCODE_DPAD_RIGHT | confirmed |
| 10 | OK (centre of the D-pad) | 353 | DPAD_CENTER | KEYCODE_DPAD_CENTER | confirmed |
| 11 | ← Back | 158 | BACK | KEYCODE_BACK | confirmed |
| 12 | ⌂ Home | 172 | HOME | KEYCODE_HOME | confirmed |
| 13 | TV (right, same row as Back) | 365 | TCL_EPG | unknown | recorded, in order |
| 14 | Numbers (left, row five) | 235 | TCL_NUMBER | unknown | recorded, in order |
| 15 | Layers (centre, row five) | 749 | TCL_HOVRING | unknown | recorded, in order |
| 16 | Grid with a list (right, row five) | 204 | TCL_DASHBOARD | unknown | recorded, in order |
| 17 | NETFLIX | 242 | TCL_NETFLIX | unknown | recorded, in order |
| 18 | YouTube | 245 | TCL_YOUTUBE | unknown | recorded, in order |
| 19 | TCL CHANNEL | 227 | TCL_QUICK_MENU | unknown | recorded, in order |
| 20 | prime video | 752 | TCL_PRIMEVIDEO | unknown | recorded, in order |
| 21 | Volume + | 115 | VOLUME_UP | KEYCODE_VOLUME_UP | confirmed |
| 22 | Volume − | 114 | VOLUME_DOWN | KEYCODE_VOLUME_DOWN | confirmed |
| 23 | Mute | 113 | VOLUME_MUTE | KEYCODE_VOLUME_MUTE | confirmed; recorded twice in one recording, once in the other |

"Recorded, in order": the code was captured, and the match to the button is based on the order of presses. It has not been
checked separately.

## Behaviour to take into account

- **Gear and Assistant.** There were two events for three buttons (bookmark, assistant, gear). The conclusion: bookmark
  = 395, gear = 233, and Assistant produces no event. Most likely Assistant is handled by the voice service directly, not through
  input. This has not been confirmed by a separate press of the gear.
- **`TCL_*` and keycodes.** The `TCL_*` labels are not standard `KEYCODE_*` values. Android most likely reports
  `KEYCODE_UNKNOWN` for them, so an app can tell the buttons apart only by scancode. Not verified (see "To verify").
- **Mute.** In one recording the press was captured twice, in the other once. A possible double press.
- **Power.** Not recorded on purpose. Its code `116` (`POWER`) is known from `Generic.kl`.

## What the implementation needs (decisions made)

1. **Our own service inside the launcher** (a "Remote buttons" screen in the launcher settings), not Home Button.
2. Remapping a button to an app (an app only, no parameters). The button list: all 23 from the table, except the power button.
3. **Default: YouTube → SmartTube.** The launch point of SmartTube on the TV:
   `org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity` (version 4.15).
4. **`flar2.homebutton` (Home Button) is disabled** during the implementation, so that two services do not intercept the same keys.

Open technical questions:

- The accessibility service is enabled manually in the TV settings (an Android restriction).
- The behaviour of `KEYCODE_UNKNOWN` for `TCL_*` (see above). The matching fields depend on it.
- Gear, Bookmark, and TV: what they do on the TV by default now, so that the system behaviour is not broken.

## Source files

- `docs/remote-captures/getevent-named.txt`: a recording with kernel names (`getevent -lt`), 132 lines.
- `docs/remote-captures/getevent-numeric.txt`: a recording with numeric codes (`getevent -t`), 21 presses.
- `docs/remote-captures/Generic.kl.txt`: the key layout from the TV, with `TCL_*` labels.

Commands for repeating the recording, if it is needed:

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 shell getevent -t /dev/input/event3
adb -s <TV_IP>:5555 shell cat /system/usr/keylayout/Generic.kl
```
