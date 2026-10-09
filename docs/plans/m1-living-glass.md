# Milestone 1 — Living-glass engine + Home screen

## Context
Architecture doc (`docs/architecture.md`) and the Figma file (built by `design/figma-plugin/code.js`) are approved. Repo still has no Gradle project. The user wants the liquid-glass UI to feel **alive**: glass that reacts to touch, device tilt, motion and its surroundings. Decisions:
- v1 reactive behaviours: **touch response, tilt highlights, motion physics, ambient adaptation** (all four).
- Battery: **adaptive** — full effects while interacting; sensors paused when idle >3 s, on Power Saving or thermal throttling; user intensity slider.
- Milestone 1 = **glass engine + home** (scaffold, reactive GlassSurface, wallpaper, home pager with app grid + dock, installable as default launcher on the S23). Widgets, drawer, settings follow in M2+.

## Design principle: "Living glass" (add to docs/architecture.md)
1. **Glass answers every touch** — press compresses (spring), light blooms from the finger, haptic tick; release overshoots slightly.
2. **Light comes from the world** — a virtual light follows device tilt; rim highlights and wallpaper parallax move with it.
3. **Nothing teleports** — every state change is a spring or a shape morph from its origin (icon → folder, dock → sheet).
4. **Glass adapts to what's behind it** — tint and highlight adjust per surface to keep text ≥ 4.5:1 on any wallpaper.
5. **Alive, never busy** — effects idle out after 3 s, respect Power Saving, thermal state and "Remove animations".
6. **Draw-phase only** — sensor/animation values are read in draw/graphicsLayer lambdas, never in composition (no recomposition at 120 Hz).

## Reactive architecture (in `:core:designsystem`)
```
GlassEnvironment (CompositionLocal, provided at HomeActivity root)
 ├─ light: State<Offset>          ← TiltSource (TYPE_GAME_ROTATION_VECTOR, SENSOR_DELAY_GAME, low-pass)
 ├─ parallax: State<Offset>       ← tilt + pager scroll offset
 ├─ intensity: State<Float>       ← EffectsPolicy (user slider × power-save × thermal × idle)
 ├─ reducedMotion: Boolean        ← Settings.Global.ANIMATOR_DURATION_SCALE == 0
 └─ backdrop: GlassBackdrop       ← cached blurred wallpaper + WallpaperPalette (luminance/colour grid)
```
- `EffectsPolicy` — pure-Kotlin state machine (Active / Idle / Saver / Throttled) fed by `PowerManager` power-save broadcasts, `addThermalStatusListener`, and an interaction-idle timer; TiltSource only registers while lifecycle is RESUMED and policy is Active. Unit-testable.
- `WallpaperPalette` — computed once per wallpaper on `Dispatchers.Default` from a downsampled bitmap; `sample(rect)` → mean luminance + dominant colour; drives the adaptive tint solver (`tintAlphaFor(luminance, textTone)` clamped to material min/max) and the accent colour.
- `GlassMotion` tokens — named springs: `press`, `release` (dampingRatio 0.6), `page`, `sheet`, `morph`; snap when reducedMotion.

