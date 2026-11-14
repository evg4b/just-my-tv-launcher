# Documentation: a custom home screen for Android TV

The app is in `launcher/` and every milestone is built; most of it still has to be checked on the TV. This folder holds
the plan and everything that has been decided, verified, and left open, plus the [user guide](USER-GUIDE.md). Personal values (IP addresses, personal package names, device identifiers) are
replaced with placeholders such as `<TV_IP>`. The original values are in the local file `private/redactions.md`, which
is not stored in git.

## Files

| File | What it contains | What to read it for |
|---|---|---|
| [USER-GUIDE.md](USER-GUIDE.md) | How to use the launcher: navigation, every setting, video preparation, remote buttons | Using TV Home |
| [PLAN.md](PLAN.md) | The main plan: architecture, screens, video, weather, inputs, remote, build, rollback, tests, milestones M0–M7 | The primary document. Everything else supplements it |
| [DECISIONS.md](DECISIONS.md) | Decision log: what was decided, when, and why | So that settled questions are not asked again |
| [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md) | Open questions, and the checks still to run on the TV (T16–T19) | What is not settled or not verified |
| [DEVICE.md](DEVICE.md) | Facts about the TV, collected over ADB (read-only) | Compatibility, packages, inputs, HOME, memory |
| [OPERATIONS.md](OPERATIONS.md) | Installation, state recording, rollback, checks after a reboot | How to change the TV safely |
| [RISKS.md](RISKS.md) | 46 problem cases with handling and verification status | What can go wrong and how to handle it |
| [BEST-PRACTICES.md](BEST-PRACTICES.md) | Practices for services, APIs, images, Media3, and the build | How to write clean code that works on the TV |
| [WALLPAPERS.md](WALLPAPERS.md) | Register of background images and videos: what is taken from CleanYourTV and Couchy, their licences, and the streamed Apple and Amazon aerials | Choosing the background and checking licences |
| [remote-buttons.md](remote-buttons.md) | 23 remote buttons: codes, behaviour, requirements | The remote and button remapping |
| [remote-captures/](remote-captures/) | Raw `getevent` recordings and the `Generic.kl` key layout from the TV | Primary data for the button codes |
| [screen-mockup.html](screen-mockup.html) | Interactive mockup of the home screen, 960×540 dp | Element placement, focus, panels |

## Milestones

| Milestone | Status | What is needed |
|---|---|---|
| M0 (skeleton) | done, checked on the TV | HOME after a reboot (T1) |
| M1 (grid with the bar) | done, checked on the TV | The 60-app scroll check (T16) |
| M2–M5 (menu, settings, inputs, wallpapers, weather) | built, not yet on the TV | T17, T18 |
| M6, M7 (polish, remote buttons) | built, not yet on the TV | Measurements (T16), T19 |

## How to read the status labels

The documents use three labels:

- **verified**: confirmed on the TV over ADB, by official documentation, or from the reference projects' files;
- **from CleanYourTV / Couchy**: described in their code or documentation, not yet checked on our TV;
- **to verify**: our assumption, which must be confirmed during implementation.

Sources: the reference projects CleanYourTV and Couchy (read, never imported; PLAN 1.1; removed from the repository on
2026-11-14, D98), the
official Android and Google documentation (links in [BEST-PRACTICES.md](BEST-PRACTICES.md) and section 17 of
[PLAN.md](PLAN.md)), live Open-Meteo API responses, and ADB checks on the TV (see [DEVICE.md](DEVICE.md)).

## What is not stored in the repository

- `private/`: personal values, ignored through `.gitignore`.
- Other people's projects. CleanYourTV and Couchy were attached as submodules in `sources/` for reference while the app was
  built, and removed afterwards (PLAN 1.1, OPERATIONS 9, D98). What was taken from CleanYourTV is credited in
  `LICENSE-CleanYourTV`.
- Screenshots and TV recordings, if any are added, are kept separately and contain no personal data in the frame.
