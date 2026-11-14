# Documentation: a custom home screen for Android TV

The project is documentation-only for now. The app code has not been written. This folder holds everything that has
been decided, verified, and left open. Personal values (IP addresses, personal package names, device identifiers) are
replaced with placeholders such as `<TV_IP>`. The original values are in the local file `private/redactions.md`, which
is not stored in git.

## Files

| File | What it contains | What to read it for |
|---|---|---|
| [PLAN.md](PLAN.md) | The main plan: architecture, screens, video, weather, inputs, remote, build, rollback, tests, milestones M0–M7 | The primary document. Everything else supplements it |
| [DECISIONS.md](DECISIONS.md) | Decision log: what was decided, when, and why | So that settled questions are not asked again |
| [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md) | Open questions and what each one blocks | What must be settled before M0 and before M1 |
| [DEVICE.md](DEVICE.md) | Facts about the TV, collected over ADB (read-only) | Compatibility, packages, inputs, HOME, memory |
| [OPERATIONS.md](OPERATIONS.md) | Installation, state recording, rollback, checks after a reboot | How to change the TV safely |
| [RISKS.md](RISKS.md) | 43 problem cases with handling and verification status | What can go wrong and how to handle it |
| [BEST-PRACTICES.md](BEST-PRACTICES.md) | Practices for services, APIs, images, Media3, and the build | How to write clean code that works on the TV |
| [WALLPAPERS.md](WALLPAPERS.md) | Register of background images and videos: what is taken from CleanYourTV and Couchy, their licences, and the streamed Apple and Amazon aerials | Choosing the background and checking licences |
| [remote-buttons.md](remote-buttons.md) | 23 remote buttons: codes, behaviour, requirements | The remote and button remapping |
| [remote-captures/](remote-captures/) | Raw `getevent` recordings and the `Generic.kl` key layout from the TV | Primary data for the button codes |
| [screen-mockup.html](screen-mockup.html) | Interactive mockup of the home screen, 960×540 dp | Element placement, focus, panels |
| [icons-showcase.html](icons-showcase.html) | Icon showcase: from CleanYourTV, needed by the plan, weather icons | Choosing icons |

## Milestones

| Milestone | Status | What is needed |
|---|---|---|
| Documentation | in progress | Close the open questions in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md) |
| M0 (the `launcher/` skeleton) | not started | Permission to start, a recorded TV state, HOME decisions |
| M1–M7 | not started | See section 15 of [PLAN.md](PLAN.md) |

## How to read the status labels

The documents use three labels:

- **verified**: confirmed on the TV over ADB, by official documentation, or from files in `sources/`;
- **from CleanYourTV / Couchy**: described in their code or documentation, not yet checked on our TV;
- **to verify**: our assumption, which must be confirmed during implementation.

Sources: `sources/cleanyourtv` and `sources/couchy-launcher` (submodules, reference only; the rule is in PLAN 1.1), the
official Android and Google documentation (links in [BEST-PRACTICES.md](BEST-PRACTICES.md) and section 17 of
[PLAN.md](PLAN.md)), live Open-Meteo API responses, and ADB checks on the TV (see [DEVICE.md](DEVICE.md)).

## What is not stored in the repository

- `private/`: personal values, ignored through `.gitignore`.
- `sources/`: other people's projects, included as submodules **for reference only**. Code, resources, and scripts may be copied
  and rewritten from them, but they must not be imported, called, or referenced by the app. The folder is removed after
  implementation (PLAN 1.1, OPERATIONS 9).
- Screenshots and TV recordings, if any are added, are kept separately and contain no personal data in the frame.
