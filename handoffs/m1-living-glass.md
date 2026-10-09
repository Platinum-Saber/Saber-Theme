# Handoff: Milestone 1 — living-glass engine + home

**Goal:** Build M1 of the Saber-Theme launcher: Gradle scaffold, reactive liquid-glass `GlassSurface`, own wallpaper, home pager with app grid + dock, installable as default home on the S23.

## Decisions
- Launcher draws its own wallpaper (bundled Aurora Dawn/Night), so glass can blur and refract it.
- Glass reacts to touch, device tilt, motion physics and its backdrop (adaptive tint for ≥4.5:1 contrast).
- Battery: effects idle out after 3 s and turn off on Power Saving, thermal throttling or "Remove animations"; user intensity slider.
- Sensor and animation values are read only in draw or graphicsLayer lambdas, never in composition.
- minSdk 33 (AGSL), compile/target 36, applicationId `com.sabertheme.launcher`, Hilt, DataStore, version catalog, `build-logic` convention plugins.
- Engine spike (own AGSL shader vs Kyant `backdrop` lib) decides the glass implementation; the `GlassSurface` API stays fixed either way.
- Widgets: native Compose glass widgets plus Glance exports (no blur, more opaque fallback). Icons: built in plus a standalone ADW/Nova icon-pack APK. Widgets/drawer/settings are M2+.
- Glass token values = `GLASS_COLORS` / `GLASS_FLOATS` / `GLANCE_ALPHA` in the plugin. User approved the current look.
- Figma is on the Starter plan: MCP calls are capped; change designs via the local plugin, which the user runs in Figma desktop.

## Files
- `C:\Users\User\.claude\plans\read-the-claude-md-and-toasty-blossom.md`: approved M1 plan (modules, reactive architecture, steps, verification).
- `docs/architecture.md`: module graph, glass pipeline, widget/icon systems. Needs a "Living glass" section and the spike result in step 7.
- `.claude/rules/glass-rendering.md`: glass constraints (path-scoped). Extend in step 7.
- `.claude/rules/launcher-manifest.md`: HOME intent, `singleTask`, LauncherApps, DataStore rules.
- `design/figma-plugin/code.js`: Figma builder; also the current source of glyph SVG data (`APPS`, `UI`) and the glass tokens.
- `design/figma-plugin/manifest.json`, `README.md`: how the user runs the plugin.
- Figma file: https://www.figma.com/design/SN5PAzoJmPaZTz9UdzJ0uu (final per user).

## State
- Done and approved: architecture doc, Figma designs (components, 9 widget sets, 63 glyphs, 16 screens).
- Uncommitted: `docs/`, `.claude/rules/glass-rendering.md`, the `CLAUDE.md` link line, `design/figma-plugin/`, this handoff. User hasn't approved committing; ask first.
- No Gradle project or Kotlin code exists yet. Nothing has been built or run on the device.

## Next step
Ask whether to commit the pending files. Then do plan step 1: create `settings.gradle.kts`, `build-logic`, `gradle/libs.versions.toml` and the M1 modules, plus a `HomeActivity` that follows the manifest rules. Then `./gradlew assembleDebug` / `installDebug` with `JAVA_HOME` set as in CLAUDE.md, and `adb shell cmd package set-home-activity com.sabertheme.launcher/.HomeActivity`.
