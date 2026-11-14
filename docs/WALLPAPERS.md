# Background resources: images and video

A register of sources for the background and the rules for using them. The resources are not copied yet: that happens in
milestone M4 (section 15 of the plan). They may and should be copied when a set from the table below is chosen (section 1.1
of the plan). They must not be imported or referenced from `sources/`.

## What the reference projects contain

| Source | Images | Video | Gradients and background in code | Notes |
|---|---|---|---|---|
| `sources/cleanyourtv` (themes) | Yes: about 20 sets, more than 100 files in total | **No** | Yes (for example, `red-rain`, `istanbul`, and `glass-gallery` are drawn in code) | Each set has its own licence, see `themes/*/CREDITS.md` |
| `sources/couchy-launcher` | **No** files. It has a list of links to the streamed Apple and Amazon aerials (`assets/aerials.json`), which we take (D49) | **No** video files; the streams are reached by links | Yes: gradient presets in `ui/Theme.kt`, `Aurora` is the basis of our poster | The Twemoji icons (MIT) and the Cookie font (OFL) are not needed for the background |

Conclusion: background images are taken from CleanYourTV. From Couchy we take only the gradient values (facts, not code).
Neither project contains video, so the video is either a user file or our own clip (see Q17).

## Sets from CleanYourTV by licence

The groups are based on what can be done without conditions.

### Group A: no conditions (public domain or CC0)

| Set | Contents | Licence | What to keep |
|---|---|---|---|
| `aerial` | About 10 photos taken by ISS astronauts: aurora over Scandinavia, sunrise over Europe, a sunset from orbit, the Nile at night, Moscow under the Moon | Public domain (NASA) | A credit that the photos are by NASA; NASA does not endorse the project |
| `istanbul` | Three layers of Istanbul drawings, drawn in code | CC0 | Nothing |
| `istanbul_v2` | Three illustrated Istanbul landscapes | CC0 | Nothing. The CREDITS file mentions Gemini: **to verify** whether that applies to the files |
| `red-rain` | A red digital rain, drawn in code | CC0 | Nothing |
| `glass-gallery` | Three abstract images, created procedurally | CC0 / public domain | Nothing |
| `cinematic` | Wallpapers | CC0 | Nothing |

### Group B: attribution required (CC BY)

| Set | Contents | Licence | What to keep |
|---|---|---|---|
| `nature-calm` | 13 nature photos: karst, autumn lakes, bluebells, frost, waterfalls | CC BY 4.0 and CC BY-SA 3.0 (some files) | The author and a link for each file. **CC BY-SA does not belong in this group, see below** |
| `storm` | 5 storm photos | CC BY 4.0 (three files) and CC BY-SA 3.0 (two files) | The same |
| `sweden` | 4 photos of Sweden | CC BY 4.0 (two) and CC BY-SA 2.0 / 4.0 (two) | The same |
| `ambient-glass` | Photos of alpine and desert landscapes | Unsplash License (no attribution required, but we give it anyway) | The author's credit |

For group B the following are mandatory: a `CREDITS.md` file in the release, a list of authors and links, and a note that the image
was changed (lightened or darkened, cropped to 16:9). CC BY allows this as long as the changes are stated.

### Group C: not used

| Set | Why |
|---|---|
| `halloween`, `diwali` | Many files are CC BY-SA. Darkening and cropping create a derivative work, which must also be CC BY-SA. That does not fit our simple scheme |
| `storm`, `sweden`, `nature-calm` (the CC BY-SA parts) | The same. We exclude the BY-SA files and keep the rest |
| `weeb`, `twilight`, `family`, `istanbul_v2` (some parts) | Made with Google Gemini. Gemini's terms of use are separate. To check if we want them |
| `retro` | The theme author's own work; the MIT licence covers the code. The pictures need a separate permission |

### Group D: streamed Apple and Amazon aerials (decision D49)

These are not files but a list of links to streamed videos. The files are not downloaded or stored: when the wallpaper is
enabled, the app streams the clip from the link.

| Source | Entries | Key in `aerials.json` | Status |
|---|---|---|---|
| Apple (`sylvan.apple.com`) | 139 | `A` | Taken (D49) |
| Amazon Fire TV (`aebcs-cdn-prod-na.services.firetv.amazon.dev`) | 112 | `Z` | Taken (D49) |
| Community (GitHub) | 38 | `C1` (20), `C2` (18) | Not decided, Q19 |

Rules:

1. The list is carried over as data: JSON with the fields `t` (title), `u` (link), and `s` (source key). This is not code, so
   section 1.1 of the plan does not apply to it: this is copying data.
2. **Trust-all TLS is not carried over.** Couchy turns off certificate checking for `sylvan.apple.com`. We do not do that.
   First we check standard TLS on the TCL. If a server fails the check, its entries are disabled.
3. Streams are enabled only by the user's choice (the "Aerials" wallpaper). The background work does not use the network.
4. Without a network: fall back to a local poster or gradient (section 6.2 of the plan).
5. Attribution in "About": the videos belong to Apple and Amazon and are streamed from their links.
6. Risks: RISKS 45.

## Video

Neither reference project contains video. The options are:

1. **A user file** through `ACTION_OPEN_DOCUMENT` (as in the plan, section 6.1). The licence is the user's own.
2. **Our own clip made from a group A image.** `ffmpeg` makes a looping clip from one frame with a slow pan
   (`zoompan`, or `scale` + `crop`). The clip's licence matches the image's licence. For `aerial` it is public domain.
3. **Download a clip from Wikimedia Commons** under CC0 or public domain. Check each file.

The command for option 2 (check it on your own frame):

```bash
ffmpeg -loop 1 -i frame.jpg -t 30 -vf "zoompan=z='min(zoom+0.0005,1.1)':d=750:s=1920x1080,format=yuv420p" \
       -c:v libx264 -preset slow -crf 22 -an -movflags +faststart wallpaper.mp4
```

## Rules for our files

1. We copy only groups A and B. Group C is never copied.
2. Files go into `assets/wallpapers/<set>/`, with the author's name kept in a `CREDITS.md` next to them.
3. Size: 1920×1080, WebP or JPEG at quality 85–90. Video: H.264, 1920×1080, no sound, 20–60 s.
4. Darkening is done in the app (the scrim from the plan), not by editing the file, so the file keeps its original licence.
   If darkening is done in the file, the result is a derivative work and must be listed in CREDITS.
5. No file is imported from `sources/`. Everything taken is copied and renamed according to our rules.
6. Before a release we check that `CREDITS.md` has a line for every file and that there are no group C files.

## Decisions needed

- Which set to take first. Default: `aerial` (public domain, NASA; space suits a "TV background").
- The video for our own background: a user file, our own clip from `aerial`, or a clip from Commons (Q17).
- Apple and Amazon streams: decided (D49). The community set on GitHub is Q19. The service terms are Q20.
- Whether to include the Unsplash set `ambient-glass` (Q18).
