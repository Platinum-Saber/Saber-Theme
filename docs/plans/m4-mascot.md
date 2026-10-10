# Next phase: interactive Saber chibi mascot on Home

## Context
You want a chibi **Saber (Fate)** that lives by the Home search bar, animates on her own and reacts when poked or dragged. Your three references set the style: big head, braided bun with a blue ribbon, ahoge, green eyes, blue dress with silver armour and gauntlets. They also set the expressions: neutral, blank white-eyed shock with a triangle mouth, wailing with tears.

I couldn't reach Pinterest pins from search. The hits were other Saber chibi emotes, e.g. a cheering one on [CommunityOne](https://communityone.io/emoji/seibah-yay-cheer-1377372844425609257/) and a crying one ([sabercry](https://communityone.io/emoji/sabercry-1513572302527266836/)). These only confirm the pose and expression set; nothing is copied from them.

Your choices:
- **Vector rig drawn in code.** It's an original drawing of Saber, made by me, for personal use on your phone; the launcher isn't published.
- **Behaviours:** idle life, poke reactions, drag and throw, and reacting to the phone and music.
- **Always animating** while Home is visible.

The last choice breaks the glass rule "a still phone renders 0 frames". The mascot gets a documented exception, kept to her own small layer.

## Architecture
- **New module `:feature:mascot`** (Compose; depends on `:core:designsystem` for `GlassEnvironment` tilt/`reducedMotion`/`GlassMotion`, and `:core:model`). It doesn't depend on any other feature; `:app` composes it.
- **`HomeScreen` gets a slot** `overlay: @Composable (anchor: () -> Rect) -> Unit`, where `anchor` is the search pill's window bounds (`onGloballyPositioned` on `SearchPill`). The slot is drawn above the pager and dock, below folders and menus.
  - The mascot is hidden while editing, or while a folder or menu is open.
  - She fades with the drawer/settings `backgroundBlur`.
  - `HomeActivity` fills the slot with `MascotLayer(anchor, musicPlaying)`.
  - `musicPlaying` comes from the `WidgetSources.media` state (`MediaState.now?.playing`), already injected in `HomeActivity`.
- **Touch:** only her own bounds take pointers (offset box). Everything else (search pill tap, swipe-up, pager) passes through. Her `pointerInput` consumes its own gestures so the search pill under her never opens.
- **Pure logic in Kotlin, unit-tested:**
  - `MascotBrain`: state machine.
    - Inputs: `tick(dt)`, `poke`, `longPress`, `grab`, `drag`, `release(velocity)`, `land(impact)`, `music(playing)`, `tilt(x)`.
    - Output: the current `Activity` and a target `Pose`.
  - `MascotPhysics`: position, velocity, gravity, wall/floor bounces, damping.
    - The "floor" is the top edge of the search pill across the screen width. She stands on the bar.
- **Rendering:**
  - `SaberRig` draws a `Pose` on a `Canvas` in a 100×140-unit box, about 72 dp tall.
  - The parts: back braid/bun, ribbon, back hair, legs/boots, skirt + frill, dress + breastplate, arms/gauntlets (shoulder pivots), head, face (eyes, mouth, blush, tears), bangs, ahoge (Bézier with sway), and an optional sword.
  - A `Pose` is a small data class: body/head tilt, ahoge angle, arm/leg angles, squash and stretch, bounce, facing, eye and mouth variants, blush, tears, zzz, sword.
  - Poses blend with springs (`GlassMotion`); variants swap.
- **Frame loop:** `withFrameNanos`, capped at ~30 fps.
  - It runs only while Home is resumed and visible (no drawer, settings or edit).
  - Power Saving or thermal throttling (`EffectsPolicy` Saver/Throttled) drop it to idle bursts.
  - `reducedMotion` gives static poses with snap transitions and no physics animation (dropping her places her at the anchor).
  - All drawing sits in her own `graphicsLayer`, so glass nodes never re-record because of her.

## Behaviours (v1)
- **Idle (always on):** breathing bob, ahoge sway, blinks every 2–6 s. Every 8–20 s a random moment:
  - looking left or right;
  - a stretch;
  - sword practice, a swing with a gleam;
  - walking a few steps along the bar and turning;
  - sitting on the bar edge, legs swinging.
