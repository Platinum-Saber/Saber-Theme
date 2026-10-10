# Mascot follow-ups: finger reactions, charging glow, WhatsApp cloud

## Context
Saber already pokes, drags, dances, sleeps and has idle moments (handoff `handoffs/m4-mascot-and-polish.md`). Four new reactions were requested: duel a nearby finger, point at a far one, a golden sword while charging, and a WhatsApp message cloud with a preview. Settled choices: while charging she **plants the sword and it glows**; tapping the cloud **previews, and tapping the preview opens the chat**; there is a **"Message cloud" setting, on by default**.

Constraint (unchanged): everything she shows is drawn into `MascotSurface` (SurfaceView). That includes the cloud and its text. Compose only hosts invisible touch boxes, so Home's glass never redraws because of her.

## Steps (one commit + push each, tested on the S23)

### 1. Finger reactions (look + duel near / look + point far)
- **Touch position**: `GlassEnvironment` gets `lastTouch: Offset` (window px), `touchDown: Boolean` and `touchDownNanos`. Set them in `glassInteractionTracker` (`EffectsController.kt:118`): convert the first change's position to window coordinates with the root's `LayoutCoordinates`. It still only observes (Initial pass) and never consumes.
- **MascotBrain** (pure, tested): new moods `Duel` and `Point` plus `fun fingerDown(near: Boolean, now)`, `fingerMoved`, `fingerUp(now)`.
  - Ignored while Held / Falling / Crying / Pout / Dizzy. Sleeping keeps its existing start-awake (Surprised) behaviour.
  - Near (Duel) lasts while the finger is down, plus 1.4 s after it lifts. Far (Point) does the same with 1.8 s. A finger that drags across the threshold switches the mood.
  - They interrupt Idle, Moment, Wander, Walking, Dancing, Happy and Surprised. Walking and dancing resume through the existing `tick` rules.
- **MascotLayer**: each frame, watch `env.touchDownNanos` / `touchDown` and skip touches inside her own box or the cloud box. Also skip them when `alpha() < 0.5` (drawer or settings over Home). Distance is measured from her chest: **≤ 130 dp is near**, anything farther is far. Keep `motion.target` (layer px) up to date while the finger is down.
- **Pose**: add `gazeX/gazeY` (−1..1, lerped). `eye()` in `SaberRig.kt` shifts the pupils for `Eyes.Open`. Add `Prop.Point`: the hand is drawn with an extended index finger.
- **Aiming** (in `Motion.animate`): `facing` turns toward the target. Head turn, tilt and gaze come from the direction. The arm angle comes from `atan2` (shoulder→target), converted to the rig's "outward from hanging" convention and clamped 0..175°.
  - Point: right arm aims straight at the target, with a small settle wobble. Face is `Brows.Calm`, mouth open.
  - Duel: `Prop.Sword` with the arm aimed at the target ±35° at ~3 Hz, so the blade swishes across the finger. Small hops, `Eyes.Happy` alternating with an open look, blush and a smile so it reads playful. A light `CLOCK_TICK` haptic on the first swing only.
- Tests: `MascotBrainTest` covers near→Duel, far→Point, linger after lift, being ignored while Held/Crying, and the crossover between near and far.

### 2. Charging: planted sword with a golden shimmer
- `MascotLayer(charging: () -> Boolean)`, wired in `HomeActivity` from `widgets.battery` (`BatterySource`, which already exposes `charging`), the same way as `musicPlaying` (`HomeActivity.kt:82`).
- `Pose.Guard` is a new gallery expression: sword planted point-down in front, both hands on the pommel, calm brows. While charging, `basePose` maps Idle to Guard. Other moods override it as usual, and Dancing still wins when music plays.
- `Pose.glow: Float` (lerped, so it fades in and out) is set to 1 while charging, for any sword pose. `sword()` gets `time`:
  - gold blade gradient
  - halo: 3 widening, low-alpha gold strokes along the blade (no blur filter)
  - a bright band sliding from hilt to tip every ~1.6 s
  - 2–3 small twinkling `star()` sparks
  This needs `arm()` to pass `time` through.
- The gallery gets "Guard" and "Guard (glow)".