### GlassSurface API
```kotlin
@Composable fun GlassSurface(
  modifier: Modifier = Modifier,
  shape: GlassShape = GlassShape.Rounded(28.dp),
  material: GlassMaterial = GlassMaterial.Regular,   // Thin / Regular / Thick (tokens = Figma values)
  interactionSource: MutableInteractionSource? = null,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
)
```
Built from modifiers so other components can compose them: `Modifier.glassBackdrop()` (samples cached blurred wallpaper at the node's window position + parallax), `Modifier.glassShader()` (AGSL: rounded-rect SDF → edge refraction, specular rim = f(normal, light), press bloom lens at touch point, anisotropic stretch from drag velocity, adaptive tint), `Modifier.glassPress()` (pointer position → `Animatable` scale 0.96 + bloom progress, `performHapticFeedback(CLOCK_TICK)` on down, `VIRTUAL_KEY` on click), `Modifier.glassStretch()` (VelocityTracker → stretch vector, springs back).
Live (non-static) backdrop only for overlays (context menu, later sheets/folders): content layer recorded with `rememberGraphicsLayer()` + `RenderEffect` blur while the overlay is visible.

**Engine spike first (step 4):** implement own AGSL path and compare with Kyant `backdrop` liquid-glass library on the S23 (`dumpsys gfxinfo`: p90 frame < 8 ms at 120 Hz, jank < 1 %). Record the pick in docs/architecture.md; the GlassSurface API above stays the same either way.

## Milestone 1 modules
| Module | M1 contents |
|---|---|
| `build-logic` | convention plugins: `saber.android.application`, `saber.android.library`, `saber.compose`, `saber.hilt` |
| `:app` | `HomeActivity` (MAIN/HOME/DEFAULT, `singleTask`, configChanges handled, `windowShowWallpaper=false`), Hilt app, GlassEnvironment provider, debug-only **Glass Lab** sheet (toggle each effect, intensity, frame-time overlay via JankStats) |
| `:core:model` | `AppEntry`, `HomeLayout`, `DockSlot`, `GlassSettings` |
| `:core:data` | `AppRepository` (LauncherApps + Callback, Flow), DataStore (layout, glass settings), `WallpaperStore` (bundled Aurora Dawn/Night as WebP) |
| `:core:designsystem` | tokens (colors, Google Sans Flex via downloadable fonts, materials, motion), reactive layer above, AGSL shader in `res/raw` or Kotlin string |
| `:core:icons` | generated VectorDrawables + `IconMapper` (package → glyph, fallback monochrome adaptive layer, then letter glyph) |
| `:feature:home` | `WallpaperLayer` (parallax), `HorizontalPager` (2 pages, rubber-band edges), app grid 4×5 + folders (closed state), dock, search pill (visual only), page indicator, long-press glass context menu, app launch via `LauncherApps.startMainActivity` with clip-reveal from icon bounds |

Package / applicationId: `com.sabertheme.launcher` (changeable). minSdk 33 (AGSL), compile/target 36. All deps via `gradle/libs.versions.toml` at latest stable versions at scaffold time (AGP, Kotlin 2.x + Compose compiler plugin, Compose BOM, Hilt, DataStore, Lifecycle, JankStats).

## Single source for glyphs
Move `APPS`/`UI` glyph data out of `design/figma-plugin/code.js` into `design/icons/glyphs.js`; add Node scripts:
- `tools/build-icons.mjs` → converts each glyph (path/circle/rect → pathData) to `core/icons/src/main/res/drawable/glyph_*.xml` (stroke 1.75, round caps, tint via `?attr`/Compose tint).
- `tools/build-plugin.mjs` → concatenates glyphs + plugin source into `design/figma-plugin/dist/code.js`; manifest `main` points there.

## Execution order
1. Scaffold: settings/build-logic/catalog, modules, manifest per `.claude/rules/launcher-manifest.md`; empty HomeActivity builds and installs; set as home on S23.
2. Glyph pipeline (scripts above) + `:core:icons` mapping for the 42 apps.
3. Design tokens in `:core:designsystem` mirroring Figma variables (`GLASS_COLORS`/`GLASS_FLOATS` values).
4. Engine spike → decide implementation; static GlassSurface over cached blurred wallpaper.
5. Reactive layer: EffectsPolicy, TiltSource, WallpaperPalette + tint solver, GlassMotion, press/stretch modifiers; Glass Lab.
6. Home feature: wallpaper + parallax, pager, grid, dock, indicator, search pill, context menu, launch animation.
7. Docs: add "Living glass" section + spike result to docs/architecture.md; extend `.claude/rules/glass-rendering.md` with the draw-phase-only and idle/battery rules. Commit per step (with user OK).

## Verification
- `./gradlew testDebugUnitTest` — EffectsPolicy transitions (interaction → idle 3 s → sensor off; power-save/thermal → Saver), tint solver contrast ≥ 4.5:1 across luminance 0–1, IconMapper fallbacks, AppRepository with fake LauncherApps.
- `./gradlew lintDebug`, `assembleDebug`, `installDebug`; `adb shell cmd package set-home-activity com.sabertheme.launcher/.HomeActivity`.
- On the S23: tilt → rim highlight + parallax move; press icon → squish, bloom, haptic; swipe pages → spring/rubber-band; long-press → glass menu morphs in; light/dark wallpaper → text stays legible.
- Battery/perf: `adb shell dumpsys sensorservice` shows listener removed after 3 s idle and under Power Saving; `adb shell dumpsys gfxinfo com.sabertheme.launcher` p90 < 8 ms, jank < 1 %; bounded logcat via the CLAUDE.md command.
