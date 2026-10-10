# Handoff: mascot follow-ups (finger reactions, charging glow, WhatsApp cloud)

**Goal:** Implement the approved plan `docs/plans/m4-mascot-followups.md`, one step per commit, testing each on the S23.

## Decisions
- Near touch (≤ 130 dp from her chest): she faces the finger and swings her sword at it playfully, following it while it's held down. Far touch: she looks at it and points. Touches on her box or the cloud box are skipped, and so is everything while `alpha() < 0.5`.
- Touch position comes from `glassInteractionTracker` (it observes only, never consumes) through new `GlassEnvironment` fields. There's no new full-screen pointerInput, because sibling hit-testing would steal touches.
- Charging: Idle maps to a new `Pose.Guard` (sword planted point-down, hands on the pommel). Any sword pose gets a gold shimmer through a lerped `Pose.glow`. The halo is drawn with alpha strokes; no blur filter.
- WhatsApp (`com.whatsapp`, `com.whatsapp.w4b`): the existing `MediaListenerService` is extended to read their notifications into a process-wide `ChatNotifications` StateFlow. Group summaries and ongoing notifications are skipped. An entry is dropped when its notification is removed.
- Cloud: drawn on `MascotSurface`, never in Compose; the text is drawn with a cached `StaticLayout`.
  - Tap the cloud → preview (up to 3 chats). Tap a row → `contentIntent.send()`.
  - It collapses after 8 s or a tap elsewhere.
  - The "Message cloud" setting defaults to on.
- Unchanged: commit + push per step with `Co-Authored-By: Claude Opus 5.5`; back up `settings.preferences_pb` before device tests (memory `device-layout-backup`).

## Files
- `docs/plans/m4-mascot-followups.md`: the full approved plan (steps 1–5, verification).
- `feature/mascot/.../MascotBrain.kt`: moods. Add `Duel`/`Point` and `fingerDown/Moved/Up`. Pure Kotlin, tested in `MascotBrainTest.kt`.
- `feature/mascot/.../MascotLayer.kt`: frame loop, `basePose`, `Motion.animate` (aiming goes here), touch boxes.
- `feature/mascot/.../Pose.kt`: add `gazeX/gazeY`, `glow`, `Prop.Point`, `Guard`, and gallery entries.
- `feature/mascot/.../SaberRig.kt`: `eye()` pupils, `arm()` → `sword(hand, angle)` (line ~250; add time + glow), pointing hand.
- `core/designsystem/.../glass/GlassEnvironment.kt`, `EffectsController.kt:118` (`glassInteractionTracker`): touch position.
- `core/widgetdata/.../MediaSource.kt:26`: `MediaListenerService`. Also `AndroidManifest.xml` (its comment says notifications are never read; update it) and `BatterySource.kt` (`charging`).
- `app/.../HomeActivity.kt:82,121`: wires `musicPlaying`; add `charging` and `messages` the same way.
- `core/model/.../GlassSettings.kt`, `core/data/.../SettingsRepository.kt`, `feature/settings/.../SettingsScreen.kt`: the `mascotMessageCloud` toggle.

## State
- Done and verified on the S23: finger duel/point, charging guard + glow, WhatsApp feed, glass message cloud (red dot) + Thick-glass preview, drag spring-back.
- Not verified on device: tapping a chat opens WhatsApp, and flick-to-dismiss (both would touch the owner's real chats; left for the owner to try). The "Message cloud" setting toggle wasn't tried on device either.
- Earlier open items (calendar PROVIDER_CHANGED refresh, dock gap, etc.) are in `handoffs/m4-mascot-and-polish.md` and come after this work.

## Next step
Step 1:
1. Add `lastTouch`/`touchDown`/`touchDownNanos` to `GlassEnvironment` and set them in `glassInteractionTracker` (window coords).
2. Add the `Duel`/`Point` moods with tests to `MascotBrain`.
3. Add aiming (facing, head/gaze, arm `atan2`), gaze pupils and the pointing hand.
4. Run `testDebugUnitTest`, then `installDebug`, and test near/far taps on the S23.
5. Commit and push.
