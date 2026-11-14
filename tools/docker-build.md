# Building in Docker

Based on the CleanYourTV build guide (MIT, Copyright (c) 2026 Gathin).

`tools/docker-build.sh` builds the launcher inside a container that has JDK 17, Android SDK 36 and build-tools
36.0.0. This computer needs Docker and nothing else: no JDK, no Android Studio, no `ANDROID_HOME`. The APK lands
where a native build puts it: `launcher/app/build/outputs/apk/release/app-release.apk`.

## Build

```bash
tools/docker-build.sh                                                            # assembleRelease
tools/docker-build.sh launcher testReleaseUnitTest lintRelease assembleRelease   # tests, lint, APK
```

The first run builds the image from `tools/docker/Dockerfile` (a few minutes, about 1.5 GB) and Gradle downloads
its distribution into the `tvhome-gradle` volume; later builds reuse both. On Linux and macOS the container runs
as your user, so the build output is not owned by root.

| Variable | Default | What it changes |
|---|---|---|
| `IMAGE` | `tvhome-android-build` | The image name |
| `GRADLE_VOLUME` | `tvhome-gradle` | The Docker volume that keeps Gradle's caches and the signing key |
| `KEY_DIR` | `/gradle/.android` | Where the debug signing key is kept inside the container |
| `HOST_KEY_DIR` | (none) | A folder on this computer to take the key from instead, such as `"$HOME/.android"` |

## On an Apple Silicon Mac or another ARM computer

The Linux AAPT2 that the Android Gradle plugin downloads is built for x86-64 only, so the script builds and runs
the image as `linux/amd64` whatever the computer is. Docker Desktop runs it under Rosetta (turn on "Use Rosetta
for x86_64/amd64 emulation" in its settings) or QEMU; Colima needs `colima start --vm-type vz --vz-rosetta`. The
first build takes about 7 minutes, later ones well under a minute.

## Install

```bash
adb connect <TV_IP>:5555
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
```

Record the TV state before the first install (PLAN 11.8, OPERATIONS).

## The signing key

The release APK is signed with a debug key, and Android only installs an update signed with the same key as the
app already on the TV. Docker keeps its key in the Gradle volume, which is a different key from the one a native
build uses (`~/.android/debug.keystore`). Switching between a Docker build and a native build therefore fails
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

- Sign Docker builds with your native key: `HOST_KEY_DIR="$HOME/.android" tools/docker-build.sh`, or
- build the same way every time, or
- uninstall the app on the TV once and install the new build (its settings on the TV are lost).

The script refuses a `HOST_KEY_DIR` that is not an absolute path to an existing folder: Docker would create an
empty folder or read the name as a volume, and the build would sign with a new key without a word. `'~/.android'`
inside quotes is not expanded.

To see which key an installed app carries, compare `apksigner verify --print-certs` on the APK with
`keytool -list -v -keystore <dir>/debug.keystore -storepass android`.