- **Sleep:** after 60 s without touches she sits, eyes close, and "zzz" drifts up. A touch anywhere on Home wakes her with a start.
- **Pokes:**
  - one poke: a surprised hop with white eyes and a triangle mouth, then back to neutral;
  - 3 pokes within 2 s: a pout (puffed cheeks, looks away);
  - 5 or more: crying (tears and wailing, like reference 2), then she recovers after about 3 s.
- **Long-press:** happy, with eyes closed in a smile, blush and a little twirl.
- **Drag:**
  - She's held at the grab point and dangles: her body swings opposite to the finger's movement, her legs kick and her arms flail.
  - On release she's thrown with the finger's velocity. Gravity applies, she bounces off the screen edges and lands on the bar.
  - Landing impact sets the reaction: soft gives a cute landing; hard gives dizzy stars; very hard gives crying.
  - She then walks back toward the anchor, the right end of the search bar.
- **Phone tilt:** she leans and her hair sways with `env.light`/tilt. She stays tilt-reactive even while idle, as the glass now does.
- **Music:** while the Media widget's player is playing, she nods and sways on a ~110 bpm bob. Sleep is suspended. The app gives no beat data, so this is a steady groove, not beat-matched.
- **Haptics:** a light tick on a poke and on pickup, a soft one on landing.

## Settings
- `GlassSettings.mascotEnabled` (default on) in DataStore (`SettingsRepository` key).
- Settings → new **Mascot** group with a "Show Saber" switch (`GlassSwitch`).

## Steps (commit + push after each)
1. **Rig + poses.** Create `:feature:mascot`, `SaberRig`, `Pose`, the face variants and the anchor slot in `HomeScreen`. She stands statically at the anchor. Also a debug-only pose gallery (Glass Lab entry) to review every expression on the S23. **You check the look here before I animate it.**
2. **Brain + physics + interactions.** `MascotBrain`, `MascotPhysics` with unit tests for the transitions, poke counting, sleep timer, landing impact classes and floor/wall bounces. Then poke, long-press, drag and throw, and the return walk.
3. **Life.** The idle moments, sleep, tilt lean, music groove, the settings toggle, the frame-loop gating (resume/overlays/power/`reducedMotion`) and haptics.
4. **Perf + docs.**
   - Measure on a `benchmark` build: `gfxinfo` while idle on Home, and swipes, compared with before.
   - Target: no extra jank, and her layer under ~1 ms of draw time.
   - Docs: `docs/architecture.md` gets a Mascot section, and `.claude/rules/glass-rendering.md` gets the 0-frames exception (mascot layer only, gated as above). CLAUDE.md is unchanged.

## Files
- New: `feature/mascot/` (`build.gradle.kts`, `MascotLayer.kt`, `SaberRig.kt`, `Pose.kt`, `MascotBrain.kt`, `MascotPhysics.kt`, tests), plus `settings.gradle.kts`.
- Changed:
  - `feature/home/.../HomeScreen.kt`: the overlay slot and the search-pill anchor;
  - `app/.../HomeActivity.kt` and `app/build.gradle.kts`: compose the mascot and pass media playing;
  - `core/model/.../GlassSettings.kt` and `core/data/.../SettingsRepository.kt`: `mascotEnabled`;
  - `feature/settings/...`: the Mascot group;
  - docs and rules.
- Reused:
  - `GlassEnvironment` (tilt, `reducedMotion`);
  - `GlassMotion` springs;
  - `EffectsPolicy` modes;
  - `GlassSwitch`;
  - the `WidgetSources.media` state.

## Verification
- Every step: `./gradlew assembleDebug testDebugUnitTest lintDebug`. Install on the S23, with screenshots plus short screen recordings (`adb shell screenrecord --time-limit 8`) for the motion.
- Step 1: the pose gallery for your review.
- Step 2: the scripted gestures, `input tap` for pokes and `input motionevent` for drag and fling:
  - check reactions, the landing on the bar and the walk back;
  - the search pill doesn't open when you poke her;
  - swipe-up and the pager still work around her.
- Step 3: the sleep timer, waking her up, playing VLC/Spotify (groove), the settings toggle, and Power Saving reducing her to bursts.
- Step 4: the `benchmark` `gfxinfo` numbers are reported. No crashes in `logcat -b crash`.
- Back up and restore `settings.preferences_pb` around any on-device settings tests (memory `device-layout-backup`).
