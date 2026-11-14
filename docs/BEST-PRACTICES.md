# Best practices for the launcher and the remote service

This is a reference, not a list of requirements. It is based on the official Android and Google documentation, plus a few community
articles where no official page was found. Each item is marked with its level of evidence:

- **official**: read on developer.android.com;
- **community**: a blog post or Stack Overflow answer, to be checked before use;
- **to verify**: not confirmed; needs a check on the TV or on an official page.

Research date: 13 November 2026.

**Important:** examples from `sources/` are reference only. Code from there may be copied and rewritten, but it must not be imported
or referenced (PLAN 1.1).

---

## 0. Target platform

What is known about the TV (`docs/PLAN.md`, section 18): Android 11, API 30, a 32-bit ABI (`armeabi-v7a`), about 2 GB of memory.

| Parameter | Value | Why |
|---|---|---|
| `minSdk` | 26 | Compatibility, as in CleanYourTV. The Google Play TV-PS criterion requires 31 or lower: 26 fits. |
| `compileSdk` | 34 | Modern APIs are available, including `java.lang.Record`. |
| `targetSdk` | 34 | As in CleanYourTV. The behaviour of newer Android versions on a TV running Android 11 does not change, but manifest fields (for example `exported`) are checked. |
| `buildTools` | 34.0.0 | As in CleanYourTV. |

