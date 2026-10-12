# Handoff: glass pill Saber slashes

**Goal:** a glass pill around the camera that you drag onto Home, and Saber runs, leaps and slashes it in half.

## Decisions
- Drawn on her `MascotSurface` with `GlassBlobPainter`, never in Compose. `GlassPill.kt` follows the `MessageCloud` pattern: logic and drawing in one class, plus a touch box in `MascotLayer`.
- One UI hands a touch that starts within about 40 dp of the status bar to its own pull-down gesture (`transferTouchGesture` in logcat). The docked pill is therefore pulled from a strip 16–56 dp below it; the strip is invisible (owner asked for no handle).
- Brain moods `Chase` and `Leap`, and `MascotPhysics.leapTo` (pure, tested). Her landing after a Leap is always Happy. Grabbing the pill again calls `pillTaken`.
- Setting: Settings → Mascot → Glass pill (`mascotPill`, default on).

## State
- **Verified on the S23** (screenrecord frames):
  - The pill sits around the camera.
  - It drags down from the strip under it.
  - She runs, leaps and slashes, and the streak and both halves show.
  - It reforms around the camera after 3 s.
- **Untested:**
  - The ground swing (pill dropped low).
  - The snap-back near the dock.
  - Re-grabbing it mid-chase.
  - The setting toggle.
  - "Remove animations".
  - The perf numbers (`dumpsys gfxinfo framestats`).

## Next step
The owner should try a low drop and a re-grab. Then run the perf check, and the 12-swipe benchmark from `handoffs/icons-tint-recents.md`.
