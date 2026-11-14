# Operations: installation, state recording, rollback, checks

This document describes how to change the TV safely and how to restore everything. The scripts:
`TV_HOST=<TV_IP> tools/record.sh` (read-only) and `TV_HOST=<TV_IP> tools/restore.sh [record.json]` (changes the TV).
`record.sh` records the packages listed in section 3 plus every app that has a HOME activity (so private package names
stay out of the repository), and the full list of disabled packages. The `enabled` value is the `User 0` number from
`dumpsys package`: 0 default, 1 on, 2 off, 3 off by the user.

All ADB commands below use the address `<TV_IP>`. Substitute your own value from `private/redactions.md`.

## 1. Principles

1. **Record first, then change.** Before any change to the TV, its state is recorded (section 3).
2. **Rollback replays the record,** it does not guess. If there is no record, the rollback is manual and nothing is enabled automatically.
3. **Never enable `launcherx`** and never change the firmware.
4. **A check after a reboot is mandatory** before M1 (section 5).
5. **Read-only until permitted.** Commands that change the TV (installation, reboot, `pm disable`, `settings put`) run only with the user's permission.

## 2. ADB: connecting

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 shell getprop ro.build.version.release
adb disconnect <TV_IP>:5555
```

Port 5555 is opened with `adb tcpip 5555` (over USB, once). After a reboot the port closes and has to be opened again over
USB. This limitation is documented for CleanYourTV (`docs/TROUBLESHOOTING.md`). Without it, installation and rollback over
the network are impossible after a reboot.

A second device in ADB (the phone) is not touched. Commands always use `-s <TV_IP>:5555`. In zsh, a variable that holds a
command does not work, so write the command out in full.

## 3. Recording the state (before M0)

What is recorded:

| What | How it is read (read-only) |
|---|---|
| HOME owner | `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` |
| Packages from the list (enabled / disabled / not installed) | `adb shell dumpsys package <package>` (the `enabled=` line) |
| Packages disabled by the user | `adb shell pm list packages -d` |
| Accessibility services | `adb shell settings get secure enabled_accessibility_services` |

The packages to record: `com.google.android.apps.tv.launcherx`, `<PKG_OLD_LAUNCHER>`, `flar2.homebutton`,
`com.conreo.couchytv`, `org.smarttube.stable`.

Record file: `~/.just-my-tv-launcher/<TV_IP>.json` (one file per TV). It is not stored in the repository.

Example content (values from the check on 13 November 2026):

```json
{
  "tv": "<TV_IP>",
  "recorded": "2026-11-13",
  "home": "com.conreo.couchytv/.MainActivity",
  "packages": {
    "com.google.android.apps.tv.launcherx": "disabled-user",
    "<PKG_OLD_LAUNCHER>": "enabled",
    "flar2.homebutton": "installed",
    "com.conreo.couchytv": "installed",
    "org.smarttube.stable": "installed"
  },
  "accessibility": "flar2.homebutton/a.i"
}
```

A record is made again on every change to the TV, not just once: an old record is never overwritten. It gets a new name with the date.

## 4. Rollback

The goal of the rollback is to restore the whole state from the record. For our case this means:

- HOME goes back to Couchy (`com.conreo.couchytv/.MainActivity`);
- `launcherx` stays disabled (as in the record), and **is not enabled**;
- `<PKG_OLD_LAUNCHER>` stays in the state from the record;
- the list of accessibility services is restored as in the record;
- our launcher stays installed. Remove it manually: Settings → Apps.

The command to restore HOME (check it on the TV before using it):

```bash
adb -s <TV_IP>:5555 shell cmd package set-home-activity com.conreo.couchytv/.MainActivity   # checked on the TCL: works for our launcher
```

If the command does not work, choose HOME on the TV: Home → select Couchy → "Always". This is a manual rollback, and no record is needed for it.

If there is no record, the script prints the current state and says that the rollback is manual. It does not guess and it does not enable anything.

## 5. Check after a reboot

This check is run before M1 and repeated after every firmware update and every installation.

**Before the reboot:**

1. The state record has been made (section 3).
2. `adb tcpip 5555` has been run over USB, if you plan to work over the network after the reboot.

**Reboot.** With the power button on the TV itself. The command `tools/tv.py --reboot` changes the state of the TV, so it runs only with the user's permission.

**After boot:**

1. Press Home on the remote. Our launcher opens (after M0) or Couchy opens (before M0).
   ```bash
   adb -s <TV_IP>:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```
   This check is read-only. The result is the package that is currently HOME.
2. `launcherx` is still disabled:
   ```bash
   adb -s <TV_IP>:5555 shell pm list packages -d
   ```
3. The accessibility services match the record (our remote service exists only after M7):
   ```bash
   adb -s <TV_IP>:5555 shell settings get secure enabled_accessibility_services
   ```
4. Our remote assignments are in place (after M7).
5. Record the result in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md), item T1.

If HOME became the stock one after the reboot, that is an observation, not a reason to change the firmware (RISKS 6, 8).
Record exactly what happened, then perform the rollback (section 4).

## 6. Installation (after M0)

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r launcher/app/build/outputs/apk/release/app-release.apk
```

Before installing: the state record must exist (section 3). After installing: installation does not make our launcher HOME by itself.
Do it with Home → "Always" on the TV, or with the `set-home-activity` command. Then run the check after a reboot (section 5).

The signing key must be the same for the Docker and native builds (`HOST_KEY_DIR`, PLAN 11.6). Otherwise you get
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## 7. What happens to each package

| Package | Action | When |
|---|---|---|
| `com.google.android.apps.tv.launcherx` | Not touched, stays disabled | Always |
| `<PKG_OLD_LAUNCHER>` | Disabled (`pm disable-user --user 0`) by agreement | During implementation (S1) |
| `flar2.homebutton` | Its remote service is disabled | During implementation of the remote service (D25) |
| `com.conreo.couchytv` | Kept, it is the rollback point | Always |
| `org.smarttube.stable` | Not touched, it is the target of the assignment | Always |

The disabling commands are written into the record before they run.

## 8. What we do not do

- We do not change the firmware and do not enable `launcherx`.
- We do not remove Couchy until our launcher has passed the check after a reboot.
- We do not touch the `<OTHER_DEVICE>`.
- We do not run commands that change the TV without the user's permission.

## 9. Removing the reference projects (`sources/`)

This is done only after the last milestone (M7) and verification on the TV. Rule: the app does not depend on `sources/` (PLAN 1.1).

1. Check that there are no references to `sources/` in the code, manifests, resources, scripts, or the build.
   ```bash
   grep -rn "sources/" --include=*.java --include=*.xml --include=*.gradle --include=*.sh --include=Dockerfile .
   ```
   The result must be empty.
2. Check the build with an empty `sources/`: run `tools/docker-build.sh launcher assembleRelease` before the folder is removed.
3. Detach the submodules:
   ```bash
   git submodule deinit -f sources/cleanyourtv sources/couchy-launcher
   git rm -f sources/cleanyourtv sources/couchy-launcher
   rm -rf .git/modules/sources
   ```
4. Remove the entries from `.gitmodules`.
5. In the documents, replace the references to `sources/` with the text "based on CleanYourTV" or delete them.
6. Commit "Remove reference sources". Push only with the user's permission.