**Rule for newer APIs.** APIs above 26 are used only behind a version check:

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {          // API 30, our TV
    format = Bitmap.CompressFormat.WEBP_LOSSY;
} else {
    format = Bitmap.CompressFormat.WEBP;                        // API 26–29
}
```

`WEBP_LOSSY` was added in API 30 (**to verify** on the `Bitmap.CompressFormat` page; the search did not confirm it).

For Android TV 11 and the HOME intent, see [docs/RISKS.md](RISKS.md), items 1–8.

---

## 1. The remote service (AccessibilityService)

### 1.1. What is official

- The service is declared in the manifest and protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`. This is **official**:
  "strictly enforce the `BIND_ACCESSIBILITY_SERVICE` permission so that only the system can bind to your service"
  ([guide](https://developer.android.com/guide/topics/ui/accessibility/service)).
- The XML settings are `android:accessibilityEventTypes`, `android:canRetrieveWindowContent`, `android:accessibilityFlags`,
  and `android:accessibilityFeedbackType` ([guide](https://developer.android.com/guide/topics/ui/accessibility/service)).
- **Official:** "Be careful: using `typeAllMask` can be resource intensive because it forces the system to notify
  your application of every accessibility event that occurs across the entire OS."
  The remote service needs no UI events at all: `android:accessibilityEventTypes="typeNone"`.
- `canRetrieveWindowContent="false"`: the service does not read screen content. This is what we need.

### 1.2. About keys (community, to verify)

- The flag `flagRequestFilterKeyEvents` gives the service the keys before apps receive them. According to the search, only one
  service with this flag is active at a time. This matters for the conflict with `flar2.homebutton` (see [docs/remote-buttons.md](remote-buttons.md)).
  Sources: [the .NET mirror of the AccessibilityServiceInfo reference](https://docs.microsoft.com/en-us/dotnet/api/android.accessibilityservices.accessibilityserviceinfo.flagrequestfilterkeyevents),
  [a 2018 blog post](https://blog.joshsoftware.com/2018/03/28/android-accessibility-service-customization-for-keypress-event/).
- `onKeyEvent(KeyEvent)` returns `true` if the event is consumed, and `false` if it should be passed on.
  From AOSP commits and one Stack Overflow answer: it is better to consume **both DOWN and UP** the same way, otherwise some
  keys (for example, volume) may behave oddly. **Check on the TV.**
- Events are copies: they cannot be modified, and the service cannot inject new ones (community, to verify against the reference).
- The handler runs on a single thread. A slow `onKeyEvent` delays the next key.
  So the handler should only compare the code and start an `Intent`.

### 1.3. What follows for us

1. Manifest: `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`, `android:exported="false"` for the
   service, and a `<meta-data android:name="android.accessibilityservice">` pointing to the XML configuration.
2. XML: `accessibilityEventTypes="typeNone"`, `canRetrieveWindowContent="false"`, `accessibilityFlags="flagRequestFilterKeyEvents"`.
   `notificationTimeout` is not needed (there are no UI events).
3. `onKeyEvent`: a cheap check first (a map from code to package), then the launch. The map is built at startup from the settings,
   not read from a file on every press.
4. Do not consume keys the interface itself needs: the D-pad, OK, Back, Home, volume, and power. This rule is already recorded in
   [docs/RISKS.md](RISKS.md), item 18.
5. Launching an app from the service: an `Intent` with `FLAG_ACTIVITY_NEW_TASK`. Whether launching from the background is allowed on the
   TV must be **checked** (RISKS, item 11).
6. One service per set of keys. If `flar2.homebutton` stays enabled, our service may not receive the keys at all (community, to verify).

### 1.4. Checking the service

- Enabled status: `adb shell settings get secure enabled_accessibility_services` (read-only).
- Log the consumed keys with `Log.d` and a tag; log the code and the scancode, with no personal data.
- In a debug build, log `KeyEvent.getKeyCode()` and `getScanCode()` to settle the `TCL_*` question.

---

## 2. Android TV: what the store requires and what the launcher needs

Official, from the [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality) page, tier 3
(the minimum for a TV app):

| Criterion | What is required | For our launcher |
|---|---|---|
| TV-LB | A 320×180 px banner and an icon of at least 160×160 px (xhdpi) | Draw `banner_tv` at 320×180 with text, otherwise the TV shows an empty tile |
| TV-BN | The banner contains the app's name | Text on the banner |
| TV-ML | `ACTION_MAIN` + `CATEGORY_LEANBACK_LAUNCHER` | Already in the manifest |
| TV-MT | `android.hardware.touchscreen` is not required | Already `required="false"` |
| TV-DB | Back leads to the home screen | Back on Home does nothing; the panels close |
| TV-OV | Text and buttons are not cut off by the screen edge | A margin from the edge (RISKS, item 32) |
| TV-TR | A full-screen, opaque background | A poster or a gradient, as in the plan |
| TV-DM | Does not depend on the remote's Menu button | The gear is on screen |
| TV-ME | On low-RAM devices (`ActivityManager.isLowRamDevice()`), memory limits for the foreground app | Section 5.6 of the plan (onTrimMemory) |
| TV-BP | Baseline Profiles to speed up startup (tier 2) | See section 5 |
| TV-G6 | From 1 August 2026: 32- and 64-bit ABIs and 16 KB pages | Not required for sideloading; there is no native code in the APK |

These are Google Play rules. They are not mandatory for personal sideloading, but they make a useful quality checklist.

---

## 3. The app list (package visibility)

### 3.1. Official

- From Android 11 (API 30), an app does not see all installed packages. A `<queries>` block in the manifest is needed.
- Packages can be listed by package name, by an intent filter signature, or by a provider authority
  ([declaring](https://developer.android.com/training/package-visibility/declaring)).
- `<intent>` has restrictions: exactly one `<action>`; the attributes `path`, `pathPattern`, and similar are not used; `mimeGroup` is not allowed.
- `QUERY_ALL_PACKAGES` is for "rare cases": the official advice is to try `<queries>` first, and to use the broad permission only
  when it is really needed.

### 3.2. What we do

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent>
</queries>
```

The documentation does not explicitly describe categories inside `<intent>`. CleanYourTV uses exactly this form and it works
(`AndroidManifest.xml`, the `<queries>` block), so the form is workable. **To verify** on the TV: the tile list must match
the launcher's list from `queryIntentActivities`.

We do not use `QUERY_ALL_PACKAGES`. This follows the documentation's rule of requesting the smallest necessary visibility.

---

## 4. Images and memory

### 4.1. Official

- The memory footprint depends on the dimensions and the `Bitmap.Config`. ARGB_8888 uses 4 bytes per pixel, which is the default
  ([bitmaps](https://developer.android.com/topic/performance/memory/guide/bitmaps)).
- Two-pass loading: first `inJustDecodeBounds = true` (no memory is allocated, only the size and MIME type), then `inSampleSize`
  and the real decode ([load-bitmap](https://developer.android.com/topic/performance/graphics/load-bitmap)).
- `inSampleSize` is a power of two; the decoder rounds down. The target is the size of the component that shows the picture.
- Official: check the dimensions before decoding if the source size is not predictable.
- Google recommends libraries (Glide, Coil, Picasso, Fresco). We do not use them: they add new dependencies, and the task is simple.
  The decision is recorded in the plan (section 11.5).

### 4.2. What we do

- **Banner size** (section 5.2 of the plan): `clamp(180 dp × 1.10 × density, 320, 720)` pixels. This is the decoder target.
- **Decoding in the background.** Scanning and decoding are not done on the main thread (`ExecutorService`, section 5.2 of the plan).
- **Two-pass decoding.** The dimensions are read without allocating memory, and `inSampleSize` is computed with the function from the
  official example (a power of two, not below the target).
- **Cache format.** Lossy WebP on API 30 and later, as in section 5.2 of the plan.
- **Pixel configuration.** For banners without transparency `RGB_565` can be used (half the memory). For banners with transparency, `ARGB_8888`.
  Choose by the `hasAlpha` flag of the source. **To verify** that `RGB_565` does not produce visible banding on gradients
  (on the TV, as in Couchy's `wallpaper_true_color`).
- **Hardware bitmaps** (`Bitmap.Config.HARDWARE`): the pixels live in GPU memory. The benefits and limits are not confirmed
  (the search did not give a full list). **Not used** until checked on the TV.
- **`prepareToDraw()`** after creating a bitmap in the background: uploads the texture before the first frame (Couchy `AppRepository.kt:155`).
- **Releasing memory under pressure.** See section 5.6 of the plan and [RISKS.md](RISKS.md), item 43.

### 4.3. `ImageDecoder`

Official API level: 28 and later. Our `minSdk` is 26, so a second code path would be needed. It is simpler to keep one path on
`BitmapFactory` (it works on all versions), so `ImageDecoder` is not used.

---

## 5. Startup and performance

### 5.1. Baseline Profiles (official)

- Profiles are shipped inside the APK through `androidx.profileinstaller`. Google Play cloud profiles are unavailable on API 24–26,
  so a bundled profile replaces them ([baseline-profiles](https://developer.android.com/baseline-profiles)).
- ART uses the profile at install time on Android 9, and on Android 7 with `ProfileInstaller`
  ([baseline-profiles](https://developer.android.com/baseline-profiles)).
- Google describes an additional gain of about 15% at startup from Startup Profiles. The figures from blogs (20–40%) are not verified.
- **Community (to verify):** the `profileinstaller` version must match the AGP version. Otherwise the profile is silently not installed
  ([blog](https://dev.to/software_mvp-factory/android-baseline-profiles-and-macrobenchmark-in-2026-22o3)).
- **Measuring the gain:** Macrobenchmark (official, in the codelab
  [Improve app performance with Baseline Profiles](https://codelabs.developers.google.com/android-baseline-profiles-improve)).

Decision: add the Baseline Profile in M6 (section 15 of the plan), once there is a working launcher that needs the profile.

### 5.2. RecyclerView (community, there is no official page)

- `setHasFixedSize(true)` when the size of the RecyclerView does not depend on its content. Our tile sizes are fixed by the
  formula in section 7.2 of the plan.
- `setItemAnimator(null)` when list animations are not needed (as in CleanYourTV, `MainActivity.java:678`).
- Do not change the layout in `onBindViewHolder`: sizes are set once (section 7.3 of the plan).
- Prefetch: `GridLayoutManager` has it enabled by default. A custom `LayoutManager` must override `collectAdjacentPrefetchPositions()`
  (the official `RecyclerView` reference).
- A shared `RecycledViewPool` is useful when several lists share a view type. We have one list, so it is not needed.
- Checking: a GPU profiler and `dumpsys gfxinfo` (section 12.3 of the plan). The community advice
  ([blog](https://medium.com/rosberryapps/how-to-cook-the-60-fps-recyclerview-3e7f3885a55)) is not taken on trust.

### 5.3. General code

- Do not allocate memory on hot paths: `onDraw`, `onBind`, and key handlers. Example from section 2.2 of the plan:
  `BarBand` reuses its `Paint` and `RectF`.
- Primitives instead of wrappers: `int` instead of `Integer` in loops, and `SparseIntArray` instead of `HashMap<Integer, …>` for
  numeric keys (a common Android practice, **to verify** with a measurement if it becomes a bottleneck).
- Keep the main thread free: network, disk, and decoding run in the background. `StrictMode` in debug builds catches this kind of
  mistake (a well-known practice; the official page was not read during this research).

---

## 6. Video player (Media3)

### 6.1. Official

- The version in the official example page is `androidx.media3:media3-exoplayer:1.11.1`
  ([hello-world](https://developer.android.com/media/media3/exoplayer/hello-world)). This matches our choice.
- Creation through `ExoPlayer.Builder(context).build()`.
- **Threads:** ExoPlayer must be accessed from a single thread, usually the main one. For UI components the main thread is
  required. The error "Player is accessed on the wrong thread" indicates a violation.
- Output: `setVideoSurfaceView(SurfaceView)`, `setVideoTextureView`, or `setVideoSurface` to choose the surface.
- Release: call `release()` when the player is no longer needed. This frees the hardware decoders.
- The official page recommends `android:keepScreenOn="true"` for playback. **We do not set it for the Home wallpaper:**
  it is not user-initiated playback, and TV-BU and TV-BY allow the device to enter ambient mode.

### 6.2. Community (to verify)

- `SurfaceView` is preferred over `TextureView`: it uses less power, and the compositor runs apart from the UI thread
  ([PlayerView](https://developer.android.com/media/media3/ui/playerview), from search). Already in the plan.
- Audio focus: `setAudioAttributes(attributes, handleAudioFocus)`. Volume 0 does not release the focus, so
  `handleAudioFocus = false` for background wallpapers (RISKS, item 23). **Check on the TV** with music playing in the background.
- From a blog (search): `release()` frees the decoders and the audio focus, if it was taken.

### 6.3. What we do

- One instance for the lifetime from `onStart` to `onStop` (section 6.1 of the plan).
- `setVolume(0)`, `REPEAT_MODE_ONE`, `SCALE_TO_FIT_WITH_CROPPING`, as in the plan.
- Audio focus turned off (see 6.2).
- `release()` in `onStop`, not in `onDestroy`: the decoder is released when the app leaves the screen.

---

## 7. Gradle, R8, and the language

- **Java 17** as `sourceCompatibility` (AGP 8.5 supports it, as in CleanYourTV).
- **Records:** according to the Android Developers article ([records](https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html)),
  D8 and R8 rewrite them into ordinary classes. AGP support starts from version 8.1, and compiling needs SDK 34.
  So records technically work. The plan earlier said "no `record`, because of a risk on Android 11". **That was not accurate:**
  the issue is not the runtime but unnecessary complexity. We simply do not use them, and there is no reason in the code to do so.
- **R8:** `shrinkResources true` and `minifyEnabled true` (as in CleanYourTV). If reflection or record `toString` is used,
  keep rules are needed (from the official records article).
- **Names:** `-dontobfuscate` (as in CleanYourTV): readable stack traces for sideloaded builds.
- **Dependencies:** only `recyclerview`, `dynamicanimation`, `media3-exoplayer`, `profileinstaller` (with a Baseline Profile),
  and `junit` for tests. The `profileinstaller` version belongs to the same release line as AGP.

---

## 8. Package visibility and HOME: what matters at startup

- Listen to `ACTION_PACKAGE_ADDED` and `ACTION_PACKAGE_REMOVED` with the `package:` filter, only while Home is on screen
  (section 9.4 of the plan). This is not a background service.
- The launcher's own package does not need to be in `<queries>`: we can see ourselves.

---

## 9. What was corrected in the plan after the research

| What the plan said | What is correct | Where |
|---|---|---|
| "No `record`: a runtime risk on Android 11" | Records technically work (AGP 8.1 or later, SDK 34). We simply do not use them, because of complexity, not the runtime | plan section 9.2, plan section 14 |
| Media3 version 1.11.1 | Matches the official example | plan section 11.5 |
| No requirement for the TV banner was listed | Official: a 320×180 px banner with the app name (TV-LB, TV-BN) | section 2 of this file, plan section 11.2 |
| No event types were specified for the service | For the remote service, `typeNone` and `canRetrieveWindowContent="false"` | section 1 of this file, plan section 7.8 |

## Sources

Official:

- [AccessibilityService: guide](https://developer.android.com/guide/topics/ui/accessibility/service)
- [AccessibilityService: reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) (the page was truncated; the key details were not read)
- [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality)
- [Package visibility: declaring](https://developer.android.com/training/package-visibility/declaring)
- [Bitmaps and memory](https://developer.android.com/topic/performance/memory/guide/bitmaps)
- [Loading large bitmaps efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap)
- [Baseline Profiles](https://developer.android.com/baseline-profiles)
- [ExoPlayer: getting started](https://developer.android.com/media/media3/exoplayer/hello-world)
- [PlayerView (Media3 UI)](https://developer.android.com/media/media3/ui/playerview)
- [Records in Android Studio Flamingo](https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html)
- [GridLayoutManager: reference](https://developer.android.com/reference/androidx/recyclerview/widget/GridLayoutManager)

Community (check before use):

- [Joshsoftware: AccessibilityService and keypress (2018)](https://blog.joshsoftware.com/2018/03/28/android-accessibility-service-customization-for-keypress-event/)
- [Microsoft mirror of the `flagRequestFilterKeyEvents` reference](https://docs.microsoft.com/en-us/dotnet/api/android.accessibilityservices.accessibilityserviceinfo.flagrequestfilterkeyevents)
- [dev.to: Baseline Profiles and Macrobenchmark (2026)](https://dev.to/software_mvp-factory/android-baseline-profiles-and-macrobenchmark-in-2026-22o3)
- [Medium: 60 FPS RecyclerView](https://medium.com/rosberryapps/how-to-cook-the-60-fps-recyclerview-3e7f3885a55)

Not confirmed in this research: `Bitmap.Config.HARDWARE`, `WEBP_LOSSY` and API 30, the behaviour of `handleAudioFocus`
with `SurfaceView`, the behaviour of the service alongside `flar2.homebutton`, and launching an activity from a service on TV 11.