### 3. WhatsApp notifications → `ChatNotifications`
- `MediaListenerService` (`core/widgetdata/.../MediaSource.kt:26`) overrides `onListenerConnected` (seeds from `activeNotifications`), `onNotificationPosted` and `onNotificationRemoved`.
  - Packages: `com.whatsapp`, `com.whatsapp.w4b`.
  - Skips `FLAG_GROUP_SUMMARY` and ongoing notifications.
  - Extracts the chat title, plus the latest `EXTRA_MESSAGES` entry (sender + text, `MessagingStyle`), falling back to `EXTRA_TEXT`. Also keeps `contentIntent`, `postTime` and the key.
- New `ChatNotifications` in `core/widgetdata`: a process-wide object with `StateFlow<List<ChatMessage>>`, sorted newest first and keyed by notification key. Removal (read on the phone or dismissed) drops the entry.
- Update the class KDoc and the manifest comment: it now reads WhatsApp notifications only. Check `.claude/rules/launcher-manifest.md` and update it if it describes this service.

### 4. Message cloud + preview (drawn on the surface)
- `MascotLayer(messages: () -> List<ChatMessage>)` is wired from `ChatNotifications` in `HomeActivity`, gated on the new setting.
- **Setting**: `GlassSettings.mascotMessageCloud = true`, a `SettingsRepository` key, and a toggle beside the outfit in `SettingsScreen.kt`.
- **Collapsed** (`MessageCloud.kt`, new file in `:feature:mascot`): a small puffy cloud up-left of her head (flips side near the screen edge), with a WhatsApp-green chat glyph and a count badge.
  - Gentle bob; it pops in with a scale spring.
  - When a new message arrives she does a quick Surprised→Curious glance at it.
- **Expanded** (tap the cloud): a bigger cloud bubble with up to 3 chats, each showing a bold sender/chat title and a one-line ellipsized message.
  - Text is drawn with `StaticLayout` on `drawContext.canvas.nativeCanvas`. Layouts are cached per message list, never rebuilt each frame.
  - Tapping a row sends its `contentIntent`, and the cloud collapses. Tapping elsewhere on Home or waiting 8 s also collapses it.
- **Touch**: a second invisible Compose `Box` sized to the cloud's current bounds (collapsed or expanded), positioned with `offset {}` from the same `feet` state as her touch box. Its `pointerInput` handles taps: the row index comes from the y position in the expanded layout.
- Hidden while she is Held or Falling, when `alpha()` is 0, and when the list is empty.

### 5. Docs
`docs/architecture.md` Mascot section (finger reactions, charging, message cloud); handoff open item 4 updated.

## Critical files
- `feature/mascot/.../MascotBrain.kt`, `MascotLayer.kt`, `Pose.kt`, `SaberRig.kt`, new `MessageCloud.kt`, `MascotBrainTest.kt`, debug `MascotGalleryActivity.kt`
- `core/designsystem/.../glass/GlassEnvironment.kt`, `EffectsController.kt`
- `core/widgetdata/.../MediaSource.kt` (listener), new `ChatNotifications.kt`, `AndroidManifest.xml` (comment)
- `app/.../HomeActivity.kt`; `core/model/.../GlassSettings.kt`, `core/data/.../SettingsRepository.kt`, `feature/settings/.../SettingsScreen.kt`

## Verification
- `./gradlew testDebugUnitTest` (new brain tests), `lintDebug`, `assembleDebug` (all through `trim_output.py`, with JAVA_HOME set to Studio's jbr).
- Back up `settings.preferences_pb` before installing (memory `device-layout-backup`), then `installDebug` on the S23.
- On device:
  - Tap near her → she faces the finger and swings. Hold and drag the finger around her → the swings follow it. Tap far (top-left, bottom-left) → she looks and points. Tapping her still pokes. Drawer open → no reactions.
  - Plug in the charger → Guard pose with a shimmering gold blade. Unplug → it fades back to idle.
  - Send a WhatsApp message to the phone → the cloud appears with count 1. Tap → preview with sender and text. Tap the row → the WhatsApp chat opens. Read it on the phone → the cloud disappears. With the toggle off → no cloud.
- Perf: `installBenchmark` + `dumpsys gfxinfo`. The Home window should still draw 0 frames while she idles, duels or shows the cloud.
